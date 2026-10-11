package com.modelscout.mobile.keyhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.StatusTone

/**
 * KeyHub 自有配色（不 import 既有 ui/theme，遵守包隔离）。
 * 徽章色只按 [StatusTone] 取，不按具体状态取——分类单点的视觉延伸。
 */
object KeyHubColors {
    val Green = Color(0xFF2E9E5B)
    val Orange = Color(0xFFD98324)
    val Red = Color(0xFFCF4B3F)
    val Gray = Color(0xFF7C8794)

    fun of(tone: StatusTone, outline: Color): Color = when (tone) {
        StatusTone.GREEN -> Green
        StatusTone.ORANGE -> Orange
        StatusTone.RED -> Red
        StatusTone.GRAY -> Gray
        StatusTone.OUTLINE -> outline
    }
}

@Composable
fun statusColor(status: KeyStatus): Color =
    KeyHubColors.of(status.tone, MaterialTheme.colorScheme.outline)

val MonoFont = FontFamily.Monospace
val SemiBold = FontWeight.SemiBold
