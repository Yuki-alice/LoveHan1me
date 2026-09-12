package lovehan1me.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset

/**
 * 页面级横向共享轴转场（顶层 Tab / 路由 push-pop）。
 *
 * 审计 P1：此前这里是 M2 时代的产物 —— `tween(300, FastOutSlowInEasing)` + 手写
 * `ProgressThreshold` 错峰淡入，与 M3 Expressive 的弹簧物理不是同一套语言。
 * 现在改为 motionScheme 驱动：
 * - **位移走 slow spatial**：页面级转场位移距离最大（整屏），是 Expressive 里 slow 档的
 *   目标场景，弹簧收尾带来的"呼吸感"正是此前缺的那口气；
 * - **淡入 default / 淡出 fast effects**：进场略缓给内容一点时间，旧内容别赖着不走。
 *
 * 与旧实现的差异（有意为之）：不再做淡入淡出的错峰（原来先淡出 35% 再淡入），
 * 改为位移+淡化同时进行 —— 弹簧档位自己会收出节奏，手工错峰反而会和物理打架。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun sharedAxisX(
    initialOffsetX: (fullWidth: Int) -> Int,
    targetOffsetX: (fullWidth: Int) -> Int,
    spatialSpec: FiniteAnimationSpec<IntOffset> = MaterialTheme.motionScheme.slowSpatialSpec(),
    enterEffectSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec(),
    exitEffectSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec(),
): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = spatialSpec,
        initialOffsetX = initialOffsetX,
    ) + fadeIn(enterEffectSpec),
    initialContentExit = slideOutHorizontally(
        animationSpec = spatialSpec,
        targetOffsetX = targetOffsetX,
    ) + fadeOut(exitEffectSpec),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun fadeScaleIn(
    effectSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec(),
    spatialSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastSpatialSpec(),
): EnterTransition = fadeIn(effectSpec) + scaleIn(
    animationSpec = spatialSpec,
    initialScale = 0.92f,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun fadeScaleOut(
    effectSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec(),
): ExitTransition = fadeOut(effectSpec)

/**
 * 内容切换的淡入淡出（P6 动效统一）。
 *
 * 取代散落各处的 `fadeIn(tween(300)) togetherWith fadeOut(tween(200))` —— 那两串数字
 * 是 M2 时代的 tween 语言，与 M3 Expressive 的弹簧物理不是同一套。进来用 default 档
 * （略慢，给内容一点呼吸），出去用 fast 档（别让旧内容赖着不走）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun contentFade(
    enterSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec(),
    exitSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec(),
): ContentTransform = fadeIn(enterSpec) togetherWith fadeOut(exitSpec)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun fadeScale(
    effectSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec(),
    spatialSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastSpatialSpec(),
): ContentTransform = ContentTransform(
    targetContentEnter = fadeScaleIn(effectSpec, spatialSpec),
    initialContentExit = fadeScaleOut(effectSpec),
)
