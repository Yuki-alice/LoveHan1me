package lovehan1me.core.platform

/**
 * 播放 / 窗口能力的**平台真值表**（阶段 4.3 守卫）。
 *
 * ## 为什么需要它
 * 「声称支持、实际空转」的假动作此前四处漏风：
 * - `VideoPageHost.supportsFullscreen()` 默认 `true`，而 [lovehan1me.app.bridge.NoopVideoPageHost]
 *   根本没实现 `applyFullscreen` —— 谁忘了注入真实宿主，UI 就会亮出一个按了没反应的全屏键；
 * - iOS `PlayerNetworkConfig` 的 `rewriteForGate` / `proxyUrlFor` 恒 `null`（AVPlayer 无按请求
 *   注入头），桌面 `rewriteForGate` 有实现 —— 两端能力相反，却没有任何地方把真值写下来；
 * - `supportsBrightness()` 与 `supportsFullscreen()` 默认值方向相反（`false` / `true`），
 *   同一个接口两套口径。
 *
 * 本表把「本平台这几项能力到底有没有」收敛成**一份平台常量**：宿主与网络配置的静态答案
 * 一律从它派生（而不是各写各的），守卫测试再把它钉成期望值。任何一侧单独被翻转都会转红。
 *
 * ## 分工
 * - [lovehan1me.app.bridge.VideoPageHost]：本表给出 `fullscreen` / `brightness` 的静态真值；
 *   `shouldEnterPip()` 是**运行时**判定（iOS 还要看播放器与 PiP 可用性），本表只回答
 *   `pipMode`（本平台有没有这个入口），与 `settingsPlatformCapabilities().pipMode` 同口径。
 * - [lovehan1me.feature.player.PlayerNetworkConfig]：`gateRewrite` / `mediaProxy` 表示该端
 *   这两条通道**是否有真实实现**（`true` 项的方法仍可能按上下文返回 `null`，例如网关未运行）。
 */
data class PlayerPlatformCapabilities(
    /** [lovehan1me.app.bridge.VideoPageHost.supportsFullscreen] 的静态真值。 */
    val fullscreen: Boolean,
    /** [lovehan1me.app.bridge.VideoPageHost.supportsBrightness] 的静态真值。 */
    val brightness: Boolean,
    /** 本平台是否有画中画入口（运行时 `shouldEnterPip()` 还要看播放器状态）。 */
    val pipMode: Boolean,
    /** [lovehan1me.feature.player.PlayerNetworkConfig.rewriteForGate] 是否有真实实现。 */
    val gateRewrite: Boolean,
    /** [lovehan1me.feature.player.PlayerNetworkConfig.proxyUrlFor] 是否有真实实现。 */
    val mediaProxy: Boolean,
)

expect fun playerPlatformCapabilities(): PlayerPlatformCapabilities