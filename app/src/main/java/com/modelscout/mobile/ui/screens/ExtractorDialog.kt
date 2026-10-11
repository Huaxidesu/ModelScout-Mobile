package com.modelscout.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.ui.ScanViewModel
import com.modelscout.mobile.ui.UiState
import com.modelscout.mobile.ui.components.SectionCard

/** 从文本提取 Base URL / Key 的弹层。 */
@Composable
fun ExtractorDialog(state: UiState, vm: ScanViewModel) {
    val result = state.extractorResult

    AlertDialog(
        onDismissRequest = vm::closeExtractor,
        title = { Text("从文本提取") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.extractorText,
                    onValueChange = vm::onExtractorTextChange,
                    label = { Text("粘贴文档片段 / JSON / curl 命令") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                )
                SectionCard(title = "识别结果") {
                    Text(
                        text = "Base URL：${result?.baseUrl ?: "未识别"}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "API Key：${result?.apiKey ?: "未识别"}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    result?.keyRule?.let {
                        Text("Key 命中规则：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    val others = result?.urlCandidates?.drop(1).orEmpty()
                    if (others.isNotEmpty()) {
                        Text("其它候选（未采用）：", style = MaterialTheme.typography.bodySmall)
                        others.take(3).forEach { candidate ->
                            Text(
                                "· ${candidate.url}（得分 ${candidate.score}）",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = vm::applyExtraction,
                enabled = result?.hasUrl == true || result?.hasKey == true,
            ) {
                Text("填入")
            }
        },
        dismissButton = {
            TextButton(onClick = vm::closeExtractor) { Text("取消") }
        },
    )
}
