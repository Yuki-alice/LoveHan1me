package lovehan1me.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 桌面端不做系统动态取色（无平台壁纸/窗口强调色管线）；命名槽统一走 materialkolor 现场算。
@Composable
internal actual fun rememberSystemAccentColorOrNull(): Color? = null

@Composable
internal actual fun ConfigureSystemBars(
    colorScheme: ColorScheme,
    isDark: Boolean,
) = Unit
