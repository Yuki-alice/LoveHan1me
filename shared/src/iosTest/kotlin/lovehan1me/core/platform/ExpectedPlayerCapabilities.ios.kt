package lovehan1me.core.platform

/** iOS 期望：全屏与亮度都真实现、真 PiP；但网关改写与媒体代理两条通道恒 null（无注入口）。 */
actual val expectedPlayerCapabilities: PlayerPlatformCapabilities =
    PlayerPlatformCapabilities(
        fullscreen = true,
        brightness = true,
        pipMode = true,
        gateRewrite = false,
        mediaProxy = false,
    )