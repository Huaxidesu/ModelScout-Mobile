package com.modelscout.mobile.keyhub.checker

import com.modelscout.mobile.keyhub.AbortFlag
import com.modelscout.mobile.keyhub.KeyClassifier
import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.PlatformRegistry
import com.modelscout.mobile.keyhub.model.CheckOutcome
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 检测引擎。单 Key 重测与整批共用同一入口。
 * 并发用有界 worker 池；中止是协作式的，在阶段边界检查。
 */
class KeyCheckEngine(
    private val checker: KeyChecker,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _events = MutableSharedFlow<KeyHubEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<KeyHubEvent> = _events.asSharedFlow()

    /** 单 Key 检测。 */
    suspend fun checkOne(record: KeyRecord, abort: AbortFlag = AbortFlag()): KeyRecord {
        if (abort.isAborted) return record

        val spec = PlatformRegistry.spec(record.platform)
        if (!spec.supportsDetection) {
            // 仅存储平台：保持 ⏳ 待检测
            _events.emit(KeyHubEvent.KeyChecked(record.id, KeyStatus.PENDING))
            return record
        }

        val outcome = checker.check(record)
        val status = KeyClassifier.classify(outcome)
        val balance = outcome.balance
        val updated = record.copy(
            lastStatus = status,
            lastBalance = balance?.amount,
            lastCurrency = balance?.currency,
            lastUnlimited = balance?.unlimited == true,
            lastCheckedAt = clock(),
        )
        _events.emit(KeyHubEvent.KeyChecked(record.id, status, balance, outcome.errorKind))
        return updated
    }

    /**
     * 批量检测。每完成一个就回调 [onResult]，进度事件同步下发。
     * 单一数据源：调用方把 [onResult] 的结果写回唯一那份列表即可。
     */
    suspend fun checkAll(
        records: List<KeyRecord>,
        concurrency: Int = KeyHubConfig.DEFAULT_CONCURRENCY,
        abort: AbortFlag = AbortFlag(),
        onResult: (KeyRecord) -> Unit = {},
    ): BatchSummary {
        val total = records.size
        if (total == 0) {
            val empty = BatchSummary()
            _events.emit(KeyHubEvent.BatchFinished(empty))
            return empty
        }

        val workers = KeyHubConfig.effectiveConcurrency(concurrency)
        val checked = AtomicInteger(0)
        val results = Collections.synchronizedList(mutableListOf<KeyRecord>())

        _events.emit(KeyHubEvent.BatchProgress(0, total))

        coroutineScope {
            val channel = Channel<KeyRecord>(Channel.UNLIMITED)
            val jobs = List(workers) {
                launch {
                    for (record in channel) {
                        if (abort.isAborted) break
                        val updated = checkOne(record, abort)
                        results += updated
                        onResult(updated)
                        val done = checked.incrementAndGet()
                        _events.emit(KeyHubEvent.BatchProgress(done, total))
                    }
                }
            }
            for (record in records) {
                if (abort.isAborted) break
                channel.send(record)
            }
            channel.close()
            jobs.forEach { it.join() }
        }

        val ordered = records.mapNotNull { original ->
            results.firstOrNull { it.id == original.id }
        }
        val summary = BatchSummary.of(ordered, cancelled = abort.isAborted)
        _events.emit(KeyHubEvent.BatchFinished(summary))
        return summary
    }
}
