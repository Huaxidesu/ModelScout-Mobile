package com.modelscout.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.core.model.Capability
import com.modelscout.mobile.core.model.ModelResult
import com.modelscout.mobile.ui.ScanViewModel
import com.modelscout.mobile.ui.UiState
import com.modelscout.mobile.ui.components.CapabilityIcon
import com.modelscout.mobile.ui.components.LimitBadge
import com.modelscout.mobile.ui.components.SectionCard

private enum class CapabilityFilter(val label: String) {
    TOOL("支持工具"),
    VISION("支持视觉"),
    REASONING("推理模型"),
}

@Composable
fun ResultScreen(state: UiState, vm: ScanViewModel) {
    var filter by remember { mutableStateOf<CapabilityFilter?>(null) }
    var editing by remember { mutableStateOf<ModelResult?>(null) }

    val visible = state.results.filter { model ->
        when (filter) {
            null -> true
            CapabilityFilter.TOOL -> model.tool == Capability.SUPPORTED
            CapabilityFilter.VISION -> model.vision == Capability.SUPPORTED
            CapabilityFilter.REASONING -> model.reasoning == Capability.SUPPORTED
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionCard(title = "模型（${visible.size} / ${state.results.size}）") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.setAllSelected(true) }) { Text("全选") }
                TextButton(onClick = { vm.setAllSelected(false) }) { Text("全不选") }
                Text(
                    "已选 ${state.selectedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CapabilityFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = if (filter == option) null else option },
                        label = { Text(option.label, fontSize = 12.sp) },
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            Text(
                "没有模型。回到连接页开始扫描，或调整筛选条件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        onToggle = { vm.toggleSelected(model.id) },
                        onEditLimit = { editing = model },
                    )
                }
            }
        }
    }

    editing?.let { model ->
        LimitEditDialog(
            model = model,
            onDismiss = { editing = null },
            onConfirm = { context, output ->
                vm.updateLimit(model.id, context, output)
                editing = null
            },
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelResult,
    onToggle: () -> Unit,
    onEditLimit: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Checkbox(checked = model.selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    model.id,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CapabilityIcon(model.tool, "工具")
                    CapabilityIcon(model.vision, "视觉")
                    CapabilityIcon(model.reasoning, "推理")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(onClick = onEditLimit),
                ) {
                    Text(
                        "上下文 ${model.contextLimit?.toString() ?: "—"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "输出 ${model.outputLimit?.toString() ?: "—"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LimitBadge(model.limitSource)
                    Text("改", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                model.note?.let {
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LimitEditDialog(
    model: ModelResult,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Int?) -> Unit,
) {
    var context by remember { mutableStateOf(model.contextLimit?.toString().orEmpty()) }
    var output by remember { mutableStateOf(model.outputLimit?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改上限 · ${model.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = context,
                    onValueChange = { context = it.filter { ch -> ch.isDigit() } },
                    label = { Text("上下文上限 context") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = output,
                    onValueChange = { output = it.filter { ch -> ch.isDigit() } },
                    label = { Text("输出上限 output") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    "保存后该模型上限来源会标记为「手动」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(context.toIntOrNull(), output.toIntOrNull()) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
