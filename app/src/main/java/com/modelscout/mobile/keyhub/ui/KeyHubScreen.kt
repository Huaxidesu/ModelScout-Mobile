package com.modelscout.mobile.keyhub.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.modelscout.mobile.keyhub.KeyHubPrefs
import com.modelscout.mobile.keyhub.KeyHubStore
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform

/** KeyHub 入口。作为既有导航的一个目的地，内部自己管「列表 / 详情」两级。 */
@Composable
fun KeyHubRoot(store: KeyHubStore, prefs: KeyHubPrefs) {
    val vm: KeyHubViewModel = viewModel(factory = KeyHubViewModel.factory(store, prefs))
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                KeyHubScreen.LIST -> KeyHubListPage(state, vm)
                KeyHubScreen.DETAIL -> KeyHubDetailPage(state, vm)
            }
        }
    }

    if (state.addSheetOpen) KeyHubAddSheet(state, vm)
    if (state.settingsSheetOpen) KeyHubSettingsSheet(state, vm)
}

// ---------------------------------------------------------------- 列表页

@Composable
private fun KeyHubListPage(state: KeyHubUiState, vm: KeyHubViewModel) {
    var sortExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (state.selectionMode) {
            SelectionToolbar(state, vm)
        } else {
            MainToolbar(state, vm, sortExpanded, onSortExpanded = { sortExpanded = it })
        }

        // 批量进度
        if (state.checking) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${state.progressChecked} / ${state.progressTotal}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(onClick = { vm.cancelCheck() }) { Text("取消", fontSize = 12.sp) }
                }
                LinearProgressIndicator(
                    progress = {
                        if (state.progressTotal <= 0) {
                            0f
                        } else {
                            state.progressChecked.toFloat() / state.progressTotal
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (state.canRetryFailed) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("上次有 ${state.lastSummary?.failed} 个查询失败", fontSize = 12.sp)
                TextButton(onClick = { vm.checkAll(onlyFailed = true) }) { Text("仅重测失败", fontSize = 12.sp) }
            }
        }

        // 过滤 chips
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                FilterChip(
                    selected = state.statusFilter == null,
                    onClick = { vm.setStatusFilter(null) },
                    label = { Text("全部", fontSize = 12.sp) },
                )
            }
            items(KeyStatus.entries.toList()) { status ->
                FilterChip(
                    selected = state.statusFilter == status,
                    onClick = { vm.setStatusFilter(status) },
                    label = { Text("${status.symbol}${status.label}", fontSize = 12.sp) },
                )
            }
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                FilterChip(
                    selected = state.platformFilter == null,
                    onClick = { vm.setPlatformFilter(null) },
                    label = { Text("全部平台", fontSize = 12.sp) },
                )
            }
            items(vm.platforms()) { platform ->
                FilterChip(
                    selected = state.platformFilter == platform,
                    onClick = { vm.setPlatformFilter(platform) },
                    label = { Text(platform.displayName, fontSize = 12.sp) },
                )
            }
        }

        if (state.isEmpty) {
            EmptyState(onAdd = { vm.openAddSheet() })
            return@Column
        }

        val groups: List<Pair<Platform?, List<KeyRecord>>> = when (state.sortMode) {
            SortMode.PLATFORM -> KeyHubListOps.groupByPlatform(state.visible).map { it.first to it.second }
            else -> listOf(null to state.visible)
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groups.forEach { (platform, records) ->
                if (platform != null) {
                    item(key = "header-${platform.name}") {
                        Text(
                            "${platform.displayName} · ${records.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                }
                items(records, key = { it.id }) { record ->
                    KeyCard(
                        record = record,
                        selectionMode = state.selectionMode,
                        selected = record.id in state.selectedIds,
                        onClick = { vm.openDetail(record.id) },
                        onToggle = { vm.toggleSelect(record.id) },
                    )
                }
            }
        }

        if (state.selectionMode) BatchActionBar(state, vm)
    }

    if (state.batchPlatformDialog) BatchPlatformDialog(state, vm)
    if (state.batchLabelDialog) BatchLabelDialog(state, vm)
    if (state.batchDeleteDialog) BatchDeleteDialog(state, vm)
}

@Composable
private fun MainToolbar(
    state: KeyHubUiState,
    vm: KeyHubViewModel,
    sortExpanded: Boolean,
    onSortExpanded: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            OutlinedButton(onClick = { onSortExpanded(true) }) {
                Text(state.sortMode.label, fontSize = 12.sp)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { onSortExpanded(false) }) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            vm.setSortMode(mode)
                            onSortExpanded(false)
                        },
                    )
                }
            }
        }
        TextButton(onClick = { vm.openAddSheet() }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("添加", fontSize = 12.sp)
        }
        TextButton(onClick = { vm.checkAll() }, enabled = !state.checking && !state.isEmpty) {
            Icon(Icons.Filled.Sync, contentDescription = null)
            Text("检测全部", fontSize = 12.sp)
        }
        TextButton(onClick = { vm.toggleSelectionMode() }, enabled = !state.isEmpty) {
            Icon(Icons.Filled.Checklist, contentDescription = null)
            Text("批量", fontSize = 12.sp)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            IconButton(onClick = { vm.openSettings() }) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
    }
}

@Composable
private fun SelectionToolbar(state: KeyHubUiState, vm: KeyHubViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = { vm.toggleSelectionMode() }) {
            Icon(Icons.Filled.Close, contentDescription = null)
            Text("退出批量", fontSize = 12.sp)
        }
        TextButton(onClick = { vm.toggleSelectAll() }) {
            Text(if (state.allSelected) "取消全选" else "全选", fontSize = 12.sp)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(
                "已选 ${state.selectedCount}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 底部批量操作栏。四个动作都作用于勾选项，没勾选时置灰。 */
@Composable
private fun BatchActionBar(state: KeyHubUiState, vm: KeyHubViewModel) {
    val enabled = state.selectedCount > 0
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BatchAction(Icons.Filled.SwapHoriz, "改平台", enabled) { vm.openBatchPlatform() }
                BatchAction(Icons.AutoMirrored.Filled.Label, "改标签", enabled) { vm.openBatchLabel() }
                BatchAction(Icons.Filled.Sync, "检测", enabled && !state.checking) { vm.checkSelected() }
                BatchAction(Icons.Filled.Delete, "删除", enabled, danger = true) { vm.openBatchDelete() }
            }
        }
    }
}

@Composable
private fun BatchAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = tint)
    }
}

// ---------------------------------------------------------------- 批量弹窗

@Composable
private fun BatchPlatformDialog(state: KeyHubUiState, vm: KeyHubViewModel) {
    val count = state.selectedCount
    PlatformPickerDialog(
        current = KeyHubListOps.dominantPlatform(state.selectedRecords) ?: Platform.CUSTOM,
        title = "批量改平台",
        hint = "将把选中的 $count 个 Key 全部改为所选平台，并作废它们的旧检测结论。",
        confirmLabel = "应用到 $count 个",
        onDismiss = { vm.closeBatchDialogs() },
        onConfirm = { platform, baseUrl, balanceUrl -> vm.applyBatchPlatform(platform, baseUrl, balanceUrl) },
    )
}

@Composable
private fun BatchLabelDialog(state: KeyHubUiState, vm: KeyHubViewModel) {
    val count = state.selectedCount
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.closeBatchDialogs() },
        title = { Text("批量改标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "将覆盖选中 $count 个 Key 的标签；留空则清除它们的标签。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.applyBatchLabel(text) }) { Text("应用到 $count 个") }
        },
        dismissButton = { TextButton(onClick = { vm.closeBatchDialogs() }) { Text("取消") } },
    )
}

@Composable
private fun BatchDeleteDialog(state: KeyHubUiState, vm: KeyHubViewModel) {
    val count = state.selectedCount
    AlertDialog(
        onDismissRequest = { vm.closeBatchDialogs() },
        title = { Text("删除选中的 $count 个 Key？") },
        text = { Text("删除后无法恢复（历史快照保留指纹，不含明文）。") },
        confirmButton = {
            TextButton(onClick = { vm.confirmBatchDelete() }) {
                Text("删除 $count 个", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { vm.closeBatchDialogs() }) { Text("取消") } },
    )
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有 Key", style = MaterialTheme.typography.titleMedium)
        Text(
            "粘贴一段文本，自动识别平台并入库",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        Button(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  粘贴 Key", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun KeyCard(
    record: KeyRecord,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = if (selectionMode) onToggle else onClick),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggle() })
                }
                KeyText(record.rawKey, revealed = false, modifier = Modifier.weight(1f))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlatformChip(record.platform, onClick = if (selectionMode) onToggle else onClick)
                StatusBadge(record.lastStatus)
                BalanceLabel(record)
                if (record.platformOverridden) {
                    Text("手动", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                record.label?.let {
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TimeLabel(record.lastCheckedAt)
            }
        }
    }
}
