package com.modelscout.mobile.ui

import com.modelscout.mobile.core.Constants
import com.modelscout.mobile.core.engine.AbortFlag
import com.modelscout.mobile.core.engine.ScanConfig
import com.modelscout.mobile.core.extract.ConnectionExtractor
import com.modelscout.mobile.core.gen.DshFormat
import com.modelscout.mobile.core.gen.DshGenerator
import com.modelscout.mobile.core.gen.GenerationInput
import com.modelscout.mobile.core.gen.OpenCodeGenerator
import com.modelscout.mobile.core.gen.ProviderCatalog
import com.modelscout.mobile.core.model.AppError
import com.modelscout.mobile.core.model.LimitSource
import com.modelscout.mobile.core.model.ModelResult
import com.modelscout.mobile.core.model.Stage
import com.modelscout.mobile.core.net.UrlNormalizer
import com.modelscout.mobile.data.AppSettingsStore
import com.modelscout.mobile.di.AppContainer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { CONNECTION, RUN, RESULT, PREVIEW, ABOUT, KEYHUB }

enum class PreviewTarget(val label: String) {
    OPENCODE("OpenCode"),
    DSH("DeepSeekHardness"),
}

enum class KeyMode(val label: String) {
    ENV("{env:VAR} 引用"),
    PLAIN("明文"),
}

data class UiState(
    val screen: Screen = Screen.CONNECTION,
    val previousScreen: Screen = Screen.CONNECTION,
    val baseUrl: String = "",
    val apiKey: String = "",
    val concurrency: Int = Constants.DEFAULT_CONCURRENCY,
    val timeoutSeconds: Long = Constants.DEFAULT_TIMEOUT_SECONDS,
    val probeLimit: Boolean = true,
    val filter: String = "",
    val keyMode: KeyMode = KeyMode.ENV,
    val envVar: String = "DEEPSEEK_API_KEY",

    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val percent: Int = 0,
    val stage: Stage = Stage.TOOL,
    val currentModel: String? = null,
    val results: List<ModelResult> = emptyList(),
    val logs: List<String> = emptyList(),
    val fatalError: AppError? = null,

    val extractorOpen: Boolean = false,
    val extractorText: String = "",
    val extractorResult: ConnectionExtractor.Result? = null,

    val previewTarget: PreviewTarget = PreviewTarget.OPENCODE,
    val dshFormat: DshFormat = DshFormat.JSONC,
    val previewText: String = "",
    val message: String? = null,
) {
    val selectedCount: Int get() = results.count { it.selected }
    val hasResults: Boolean get() = results.isNotEmpty()
}

class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var abortFlag: AbortFlag? = null
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            container.settingsStore.snapshot.collect { snapshot ->
                _state.update { current ->
                    current.copy(
                        baseUrl = current.baseUrl.ifBlank { snapshot.baseUrl },
                        concurrency = snapshot.concurrency,
                        timeoutSeconds = snapshot.timeoutSeconds,
                        probeLimit = snapshot.probeLimit,
                        filter = snapshot.filter,
                        keyMode = if (snapshot.keyMode == AppSettingsStore.KEY_MODE_PLAIN) {
                            KeyMode.PLAIN
                        } else {
                            KeyMode.ENV
                        },
                        dshFormat = runCatching { DshFormat.valueOf(snapshot.dshFormat) }
                            .getOrDefault(DshFormat.JSONC),
                    )
                }
            }
        }
        viewModelScope.launch {
            container.logBuffer.flow.collect { lines -> _state.update { it.copy(logs = lines) } }
        }
    }

    // ------------------------------------------------------------ 连接页

    fun onBaseUrlChange(value: String) = _state.update { it.copy(baseUrl = value) }

    fun onApiKeyChange(value: String) = _state.update { it.copy(apiKey = value) }

    fun onConcurrencyChange(value: Int) = _state.update {
        it.copy(concurrency = Constants.effectiveConcurrency(value))
    }

    fun onTimeoutChange(value: Long) = _state.update {
        it.copy(timeoutSeconds = Constants.effectiveTimeoutSeconds(value))
    }

    fun onProbeLimitChange(value: Boolean) = _state.update { it.copy(probeLimit = value) }

    fun onFilterChange(value: String) = _state.update { it.copy(filter = value) }

    fun applyPreset(preset: GatewayPreset) = _state.update {
        it.copy(baseUrl = preset.baseUrl, envVar = preset.envVar)
    }

    // ------------------------------------------------------------ 文本提取

    fun openExtractor() = _state.update {
        it.copy(extractorOpen = true, extractorText = "", extractorResult = null)
    }

    fun closeExtractor() = _state.update { it.copy(extractorOpen = false) }

    fun onExtractorTextChange(value: String) = _state.update {
        it.copy(extractorText = value, extractorResult = ConnectionExtractor.extract(value))
    }

    fun applyExtraction() {
        val result = _state.value.extractorResult ?: return
        _state.update {
            it.copy(
                baseUrl = result.baseUrl ?: it.baseUrl,
                apiKey = result.apiKey ?: it.apiKey,
                extractorOpen = false,
                message = when {
                    result.hasUrl && result.hasKey -> "已提取 Base URL 与 Key"
                    result.hasUrl -> "已提取 Base URL，未识别到 Key"
                    result.hasKey -> "已提取 Key，未识别到 Base URL"
                    else -> "没有识别到可用的 Base URL 或 Key"
                },
            )
        }
    }

    // ------------------------------------------------------------ 运行

    fun startScan() {
        val s = _state.value
        if (s.running) return
        if (s.baseUrl.isBlank()) {
            _state.update { it.copy(message = "请先填写 Base URL") }
            return
        }

        viewModelScope.launch {
            container.settingsStore.saveConnection(
                baseUrl = s.baseUrl,
                concurrency = s.concurrency,
                timeoutSeconds = s.timeoutSeconds,
                probeLimit = s.probeLimit,
                filter = s.filter,
                keyMode = if (s.keyMode == KeyMode.PLAIN) {
                    AppSettingsStore.KEY_MODE_PLAIN
                } else {
                    AppSettingsStore.KEY_MODE_ENV
                },
            )
        }

        container.logBuffer.clear()
        abortFlag = AbortFlag()
        _state.update {
            it.copy(
                screen = Screen.RUN,
                running = true,
                fatalError = null,
                results = emptyList(),
                total = 0,
                completed = 0,
                percent = 0,
                stage = Stage.TOOL,
                currentModel = null,
            )
        }

        scanJob = viewModelScope.launch {
            val outcome = container.newEngine().run(
                config = ScanConfig(
                    baseUrl = s.baseUrl,
                    apiKey = s.apiKey,
                    concurrency = s.concurrency,
                    timeoutSeconds = s.timeoutSeconds,
                    probeLimit = s.probeLimit,
                    filter = s.filter,
                ),
                abort = abortFlag ?: AbortFlag(),
                onLog = { line -> container.logBuffer.append(line) },
                onState = { scanState ->
                    _state.update {
                        it.copy(
                            total = scanState.total,
                            completed = scanState.completed,
                            percent = scanState.percent,
                            stage = scanState.stage,
                            currentModel = scanState.currentModel,
                            results = scanState.results,
                            fatalError = scanState.fatalError,
                        )
                    }
                },
            )
            _state.update {
                it.copy(
                    running = false,
                    results = outcome.results,
                    total = outcome.total,
                    completed = outcome.results.size,
                    percent = if (outcome.total > 0) 100 else 0,
                    stage = Stage.DONE,
                    currentModel = null,
                    fatalError = outcome.fatalError,
                    message = outcome.fatalError?.message
                        ?: if (outcome.aborted) "已中止" else "扫描完成：${outcome.results.size}/${outcome.total}",
                )
            }
        }
    }

    fun abortScan() {
        abortFlag?.abort()
        scanJob?.cancel()
        _state.update { it.copy(running = false, message = "已中止") }
    }

    fun goTo(screen: Screen) = _state.update {
        if (screen == Screen.ABOUT) {
            it.copy(previousScreen = it.screen.takeIf { s -> s != Screen.ABOUT } ?: it.previousScreen, screen = screen)
        } else {
            it.copy(screen = screen)
        }
    }

    fun closeAbout() = _state.update { it.copy(screen = it.previousScreen) }

    // ------------------------------------------------------------ 结果页

    fun toggleSelected(id: String) = _state.update { s ->
        s.copy(results = s.results.map { if (it.id == id) it.copy(selected = !it.selected) else it })
    }

    fun setAllSelected(selected: Boolean) = _state.update { s ->
        s.copy(results = s.results.map { it.copy(selected = selected) })
    }

    /** 手动改上限：改后标「手动」。 */
    fun updateLimit(id: String, context: Int?, output: Int?) = _state.update { s ->
        s.copy(
            results = s.results.map {
                if (it.id == id) {
                    it.copy(
                        contextLimit = context ?: it.contextLimit,
                        outputLimit = output ?: it.outputLimit,
                        limitSource = LimitSource.MANUAL,
                    )
                } else {
                    it
                }
            },
        )
    }

    // ------------------------------------------------------------ 预览页

    fun onPreviewTargetChange(target: PreviewTarget) {
        _state.update { it.copy(previewTarget = target) }
        regeneratePreview()
    }

    fun onDshFormatChange(format: DshFormat) {
        _state.update { it.copy(dshFormat = format) }
        viewModelScope.launch { container.settingsStore.saveDshFormat(format.name) }
        regeneratePreview()
    }

    fun onKeyModeChange(mode: KeyMode) {
        _state.update { it.copy(keyMode = mode) }
        regeneratePreview()
    }

    fun onEnvVarChange(value: String) {
        _state.update { it.copy(envVar = value) }
    }

    fun onPreviewTextChange(value: String) = _state.update { it.copy(previewText = value) }

    fun regeneratePreview() {
        val s = _state.value
        val selected = s.results.filter { it.selected }
        if (selected.isEmpty()) {
            _state.update { it.copy(previewText = "", message = "请先在结果页勾选模型") }
            return
        }
        val meta = ProviderCatalog.infer(UrlNormalizer.normalizeBaseUrl(s.baseUrl))
        val envVar = s.envVar.ifBlank { meta.key.uppercase().replace('-', '_') + "_API_KEY" }
        val input = GenerationInput(
            providerKey = meta.key,
            providerName = meta.displayName,
            npm = meta.npm,
            baseUrl = UrlNormalizer.normalizeBaseUrl(s.baseUrl),
            apiKeyLiteral = if (s.keyMode == KeyMode.PLAIN) s.apiKey else null,
            envVar = if (s.keyMode == KeyMode.ENV) envVar else null,
            models = selected,
        )
        val text = when (s.previewTarget) {
            PreviewTarget.OPENCODE -> OpenCodeGenerator.generate(input)
            PreviewTarget.DSH -> DshGenerator.generate(input, s.dshFormat)
        }
        _state.update { it.copy(previewText = text) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScanViewModel(container) }
        }
    }
}
