package lovehan1me.core.platform

/** 桌面期望：全屏有（AWT）、亮度无、无 PiP、网关改写与媒体代理两条通道都接了实现。 */
actual val expectedPlayerCapabilities: PlayerPlatformCapabilities =
    PlayerPlatformCapabilities(
        fullscreen = true,
        brightness = false,
        pipMode = false,
        gateRewrite = true,
        mediaProxy = true,
    )