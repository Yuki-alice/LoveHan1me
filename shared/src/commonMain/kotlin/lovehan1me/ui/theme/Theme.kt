package lovehan1me.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import lovehan1me.data.SettingsRepository

// P6d-1：从 :app 下沉（包名不变）。平台相关三件套见 DynamicSchemeProvider：
// 动态取色（m3color，仅 Android）、系统强调色（Android S+）、系统栏配置（Android）。
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
    val accentColor = ThemeAccentColor.fromId(settings.themeAccent.id)
    val paletteStyle = AppPaletteStyle.fromId(settings.paletteStyle.id)
    val fallbackKeyColor = accentColor.colors.first()
    val keyColor = if (settings.useDynamicColor) {
        rememberSystemAccentColorOrNull() ?: fallbackKeyColor
    } else {
        fallbackKeyColor
    }
    // 非 Android 平台 provideDynamicColorScheme 返回 null，回退固定浅/深色板（P7 可选增强）。
    val colorScheme = provideDynamicColorScheme(
        keyColorArgb = keyColor.toArgb(),
        isDark = resolvedDarkTheme,
        style = paletteStyle,
        // 审计 P2：动态对比度接设置（默认档 = 0.0，即原先写死的值）。
        contrastLevel = settings.contrastLevel.spec,
    ) ?: if (resolvedDarkTheme) darkColorScheme() else lightColorScheme()
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

/**
 * P6d-1：原 :app internal 主题预览入口（包名签名不变，:app AppearancePickers 调用点零改动）。
 * Android 走 m3color 动态 scheme；其他平台回退固定色板。
 *
 * 审计 P2：删掉了声明后从未被使用的 `animationSpec` 参数 —— `provideDynamicColorScheme`
 * 不接收它，注释里"含逐色块动画"的说法是错的（动态取色本身不带动画，色块动画来自 UI 层）。
 */
@Composable
fun expressiveColorScheme(
    keyColor: Color,
    isDark: Boolean,
    style: AppPaletteStyle = AppPaletteStyle.TonalSpot,
    contrastLevel: Double = 0.0,
): ColorScheme = provideDynamicColorScheme(
    keyColorArgb = keyColor.toArgb(),
    isDark = isDark,
    style = style,
    contrastLevel = contrastLevel,
) ?: if (isDark) darkColorScheme() else lightColorScheme()
