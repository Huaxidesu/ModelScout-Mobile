package com.modelscout.mobile.keyhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
        // 工具栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box {
                OutlinedButton(onClick = { sortExpanded = true }) {
                    Text(state.sortMode.label, fontSize = 12.sp)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                vm.setSortMode(mode)
                                sortExpanded = false
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = { vm.openSettings() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }
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
            Modifier.fillMaxSize(),
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
                        onClick = { vm.openDetail(record.id) },
                        onPlatformClick = { vm.openDetail(record.id) },
                    )
                }
            }
        }
    }
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
private fun KeyCard(record: KeyRecord, onClick: () -> Unit, onPlatformClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyText(record.rawKey, revealed = false)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlatformChip(record.platform, onClick = onPlatformClick)
                StatusBadge(record.lastStatus)
                BalanceLabel(record)
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
