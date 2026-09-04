package io.github.daisukikaffuchino.han1meviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository

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
        contrastLevel = 0.0,
    ) ?: if (resolvedDarkTheme) darkColorScheme() else lightColorScheme()
    ConfigureSystemBars(
        colorScheme = colorScheme,
        isDark = resolvedDarkTheme,
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}

/**
 * P6d-1：原 :app internal 主题预览入口（包名签名不变，:app AppearancePickers 调用点零改动）。
 * Android 走 m3color 动态 scheme（含逐色块动画）；其他平台回退固定色板。
 */
@Composable
fun expressiveColorScheme(
    keyColor: Color,
    isDark: Boolean,
    style: AppPaletteStyle = AppPaletteStyle.TonalSpot,
    contrastLevel: Double = 0.0,
    animationSpec: AnimationSpec<Color> = spring(),
): ColorScheme = provideDynamicColorScheme(
    keyColorArgb = keyColor.toArgb(),
    isDark = isDark,
    style = style,
    contrastLevel = contrastLevel,
) ?: if (isDark) darkColorScheme() else lightColorScheme()
