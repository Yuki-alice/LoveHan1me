package lovehan1me.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import lovehan1me.logic.model.PaletteStyle

// P6d-1 占位：桌面不做动态取色，返回 null 调用方回退固定色板（P7 可选增强）。
@Composable
internal actual fun provideDynamicColorScheme(
    keyColorArgb: Int,
    isDark: Boolean,
    style: PaletteStyle,
    contrastLevel: Double,
): ColorScheme? = null

@Composable
internal actual fun rememberSystemAccentColorOrNull(): Color? = null

@Composable
internal actual fun ConfigureSystemBars(
    colorScheme: ColorScheme,
    isDark: Boolean,
) = Unit
