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
import com.modelscout.mobile.keyhub.detect.ExtractedKey
import com.modelscout.mobile.keyhub.detect.ExtractionResult
import com.modelscout.mobile.keyhub.detect.KeyDetector
import com.modelscout.mobile.keyhub.model.Confidence
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

/** 批量入库时的平台来源。 */
enum class AddMode(val label: String) {
    /** 逐条按格式自动识别（现状）。 */
    AUTO("自动识别"),

    /** 整批都用用户选的平台，落库时置 platformOverridden。 */
    MANUAL("手动指定"),
}

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
    val addMode: AddMode = AddMode.AUTO,
    /** 手动指定模式下用户显式选过的平台；null 表示用「整批里最常见的平台」预选。 */
    val addPlatformOverride: Platform? = null,
    val addCustomBaseUrl: String = "",
    val addCustomBalanceEndpoint: String = "",
    val settingsSheetOpen: Boolean = false,

    // ---- 批量编辑（多选） ----
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val batchPlatformDialog: Boolean = false,
    val batchLabelDialog: Boolean = false,
    val batchDeleteDialog: Boolean = false,

    val revealedIds: Set<String> = emptySet(),
    val message: String? = null,
) {
    val visible: List<KeyRecord>
        get() = KeyHubListOps.sort(KeyHubListOps.filter(records, statusFilter, platformFilter), sortMode)

    val detail: KeyRecord? get() = records.firstOrNull { it.id == detailId }

    val isEmpty: Boolean get() = records.isEmpty()

    val addPreview: ExtractionResult get() = KeyDetector.extractKeys(addText)

    /**
     * 手动模式下的预选平台：用户显式选过就用它，否则用整批里最常见的平台
     * （例如整批 16 个 DeepSeek Key → 直接预选 DeepSeek），最后后备「自定义」。
     */
    val addManualPlatform: Platform
        get() = addPlatformOverride
            ?: KeyDetector.dominantPlatform(addPreview.keys)
            ?: Platform.CUSTOM

    /** 真正会入库的条目：手动模式整批覆盖成所选平台。 */
    val addEffectiveKeys: List<ExtractedKey>
        get() = if (addMode == AddMode.AUTO) {
            addPreview.keys
        } else {
            val platform = addManualPlatform
            addPreview.keys.map { it.copy(platform = platform, confidence = Confidence.CERTAIN) }
        }

    /** 当前列表里被选中的记录（按列表顺序，不是点击顺序）。 */
    val selectedRecords: List<KeyRecord> get() = visible.filter { it.id in selectedIds }

    val selectedCount: Int get() = selectedIds.size

    val allSelected: Boolean get() = KeyHubListOps.isAllSelected(records, selectedIds)

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
            store.records.collect { list ->
                _state.update {
                    val pruned = KeyHubListOps.pruneSelection(it.selectedIds, list)
                    it.copy(
                        records = list,
                        selectedIds = pruned,
                        // 列表空了就顺手退出批量模式，否则底部操作栏会一直挂着一个空壳
                        selectionMode = it.selectionMode && list.isNotEmpty(),
                    )
                }
            }
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
        it.copy(statusFilter = if (it.statusFilter == status) null else status).prunedSelection()
    }

    fun setPlatformFilter(platform: Platform?) = _state.update {
        it.copy(platformFilter = if (it.platformFilter == platform) null else platform).prunedSelection()
    }

    /**
     * 改筛选条件后把选中项收敛到新的可见集合。
     * 否则「筛到可用 → 全选 → 换成余额不足」会让批量操作作用在一批看不见的 Key 上。
     */
    private fun KeyHubUiState.prunedSelection(): KeyHubUiState =
        copy(selectedIds = KeyHubListOps.pruneSelection(selectedIds, visible))

    // ------------------------------------------------------------ 添加

    fun openAddSheet() = _state.update {
        it.copy(
            addSheetOpen = true,
            addText = "",
            // 模式保持上次的选择（连着贴几批同一家的 Key 时不用重复点），
            // 但平台预选重置，让它跟着本批内容重新算。
            addPlatformOverride = null,
            addCustomBaseUrl = "",
            addCustomBalanceEndpoint = "",
        )
    }

    fun closeAddSheet() = _state.update { it.copy(addSheetOpen = false) }

    fun onAddTextChange(text: String) = _state.update { it.copy(addText = text) }

    fun setAddMode(mode: AddMode) = _state.update { it.copy(addMode = mode) }

    fun setAddPlatform(platform: Platform) = _state.update { it.copy(addPlatformOverride = platform) }

    fun setAddCustomBaseUrl(value: String) = _state.update { it.copy(addCustomBaseUrl = value) }

    fun setAddCustomBalanceEndpoint(value: String) =
        _state.update { it.copy(addCustomBalanceEndpoint = value) }

    fun confirmAdd() {
        val current = _state.value
        val keys = current.addEffectiveKeys
        if (keys.isEmpty()) {
            _state.update { it.copy(message = "没有识别到可用的 Key") }
            return
        }
        val manual = current.addMode == AddMode.MANUAL
        val result = store.add(
            extracted = keys,
            overridden = manual,
            customBaseUrl = current.addCustomBaseUrl.takeIf { manual },
            customBalanceEndpoint = current.addCustomBalanceEndpoint.takeIf { manual },
        )
        _state.update {
            it.copy(
                addSheetOpen = false,
                addText = "",
                message = buildString {
                    append("已添加 ${result.added} 个")
                    if (manual) append("（手动指定：${current.addManualPlatform.displayName}）")
                    if (current.addPreview.duplicatesSkipped > 0) {
                        append("，本次粘贴内重复跳过 ${current.addPreview.duplicatesSkipped} 个")
                    }
                    if (result.skippedExisting > 0) append("，列表已存在跳过 ${result.skippedExisting} 个")
                },
            )
        }
    }

    // ------------------------------------------------------------ 批量编辑（多选）

    fun toggleSelectionMode() = _state.update {
        if (it.selectionMode) {
            it.copy(selectionMode = false, selectedIds = emptySet())
        } else {
            it.copy(selectionMode = true)
        }
    }

    fun toggleSelect(id: String) = _state.update {
        it.copy(selectedIds = KeyHubListOps.toggleSelection(it.selectedIds, id))
    }

    /** 全选 / 取消全选（作用于当前可见的列表，尊重筛选条件）。 */
    fun toggleSelectAll() = _state.update {
        if (KeyHubListOps.isAllSelected(it.visible, it.selectedIds)) {
            it.copy(selectedIds = emptySet())
        } else {
            it.copy(selectedIds = KeyHubListOps.selectAll(it.visible))
        }
    }

    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }

    fun openBatchPlatform() = openBatchDialog { it.copy(batchPlatformDialog = true) }

    fun openBatchLabel() = openBatchDialog { it.copy(batchLabelDialog = true) }

    fun openBatchDelete() = openBatchDialog { it.copy(batchDeleteDialog = true) }

    fun closeBatchDialogs() = _state.update {
        it.copy(batchPlatformDialog = false, batchLabelDialog = false, batchDeleteDialog = false)
    }

    /** 三个批量弹窗共用同一道「先看有没有选中」的闸门。 */
    private fun openBatchDialog(transform: (KeyHubUiState) -> KeyHubUiState) {
        val current = _state.value
        if (current.selectedIds.isEmpty()) {
            _state.update { it.copy(message = "请先勾选要处理的 Key") }
            return
        }
        _state.update(transform)
    }

    fun applyBatchPlatform(
        platform: Platform,
        customBaseUrl: String?,
        customBalanceEndpoint: String?,
    ) {
        val ids = _state.value.selectedIds
        val changed = store.updatePlatformBulk(ids, platform, customBaseUrl, customBalanceEndpoint)
        _state.update {
            it.copy(
                batchPlatformDialog = false,
                message = "已把 $changed 个 Key 改为 ${platform.displayName}，重新检测将走新端点",
            )
        }
    }

    fun applyBatchLabel(label: String) {
        val ids = _state.value.selectedIds
        val trimmed = label.trim()
        val changed = store.updateLabelBulk(ids, trimmed.ifBlank { null })
        _state.update {
            it.copy(
                batchLabelDialog = false,
                message = if (trimmed.isBlank()) {
                    "已清除 $changed 个 Key 的标签"
                } else {
                    "已把 $changed 个 Key 的标签改为「$trimmed」"
                },
            )
        }
    }

    fun confirmBatchDelete() {
        val ids = _state.value.selectedIds
        val removed = store.removeBulk(ids)
        _state.update {
            it.copy(
                batchDeleteDialog = false,
                selectedIds = emptySet(),
                // 列表被删空时，records 的收集器会把 selectionMode 关掉
                selectionMode = true,
                message = "已删除 $removed 个 Key",
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
        val targets = if (onlyFailed) {
            val ids = _state.value.lastSummary?.failedIds.orEmpty()
            _state.value.records.filter { it.id in ids }
        } else {
            _state.value.records
        }
        startCheck(targets)
    }

    /** 只测勾选的那些（批量编辑里的「检测选中」）。 */
    fun checkSelected() {
        val ids = _state.value.selectedIds
        if (ids.isEmpty()) {
            _state.update { it.copy(message = "请先勾选要检测的 Key") }
            return
        }
        startCheck(_state.value.records.filter { it.id in ids })
    }

    private fun startCheck(targets: List<KeyRecord>) {
        if (_state.value.checking) return
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
