package lovehan1me.video.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 播放器的槽位骨架：把"画在哪一层"与"画什么"分开。
 *
 * 参数顺序即绘制顺序，自下而上：
 * [video]（画面与其上方的弹幕）→ [gestureHost]（手势捕获层与其 HUD）→ [backdrop]（海报/暗化）
 * → [topBar] → [center]（长按徽标、缓冲圈、中央键、音量 pill）→ [bottomBar]
 * → [overlayHost]（状态卡与弹窗）。
 *
 * [overlayHost] 压在最高一层，且**不在任何会自动隐藏的槽里**：弹幕设置这类弹窗若组合在
 * 底栏的可见性容器内，控件一自动隐藏就会被连坐销毁，表现成"弹窗自己没了"。
 *
 * 手势层与 [center] 里的各件都靠 [BoxScope] 的 `align` 定位，故除 [video] 外都带接收者。
 */
@Composable
fun VideoScaffold(
    modifier: Modifier = Modifier,
    /** 画面槽。引擎渲染面与弹幕层必须共用同一矩形，故由调用方在同一槽内叠放。 */
    video: @Composable () -> Unit,
    gestureHost: @Composable BoxScope.() -> Unit = {},
    backdrop: @Composable BoxScope.() -> Unit = {},
    topBar: @Composable BoxScope.() -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
    bottomBar: @Composable BoxScope.() -> Unit = {},
    overlayHost: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier) {
        video()
        gestureHost()
        backdrop()
        topBar()
        center()
        bottomBar()
        overlayHost()
    }
}