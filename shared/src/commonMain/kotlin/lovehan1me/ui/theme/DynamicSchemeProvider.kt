package lovehan1me.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import lovehan1me.core.domain.model.PaletteStyle

/**
 * P6d-1-C：主题平台胶水（m3color 无 KMP 坐标，只能 JVM 源集）。
 *
 * - androidMain：m3color 原逻辑（Hct + 8 Scheme 分发 + ColorScheme 映射，含逐色块动画），
 *   仅"跟随系统"槽需要它；
 * - desktopMain / iosMain：返回 null，调用方一律查预生成表（P2 起运行时无现场色算）。
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
