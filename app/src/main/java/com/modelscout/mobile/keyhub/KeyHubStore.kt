package com.modelscout.mobile.keyhub

import com.modelscout.mobile.keyhub.checker.BatchSummary
import com.modelscout.mobile.keyhub.checker.KeyCheckEngine
import com.modelscout.mobile.keyhub.detect.ExtractedKey
import com.modelscout.mobile.keyhub.model.HistoryPoint
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import com.modelscout.mobile.keyhub.security.KeyMasker
import com.modelscout.mobile.keyhub.storage.KeyVault
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AddResult(
    val added: Int = 0,
    val skippedExisting: Int = 0,
    val skippedDuplicates: Int = 0,
) {
    val total: Int get() = added + skippedExisting + skippedDuplicates
}

/**
 * KeyHub 的**单一数据源**。列表与历史都只有这一份 StateFlow，UI 派生显示，不另存副本。
 * 检测结果由引擎算好回写，进度由引擎下发（沿用「界面永远 0/0」的教训）。
 */
class KeyHubStore(
    private val vault: KeyVault,
    private val engine: KeyCheckEngine,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 「本地保存」开关的当前值，由 ViewModel 跟随偏好流同步。 */
    @Volatile
    var persistEnabled: Boolean = false

    private val _records = MutableStateFlow<List<KeyRecord>>(emptyList())
    val records: StateFlow<List<KeyRecord>> = _records.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryPoint>>(emptyList())
    val history: StateFlow<List<HistoryPoint>> = _history.asStateFlow()

    /** 引擎事件流（进度 / 单条完成 / 批次结束），UI 只读。 */
    val events get() = engine.events

    // ------------------------------------------------------------ 生命周期

    /** 开关开启时把磁盘上的数据读回内存。 */
    fun restore() {
        if (!persistEnabled) return
        _records.value = vault.loadKeys()
        _history.value = vault.loadHistory()
    }

    /** 开关关闭：删除两个加密文件（内存保留到进程结束）。 */
    fun onPersistDisabled() {
        vault.clearAll()
    }

    /** 清除全部数据（双确认后调用）。 */
    fun clearAll() {
        vault.clearAll()
        _records.value = emptyList()
        _history.value = emptyList()
    }

    // ------------------------------------------------------------ 增删改

    fun add(
        extracted: List<ExtractedKey>,
        /** 「手动指定平台」模式入库时为 true：重测不会被自动识别覆盖。 */
        overridden: Boolean = false,
        customBaseUrl: String? = null,
        customBalanceEndpoint: String? = null,
    ): AddResult {
        val existing = _records.value.map { it.rawKey }.toHashSet()
        val fresh = mutableListOf<KeyRecord>()
        var skippedExisting = 0

        extracted.forEach { item ->
            if (!existing.add(item.rawKey)) {
                skippedExisting++
                return@forEach
            }
            fresh += KeyRecord(
                id = UUID.randomUUID().toString(),
                rawKey = item.rawKey,
                label = item.label,
                platform = item.platform,
                platformOverridden = overridden,
                addedAt = clock(),
                // 五态里的「⏳ 待检测」只是初始态，不算检测结论
                lastStatus = KeyStatus.PENDING,
                customBaseUrl = customBaseUrl?.takeIf { v -> v.isNotBlank() },
                customBalanceEndpoint = customBalanceEndpoint?.takeIf { v -> v.isNotBlank() },
            )
        }

        if (fresh.isNotEmpty()) {
            _records.update { it + fresh }
            persistKeys()
        }
        return AddResult(added = fresh.size, skippedExisting = skippedExisting)
    }

    fun remove(id: String) {
        _records.update { list -> list.filterNot { it.id == id } }
        persistKeys()
    }

    fun updateLabel(id: String, label: String?) {
        mutate(id) { it.copy(label = label?.takeIf { v -> v.isNotBlank() }) }
    }

    /** 手动改平台：置 platformOverridden，重测即走新平台端点（决策 3）。 */
    fun updatePlatform(
        id: String,
        platform: Platform,
        customBaseUrl: String? = null,
        customBalanceEndpoint: String? = null,
    ) {
        mutate(id) { it.withPlatform(platform, customBaseUrl, customBalanceEndpoint) }
    }

    private fun mutate(id: String, transform: (KeyRecord) -> KeyRecord) {
        _records.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persistKeys()
    }

    // ------------------------------------------------------------ 批量编辑

    /**
     * 批量改平台。返回真正被改动的条数（[ids] 里不存在的会被忽略）。
     * 与单条版本同样置 `platformOverridden` 并作废旧结论。
     */
    fun updatePlatformBulk(
        ids: Collection<String>,
        platform: Platform,
        customBaseUrl: String? = null,
        customBalanceEndpoint: String? = null,
    ): Int = mutateBulk(ids) { it.withPlatform(platform, customBaseUrl, customBalanceEndpoint) }

    /** 批量改标签。[label] 为 null / 空白表示清除标签。 */
    fun updateLabelBulk(ids: Collection<String>, label: String?): Int =
        mutateBulk(ids) { it.copy(label = label?.takeIf { v -> v.isNotBlank() }) }

    /** 批量删除。返回删掉的条数。 */
    fun removeBulk(ids: Collection<String>): Int {
        val targets = ids.toHashSet()
        if (targets.isEmpty()) return 0
        val before = _records.value.size
        _records.update { list -> list.filterNot { it.id in targets } }
        val removed = before - _records.value.size
        if (removed > 0) persistKeys()
        return removed
    }

    /** 批量变换：一次 update + 一次落盘，避免 N 次写文件。 */
    private fun mutateBulk(ids: Collection<String>, transform: (KeyRecord) -> KeyRecord): Int {
        val targets = ids.toHashSet()
        if (targets.isEmpty()) return 0
        var changed = 0
        _records.update { list ->
            list.map { record ->
                if (record.id in targets) {
                    changed++
                    transform(record)
                } else {
                    record
                }
            }
        }
        if (changed > 0) persistKeys()
        return changed
    }

    /** 改平台后的统一处理：置手动标记 + 作废旧结论。单条 / 批量共用，避免两处走样。 */
    private fun KeyRecord.withPlatform(
        platform: Platform,
        customBaseUrl: String?,
        customBalanceEndpoint: String?,
    ): KeyRecord = copy(
        platform = platform,
        platformOverridden = true,
        customBaseUrl = customBaseUrl?.takeIf { v -> v.isNotBlank() },
        customBalanceEndpoint = customBalanceEndpoint?.takeIf { v -> v.isNotBlank() },
        lastStatus = KeyStatus.PENDING,
        lastBalance = null,
        lastCurrency = null,
        lastUnlimited = false,
        lastCheckedAt = null,
    )

    // ------------------------------------------------------------ 检测

    suspend fun checkOne(id: String, abort: AbortFlag = AbortFlag()): KeyRecord? {
        val record = _records.value.firstOrNull { it.id == id } ?: return null
        val updated = engine.checkOne(record, abort)
        applyResults(listOf(updated))
        return updated
    }

    suspend fun checkAll(
        concurrency: Int = KeyHubConfig.DEFAULT_CONCURRENCY,
        abort: AbortFlag = AbortFlag(),
        only: List<String>? = null,
    ): BatchSummary {
        val targets = _records.value.filter { only == null || it.id in only }
        val summary = engine.checkAll(targets, concurrency, abort) { updated ->
            applyResults(listOf(updated))
        }
        return summary
    }

    /** 把检测结果写回唯一那份列表，并追加历史快照。 */
    private fun applyResults(updated: List<KeyRecord>) {
        if (updated.isEmpty()) return
        val byId = updated.associateBy { it.id }
        _records.update { list -> list.map { byId[it.id] ?: it } }

        val points = updated.mapNotNull { record ->
            val ts = record.lastCheckedAt ?: return@mapNotNull null
            HistoryPoint(
                fp = KeyMasker.fingerprint(record.rawKey),
                ts = ts,
                status = record.lastStatus,
                balance = record.lastBalance,
                currency = record.lastCurrency,
            )
        }
        if (points.isNotEmpty()) {
            _history.value = _history.value + points
            if (persistEnabled) {
                _history.value = vault.appendHistory(points)
            }
        }
        persistKeys()
    }

    fun historyFor(record: KeyRecord): List<HistoryPoint> {
        val fp = KeyMasker.fingerprint(record.rawKey)
        return _history.value.filter { it.fp == fp }.sortedBy { it.ts }
    }

    // ------------------------------------------------------------ 持久化

    private fun persistKeys() {
        if (!persistEnabled) return
        runCatching { vault.saveKeys(_records.value) }
    }
}
