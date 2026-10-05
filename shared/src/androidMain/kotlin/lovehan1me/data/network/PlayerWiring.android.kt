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
     * 原因：Android 播放链路经 media3 的 `DefaultHttpDataSource` 取代理，**没有
     * "URL 形式的代理"可以传进去**（那是给 ffmpeg 用的形状）。
     *
     * ## 系统属性这条通道：核对结论（阶段 4.4，替换此前的"未实测"）
     * 反编译 media3 1.10.1 `media3-datasource` 的 `DefaultHttpDataSource` 确认：
     * 该类**没有任何 proxy 字段**，`makeConnection(...)` 直接调 `URL.openConnection()`
     * （本机复现命令与输出见 `docs/evidence/2026-10-05-Android-Exo代理通道核对.md`）。
     * 于是出口由 **JVM 默认 `ProxySelector`** 决定，而本应用在
     * `HanimeApplication.kt` 启动时已 `ProxySelector.setDefault(HanimeProxySelector())`
     * —— 显式代理正是**从这条默认选择器**生效，不是靠 `rebuildNetwork()` 写的那几个
     * 系统属性（那对 Exo 而言只是旁路）。此前注释把两者混为一谈，方向说反了。
     *
     * ⚠️ **待真机**：Android 平台 `HttpURLConnection` 是否**额外**读 `http.proxyHost`
     * 系统属性，本机无设备、无法实跑，未验。已验的是"media3 经默认 ProxySelector 取出口"
     * 这条代码链；真机回归时以抓包为准。
     *
     * 将来若要让 Exo 用**显式代理对象**（不经选择器），正确做法是自建带 `Proxy` 的
     * DataSource，而不是把 URL 塞进本方法。
     */
    override fun proxyUrlFor(mediaUri: String): String? = null

    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? =
        gateRewrite(uri)

    /** 网关链路结局回 [EgressReporter]（F9）：`EchGateDataSource` 每次走网关改写的
     *  open 都回报，让视频域拥有自己的网关健康数据。详见 [reportGateLoadOutcome]。 */
    override fun onGateLoadOutcome(uri: String, ok: Boolean, reason: String?) =
        reportGateLoadOutcome(uri, ok, reason)
}
