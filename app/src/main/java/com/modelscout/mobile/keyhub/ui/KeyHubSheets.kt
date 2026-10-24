package com.modelscout.mobile.keyhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.model.Confidence
import com.modelscout.mobile.keyhub.model.Platform

/** 添加 Sheet。支持「自动识别 / 手动指定平台」两种批量入库模式。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyHubAddSheet(state: KeyHubUiState, vm: KeyHubViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val preview = state.addPreview
    val manual = state.addMode == AddMode.MANUAL
    val effective = state.addEffectiveKeys
    var pickingPlatform by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { vm.closeAddSheet() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("粘贴 Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "支持换行 / 逗号 / 分号 / 空白分隔，也支持整段 JSON。每行 Key 前面的文字会作为标签。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.persistEnabled) {
                Text(
                    "⚠ 本地保存当前为关闭，本次添加仅在本次会话有效，退出即清空。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = state.addText,
                onValueChange = vm::onAddTextChange,
                label = { Text("在此粘贴") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp),
            )

            // ---------------- 平台来源（批量模式选择） ----------------
            Text("平台来源", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AddMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.addMode == mode,
                        onClick = { vm.setAddMode(mode) },
                        label = { Text(mode.label, fontSize = 12.sp) },
                    )
                }
            }

            if (manual) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "整批统一为：${state.addManualPlatform.displayName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(onClick = { pickingPlatform = true }) { Text("选择平台", fontSize = 12.sp) }
                }
                Text(
                    "手动指定会把整批 Key 都记成该平台（标记为「手动指定」），自动识别不会再改动它。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.addManualPlatform == Platform.CUSTOM) {
                    OutlinedTextField(
                        value = state.addCustomBaseUrl,
                        onValueChange = vm::setAddCustomBaseUrl,
                        label = { Text("baseUrl（会拼 /models）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.addCustomBalanceEndpoint,
                        onValueChange = vm::setAddCustomBalanceEndpoint,
                        label = { Text("余额端点（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    "逐条按格式识别：带厂商标识的前缀优先，其次是 sk- + 32 位小写 hex（DeepSeek），" +
                        "其余裸 sk- 归「OpenAI（推断）」。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- 预览 ----------------
            if (effective.isNotEmpty() || preview.duplicatesSkipped > 0) {
                Text(
                    "识别到 ${effective.size} 个" +
                        if (preview.duplicatesSkipped > 0) "，本次粘贴内重复 ${preview.duplicatesSkipped} 个" else "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                effective.take(12).forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.rawKey.take(5) + "***" + item.rawKey.takeLast(4),
                            fontFamily = MonoFont,
                            fontSize = 12.sp,
                        )
                        Text(
                            item.platform.displayName +
                                if (item.confidence == Confidence.INFERRED) "（推断）" else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.label?.let { Text("标签：$it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (effective.size > 12) {
                    Text("… 还有 ${effective.size - 12} 个", fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.confirmAdd() },
                    enabled = effective.isNotEmpty(),
                ) {
                    Text("确认入库（${effective.size}）")
                }
                TextButton(onClick = { vm.closeAddSheet() }) { Text("取消") }
            }
        }
    }

    if (pickingPlatform) {
        PlatformPickerDialog(
            current = state.addManualPlatform,
            title = "整批指定平台",
            hint = "本次粘贴的所有 Key 都会按这个平台入库。",
            confirmLabel = "用这个平台",
            onDismiss = { pickingPlatform = false },
            onConfirm = { platform, _, _ ->
                vm.setAddPlatform(platform)
                pickingPlatform = false
            },
        )
    }
}

/** 设置 Sheet。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyHubSettingsSheet(state: KeyHubUiState, vm: KeyHubViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmingClear by remember { mutableStateOf(false) }
    var confirmStage by remember { mutableStateOf(0) }

    ModalBottomSheet(onDismissRequest = { vm.closeSettings() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("本地保存（加密）", fontSize = 14.sp)
                    Text(
                        "开启后 Key 列表与历史用 AndroidKeyStore 生成的 AES256-GCM 主钥加密落盘；" +
                            "关闭则仅存内存，退出即清，并删除已存在的加密文件。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.persistEnabled, onCheckedChange = { vm.setPersistEnabled(it) })
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("检测并发：${state.concurrency}（1~${KeyHubConfig.MAX_CONCURRENCY}）", fontSize = 14.sp)
                Slider(
                    value = state.concurrency.toFloat(),
                    onValueChange = { vm.setConcurrency(it.toInt()) },
                    valueRange = KeyHubConfig.MIN_CONCURRENCY.toFloat()..KeyHubConfig.MAX_CONCURRENCY.toFloat(),
                    steps = KeyHubConfig.MAX_CONCURRENCY - KeyHubConfig.MIN_CONCURRENCY - 1,
                )
                Text(
                    "并发过高容易触发平台限流，429 会被记为「查询失败」而不是「已失效」。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    confirmingClear = true
                    confirmStage = 1
                }) {
                    Text("清除全部数据", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { vm.closeSettings() }) { Text("关闭") }
            }
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(if (confirmStage == 1) "清除全部数据？" else "再确认一次") },
            text = {
                Text(
                    if (confirmStage == 1) {
                        "将删除所有 Key 与历史快照，并删除本地加密文件。此操作不可恢复。"
                    } else {
                        "真的要清除吗？这一步之后无法撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (confirmStage == 1) {
                        confirmStage = 2
                    } else {
                        vm.clearAllData()
                        vm.closeSettings()
                        confirmingClear = false
                    }
                }) {
                    Text(if (confirmStage == 1) "继续" else "确认清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text("取消") }
            },
        )
    }
}
