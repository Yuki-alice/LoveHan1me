package lovehan1me.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主题平台胶水。
 *
 * 命名槽配色现统一走 materialkolor 在 commonMain 现场算（见 [boardColorScheme]），
 * 平台层只保留两块无法在 common 表达的能力：
 * - [rememberSystemAccentColorOrNull]：系统动态强调色（Android S+ 壁纸色），其余平台返回 null；
 * - [ConfigureSystemBars]：状态栏/导航栏外观 + 窗口背景，desktop/ios 为 no-op。
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
