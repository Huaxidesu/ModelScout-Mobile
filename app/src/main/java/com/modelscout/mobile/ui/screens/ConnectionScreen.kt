package com.modelscout.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.modelscout.mobile.core.Constants
import com.modelscout.mobile.ui.GATEWAY_PRESETS
import com.modelscout.mobile.ui.ScanViewModel
import com.modelscout.mobile.ui.UiState
import com.modelscout.mobile.ui.components.SectionCard

@Composable
fun ConnectionScreen(state: UiState, vm: ScanViewModel) {
    var presetExpanded by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "网关",
            trailing = {
                Row {
                    OutlinedButton(onClick = { presetExpanded = true }) {
                        Text("预设")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false },
                    ) {
                        GATEWAY_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    vm.applyPreset(preset)
                                    presetExpanded = false
                                },
                            )
                        }
                    }
                }
            },
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = vm::onBaseUrlChange,
                label = { Text("Base URL") },
                placeholder = { Text("https://api.deepseek.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = vm::onApiKeyChange,
                label = { Text("API Key（只存内存，不落盘）") },
                singleLine = true,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (keyVisible) "隐藏" else "显示",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AssistChip(
                onClick = vm::openExtractor,
                label = { Text("从文本提取 URL / Key") },
                leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
            )
        }

        SectionCard(title = "扫描参数") {
            Text("并发数：${state.concurrency}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.concurrency.toFloat(),
                onValueChange = { vm.onConcurrencyChange(it.toInt()) },
                valueRange = Constants.MIN_CONCURRENCY.toFloat()..Constants.MAX_CONCURRENCY.toFloat(),
                steps = Constants.MAX_CONCURRENCY - Constants.MIN_CONCURRENCY - 1,
            )
            OutlinedTextField(
                value = state.timeoutSeconds.toString(),
                onValueChange = { raw -> raw.filter { it.isDigit() }.toLongOrNull()?.let(vm::onTimeoutChange) },
                label = { Text("超时（秒，下限 ${Constants.MIN_TIMEOUT_SECONDS}）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("探测上限", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "关闭则只用声明与内置字典",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.probeLimit, onCheckedChange = vm::onProbeLimitChange)
            }
            OutlinedTextField(
                value = state.filter,
                onValueChange = vm::onFilterChange,
                label = { Text("模型筛选（子串匹配，留空为全部）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = vm::startScan,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.running) "扫描中…" else "开始扫描")
        }

        Text(
            text = "提示：若 /models 返回 404，通常是 Base URL 缺 /v1 路径。",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
