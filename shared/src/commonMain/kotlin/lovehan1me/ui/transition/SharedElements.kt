package lovehan1me.ui.transition

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * 共享元素过渡所需的 [SharedTransitionScope]。
 *
 * `SharedTransitionLayout` 在 `SharedTopNavigation` 里已经包住了整个 `NavDisplay`
 * （之前只 import 了却没用过，等于舞台搭好没上演员）。但 `Modifier.sharedElement`
 * 必须在该 layout 的接收者作用域里调用，而真正的调用点（视频卡片、详情页封面）
 * 深埋在导航的若干层之下。这里用 CompositionLocal 把 scope 下发，调用方只管用
 * [sharedCoverElement]，不用自己层层传 scope。
 *
 * 取不到时返回 null（预览环境、或没包 SharedTransitionLayout 的地方），
 * 此时修饰词退化为恒等，组件照常渲染 —— 因此加共享元素不会影响预览与单测。
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** 封面共享元素的 key 前缀：两侧用同一 key 才会配对。 */
private const val COVER_KEY_PREFIX = "cover-"

/** 生成封面共享元素的 key。卡片与详情页各自调用一次，入参相同即可配对。 */
fun coverSharedElementKey(videoCode: String): String = COVER_KEY_PREFIX + videoCode

/**
 * 封面共享元素修饰词：key 相同的两个封面（卡片 ↔ 详情）在页面切换时做形变过渡。
 *
 * @param key 为空、或当前不在 `SharedTransitionLayout` 内时，退化为原 [Modifier]。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCoverElement(key: String?): Modifier {
    if (key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current ?: return this
    return with(sharedScope) {
        this@sharedCoverElement.sharedElement(
            rememberSharedContentState(key),
            animatedScope,
        )
    }
}
