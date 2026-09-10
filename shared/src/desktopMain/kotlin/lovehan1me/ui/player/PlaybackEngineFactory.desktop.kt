package lovehan1me.ui.player

import lovehan1me.core.util.LogUtil

// M3：桌面真引擎（mediamp-mpv；占位 Placeholder 退役）。
// kernel 参数暂忽略（mpv 通吃 mp4/HLS；Exo/System 语义在桌面无区分）。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
    allowCast: Boolean,
): PlaybackEngine {
    LogUtil.d("DesktopMpv", "createPlaybackEngine(kernel=$kernel, allowCast=$allowCast)")
    return DesktopMpvPlaybackEngine()
}
