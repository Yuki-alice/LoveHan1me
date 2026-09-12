package lovehan1me.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import lovehan1me.data.SettingsRepository

// P6d-1：从 :app 下沉（包名不变）。色算三件套见 DynamicSchemeProvider；
// 槽位配方见 ThemeBoards（8 槽 = 7 命名 + 1 跟随系统）。
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // 审计 P1：显式形状阶梯（数值与 M3 默认一致），散装圆角已收敛到 token。
        shapes = AppShapes,
        // 审计 P0：不再传 Typography() 默认值（等于"不定制"），改用显式字阶。
        typography = AppTypography,
        content = content,
    )
}
