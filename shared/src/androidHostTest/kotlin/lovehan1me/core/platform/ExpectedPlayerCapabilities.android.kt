package lovehan1me.core.platform

/** Android 期望：全屏与亮度真实现、真 PiP、网关改写接了实现；Exo 不消费 URL 形式代理故 mediaProxy 为 false。 */
actual val expectedPlayerCapabilities: PlayerPlatformCapabilities =
    PlayerPlatformCapabilities(
        fullscreen = true,
        brightness = true,
        pipMode = true,
        gateRewrite = true,
        mediaProxy = false,
    )