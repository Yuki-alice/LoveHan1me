package lovehan1me.feature.video

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 桌面：**Ctrl+滚轮 = 缩放画面**（与浏览器 / 系统看图的语义一致，用来模拟触摸侧的双指缩放）。
 *
 * 只认带 Ctrl 的滚轮：裸滚轮要留给页面滚动，否则鼠标停在播放器上就滚不动页面了。
 */
internal actual fun Modifier.playerWheelZoom(
    scale: Float,
    onScaleChange: (Float) -> Unit,
): Modifier = this.pointerInput(scale) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (deltaY != 0f) {
                    // 与双指缩放同一套"跟随手指"的语义：按增量走，不记起点
                    onScaleChange((scale * (1f - deltaY * 0.002f)).coerceIn(0.5f, 4f))
                }
            }
        }
    }
}
