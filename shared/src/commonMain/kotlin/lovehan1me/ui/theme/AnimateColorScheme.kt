package lovehan1me.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主题切换平滑过渡。
 *
 * 对每个 color role 单独做 [animateColorAsState]：切槽位 / 深浅 / 对比度 / AMOLED 时不再硬切，
 * 而是按 [animationSpec] 渐变。时长沿用 M3 标准 300ms。
 *
 * 输入是 [boardColorScheme] 现场算出的目标配色；本函数只负责"过渡"，不重新算色，故与
 * 任何色算后端（materialkolor / 预生成表）解耦。
 */
@Composable
fun animateColorScheme(
    targetColorScheme: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(300),
): ColorScheme {
    return ColorScheme(
        primary = animateColorAsState(targetColorScheme.primary, animationSpec).value,
        onPrimary = animateColorAsState(targetColorScheme.onPrimary, animationSpec).value,
        primaryContainer = animateColorAsState(targetColorScheme.primaryContainer, animationSpec).value,
        onPrimaryContainer = animateColorAsState(targetColorScheme.onPrimaryContainer, animationSpec).value,
        inversePrimary = animateColorAsState(targetColorScheme.inversePrimary, animationSpec).value,
        secondary = animateColorAsState(targetColorScheme.secondary, animationSpec).value,
        onSecondary = animateColorAsState(targetColorScheme.onSecondary, animationSpec).value,
        secondaryContainer = animateColorAsState(targetColorScheme.secondaryContainer, animationSpec).value,
        onSecondaryContainer = animateColorAsState(targetColorScheme.onSecondaryContainer, animationSpec).value,
        tertiary = animateColorAsState(targetColorScheme.tertiary, animationSpec).value,
        onTertiary = animateColorAsState(targetColorScheme.onTertiary, animationSpec).value,
        tertiaryContainer = animateColorAsState(targetColorScheme.tertiaryContainer, animationSpec).value,
        onTertiaryContainer = animateColorAsState(targetColorScheme.onTertiaryContainer, animationSpec).value,
        background = animateColorAsState(targetColorScheme.background, animationSpec).value,
        onBackground = animateColorAsState(targetColorScheme.onBackground, animationSpec).value,
        surface = animateColorAsState(targetColorScheme.surface, animationSpec).value,
        onSurface = animateColorAsState(targetColorScheme.onSurface, animationSpec).value,
        surfaceVariant = animateColorAsState(targetColorScheme.surfaceVariant, animationSpec).value,
        onSurfaceVariant = animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec).value,
        surfaceTint = animateColorAsState(targetColorScheme.surfaceTint, animationSpec).value,
        inverseSurface = animateColorAsState(targetColorScheme.inverseSurface, animationSpec).value,
        inverseOnSurface = animateColorAsState(targetColorScheme.inverseOnSurface, animationSpec).value,
        surfaceBright = animateColorAsState(targetColorScheme.surfaceBright, animationSpec).value,
        surfaceDim = animateColorAsState(targetColorScheme.surfaceDim, animationSpec).value,
        surfaceContainer = animateColorAsState(targetColorScheme.surfaceContainer, animationSpec).value,
        surfaceContainerHigh = animateColorAsState(targetColorScheme.surfaceContainerHigh, animationSpec).value,
        surfaceContainerHighest = animateColorAsState(targetColorScheme.surfaceContainerHighest, animationSpec).value,
        surfaceContainerLow = animateColorAsState(targetColorScheme.surfaceContainerLow, animationSpec).value,
        surfaceContainerLowest = animateColorAsState(targetColorScheme.surfaceContainerLowest, animationSpec).value,
        error = animateColorAsState(targetColorScheme.error, animationSpec).value,
        onError = animateColorAsState(targetColorScheme.onError, animationSpec).value,
        errorContainer = animateColorAsState(targetColorScheme.errorContainer, animationSpec).value,
        onErrorContainer = animateColorAsState(targetColorScheme.onErrorContainer, animationSpec).value,
        outline = animateColorAsState(targetColorScheme.outline, animationSpec).value,
        outlineVariant = animateColorAsState(targetColorScheme.outlineVariant, animationSpec).value,
        scrim = animateColorAsState(targetColorScheme.scrim, animationSpec).value,
        primaryFixed = animateColorAsState(targetColorScheme.primaryFixed, animationSpec).value,
        primaryFixedDim = animateColorAsState(targetColorScheme.primaryFixedDim, animationSpec).value,
        onPrimaryFixed = animateColorAsState(targetColorScheme.onPrimaryFixed, animationSpec).value,
        onPrimaryFixedVariant = animateColorAsState(targetColorScheme.onPrimaryFixedVariant, animationSpec).value,
        secondaryFixed = animateColorAsState(targetColorScheme.secondaryFixed, animationSpec).value,
        secondaryFixedDim = animateColorAsState(targetColorScheme.secondaryFixedDim, animationSpec).value,
        onSecondaryFixed = animateColorAsState(targetColorScheme.onSecondaryFixed, animationSpec).value,
        onSecondaryFixedVariant = animateColorAsState(targetColorScheme.onSecondaryFixedVariant, animationSpec).value,
        tertiaryFixed = animateColorAsState(targetColorScheme.tertiaryFixed, animationSpec).value,
        tertiaryFixedDim = animateColorAsState(targetColorScheme.tertiaryFixedDim, animationSpec).value,
        onTertiaryFixed = animateColorAsState(targetColorScheme.onTertiaryFixed, animationSpec).value,
        onTertiaryFixedVariant = animateColorAsState(targetColorScheme.onTertiaryFixedVariant, animationSpec).value,
    )
}
