package com.modelscout.mobile.core.engine

import com.modelscout.mobile.core.Constants
import com.modelscout.mobile.core.dict.ModelParamDictionary
import com.modelscout.mobile.core.model.AppError
import com.modelscout.mobile.core.model.Capability
import com.modelscout.mobile.core.model.ErrorClassifier
import com.modelscout.mobile.core.model.ErrorKind
import com.modelscout.mobile.core.model.LimitSource
import com.modelscout.mobile.core.model.ModelEntry
import com.modelscout.mobile.core.model.ModelResult
import com.modelscout.mobile.core.model.Stage
import com.modelscout.mobile.core.net.HttpClients
import com.modelscout.mobile.core.net.ModelsFetcher
import com.modelscout.mobile.core.net.ProbeClient
import com.modelscout.mobile.core.net.ProbeRequests
import com.modelscout.mobile.core.net.RawResponse
import com.modelscout.mobile.core.net.UrlNormalizer
import com.modelscout.mobile.core.parse.CapabilityJudge
import com.modelscout.mobile.core.parse.LimitParser
import com.modelscout.mobile.core.parse.ReasoningHeuristics
import com.modelscout.mobile.core.security.Redactor
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 计划书 2.8 引擎行为。
 * 单模型流水线：工具 -> 视觉 -> 推理（复用响应）-> 上限（字典优先）。
 * Semaphore 语义用有界 worker 池实现；进度公式 percent = (已完成 + 当前阶段进度) * 100 / 总数。
 */
class ScanEngine(
    private val dictionary: ModelParamDictionary = ModelParamDictionary.EMPTY,
    private val clientFactory: (Long) -> OkHttpClient = { HttpClients.create(it) },
) {

    suspend fun run(
        config: ScanConfig,
        abort: AbortFlag = AbortFlag(),
        onLog: (String) -> Unit = {},
        onState: (ScanState) -> Unit = {},
    ): ScanOutcome = ScanSession(config, abort, onLog, onState, dictionary, clientFactory).execute()
}

internal class ScanSession(
    private val config: ScanConfig,
    private val abort: AbortFlag,
    private val onLog: (String) -> Unit,
    private val onState: (ScanState) -> Unit,
    private val dictionary: ModelParamDictionary,
    private val clientFactory: (Long) -> OkHttpClient,
) {

    private val lock = Any()

    /** 唯一结果源。completed / results / 进度都从这里派生。 */
    private val store = LinkedHashMap<String, ModelResult>()

    /** 在途模型的阶段进度（0.2/0.4/0.6/0.8/1.0）。 */
    private val inflight = HashMap<String, Double>()

    private val fatal = AtomicReference<AppError?>(null)
    private var total: Int = 0

    private lateinit var probeClient: ProbeClient

    suspend fun execute(): ScanOutcome {
        val baseUrl = UrlNormalizer.normalizeBaseUrl(config.baseUrl)
        if (baseUrl.isBlank()) {
            return fail(ErrorKind.BASE_URL_INVALID)
        }
        val timeout = Constants.effectiveTimeoutSeconds(config.timeoutSeconds)
        val concurrency = Constants.effectiveConcurrency(config.concurrency)

        log("Base URL: $baseUrl")
        log("并发 $concurrency，超时 ${timeout}s，探测上限=${config.probeLimit}")

        // ---------------- 1. 拉取 /models ----------------
        val modelsClient = clientFactory(Constants.modelsTimeoutSeconds(timeout))
        val fetchResult = withContext(Dispatchers.IO) {
            ModelsFetcher(modelsClient, baseUrl, config.apiKey).fetch()
        }
        fetchResult.error?.let { error ->
            log("拉取 /models 失败：${error.message}")
            onState(ScanState(total = 0, running = false, fatalError = error))
            return ScanOutcome(total = 0, fatalError = error)
        }

        val filter = config.filter.trim()
        val models = fetchResult.models
            .filter { filter.isEmpty() || it.id.contains(filter, ignoreCase = true) }

        total = models.size
        log("/models 返回 ${fetchResult.models.size} 个模型" + if (filter.isNotEmpty()) "，筛选后 ${models.size} 个" else "")

        // 先把 total 抛给 UI，避免出现 0/0
        emit(Stage.TOOL, null)

        if (models.isEmpty()) {
            log("没有可扫描的模型，结束。")
            onState(ScanState(total = 0, completed = 0, percent = 0, running = false, results = emptyList()))
            return ScanOutcome(total = 0)
        }

        probeClient = ProbeClient(clientFactory(timeout), baseUrl, config.apiKey)

        // ---------------- 2. 有界调度扫描 ----------------
        coroutineScope {
            val channel = Channel<ModelEntry>(Channel.UNLIMITED)
            val workers = List(concurrency) {
                launch(Dispatchers.IO) {
                    for (entry in channel) {
                        if (abort.isAborted || fatal.get() != null) break
                        processModel(entry)
                    }
                }
            }
            for (model in models) {
                if (abort.isAborted || fatal.get() != null) break
                channel.send(model)
            }
            channel.close()
            workers.forEach { it.join() }
        }

        val outcome = snapshot(fatal.get())
        val finalState = ScanState(
            total = total,
            completed = store.size,
            percent = outcomePercent(),
            currentModel = null,
            stage = Stage.DONE,
            running = false,
            fatalError = fatal.get(),
            results = snapshotResults(),
        )
        onState(finalState)

        when {
            abort.isAborted -> log("已中止，完成 ${store.size}/$total。")
            fatal.get() != null -> log("端点级错误，已终止：${fatal.get()!!.message}")
            else -> log("扫描完成：${store.size}/$total。")
        }
        return outcome
    }

    // ------------------------------------------------------------ 单模型流水线

    private suspend fun processModel(entry: ModelEntry) {
        var result = ModelResult(id = entry.id, displayName = entry.displayName)
        var reasoningFromBody = false

        // ---- 阶段 1：工具（声明优先；仅当推理还没判定时才发探测拿信号）----
        beginStage(entry.id, Stage.TOOL)
        val reasoningSettled = entry.declaredReasoning == true ||
            ReasoningHeuristics.looksLikeReasoning(entry.id)
        val needToolProbe = entry.declaredTool != true || !reasoningSettled

        if (!needToolProbe) {
            result = result.copy(tool = Capability.SUPPORTED)
            log("[${entry.id}] 工具：网关声明支持，且推理已判定，跳过探测")
        } else {
            val response = probeClient.postChatCompletions(ProbeRequests.toolProbe(entry.id))
            fatalIfEndpointLevel(response, entry.id)?.let { return }
            result = if (entry.declaredTool == true) {
                log("[${entry.id}] 工具：网关声明支持（探测仅用于取推理信号）HTTP ${response.code}")
                result.copy(tool = Capability.SUPPORTED)
            } else {
                val judged = CapabilityJudge.judge(response.code, response.body, vision = false)
                log("[${entry.id}] 工具探测 -> HTTP ${response.code} => $judged")
                result.copy(tool = judged)
            }
            reasoningFromBody = CapabilityJudge.hasReasoningContent(response.body)
            if (!response.isSuccess) logUpstream(entry.id, "工具探测", response)
        }
        if (stopNow()) return

        // ---- 阶段 2：视觉（声明优先）----
        beginStage(entry.id, Stage.VISION)
        when (entry.declaredVision) {
            true -> {
                result = result.copy(vision = Capability.SUPPORTED)
                log("[${entry.id}] 视觉：网关声明支持，跳过探测")
            }
            false -> {
                result = result.copy(vision = Capability.UNSUPPORTED)
                log("[${entry.id}] 视觉：网关声明的输入模态不含 image，判定不支持")
            }
            null -> {
                val response = probeClient.postChatCompletions(ProbeRequests.visionProbe(entry.id))
                fatalIfEndpointLevel(response, entry.id)?.let { return }
                result = result.copy(vision = CapabilityJudge.judge(response.code, response.body, vision = true))
                log("[${entry.id}] 视觉探测 -> HTTP ${response.code} => ${result.vision}")
                if (!response.isSuccess) logUpstream(entry.id, "视觉探测", response)
            }
        }
        if (stopNow()) return

        // ---- 阶段 3：推理（复用上一步响应，不发新请求）----
        beginStage(entry.id, Stage.REASONING)
        val reasoning = when {
            entry.declaredReasoning == true -> Capability.SUPPORTED
            ReasoningHeuristics.looksLikeReasoning(entry.id) -> Capability.SUPPORTED
            reasoningFromBody -> Capability.SUPPORTED
            else -> Capability.UNKNOWN
        }
        result = result.copy(reasoning = reasoning)
        log("[${entry.id}] 推理：$reasoning")

        // ---- 阶段 4：上限（声明 -> 字典 -> 探测）----
        beginStage(entry.id, Stage.LIMIT)
        result = resolveLimits(entry, result)
        if (stopNow()) return

        finishModel(result)
    }

    private suspend fun resolveLimits(entry: ModelEntry, result: ModelResult): ModelResult {
        // 1) 接口声明
        if (entry.declaredContext != null || entry.declaredOutput != null) {
            log("[${entry.id}] 上限：接口声明 context=${entry.declaredContext} output=${entry.declaredOutput}")
            return result.withLimits(entry.declaredContext, entry.declaredOutput, LimitSource.DECLARED)
        }
        // 2) 内置字典
        dictionary.lookup(entry.id)?.let { hit ->
            log("[${entry.id}] 上限：字典命中 ${hit.match} context=${hit.context} output=${hit.output}")
            return result.withLimits(hit.context, hit.output, LimitSource.DICTIONARY)
        }
        // 3) 探测
        if (!config.probeLimit) {
            log("[${entry.id}] 上限：未开启探测，跳过")
            return result
        }
        val response = probeClient.postChatCompletions(ProbeRequests.limitProbe(entry.id))
        fatalIfEndpointLevel(response, entry.id)?.let { return result }
        if (response.isSuccess) {
            log("[${entry.id}] 上限：端点不校验 max_tokens（2xx），标记为手动")
            return result.withLimits(null, null, LimitSource.MANUAL, "端点未校验 max_tokens，请手动填写")
        }
        val parsed = LimitParser.parse(response.body)
        logUpstream(entry.id, "上限探测", response)
        return if (parsed.isEmpty) {
            log("[${entry.id}] 上限：报错文本无法解析，保持未知")
            result.withLimits(null, null, null, "未能从报错中解析出上限")
        } else {
            log("[${entry.id}] 上限：实测 context=${parsed.context} output=${parsed.output}")
            result.withLimits(parsed.context, parsed.output, LimitSource.MEASURED)
        }
    }

    // ------------------------------------------------------------ 端点级错误

    /** 返回非 null 表示已抛出端点级致命错误，调用方应立即 return。 */
    private fun fatalIfEndpointLevel(response: RawResponse, modelId: String): AppError? {
        val error = response.error ?: run {
            if (response.code == 401 || response.code == 403 || response.code == 404) {
                ErrorClassifier.classify(response.code, response.body)
            } else {
                null
            }
        } ?: return null
        if (!error.kind.endpointLevel) return null

        log("[${modelId}] 端点级错误：${error.message}")
        raiseFatal(error)
        return error
    }

    private fun raiseFatal(error: AppError) {
        if (fatal.compareAndSet(null, error)) {
            runCatching { probeClient.cancelAll() }
        }
    }

    private fun fail(kind: ErrorKind): ScanOutcome {
        val error = AppError(kind)
        log(error.message)
        onState(ScanState(total = 0, running = false, fatalError = error))
        return ScanOutcome(total = 0, fatalError = error)
    }

    // ------------------------------------------------------------ 状态与进度

    private fun stopNow(): Boolean = abort.isAborted || fatal.get() != null

    private fun beginStage(modelId: String, stage: Stage) {
        synchronized(lock) { inflight[modelId] = stage.weight / 100.0 }
        emit(stage, modelId)
    }

    private fun finishModel(result: ModelResult) {
        synchronized(lock) {
            store[result.id] = result
            inflight.remove(result.id)
        }
        emit(Stage.DONE, result.id)
    }

    private fun snapshotResults(): List<ModelResult> = synchronized(lock) { store.values.toList() }

    private fun outcomePercent(): Int = synchronized(lock) { computePercentLocked() }

    private fun computePercentLocked(): Int {
        if (total <= 0) return 0
        val progress = store.size + inflight.values.sum()
        return ((progress * 100.0) / total).toInt().coerceIn(0, 100)
    }

    private fun emit(stage: Stage, current: String?) {
        val state = synchronized(lock) {
            ScanState(
                total = total,
                completed = store.size,
                percent = computePercentLocked(),
                currentModel = current,
                stage = stage,
                running = true,
                fatalError = fatal.get(),
                results = store.values.toList(),
            )
        }
        onState(state)
    }

    private fun snapshot(error: AppError?): ScanOutcome = ScanOutcome(
        results = snapshotResults(),
        total = total,
        fatalError = error,
        aborted = abort.isAborted,
    )

    private fun log(message: String) {
        onLog(Redactor.redact(message, config.apiKey))
    }

    /** 上游文本展示前一律脱敏（计划书 2.7）。 */
    private fun logUpstream(modelId: String, label: String, response: RawResponse) {
        if (response.body.isBlank()) return
        val snippet = response.body.replace(WHITESPACE, " ").trim().take(300)
        log("[$modelId] $label 上游响应：$snippet")
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
