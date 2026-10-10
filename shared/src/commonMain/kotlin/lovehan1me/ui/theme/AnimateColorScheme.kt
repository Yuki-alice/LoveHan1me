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
 * ## 起点捕获与打断（本函数的正确性核心）
 *
 * 过渡进行中再次切主题时，新起点必须是**当前显示值**（而非上一个目标），否则跳变；
 * 静置后切换时，当前显示值恰为上一个目标。两种情况可用同一公式统一：
 * `显示值 = lerp(from, to, progress)`（进度 = 1 时恰为 to）。
 *
 * 因此 `from` / `to` 都做成 state，且 effect 内**先**用旧 `to` 重建显示值、**再**更新
 * `to = 新目标`（顺序不能反 —— 重建用的是旧目标）。返回侧同样只读 `from` / `to`、
 * 不直接读参数：目标参数已变、effect 尚未运行的窗口里返回旧 `to`（而非新目标），
 * 才不会闪一帧新配色。
 *
 * ⚠️ 反例留档（2026-10-10 实测过的回归）：`from = lerp(from, 新目标, 旧进度)` ——
 * 静置切换塌缩为空动画（硬切、0 过渡帧），打断跳变 123 通道级。
 * 守卫：`AnimateColorSchemeTransitionTest`（正 / 打断两条路径，均虚拟时钟确定性）。
 *
 * ## 调用方契约
 *
 * [targetColorScheme] 必须是 **remember 过的稳定实例**：`ColorScheme` 未重写 equals
 * （按引用比），若每次重组传入新实例，[LaunchedEffect] 会每帧重启、动画永远停在起点。
 * 两个调用点（`Theme.kt` / `SubjectTheme.kt`）均已满足。
 *
 * 输入是 [boardColorScheme] 现场算出的目标配色；本函数只负责"过渡"，不重新算色，故与
 * 任何色算后端（materialkolor / 预生成表）解耦。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun animateColorScheme(
    targetColorScheme: ColorScheme,
    // M3E motion 语义：颜色 / 透明度属 **effects** 域（spatial 管位置 / 尺寸 / 形状）。
    // 全屏主题过渡对应 slowEffects 档（design-tokens：slowEffectsSpec = "Full-screen content refresh"）。
    animationSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.slowEffectsSpec(),
): ColorScheme {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(targetColorScheme) }
    var to by remember { mutableStateOf(targetColorScheme) }

    LaunchedEffect(targetColorScheme) {
        // 先以旧 to 捕获当前显示值作为新起点，再更新 to（顺序不能反，理由见 KDoc）。
        from = if (progress.value >= 1f) to else lerpColorScheme(from, to, progress.value)
        to = targetColorScheme
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec)
    }

    val fraction = progress.value
    // 返回只依赖 from / to 两个 state：effect 就绪前显示旧 to，不闪新目标。
    return if (fraction >= 1f) to else lerpColorScheme(from, to, fraction)
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
