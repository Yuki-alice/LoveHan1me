/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.support

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

inline fun Modifier.ifThen(
    condition: Boolean,
    modifier: Modifier.Companion.() -> Modifier?,
): Modifier {
    return if (condition) this.then(modifier(Modifier.Companion) ?: Modifier) else this
}

inline fun <T> Modifier.ifNotNullThen(
    value: T,
    modifier: Modifier.Companion.(T & Any) -> Modifier?,
): Modifier {
    return if (value != null) this.then(modifier(Modifier.Companion, value) ?: Modifier) else this
}

/** 描边、分隔线这类不该抢注意力的颜色。 */
@Composable
fun Color.slightlyWeaken(): Color {
    return copy(alpha = 0.618f)
}

/** 输入框底色这类要退到背景里去的颜色。 */
@Composable
fun Color.stronglyWeaken(): Color {
    return copy(alpha = 1 - 0.618f)
}

/**
 * 隐藏但仍占位：控件从 expanded 收回到 slider-only 时不能让画面跳动，
 * 同时要把初始 pass 的指针事件吃掉，否则透明层还能点到底下的东西。
 */
internal fun Modifier.keepLayoutWhenHidden(hidden: Boolean): Modifier {
    if (!hidden) return this
    return alpha(0f)
        .clearAndSetSemantics { }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
}
