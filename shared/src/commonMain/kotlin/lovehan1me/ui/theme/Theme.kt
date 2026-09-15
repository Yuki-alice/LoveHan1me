package lovehan1me.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import lovehan1me.data.SettingsRepository

/**
 * 纯装配层：给定 [colorScheme] 装配完整主题（形状 / 字阶 / Expressive 组件族）。
 *
 * 与下面的运行时重载分开，是为了让 @Preview 能绕开两样东西：
 * 1. `SettingsRepository` —— 它是 lateinit 注入的（要先 `install(store)`），
 *    预览环境没装，直接读会 `lateinit property store has not been initialized`；
 * 2. `ConfigureSystemBars` —— 需要真实窗口。
 *
 * 预览统一走 `lovehan1me.ui.preview.HanimePreviewTheme`，复用这里的同一套
 * shapes / typography，所以预览与运行时的形状、字阶一致，只有配色由调用方决定。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HanimeTheme(
    colorScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // 审计 P1：显式形状阶梯（数值与 M3 默认一致），散装圆角已收敛到 token。
        shapes = AppShapes,
        // 审计 P0：不再传 Typography() 默认值（等于"不定制"），改用显式字阶。
        typography = AppTypography,
        content = content,
    )
}

// P6d-1：从 :app 下沉（包名不变）。色算三件套见 DynamicSchemeProvider；
// 槽位配方见 ThemeBoards（8 槽 = 7 命名 + 1 跟随系统）。
@Composable
fun HanimeTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val settings by SettingsRepository.settings.collectAsState()
    val resolvedDarkTheme = darkTheme ?: when (settings.themeMode.value) {
        "always_on" -> true
        "always_off" -> false
        else -> systemDarkTheme
    }
    // 命名槽位入口（P1 现场算；P2 同签名查预生成表）。
    var colorScheme = boardColorScheme(
        board = ThemeBoard.fromId(settings.themeId),
        isDark = resolvedDarkTheme,
        // 审计 P2：动态对比度接设置（默认档 = 0.0，即原先写死的值）。
        contrastLevel = settings.contrastLevel.spec,
    )
    // AMOLED 是与深浅正交的独立开关，只在深色下叠加（Mihon 模式）。
    if (settings.amoled && resolvedDarkTheme) colorScheme = colorScheme.amoled()
    ConfigureSystemBars(
        colorScheme = colorScheme,
        isDark = resolvedDarkTheme,
    )

    HanimeTheme(colorScheme = colorScheme, content = content)
}
