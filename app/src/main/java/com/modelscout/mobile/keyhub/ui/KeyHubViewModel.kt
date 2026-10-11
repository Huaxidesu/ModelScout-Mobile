package com.modelscout.mobile.keyhub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.modelscout.mobile.keyhub.AbortFlag
import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.KeyHubPrefs
import com.modelscout.mobile.keyhub.KeyHubStore
import com.modelscout.mobile.keyhub.PlatformRegistry
import com.modelscout.mobile.keyhub.checker.BatchSummary
import com.modelscout.mobile.keyhub.checker.KeyHubEvent
import com.modelscout.mobile.keyhub.detect.ExtractionResult
import com.modelscout.mobile.keyhub.detect.KeyDetector
import com.modelscout.mobile.keyhub.model.HistoryPoint
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class KeyHubScreen { LIST, DETAIL }

/** 详情页揭示明文后自动回 mask 的时长。 */
private const val REVEAL_TIMEOUT_MS = 20_000L

data class KeyHubUiState(
    val records: List<KeyRecord> = emptyList(),
    val history: List<HistoryPoint> = emptyList(),
    val persistEnabled: Boolean = false,
    val concurrency: Int = KeyHubConfig.DEFAULT_CONCURRENCY,

    val screen: KeyHubScreen = KeyHubScreen.LIST,
    val detailId: String? = null,

    val sortMode: SortMode = SortMode.PLATFORM,
    val statusFilter: KeyStatus? = null,
    val platformFilter: Platform? = null,

    val checking: Boolean = false,
    val progressChecked: Int = 0,
    val progressTotal: Int = 0,
    val lastSummary: BatchSummary? = null,

    val addSheetOpen: Boolean = false,
    val addText: String = "",
    val settingsSheetOpen: Boolean = false,

    val revealedIds: Set<String> = emptySet(),
    val message: String? = null,
) {
    val visible: List<KeyRecord>
        get() = KeyHubListOps.sort(KeyHubListOps.filter(records, statusFilter, platformFilter), sortMode)

    val detail: KeyRecord? get() = records.firstOrNull { it.id == detailId }

    val isEmpty: Boolean get() = records.isEmpty()

    val addPreview: ExtractionResult get() = KeyDetector.extractKeys(addText)

    val canRetryFailed: Boolean get() = lastSummary?.failedIds?.isNotEmpty() == true
}

class KeyHubViewModel(
    private val store: KeyHubStore,
    private val prefs: KeyHubPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(KeyHubUiState())
    val state: StateFlow<KeyHubUiState> = _state.asStateFlow()

    private var abortFlag: AbortFlag? = null
    private var checkJob: Job? = null
    private val revealJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            store.records.collect { list -> _state.update { it.copy(records = list) } }
        }
        viewModelScope.launch {
            store.history.collect { list -> _state.update { it.copy(history = list) } }
        }
        viewModelScope.launch {
            var previous = false
            prefs.persistEnabled.collect { enabled ->
                store.persistEnabled = enabled
                _state.update { it.copy(persistEnabled = enabled) }
                if (enabled && !previous) store.restore()
                previous = enabled
            }
        }
        viewModelScope.launch {
            prefs.concurrency.collect { value -> _state.update { it.copy(concurrency = value) } }
        }
        viewModelScope.launch {
            store.events.collect { event -> onEngineEvent(event) }
        }
    }

    private fun onEngineEvent(event: KeyHubEvent) {
        when (event) {
            is KeyHubEvent.BatchProgress -> _state.update {
                it.copy(progressChecked = event.checked, progressTotal = event.total)
            }
            is KeyHubEvent.BatchFinished -> _state.update {
                it.copy(
                    checking = false,
                    lastSummary = event.summary,
                    progressChecked = event.summary.checked,
                    progressTotal = event.summary.total,
                    message = buildSummaryMessage(event.summary),
                )
            }
            is KeyHubEvent.KeyChecked -> Unit // 结果已由 store 写回唯一列表
        }
    }

    private fun buildSummaryMessage(summary: BatchSummary): String = buildString {
        append("检测完成：可用 ${summary.available}")
        if (summary.insufficient > 0) append(" · 余额不足 ${summary.insufficient}")
        if (summary.invalid > 0) append(" · 已失效 ${summary.invalid}")
        if (summary.failed > 0) append(" · 查询失败 ${summary.failed}")
        if (summary.pending > 0) append(" · 待检测 ${summary.pending}")
        if (summary.cancelled) append("（已取消）")
    }

    // ------------------------------------------------------------ 排序 / 过滤

    fun setSortMode(mode: SortMode) = _state.update { it.copy(sortMode = mode) }

    fun setStatusFilter(status: KeyStatus?) = _state.update {
        it.copy(statusFilter = if (it.statusFilter == status) null else status)
    }

    fun setPlatformFilter(platform: Platform?) = _state.update {
        it.copy(platformFilter = if (it.platformFilter == platform) null else platform)
    }

    // ------------------------------------------------------------ 添加

    fun openAddSheet() = _state.update { it.copy(addSheetOpen = true, addText = "") }

    fun closeAddSheet() = _state.update { it.copy(addSheetOpen = false) }

    fun onAddTextChange(text: String) = _state.update { it.copy(addText = text) }

    fun confirmAdd() {
        val preview = _state.value.addPreview
        if (preview.keys.isEmpty()) {
            _state.update { it.copy(message = "没有识别到可用的 Key") }
            return
        }
        val result = store.add(preview.keys)
        _state.update {
            it.copy(
                addSheetOpen = false,
                addText = "",
                message = buildString {
                    append("已添加 ${result.added} 个")
                    if (preview.duplicatesSkipped > 0) append("，已存在跳过 ${preview.duplicatesSkipped} 个")
                    if (result.skippedExisting > 0) append("，列表已存在跳过 ${result.skippedExisting} 个")
                },
            )
        }
    }

    // ------------------------------------------------------------ 详情

    fun openDetail(id: String) = _state.update { it.copy(screen = KeyHubScreen.DETAIL, detailId = id) }

    fun closeDetail() {
        revealJobs.values.forEach { it.cancel() }
        revealJobs.clear()
        _state.update { it.copy(screen = KeyHubScreen.LIST, detailId = null, revealedIds = emptySet()) }
    }

    /** 揭示明文，20 秒后自动回 mask。 */
    fun toggleReveal(id: String) {
        val revealed = id in _state.value.revealedIds
        if (revealed) {
            revealJobs.remove(id)?.cancel()
            _state.update { it.copy(revealedIds = it.revealedIds - id) }
            return
        }
        _state.update { it.copy(revealedIds = it.revealedIds + id) }
        revealJobs[id]?.cancel()
        revealJobs[id] = viewModelScope.launch {
            delay(REVEAL_TIMEOUT_MS)
            _state.update { it.copy(revealedIds = it.revealedIds - id) }
        }
    }

    fun updateLabel(id: String, label: String) = store.updateLabel(id, label)

    fun updatePlatform(
        id: String,
        platform: Platform,
        customBaseUrl: String?,
        customBalanceEndpoint: String?,
    ) {
        store.updatePlatform(id, platform, customBaseUrl, customBalanceEndpoint)
        _state.update { it.copy(message = "已改为 ${platform.displayName}，重新检测将走新端点") }
    }

    fun deleteKey(id: String) {
        store.remove(id)
        _state.update { it.copy(message = "已删除") }
        if (_state.value.detailId == id) closeDetail()
    }

    fun historyFor(record: KeyRecord): List<HistoryPoint> = store.historyFor(record)

    // ------------------------------------------------------------ 检测

    fun checkAll(onlyFailed: Boolean = false) {
        if (_state.value.checking) return
        val targets = if (onlyFailed) {
            val ids = _state.value.lastSummary?.failedIds.orEmpty()
            _state.value.records.filter { it.id in ids }
        } else {
            _state.value.records
        }
        if (targets.isEmpty()) {
            _state.update { it.copy(message = "没有需要检测的 Key") }
            return
        }

        val abort = AbortFlag()
        abortFlag = abort
        _state.update {
            it.copy(
                checking = true,
                progressChecked = 0,
                progressTotal = targets.size,
                message = null,
            )
        }
        checkJob = viewModelScope.launch {
            store.checkAll(
                concurrency = _state.value.concurrency,
                abort = abort,
                only = targets.map { it.id },
            )
        }
    }

    fun checkOne(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(message = "检测中…") }
            store.checkOne(id, abortFlag ?: AbortFlag())
        }
    }

    fun cancelCheck() {
        abortFlag?.abort()
        checkJob?.cancel()
        _state.update { it.copy(checking = false, message = "已取消检测") }
    }

    // ------------------------------------------------------------ 设置

    fun openSettings() = _state.update { it.copy(settingsSheetOpen = true) }

    fun closeSettings() = _state.update { it.copy(settingsSheetOpen = false) }

    fun setPersistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setPersistEnabled(enabled)
            if (!enabled) store.onPersistDisabled()
            _state.update {
                it.copy(
                    message = if (enabled) {
                        "已开启本地保存（Keystore 加密）"
                    } else {
                        "已关闭并删除本地加密文件"
                    },
                )
            }
        }
    }

    fun setConcurrency(value: Int) {
        viewModelScope.launch { prefs.setConcurrency(value) }
    }

    fun clearAllData() {
        store.clearAll()
        _state.update { it.copy(message = "已清除全部数据", screen = KeyHubScreen.LIST, detailId = null) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** 供 UI 取明文（复制 / 揭示用）。 */
    fun rawKeyOf(id: String): String? = _state.value.records.firstOrNull { it.id == id }?.rawKey

    fun platforms(): List<Platform> = PlatformRegistry.selectablePlatforms

    companion object {
        fun factory(store: KeyHubStore, prefs: KeyHubPrefs): ViewModelProvider.Factory = viewModelFactory {
            initializer { KeyHubViewModel(store, prefs) }
        }
    }
}
