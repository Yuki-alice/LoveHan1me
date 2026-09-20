package lovehan1me.core.platform

/**
 * 桌面：能力比 Android 窄一半，且**宽窄与内核设置无关**（`createPlaybackEngine` 忽略 kernel）。
 *
 * 逐项依据：
 * - `secureMode = false` —— `applySecureMode` 在 desktopMain 是空实现
 *   （桌面窗口没有 FLAG_SECURE 对等能力）；
 * - `hapticFeedback = false` —— `HapticFeedback.desktop.kt` 是 `HapticFeedback {}`（Gate3-平台能力）；
 * - `pipMode = false` —— `DesktopVideoPageHost.shouldEnterPip()` 恒 `false`，
 *   注释明确「桌面端恒不进入 PiP，是接口要求的空覆写，不是遗漏实现」；
 * - `meteredDataWarning = false` —— `isActiveNetworkMetered()` 恒 `false`，守卫永不触发；
 * - `playerKernelSelection = false` —— 只有 mediamp-mpv 一个真引擎，kernel 参数被忽略；
 * - `mpvAdvancedSettings = true` —— 桌面引擎**就是** mpv，这一页在桌面本应生效
 *   （参数由 `DesktopMpvPlaybackEngine.applyMpvSettings` 下发）；
 * - `mpvVideoOutput = false` —— 渲染面是 `PlatformVideoSurface.desktop` 的 Skia 面，
 *   直接持有 mediamp 的 player 且 `vo` 由 mediamp 持有；运行时改 `vo` 会重建视频输出、
 *   打断与嵌入窗口的绑定，故不提供该开关（不是漏实现）；
 * - `mpvMediacodecHwdec = false` —— 无 mediacodec，hwdec 只保留 auto / HW / SW 三档语义。
 */
actual fun settingsPlatformCapabilities(): SettingsPlatformCapabilities =
    SettingsPlatformCapabilities(
        secureMode = false,
        hapticFeedback = false,
        pipMode = false,
        meteredDataWarning = false,
        playerKernelSelection = false,
        mpvAdvancedSettings = true,
        mpvVideoOutput = false,
        mpvMediacodecHwdec = false,
    )
