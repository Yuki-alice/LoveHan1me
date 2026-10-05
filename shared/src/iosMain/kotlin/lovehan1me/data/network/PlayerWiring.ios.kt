package lovehan1me.data.network

import lovehan1me.core.constant.USER_AGENT
import lovehan1me.feature.player.PlayerNetworkConfig

/**
 * iOS 侧网络配置（Gate3-P1）。
 *
 * ## 现状（如实记录，不粉饰）
 * - **UA**：真值。此前是**空串** —— 一旦引擎把它用上，等于给 CDN 发一个空 UA，
 *   被拒都不知道为什么。
 * - **网关改写**：恒 null。AVPlayer 的出口由 `AVURLAsset` 自己发，而网关要的
 *   `X-Ech-Target` 头在 iOS 上没有可靠的按请求注入口：
 *   `AVURLAssetHTTPHeaderFieldsKey` 官方文档明确不保证对所有请求生效，HLS 分片
 *   更是不经过它。**写出一个"有时生效"的改写，比承认做不到更糟**，
 *   所以这里保持 null，并把事实摆到用户面前
 *   （`use_ech_gate_summary` 已写明"iOS 播放仍走直连，不经网关"）。
 *   正解是 `AVAssetResourceLoaderDelegate` 接管取数，见
 *   `docs/plan/后期攻坚-网络与全端-审阅与规划.md` 阶段 3.2。
 * - **代理**：恒 null。iOS 没有"URL 形式代理"的注入口（那是给 ffmpeg/mpv 的形状）；
 *   系统代理由 AVFoundation/NSURLSession 自己跟随系统设置生效，无需也无法指定。
 * - **网关结局上报**（[PlayerNetworkConfig.onGateLoadOutcome]）：**不覆写**，保持默认空实现。
 *   iOS 播放既然不过网关（上行 [rewriteForGate] 恒 null），就没有"网关链路结局"可报；
 *   在这里编一条 Gate 记录只会往该域的健康里灌假数据。
 *
 * ⚠️ 结论：受限网络下 **iOS 能浏览、不能播放**。这是当前已知的最大缺口，
 * 不是"忘了接"——改它要动引擎的资源加载层，不是本文件能补的。
 */
actual fun defaultPlayerNetworkConfig(): PlayerNetworkConfig = object : PlayerNetworkConfig {
    override val userAgent: String = USER_AGENT
    override fun proxyUrlFor(mediaUri: String): String? = null
    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? = null
}
