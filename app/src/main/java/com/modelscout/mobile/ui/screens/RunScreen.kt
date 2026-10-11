package com.modelscout.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.core.model.Stage
import com.modelscout.mobile.ui.ScanViewModel
import com.modelscout.mobile.ui.UiState
import com.modelscout.mobile.ui.components.SectionCard

private fun stageLabel(stage: Stage): String = when (stage) {
    Stage.TOOL -> "工具探测"
    Stage.VISION -> "视觉探测"
    Stage.REASONING -> "推理判定"
    Stage.LIMIT -> "上限探测"
    Stage.DONE -> "完成"
}

@Composable
fun RunScreen(state: UiState, onAbort: () -> Unit, onOpenResult: () -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.scrollToItem(state.logs.lastIndex)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "进度") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "${state.percent}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${state.completed} / ${state.total}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (state.running) {
                    "${stageLabel(state.stage)}：${state.currentModel ?: "准备中…"}"
                } else {
                    if (state.total == 0) "没有可扫描的模型" else "已结束"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAbort, enabled = state.running) { Text("中止") }
                Button(onClick = onOpenResult, enabled = state.hasResults) {
                    Text("查看结果（${state.results.size}）")
                }
            }
            state.fatalError?.let { error ->
                Text(
                    error.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        SectionCard(
            title = "日志",
            modifier = Modifier.fillMaxWidth().weight(1f),
            trailing = { Text("${state.logs.size} 行", style = MaterialTheme.typography.bodySmall) },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(8.dp),
            ) {
                items(state.logs) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
