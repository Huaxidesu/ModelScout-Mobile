package com.modelscout.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Sky = Color(0xFF0EA5E9)
private val SkyDark = Color(0xFF38BDF8)
private val Amber = Color(0xFFF59E0B)
private val Slate = Color(0xFF0F172A)

private val DarkColors = darkColorScheme(
    primary = SkyDark,
    onPrimary = Color(0xFF00263A),
    primaryContainer = Color(0xFF0B3A52),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Amber,
    onSecondary = Color(0xFF3A2600),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF222A33),
    onSurfaceVariant = Color(0xFFB7C2CE),
    outline = Color(0xFF46515C),
    error = Color(0xFFFF7B72),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE9FF),
    onPrimaryContainer = Color(0xFF001E2F),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    background = Color(0xFFF7F9FC),
    onBackground = Slate,
    surface = Color.White,
    onSurface = Slate,
    surfaceVariant = Color(0xFFE7EDF3),
    onSurfaceVariant = Color(0xFF44505C),
    outline = Color(0xFF9AA7B4),
    error = Color(0xFFB3261E),
)

/** 支持 / 不支持 / 未知 三态配色。 */
object CapabilityColors {
    val Supported = Color(0xFF2E9E5B)
    val Unsupported = Color(0xFFCF4B3F)
    val Unknown = Color(0xFFB08A2E)
}

@Composable
fun ModelScoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
