package lovehan1me.core.platform

/**
 * Android：能力齐备 —— 三个语义不同的内核（System/Exo/Mpv）、FLAG_SECURE、
 * Vibrator、PiP、ConnectivityManager 的计费网络判定全部有真实实现。
 */
actual fun settingsPlatformCapabilities(): SettingsPlatformCapabilities =
    SettingsPlatformCapabilities(
        secureMode = true,
        hapticFeedback = true,
        pipMode = true,
        meteredDataWarning = true,
        playerKernelSelection = true,
        mpvAdvancedSettings = true,
        mpvVideoOutput = true,
        mpvMediacodecHwdec = true,
    )
