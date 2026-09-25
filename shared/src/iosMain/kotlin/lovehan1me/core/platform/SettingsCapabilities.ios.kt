package lovehan1me.core.platform

/**
 * iOS：引擎是 AVPlayer（`PlaybackEngineFactory.ios.kt` 同样忽略 kernel 参数），
 * 既没有多内核也没有 mpv。
 *
 * 逐项依据：
 * - `mpvAdvancedSettings = false` —— AVPlayer 通吃且无 mpv 可调；
 * - `secureMode = false` / `hapticFeedback = false` —— `applySecureMode` 与
 *   `HapticFeedback.ios.kt` 都是空实现（Gate4-平台能力）；
 * - `pipMode = **true**` —— iOS 是唯一除 Android 外真实支持 PiP 的平台，
 *   `IosVideoPageHost` 退后台进 PiP 时会读 `allowPipMode`，这一项必须保留；
 * - `meteredDataWarning = false` —— `isActiveNetworkMetered()` 恒 `false`。
 */
actual fun settingsPlatformCapabilities(): SettingsPlatformCapabilities =
    SettingsPlatformCapabilities(
        secureMode = false,
        hapticFeedback = false,
        pipMode = true,
        meteredDataWarning = false,
        mpvAdvancedSettings = false,
        mpvVideoOutput = false,
        mpvMediacodecHwdec = false,
    )
