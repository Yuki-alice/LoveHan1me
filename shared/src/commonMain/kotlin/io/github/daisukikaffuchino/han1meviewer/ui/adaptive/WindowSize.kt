package io.github.daisukikaffuchino.han1meviewer.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * 自适应宽度采样与下发（Compose 运行时部分）。断点定义见 `Breakpoints.kt`。
 *
 * **为什么需要「内容宽度」而不只是「窗口宽度」**：原先所有宽度采样都读
 * `LocalWindowInfo.containerSize`，那是**整窗宽度**。而常驻抽屉会吃掉窗口左侧约 360dp，
 * 内容区可用宽度显著更小——840dp 窗口下内容区仅约 480dp，按整窗宽算列数会超算、
 * 卡片被压扁。因此 [ProvideContentWidth] 在导航宿主处测量真实内容区宽度并下发，
 * 消费方一律用 [rememberContentWidthDp]，**不要在 UI 层直接读 `LocalWindowInfo`**。
 */

/**
 * 内容区可用宽度（dp）。由宿主经 [ProvideContentWidth] 下发；
 * 未下发（组件预览 / 独立预览）时回退整窗宽度，保证预览不崩。
 */
private val LocalContentWidth = compositionLocalOf<Dp?> { null }

/**
 * 整窗宽度（dp）。
 *
 * 仅宿主层分档时使用；UI 消费方请用 [rememberContentWidthDp]，否则会在常驻抽屉
 * 场景下把抽屉宽度也算进去。
 */
@Composable
fun rememberWindowWidthDp(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}

/** 内容区可用宽度（dp）：常驻抽屉等 chrome 的占宽已扣除。 */
@Composable
fun rememberContentWidthDp(): Dp = LocalContentWidth.current ?: rememberWindowWidthDp()

/** 按整窗宽度分档。用于「是否上常驻 chrome」这类宿主级决策。 */
@Composable
fun rememberWindowWidthSizeClass(): WindowWidthSizeClass =
    windowWidthSizeClassOf(rememberWindowWidthDp())

/** 按内容区宽度分档。**布局决策（列数 / 卡片密度）应优先用这个。** */
@Composable
fun rememberContentWidthSizeClass(): WindowWidthSizeClass =
    windowWidthSizeClassOf(rememberContentWidthDp())

/**
 * 宿主用：测量真实内容区宽度并下发给子树。
 *
 * 放进导航内容槽（如 `PermanentNavigationDrawer` 的 content）即可自动扣除抽屉占宽；
 * 放进 `ModalNavigationDrawer` 的 content 则等于整窗宽度（模态抽屉悬浮、不占宽）。
 */
@Composable
fun ProvideContentWidth(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalContentWidth provides maxWidth) {
            content()
        }
    }
}
