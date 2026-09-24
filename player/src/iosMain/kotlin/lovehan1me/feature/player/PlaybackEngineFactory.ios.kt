package lovehan1me.feature.player

import lovehan1me.core.util.LogUtil

// M3：iOS 真引擎（AVPlayer；占位 Placeholder 退役）。
// kernel 参数暂忽略（AVPlayer 通吃 HLS/渐进；Exo/Mpv 语义在 iOS 无区分）。
// network/mpvOptions 暂未使用（iOS 直连兜底，网关待原生改写后接入），签名保留以统一。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
    network: PlayerNetworkConfig,
    mpvOptions: PlayerMpvOptionsProvider,
): PlaybackEngine {
    LogUtil.d("IosAVPlayer", "createPlaybackEngine(kernel=$kernel)")
    return IosAVPlaybackEngine()
}
