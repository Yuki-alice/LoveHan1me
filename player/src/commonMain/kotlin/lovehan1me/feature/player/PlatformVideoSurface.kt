package lovehan1me.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * P5-1：视频渲染插槽（最小拆层）。
 *
 * `VideoPlayerUi` 本体留在 :shared（1937 行，整页迁移属 Gate4 的平台补位批次）；
 * 本插槽是它与渲染面之间唯一接口。Gate3-P5/P6 后三端统一为 mediamp 自家 Surface：
 * - androidMain：mediamp-exo 的 `PlayerView`（mpv 内核已砍，原 SurfaceView 分支删除）；
 * - desktopMain：mediamp-mpv 的 Skia 面；
 * - iosMain：mediamp-avkit 的 `AVPlayerLayer`。
 *
 * 非本端引擎时画黑盒兜底，不崩。
 */
@Composable
expect fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier = Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit = {},
    onSurfaceDestroyed: (VideoSurface) -> Unit = {},
)
