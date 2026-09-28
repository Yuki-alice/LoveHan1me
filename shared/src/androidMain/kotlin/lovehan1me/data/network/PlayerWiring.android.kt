package lovehan1me.data.network

import lovehan1me.core.constant.USER_AGENT
import lovehan1me.feature.player.PlayerNetworkConfig

/**
 * Android 侧网络配置（Gate3-P1）。
 *
 * 与桌面端的**唯一**差异是 [proxyUrlFor] 的返回值，且这是刻意的，不是"忘了接"：
 * 见该方法的说明。改写判定两端共用 [gateRewrite]（同一份 [EchGatePolicy]）。
 */
actual fun defaultPlayerNetworkConfig(): PlayerNetworkConfig = object : PlayerNetworkConfig {
    override val userAgent: String = USER_AGENT

    /**
     * Exo 侧**不消费**本方法的返回值，故恒为 null。
     *
     * 原因：Android 播放链路经 media3 的 `DefaultHttpDataSource` 走 `HttpURLConnection`
     * 取代理，**没有"URL 形式的代理"可以传进去**（那是给 ffmpeg 用的形状）。
     * Android 的显式代理由 `HanimeProxySelector.rebuildNetwork()` 写入的 JVM 系统属性
     * 生效，与本方法无关。
     *
     * ⚠️ 判据本体是 jvmMain 的 [resolveMediaProxyUrl]（android 与桌面同属 jvm 中间源集）。
     * 此前这里写过**第二份**判据（判"网关有没有在跑"而不是"这个 URL 是不是网关地址"），
     * 与桌面在"网关在跑但 URL 是真实源站"的回退分支上结论相反 —— 今天不产生故障
     * 只因为没人调用它。那份判据已删除。
     *
     * 将来若要让 Exo 也显式走用户代理，正确做法是**自建带 `Proxy` 的 DataSource**
     * （在 `open()` 里用 `InetSocketAddress` 显式指定代理），而不是把 URL 塞进本方法。
     * 另：系统属性这条通道在 Android 上是否被 Exo 的 HttpDataSource 尊重，**未实测**。
     */
    override fun proxyUrlFor(mediaUri: String): String? = null

    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? =
        gateRewrite(uri)
}
