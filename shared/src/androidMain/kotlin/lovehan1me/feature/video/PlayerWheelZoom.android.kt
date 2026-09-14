package lovehan1me.feature.video

import androidx.compose.ui.Modifier

/**
 * Android 是触摸端：**没有滚轮输入**，因此这里是恒等实现。
 *
 * 不是"功能缺失"—— 缩放走的是同一套双指手势（`VideoPlayerUi` 里的 pinch 分支）；
 * "没有这个输入设备"就该没有这个入口，而不是塞一个永远不会被触发的监听器。
 */
internal actual fun Modifier.playerWheelZoom(
    scale: Float,
    onScaleChange: (Float) -> Unit,
): Modifier = this
