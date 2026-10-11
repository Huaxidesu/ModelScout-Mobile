package com.modelscout.mobile.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.core.gen.DshFormat
import com.modelscout.mobile.core.gen.DshGenerator
import com.modelscout.mobile.ui.KeyMode
import com.modelscout.mobile.ui.PreviewTarget
import com.modelscout.mobile.ui.ScanViewModel
import com.modelscout.mobile.ui.UiState
import com.modelscout.mobile.ui.components.SectionCard

@Composable
fun PreviewScreen(state: UiState, vm: ScanViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = writeToUri(context, uri, state.previewText)
            toast = if (ok) "已保存" else "保存失败"
        }
    }

    LaunchedEffect(Unit) {
        if (state.previewText.isBlank()) vm.regeneratePreview()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "目标格式") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewTarget.entries.forEach { target ->
                    FilterChip(
                        selected = state.previewTarget == target,
                        onClick = { vm.onPreviewTargetChange(target) },
                        label = { Text(target.label, fontSize = 12.sp) },
                    )
                }
            }
            if (state.previewTarget == PreviewTarget.DSH) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DshFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.dshFormat == format,
                            onClick = { vm.onDshFormatChange(format) },
                            label = { Text(format.label, fontSize = 12.sp) },
                        )
                    }
                }
                Text(
                    DshGenerator.DISCLAIMER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Key 写入方式") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.keyMode == mode,
                        onClick = { vm.onKeyModeChange(mode) },
                        label = { Text(mode.label, fontSize = 12.sp) },
                    )
                }
            }
            if (state.keyMode == KeyMode.ENV) {
                OutlinedTextField(
                    value = state.envVar,
                    onValueChange = {
                        vm.onEnvVarChange(it)
                        vm.regeneratePreview()
                    },
                    label = { Text("环境变量名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "明文模式会把当前 API Key 直接写进配置，注意不要提交到公开仓库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = "配置内容（可编辑，已选 ${state.selectedCount} 个模型）",
            trailing = {
                OutlinedButton(onClick = vm::regeneratePreview) { Text("重新生成") }
            },
        ) {
            OutlinedTextField(
                value = state.previewText,
                onValueChange = vm::onPreviewTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.previewText))
                        toast = "已复制到剪贴板"
                    },
                    enabled = state.previewText.isNotBlank(),
                ) {
                    Text("复制")
                }
                OutlinedButton(
                    onClick = {
                        val name = when (state.previewTarget) {
                            PreviewTarget.OPENCODE -> "opencode.json"
                            PreviewTarget.DSH -> "deepseekhardness.${state.dshFormat.extension}"
                        }
                        saveLauncher.launch(name)
                    },
                    enabled = state.previewText.isNotBlank(),
                ) {
                    Text("保存")
                }
            }
            toast?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun writeToUri(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(text.toByteArray(Charsets.UTF_8))
        stream.flush()
    } != null
}.getOrDefault(false)
