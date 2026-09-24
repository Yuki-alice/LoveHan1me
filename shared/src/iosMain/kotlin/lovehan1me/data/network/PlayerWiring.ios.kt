package lovehan1me.data.network

import lovehan1me.feature.player.PlayerNetworkConfig

/**
 * iOS 侧网络配置（Gate3-P1）：AVPlayer 走直连/系统代理兜底，ECH 网关待
 * `AVAssetResourceLoaderDelegate` 原生改写后接入（见引擎内注释），此前三项全惰性。
 */
actual fun defaultPlayerNetworkConfig(): PlayerNetworkConfig = object : PlayerNetworkConfig {
    override val userAgent: String = ""
    override fun proxyUrlFor(mediaUri: String): String? = null
    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? = null
}
