package lovehan1me.ui.player

import lovehan1me.utils.LogUtil

// M3：iOS 真引擎（AVPlayer；占位 Placeholder 退役）。
// kernel 参数暂忽略（AVPlayer 通吃 HLS/渐进；Exo/Mpv 语义在 iOS 无区分）。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
    allowCast: Boolean,
): PlaybackEngine {
    LogUtil.d("IosAVPlayer", "createPlaybackEngine(kernel=$kernel, allowCast=$allowCast)")
    return IosAVPlaybackEngine()
}
