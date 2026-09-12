package lovehan1me.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * 内容限宽列 —— 把内容限制到**可读行宽**并水平居中。
 *
 * 这是「栅格体系」里最后补上的一块。此前全项目只有一个设置页做了限宽，而且是手写的
 * `Box(TopCenter) { Box(widthIn(max).fillMaxSize()) }`；其余页面要么完全不限，要么写了
 * 一个**恒不生效**的限制（`widthIn(max = 局部窗口宽度)` —— 上限等于可用宽度，等于没限）。
 *
 * **为什么必须居中，不能只挂 `widthIn`**：`Modifier.fillMaxSize().widthIn(max = 720.dp)`
 * 里 `fillMaxSize` 在外层，外层节点仍占满父容器，内层内容被压到 720dp 后**贴左上角**排布 ——
 * 宽屏上会明显偏左。限宽一定要与「居中」成对出现，所以收成一个容器，避免各页面自己忘。
 *
 * 用法：
 * ```
 * ContentColumn(maxWidth = HanimeDefaults.Widths.readingMax) {
 *     // 正文 / 评论 / 表单
 * }
 * ```
 *
 * 传 [Dp.Unspecified] 表示**不限宽**（仅居中），供「双栏时由右栏自己限宽」这类场景使用。
 *
 * @param maxWidth 内容最大宽度；[Dp.Unspecified] 时不施加限制。
 * @param modifier 作用于最外层容器（例如页面级 padding）。
 */
@Composable
fun ContentColumn(
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = contentAlignment,
    ) {
        Box(
            modifier = Modifier
                .let { if (maxWidth == Dp.Unspecified) it else it.widthIn(max = maxWidth) }
                .fillMaxSize(),
            content = content,
        )
    }
}
