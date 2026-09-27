/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

// M3 运动 token：md.sys.motion.easing.standard.{decelerate,accelerate}
@Stable
val StandardDecelerateEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1f)

@Stable
val StandardAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 1f, 1f)

private const val STANDARD_DECELERATE = 250
private const val STANDARD_ACCELERATE = 200

fun enterFade(): EnterTransition = fadeIn(
    tween(durationMillis = STANDARD_DECELERATE, easing = StandardDecelerateEasing),
)

fun exitFade(): ExitTransition = fadeOut(
    tween(durationMillis = STANDARD_ACCELERATE, easing = StandardAccelerateEasing),
)

// Row/Column 用展开-收缩而不是淡入淡出：控件条挤在画面边缘，淡出会露出下面的弹幕。
private val expandShrinkSpring = spring<IntSize>(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntSize.VisibilityThreshold,
)

@Composable
fun AniAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = enterFade(),
    exit: ExitTransition = exitFade(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}

@Composable
fun RowScope.AniAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = expandHorizontally(expandShrinkSpring, expandFrom = Alignment.Start),
    exit: ExitTransition = shrinkHorizontally(expandShrinkSpring, shrinkTowards = Alignment.Start),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}

@Composable
fun ColumnScope.AniAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = expandVertically(expandShrinkSpring, expandFrom = Alignment.Top),
    exit: ExitTransition = shrinkVertically(expandShrinkSpring, shrinkTowards = Alignment.Top),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}
