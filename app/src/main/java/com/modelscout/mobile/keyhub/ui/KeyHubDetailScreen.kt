package com.modelscout.mobile.keyhub.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.model.Platform

/** 详情页（计划书 §5.4）。 */
@Composable
fun KeyHubDetailPage(state: KeyHubUiState, vm: KeyHubViewModel) {
    val record = state.detail ?: return
    val context = LocalContext.current
    var editingLabel by remember { mutableStateOf(false) }
    var changingPlatform by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val revealed = record.id in state.revealedIds

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { vm.closeDetail() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回列表")
            }
            Text("Key 详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KeyText(record.rawKey, revealed = revealed, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.toggleReveal(record.id) }) {
                        Icon(
                            if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealed) "隐藏" else "显示",
                        )
                    }
                    IconButton(onClick = { copyToClipboard(context, record.rawKey) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                    }
                }
                Text(
                    if (revealed) "20 秒后自动隐藏" else "点击眼睛查看明文",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlatformChip(record.platform, onClick = { changingPlatform = true })
                    if (record.platformOverridden) {
                        Text("手动指定", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusBadge(record.lastStatus)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("余额 ${record.displayBalance().display()}", fontSize = 12.sp)
                    TimeLabel(record.lastCheckedAt)
                }
                record.label?.let { Text("标签：$it", fontSize = 12.sp) }
                record.customBaseUrl?.let {
                    Text("baseUrl：$it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.checkOne(record.id) }, enabled = !state.checking) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("  重新检测", modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = { editingLabel = true }) { Text("改标签") }
            OutlinedButton(onClick = { changingPlatform = true }) { Text("改平台") }
            OutlinedButton(onClick = { confirmingDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
                Text("删除")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("余额历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val history = vm.historyFor(record)
                BalanceChart(points = history, modifier = Modifier.fillMaxWidth())
                Text(
                    "最近 ${history.takeLast(KeyHubConfig.CHART_MAX_POINTS).size} 个快照",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("最近快照", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val recent = vm.historyFor(record).takeLast(12).reversed()
                if (recent.isEmpty()) {
                    Text("还没有检测记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    recent.forEach { point ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TimeLabel(point.ts)
                            Text(
                                "${point.status.symbol} ${point.status.label}" +
                                    (point.balance?.let { " · $it" } ?: ""),
                                fontSize = 11.sp,
                                color = statusColor(point.status),
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingLabel) {
        var text by remember { mutableStateOf(record.label.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingLabel = false },
            title = { Text("改标签") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("标签") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateLabel(record.id, text)
                    editingLabel = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingLabel = false }) { Text("取消") } },
        )
    }

    if (changingPlatform) {
        PlatformPickerDialog(
            current = record.platform,
            onDismiss = { changingPlatform = false },
            onConfirm = { platform, baseUrl, balanceUrl ->
                vm.updatePlatform(record.id, platform, baseUrl, balanceUrl)
                changingPlatform = false
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除这个 Key？") },
            text = { Text("删除后无法恢复（历史快照保留指纹，不含明文）。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteKey(record.id)
                    confirmingDelete = false
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } },
        )
    }
}

/** 手动指定平台。决策 3：只有用户显式选择，才会把 Key 发往该平台端点。 */
@Composable
fun PlatformPickerDialog(
    current: Platform,
    onDismiss: () -> Unit,
    onConfirm: (Platform, String?, String?) -> Unit,
) {
    var selected by remember { mutableStateOf(current) }
    var baseUrl by remember { mutableStateOf("") }
    var balanceUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动指定平台") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "重测将改用所选平台的端点。裸 sk- 默认按 OpenAI（推断）处理。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Platform.entries.forEach { platform ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = platform },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == platform,
                            onClick = { selected = platform },
                        )
                        Text(platform.displayName, fontSize = 13.sp)
                    }
                }
                if (selected == Platform.CUSTOM) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("baseUrl（会拼 /models）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = balanceUrl,
                        onValueChange = { balanceUrl = it },
                        label = { Text("余额端点（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, baseUrl, balanceUrl) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 复制明文到剪贴板；API 33+ 附加敏感标志。 */
private fun copyToClipboard(context: Context, rawKey: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("api key", rawKey)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    runCatching { manager.setPrimaryClip(clip) }
}
