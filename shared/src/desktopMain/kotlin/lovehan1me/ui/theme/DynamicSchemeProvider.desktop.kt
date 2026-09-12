package lovehan1me.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import lovehan1me.core.domain.model.PaletteStyle

// P2：桌面运行时不再现场算色（一律查预生成表），回到打桩。
// m3color 只剩 desktopApp 的预生成工具（GenThemeBoards）经 api 直连使用。
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
