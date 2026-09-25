package lovehan1me.feature.player

import lovehan1me.core.util.LogUtil

// 桌面真引擎：mediamp-mpv（占位 Placeholder 早已退役）。
// Gate3-P6：`kernel` 参数在三端都已是惰性参数（本端恒返 mpv，Android 恒返 mediamp-exo，
// iOS 恒返 mediamp-avkit），保留只为与 `expect` 签名统一；`mpvOptions` 仍真用（见下）。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
    network: PlayerNetworkConfig,
    mpvOptions: PlayerMpvOptionsProvider,
): PlaybackEngine {
    LogUtil.d("DesktopMpv", "createPlaybackEngine(kernel=$kernel)")
    return DesktopMpvPlaybackEngine(network, mpvOptions)
}
