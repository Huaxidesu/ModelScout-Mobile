package com.modelscout.mobile.keyhub.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.model.HistoryPoint
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import com.modelscout.mobile.keyhub.security.KeyMasker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 状态徽章。颜色只按 [KeyStatus.tone] 取（分类单点的视觉延伸）。 */
@Composable
fun StatusBadge(status: KeyStatus, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Text(
        text = "${status.symbol} ${status.label}",
        fontSize = 11.sp,
        color = color,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun PlatformChip(
    platform: Platform,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val color = MaterialTheme.colorScheme.primary
    Text(
        text = platform.displayName,
        fontSize = 11.sp,
        color = color,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** mask 后的 Key；[revealed] 时显示明文。 */
@Composable
fun KeyText(
    rawKey: String,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    fontSize: Int = 13,
) {
    Text(
        text = if (revealed) rawKey else KeyMasker.mask(rawKey),
        fontFamily = MonoFont,
        fontSize = fontSize.sp,
        modifier = modifier,
    )
}

@Composable
fun BalanceLabel(record: KeyRecord, modifier: Modifier = Modifier) {
    Text(
        text = record.displayBalance().display(),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun TimeLabel(epochMillis: Long?, modifier: Modifier = Modifier) {
    Text(
        text = epochMillis?.let { TIME_FORMAT.format(Date(it)) } ?: "未检测",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * 自绘折线图。
 * x = 时间（最近 30 点）、y = 余额自适应、null 断开连线、首尾标数值。
 */
@Composable
fun BalanceChart(
    points: List<HistoryPoint>,
    modifier: Modifier = Modifier,
) {
    val visible = points.takeLast(KeyHubConfig.CHART_MAX_POINTS)
    val values = visible.mapNotNull { it.balance }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier.fillMaxWidth().height(160.dp)) {
        if (values.isEmpty()) {
            Text(
                "暂无余额数据",
                fontSize = 12.sp,
                color = textColor,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val left = 12f
            val right = size.width - 12f
            val top = 22f
            val bottom = size.height - 26f
            val span = (right - left).coerceAtLeast(1f)

            val maxValue = values.max()
            val minValue = minOf(values.min(), 0.0)
            val range = (maxValue - minValue).takeIf { it > 1e-9 } ?: 1.0

            fun xAt(index: Int): Float =
                if (visible.size <= 1) left + span / 2f else left + span * index / (visible.size - 1)

            fun yAt(value: Double): Float =
                (bottom - ((value - minValue) / range) * (bottom - top)).toFloat()

            // 基线
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(left, bottom), end = androidx.compose.ui.geometry.Offset(right, bottom), strokeWidth = 1f)

            // 折线：null 断开
            val path = Path()
            var started = false
            visible.forEachIndexed { index, point ->
                val value = point.balance
                if (value == null) {
                    started = false
                    return@forEachIndexed
                }
                val x = xAt(index)
                val y = yAt(value)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(path, color = lineColor, style = Stroke(width = 2.5f))

            // 数据点
            visible.forEachIndexed { index, point ->
                point.balance?.let { value ->
                    drawCircle(lineColor, radius = 3f, center = androidx.compose.ui.geometry.Offset(xAt(index), yAt(value)))
                }
            }

            // 首尾标数值
            val paint = Paint().apply {
                isAntiAlias = true
                color = textColor.toArgb()
                textSize = 26f
            }
            val native = drawContext.canvas.nativeCanvas
            val firstIndex = visible.indexOfFirst { it.balance != null }
            val lastIndex = visible.indexOfLast { it.balance != null }
            if (firstIndex >= 0) {
                native.drawText(fmt(visible[firstIndex].balance!!), xAt(firstIndex), yAt(visible[firstIndex].balance!!) - 10f, paint)
            }
            if (lastIndex >= 0 && lastIndex != firstIndex) {
                val label = fmt(visible[lastIndex].balance!!)
                val width = paint.measureText(label)
                native.drawText(label, (xAt(lastIndex) - width).coerceAtLeast(left), yAt(visible[lastIndex].balance!!) - 10f, paint)
            }
        }
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
