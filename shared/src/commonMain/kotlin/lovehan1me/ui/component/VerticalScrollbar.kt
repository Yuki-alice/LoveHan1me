package lovehan1me.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = Color.Unspecified,
    fadeDelayMillis: Long = 1500,
    // 审计 P1 说明：这条 tween 是**有意保留**的，不走 motionScheme。
    // 它不是状态转场，而是「静止 N 毫秒后自动隐去」的提示性淡化 ——
    // 用弹簧档位会让它收得忽快忽慢，反而像抖动；固定时长的线性淡化才是对的。
    fadeOutDurationMillis: Int = 500
): Modifier = composed {
    val resolvedColor = if (color == Color.Unspecified)
        LocalContentColor.current.copy(alpha = 0.4f)
    else color
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(fadeDelayMillis.milliseconds)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = fadeOutDurationMillis)
            )
        }
    }

    drawWithContent {
        drawContent()

        val viewportHeight = size.height
        val visibleItems = state.layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty()) {
            val avgItemHeight =
                (visibleItems.last().offset + visibleItems.last().size - visibleItems.first().offset) / visibleItems.size
            val totalEstimatedHeight = state.layoutInfo.totalItemsCount * avgItemHeight

            if (totalEstimatedHeight > viewportHeight) {
                val firstItem = visibleItems.first()
                val scrolledPastPx = (firstItem.index * avgItemHeight) - firstItem.offset
                val scrollbarHeight = (viewportHeight * viewportHeight / totalEstimatedHeight)
                    .coerceAtLeast(32.dp.toPx())
                val scrollFraction = scrolledPastPx / (totalEstimatedHeight - viewportHeight)
                val scrollbarOffsetY =
                    (scrollFraction * (viewportHeight - scrollbarHeight)).coerceIn(
                        0f,
                        viewportHeight - scrollbarHeight
                    )

                drawRoundRect(
                    color = resolvedColor.copy(alpha = resolvedColor.alpha * alpha.value),
                    topLeft = Offset(size.width - width.toPx(), scrollbarOffsetY),
                    size = Size(width.toPx(), scrollbarHeight),
                    cornerRadius = CornerRadius(width.toPx() / 2)
                )
            }
        }
    }
}