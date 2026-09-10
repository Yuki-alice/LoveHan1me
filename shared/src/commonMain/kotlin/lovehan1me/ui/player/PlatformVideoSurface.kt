package lovehan1me.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * P5-1：视频渲染插槽（最小拆层）。
 *
 * `VideoPlayerUi` 本体留在 :app（1937 行，P6d 视频页批次才迁移）；本插槽是 P6d 迁移视频页时
 * 唯一直接复用的接口：
 * - androidMain：现 SurfaceView 代码原样搬入（含 SurfaceHolder 生命周期回调 + Mpv 的
 *   updateSurfaceSize）。
 * - desktopMain / iosMain：占位 Box（P5-2 接真引擎渲染时充实）。
 */
@Composable
expect fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier = Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit = {},
    onSurfaceDestroyed: (VideoSurface) -> Unit = {},
)
