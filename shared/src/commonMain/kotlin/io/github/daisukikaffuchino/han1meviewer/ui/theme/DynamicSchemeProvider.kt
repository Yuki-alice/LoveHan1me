package io.github.daisukikaffuchino.han1meviewer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.daisukikaffuchino.han1meviewer.logic.model.PaletteStyle

/**
 * P6d-1-C：主题平台胶水（m3color 无 KMP 坐标，只能 androidMain）。
 *
 * - androidMain：m3color 原逻辑（Hct + 8 Scheme 分发 + ColorScheme 映射，含逐色块动画）。
 * - desktopMain / iosMain：动态取色返回 null，调用方回退固定浅/深色板（P7 可选增强）。
 */
@Composable
internal expect fun provideDynamicColorScheme(
    keyColorArgb: Int,
    isDark: Boolean,
    style: PaletteStyle = PaletteStyle.TonalSpot,
    contrastLevel: Double = 0.0,
): ColorScheme?

/**
 * 系统动态强调色（Android S+ 的 system_accent1_500），平台不支持时返回 null。
 */
@Composable
internal expect fun rememberSystemAccentColorOrNull(): Color?

/**
 * 平台系统栏配置（状态栏/导航栏外观 + 窗口背景），desktop/ios 为 no-op。
 */
@Composable
internal expect fun ConfigureSystemBars(
    colorScheme: ColorScheme,
    isDark: Boolean,
)
