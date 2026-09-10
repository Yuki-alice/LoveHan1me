package lovehan1me.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayerLayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCAGravityResizeAspect
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.clipsToBounds

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
    var playerLayer: AVPlayerLayer? = null
    UIKitView(
        factory = {
            val container = UIView()
            container.clipsToBounds = true
            container.backgroundColor = UIColor.blackColor
            val layer = AVPlayerLayer.playerLayerWithPlayer(avEngine.avPlayer)
            layer.videoGravity = kCAGravityResizeAspect
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
}
