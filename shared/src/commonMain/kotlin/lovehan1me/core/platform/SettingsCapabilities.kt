package lovehan1me.core.platform

/**
 * 设置项的**平台能力表**。
 *
 * ## 为什么需要它
 * 设置 UI 铺在 `commonMain`，但很多能力只有部分平台实现（Android 独占 FLAG_SECURE、
 * 桌面独占 mpv、iOS 既无 mpv 也无触感…）。此前判据散落各处，其中一处是
 * 「按设置值 + 名字匹配」判断：
 *
 * ```kotlin
 * mpvSettingsEnabled = (kernel == PlayerKernel.MpvPlayer.name)   // ← 错判据
 * ```
 *
 * 后果是**桌面用户一旦选了 ExoPlayer，正在跑 mpv 的桌面端反而把「MPV 高级设置」置灰了**
 * —— 用户被自己的假设置锁在门外。
 *
 * 本文件把「某平台是否可能让这一项生效」收敛成一份**平台常量**；
 * 设置 UI 一律按它决定「渲染 / 不渲染」，不再按设置值猜。
 *
 * ## 与 `PlaybackEngine.supportsSuperResolution()` 的分工
 * 那是**引擎实例**能力：要引擎在手，且构造引擎要几百 ms（播放器 lazy）。
 * 本表是**设置页**需要的静态答案 —— 设置页没有引擎实例，也不该为了渲染一行而初始化播放器。
 * 两者判据不同、用途不同，不可互相替代：
 * 「播放中要不要显示超分入口」问引擎，「设置页要不要列出这一项」问本表。
 */
data class SettingsPlatformCapabilities(
    /** 是否支持防截屏（Android 的 FLAG_SECURE 或对等能力）。 */
    val secureMode: Boolean,
    /** 是否有触感反馈硬件通路。 */
    val hapticFeedback: Boolean,
    /** 是否支持画中画。 */
    val pipMode: Boolean,
    /** 是否有「计费网络」概念 —— 没有的话「移动数据播放提醒」开关毫无意义。 */
    val meteredDataWarning: Boolean,
    /** 是否存在多个语义不同的播放内核。false ⇒ 内核选择项应当隐藏。 */
    val playerKernelSelection: Boolean,
    /** 「MPV 高级设置」在本平台是否**可能**生效（具体还要看当前内核）。 */
    val mpvAdvancedSettings: Boolean,
    /** 能否在运行时切换 mpv 视频输出（`vo` 的 gpu / gpu-next）。 */
    val mpvVideoOutput: Boolean,
    /** hwdec 档位是否含 Android 专属语义（mediacodec / vulkan-copy）。 */
    val mpvMediacodecHwdec: Boolean,
)

expect fun settingsPlatformCapabilities(): SettingsPlatformCapabilities
