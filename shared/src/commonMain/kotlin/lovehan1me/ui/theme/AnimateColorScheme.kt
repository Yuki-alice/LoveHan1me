package lovehan1me.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp

/**
 * 主题切换平滑过渡。
 *
 * ## 为什么是"单个进度动画 + 插值"，而不是 48 个 animateColorAsState
 *
 * 旧实现对每个 color role 单独起一个 `animateColorAsState`，共 **48 个并行动画实例**：
 * - 每帧要做 48 次 spring 求解（速度/位移积分），这是动画开销的大头；
 * - 每帧产生 48 次 state 写入，虽然 Compose 会合并到同一帧重组，但 invalidation 的
 *   登记与分派仍是 48 份；
 * - 48 个 `Animatable` 对象常驻，切主题时才创建、但持有整段过渡期。
 *
 * 现在改为：**1 个进度动画 + 每帧一次 `lerp`**。插值只是 4 个 float 的线性混合，
 * 比 spring 求解便宜一个数量级；动画实例从 48 降到 1，state 写入从 48 降到 1。
 *
 * 注意：**重组次数没有变**（主题色变化必然触发依赖它的 composable 重组，这是
 * Composition 层的语义，无法用 lambda modifier 推迟到 Draw 阶段）。本优化削减的是
 * 每帧的动画计算量与 invalidation 数量，不是重组次数 —— 过渡期每帧仍重组一次整树，
 * 这是"整棵子树跟着渐变"的既定代价。
 *
 * ## 打断处理
 *
 * 过渡进行中再次切主题时，以**当前显示值**（而非上一个目标）作为新起点，
 * 避免出现跳变。这是旧实现天然具备、新实现必须显式处理的性质。
 *
 * 输入是 [boardColorScheme] 现场算出的目标配色；本函数只负责"过渡"，不重新算色，故与
 * 任何色算后端（materialkolor / 预生成表）解耦。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun animateColorScheme(
    targetColorScheme: ColorScheme,
    // 审计 P1：此前写死 tween(300)，是 M2 时代的固定时长语言。
    // 主题切换是全应用规模最大的视觉事件，按 M3E 语义走 slow spatial 弹簧。
    animationSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.slowSpatialSpec(),
): ColorScheme {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(targetColorScheme) }

    LaunchedEffect(targetColorScheme) {
        // 以当前显示值（而非上一个目标）为新起点：过渡被打断时不跳变。
        from = lerpColorScheme(from, targetColorScheme, progress.value)
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec)
    }

    val fraction = progress.value
    return if (fraction >= 1f) {
        targetColorScheme
    } else {
        lerpColorScheme(from, targetColorScheme, fraction)
    }
}

/** 两套配色之间按 [fraction] 逐 role 线性插值。 */
private fun lerpColorScheme(
    from: ColorScheme,
    to: ColorScheme,
    fraction: Float,
): ColorScheme = ColorScheme(
    primary = lerp(from.primary, to.primary, fraction),
    onPrimary = lerp(from.onPrimary, to.onPrimary, fraction),
    primaryContainer = lerp(from.primaryContainer, to.primaryContainer, fraction),
    onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, fraction),
    inversePrimary = lerp(from.inversePrimary, to.inversePrimary, fraction),
    secondary = lerp(from.secondary, to.secondary, fraction),
    onSecondary = lerp(from.onSecondary, to.onSecondary, fraction),
    secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, fraction),
    onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, fraction),
    tertiary = lerp(from.tertiary, to.tertiary, fraction),
    onTertiary = lerp(from.onTertiary, to.onTertiary, fraction),
    tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, fraction),
    onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, fraction),
    background = lerp(from.background, to.background, fraction),
    onBackground = lerp(from.onBackground, to.onBackground, fraction),
    surface = lerp(from.surface, to.surface, fraction),
    onSurface = lerp(from.onSurface, to.onSurface, fraction),
    surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, fraction),
    onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, fraction),
    surfaceTint = lerp(from.surfaceTint, to.surfaceTint, fraction),
    inverseSurface = lerp(from.inverseSurface, to.inverseSurface, fraction),
    inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, fraction),
    surfaceBright = lerp(from.surfaceBright, to.surfaceBright, fraction),
    surfaceDim = lerp(from.surfaceDim, to.surfaceDim, fraction),
    surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, fraction),
    surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, fraction),
    surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, fraction),
    surfaceContainerLow = lerp(from.surfaceContainerLow, to.surfaceContainerLow, fraction),
    surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, fraction),
    error = lerp(from.error, to.error, fraction),
    onError = lerp(from.onError, to.onError, fraction),
    errorContainer = lerp(from.errorContainer, to.errorContainer, fraction),
    onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, fraction),
    outline = lerp(from.outline, to.outline, fraction),
    outlineVariant = lerp(from.outlineVariant, to.outlineVariant, fraction),
    scrim = lerp(from.scrim, to.scrim, fraction),
    primaryFixed = lerp(from.primaryFixed, to.primaryFixed, fraction),
    primaryFixedDim = lerp(from.primaryFixedDim, to.primaryFixedDim, fraction),
    onPrimaryFixed = lerp(from.onPrimaryFixed, to.onPrimaryFixed, fraction),
    onPrimaryFixedVariant = lerp(from.onPrimaryFixedVariant, to.onPrimaryFixedVariant, fraction),
    secondaryFixed = lerp(from.secondaryFixed, to.secondaryFixed, fraction),
    secondaryFixedDim = lerp(from.secondaryFixedDim, to.secondaryFixedDim, fraction),
    onSecondaryFixed = lerp(from.onSecondaryFixed, to.onSecondaryFixed, fraction),
    onSecondaryFixedVariant = lerp(from.onSecondaryFixedVariant, to.onSecondaryFixedVariant, fraction),
    tertiaryFixed = lerp(from.tertiaryFixed, to.tertiaryFixed, fraction),
    tertiaryFixedDim = lerp(from.tertiaryFixedDim, to.tertiaryFixedDim, fraction),
    onTertiaryFixed = lerp(from.onTertiaryFixed, to.onTertiaryFixed, fraction),
    onTertiaryFixedVariant = lerp(from.onTertiaryFixedVariant, to.onTertiaryFixedVariant, fraction),
)
