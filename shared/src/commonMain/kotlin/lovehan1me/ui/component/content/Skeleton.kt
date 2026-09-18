package lovehan1me.ui.component.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 骨架屏（对标 animeko 的 `thirdparty/placeholder`，自己实现，不引第三方）。
 *
 * 用法两种：
 *
 * 1. 整屏网格 —— 直接用 [SkeletonVideoGrid]，它自己驱动 shimmer 动画；
 * 2. 自定义排布 —— 用 [rememberSkeletonProgress] 拿一个共享进度，再给每个块挂
 *    [Modifier.skeleton]。
 *
 * ### 为什么进度要由父级统一驱动
 *
 * 每个骨架块各自 `rememberInfiniteTransition` 会让一屏起来几十个动画（8 卡 × 3 块 = 24 个），
 * 白白吃帧；共享一个 progress 后整屏只有一个动画。
 */
private const val SHIMMER_DURATION_MS = 1300

/** 一屏骨架共用的 shimmer 进度（0f→1f 循环）。 */
@Composable
fun rememberSkeletonProgress(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonProgress",
    )
    return progress
}

/**
 * 骨架底色与高光色。走 M3 语义色而非写死灰值，深色/自定义主题下自动适配。
 *
 * 浅色主题下 `surfaceContainerHighest` 最灰、`surfaceContainerLowest` 接近白，
 * 两者对比度刚好做微光扫过。
 */
@Composable
fun rememberSkeletonColors(): Pair<Color, Color> = Pair(
    MaterialTheme.colorScheme.surfaceContainerHighest,
    MaterialTheme.colorScheme.surfaceContainerLowest,
)

/**
 * 给任意组件套一层 shimmer 微光。
 *
 * @param progress 来自 [rememberSkeletonProgress]；同一屏请共用同一个值。
 * @param baseColor 底色，取 [rememberSkeletonColors] 的 first。
 * @param highlightColor 高光色，取 [rememberSkeletonColors] 的 second。
 */
fun Modifier.skeleton(
    progress: Float,
    shape: Shape,
    baseColor: Color,
    highlightColor: Color,
): Modifier = this
    .clip(shape)
    .drawBehind {
        val w = size.width
        // 高光带从 -w 扫到 +w（乘 2 让"扫出边界"占一半时间，节奏更自然）
        val centerX = (progress * 2f - 0.5f) * w
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to baseColor,
                    0.5f to highlightColor,
                    1.0f to baseColor,
                ),
                start = Offset(centerX - w * 0.5f, 0f),
                end = Offset(centerX + w * 0.5f, 0f),
            ),
        )
    }

/**
 * 单张视频卡片的骨架：16:9 封面 + 两行标题。
 *
 * @param progress 同屏共享的 shimmer 进度。
 */
@Composable
fun SkeletonVideoCard(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val (base, highlight) = rememberSkeletonColors()

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .skeleton(progress, shape, base, highlight),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(14.dp)
                .skeleton(progress, shape, base, highlight),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(12.dp)
                .skeleton(progress, shape, base, highlight),
        )
    }
}

/**
 * 整屏视频网格骨架。搜索/列表首屏加载时用它替代转圈，
 * 让用户在内容到达前就看到最终布局形状。
 */
@Composable
fun SkeletonVideoGrid(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    itemCount: Int = 8,
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    val progress = rememberSkeletonProgress()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // 骨架不能滚：它只是占位，滚动会让人以为内容已到
        userScrollEnabled = false,
    ) {
        items(itemCount) {
            SkeletonVideoCard(progress = progress)
        }
    }
}
