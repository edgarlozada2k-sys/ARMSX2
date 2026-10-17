package com.armsx2.exkinet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Paleta de ExKinet (ESPECIFICACION.md): base #050A10, acento #0871F9→#299AFF, borde #84DDFF. */
@Immutable
data class ExkColors(
    val bg: Color,
    val bgAlt: Color,
    val panel: Color,
    val panelBorder: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentDeep: Color,
    val glow: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val chip: Color,
    val isDark: Boolean,
) {
    val accentBrush: Brush get() = Brush.verticalGradient(listOf(accent, accentDeep))
    val navActiveBrush: Brush get() = Brush.verticalGradient(listOf(Color(0xFF075CFF), Color(0xFF0637BE)))
    val screenBrush: Brush get() = Brush.radialGradient(
        listOf(if (isDark) Color(0xFF12304D) else Color(0xFFD7E8FA), bg),
        center = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f),
        radius = 2200f,
    )
}

val ExkDark = ExkColors(
    bg = Color(0xFF050A10),
    bgAlt = Color(0xFF0A1420),
    panel = Color(0xB80C1725),
    panelBorder = Color(0x70254058),
    text = Color.White,
    textSecondary = Color(0xFFC4C8D1),
    textMuted = Color(0xFF8D9FB3),
    accent = Color(0xFF299AFF),
    accentDeep = Color(0xFF0871F9),
    glow = Color(0xFF84DDFF),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFC069),
    success = Color(0xFF5EE0A0),
    chip = Color(0x14FFFFFF),
    isDark = true,
)

val ExkLight = ExkColors(
    bg = Color(0xFFF2F6FB),
    bgAlt = Color(0xFFE6EEF7),
    panel = Color(0xF2FFFFFF),
    panelBorder = Color(0x33254058),
    text = Color(0xFF0B1622),
    textSecondary = Color(0xFF34465A),
    textMuted = Color(0xFF5E7186),
    accent = Color(0xFF1E88F2),
    accentDeep = Color(0xFF0864E0),
    glow = Color(0xFF3AA9FF),
    danger = Color(0xFFD63B3B),
    warning = Color(0xFFB7791F),
    success = Color(0xFF1F9D63),
    chip = Color(0x14000000),
    isDark = false,
)

val LocalExk = staticCompositionLocalOf { ExkDark }

@Composable
fun ExkThemeProvider(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExk provides if (dark) ExkDark else ExkLight, content = content)
}
