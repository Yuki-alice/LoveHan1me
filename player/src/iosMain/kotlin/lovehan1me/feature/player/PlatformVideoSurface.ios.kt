package lovehan1me.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayerLayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCAGravityResize
import platform.QuartzCore.kCAGravityResizeAspect
import platform.QuartzCore.kCAGravityResizeAspectFill
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.clipsToBounds

/**
 * G2-3b：画面比例 → `AVPlayerLayer.videoGravity`。
 *
 * 三档是系统原生能力，切档不需要重建 item、也不打断播放：
 * - Fit → `resizeAspect`（等比，留黑边，默认）
 * - Stretch → `resize`（拉成图层形状，画面变形）
 * - Crop → `resizeAspectFill`（等比铺满，溢出裁掉）
 */
private fun videoGravityFor(mode: VideoAspectMode): String? = when (mode) {
    VideoAspectMode.Fit -> kCAGravityResizeAspect
    VideoAspectMode.Stretch -> kCAGravityResize
    VideoAspectMode.Crop -> kCAGravityResizeAspectFill
}

/**
 * M3：iOS 真渲染（`AVPlayerLayer` 嵌进 `UIKitView`）。
 *
 * 图层直连引擎的 `avPlayer`；`update` 回调里同步 `frame = bounds` 跟随布局变化
 *（旋转/分屏），`videoGravity` 保持等比。图层引用由闭包持有（`UIKitView`
 * 的 factory 必须返回 `UIView` 本体，不能用 Pair 中转）。
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
    val avEngine = engine as? IosAVPlaybackEngine
    if (avEngine == null) {
        Box(modifier.background(Color.Black))
        return
    }
    DisposableEffect(avEngine) {
        val surface = VideoSurface()
        onSurfaceAvailable(surface)
        onDispose { onSurfaceDestroyed(surface) }
    }
    // G2-3b：画面比例由引擎状态驱动（引擎不持 layer，这里订阅后改图层）。
    // 走 collect 而不是 collectAsState：后者在本文件会被解析成多个候选里的一个，
    // 类型推不出来；而这里要的只是"状态变了就重组合"。
    var aspectMode by remember(avEngine) { mutableStateOf(avEngine.state.value.videoAspect) }
    LaunchedEffect(avEngine) {
        avEngine.state.collect { aspectMode = it.videoAspect }
    }
    var playerLayer by remember { mutableStateOf<AVPlayerLayer?>(null) }
    UIKitView(
        factory = {
            val container = UIView()
            container.clipsToBounds = true
            container.backgroundColor = UIColor.blackColor
            val layer = AVPlayerLayer.playerLayerWithPlayer(avEngine.avPlayer)
            layer.videoGravity = videoGravityFor(aspectMode)
            layer.frame = container.bounds
            layer.needsDisplayOnBoundsChange = true
            container.layer.addSublayer(layer)
            playerLayer = layer
            container
        },
        update = { container ->
            playerLayer?.let { layer ->
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                layer.frame = container.bounds
                CATransaction.commit()
            }
        },
        modifier = modifier.background(Color.Black),
    )
    // 比例变化时切 gravity。放在 LaunchedEffect 里而不是 update 块：
    // UIKitView 的 update 不保证订阅 Compose 状态，挂 key 才是确定生效的写法。
    LaunchedEffect(playerLayer, aspectMode) {
        playerLayer?.let { layer ->
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            layer.videoGravity = videoGravityFor(aspectMode)
            CATransaction.commit()
        }
    }
}
