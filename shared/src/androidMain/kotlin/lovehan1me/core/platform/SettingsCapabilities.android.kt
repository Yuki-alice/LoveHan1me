package lovehan1me.core.platform

/**
 * Android：FLAG_SECURE / Vibrator / PiP / ConnectivityManager 计费网络判定都有真实实现；
 * **但引擎侧已收敛成一条**（Gate3-P6 砍掉 mpv 内核后只剩 mediamp-exo，
 * `PlaybackEngineFactory` 恒返它），故 MPV 高级设置、mpv 的 `vo` 与 mediacodec hwdec
 * 档位三项全部置 false —— 那不是"未实现"，是本平台不再有该项语义。
 *
 * 判据一律按平台能力，不按设置值：历史存下的 `switch_player_kernel = MpvPlayer`
 * 不会让这里的答案变化（设置值只影响存储，不再影响引擎选择）。
 */
actual fun settingsPlatformCapabilities(): SettingsPlatformCapabilities =
    SettingsPlatformCapabilities(
        secureMode = true,
        hapticFeedback = true,
        pipMode = true,
        meteredDataWarning = true,
        mpvAdvancedSettings = false,
        mpvVideoOutput = false,
        mpvMediacodecHwdec = false,
    )

/**
 * Android 播放能力真值。依据：
 * - `fullscreen = true` —— `AndroidVideoPageHost.applyFullscreen` 真实现（方向 + 系统栏）；
 * - `brightness = true` —— `window.screenBrightness` 覆盖真实现；
 * - `pipMode = true` —— 真支持 PiP（`settingsPlatformCapabilities().pipMode` 同口径）；
 * - `gateRewrite = true` —— `PlayerWiring.android.rewriteForGate` 接 `gateRewrite`；
 * - `mediaProxy = false` —— Exo 的 `DefaultHttpDataSource` 不消费 URL 形式代理，
 *   `proxyUrlFor` 恒 `null`（显式代理走 JVM 系统属性，与本方法无关）。
 */
actual fun playerPlatformCapabilities(): PlayerPlatformCapabilities =
    PlayerPlatformCapabilities(
        fullscreen = true,
        brightness = true,
        pipMode = true,
        gateRewrite = true,
        mediaProxy = false,
    )
