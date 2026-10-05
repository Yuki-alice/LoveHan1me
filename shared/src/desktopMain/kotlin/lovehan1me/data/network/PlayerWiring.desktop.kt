package lovehan1me.data.network

import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.EgressRequest
import lovehan1me.data.network.egress.EgressScheduler
import lovehan1me.data.network.egress.ForceMode
import lovehan1me.data.network.egress.ProxyState
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.data.network.egress.classifyDomain
import lovehan1me.data.network.egress.currentEgressState
import lovehan1me.data.network.egress.currentForceMode
import lovehan1me.feature.player.PlayerNetworkConfig

/**
 * 桌面侧网络配置（Gate3-P1）。
 *
 * 两条判定本体都在 [EgressPlanner]（commonMain 纯函数）：网关改写道与 CONNECT 隧道
 * 同源，用户代理的解析在 jvmMain 的 [resolveMediaProxyUrl]。本文件只把它们串起来。
 */
actual fun defaultPlayerNetworkConfig(): PlayerNetworkConfig = object : PlayerNetworkConfig {
    override val userAgent: String = currentHttpUserAgent()

    /**
     * ⚠️ 判据是**这个 mediaUri 是不是网关地址**，不是"网关有没有在跑"。
     *
     * 两个方向都要成立：
     * - URL 指向本地网关时**必须**返回 null：ffmpeg 的 `http_proxy` 没有 bypass 列表，
     *   设了它连发给 `127.0.0.1` 的请求也会被代理出去，网关永远收不到。
     * - URL **不是**网关地址时该设就设 —— 尤其是引擎的"网关失败→回退直连"分支
     *   （`DesktopMpvPlaybackEngine.issueLoad`）：那时用的是真实源站 URL，
     *   而视频 CDN 多是 CDN77 之类的**非 Cloudflare 边缘**（如
     *   `vdownload.hembed.com` → `*.rsc.cdn77.org`），ECH 对它毫无作用，
     *   若此刻仍按"网关在跑"跳过代理，就只剩裸直连 —— 2026-09-25 实测拿到的是
     *   `Connection to tcp://vdownload.hembed.com:443 failed: Connection refused`。
     *
     * ## 取值顺序（HTTP 层已折叠，这里靠代理状态还原播放层的细分）
     * 改写优先（`rewriteForGate` 非 null ⟺ 计划首位是 Gate，本函数不被问到）；
     * 到这里首位只可能是折叠后的 [RouteId.Default]（或空表）。Default 在 HTTP 层把
     * 用户代理/系统代理/直连合成了一个名字，但**播放层这条细分是真的**（SOCKS 解析为
     * null 时退隧道），故按 `state.proxy` + 强制模式还原：
     * - 用户手填代理 → 解析出的 HTTP 代理（SOCKS 解析为 null，见 `resolveMediaProxyUrl`，
     *   此时退到隧道）；
     * - 系统代理 → 网关 CONNECT 隧道（mpv 用不了 JVM 系统代理，隧道至少绕开被污染的 DNS）；
     * - 无代理 / 强制直连 → null（引擎裸直连）。
     *
     * 隧道那一档给"没配代理"的场景留：它不做 ECH（客户端在隧道内自己 TLS），
     * 但会用 **DoH 解析出干净 IP 再逐 IP 拨号**，至少绕开被污染的 DNS ——
     * 2026-09-25 实测该通道完整拉下 4.3MB 视频。
     *
     * 隧道与改写道**同源**：网关熔断/关闭时两者一起消失（见
     *   [EgressScheduler.tunnelUrl]）。此前隧道只判 `port > 0`，
     * 于是"改写道已退让、隧道还在劫持媒体"会把故障原样留着 —— 媒体没有回退，
     * 撞不通就是直接失败。
     */
    override fun proxyUrlFor(mediaUri: String): String? {
        if (mediaUri.isGateLoopback()) return null
        val domain = classifyDomain(mediaUri, EgressPurpose.Video)
        val force = currentForceMode()
        val state = runCatching { currentEgressState() }.getOrNull()
            ?: return resolveMediaProxyUrl()
        val plan = EgressScheduler.plan(
            EgressRequest(mediaUri, "GET", EgressPurpose.Video, force),
            state,
            RouteRegistry.healthOf(domain),
        )
        // 改写道优先：引擎先问 rewrite，本函数只处理"没走改写"的分支。折叠后首位
        // 只可能是 Gate 或 Default；Gate（或空表）→ null（真被问到说明拿的是源站 URL）。
        if (plan.attempts.firstOrNull()?.route != RouteId.Default) return null
        // 用户已声明直连：播放层同样不能给代理（否则"强制直连"在播放上是假动作）。
        if (force == ForceMode.ForceDirect) return null
        return when (state.proxy) {
            // SOCKS 解析为 null（ffmpeg 只认 HTTP 代理）：退到隧道，不断流。
            is ProxyState.Explicit -> resolveMediaProxyUrl() ?: EgressScheduler.tunnelUrl(domain)
            is ProxyState.SystemResolved -> EgressScheduler.tunnelUrl(domain)
            ProxyState.None -> null
        }
    }

    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? =
        gateRewrite(uri)

    /** 网关链路结局回 [EgressReporter]（F9）：桌面 `DesktopMpvPlaybackEngine` 在
     *  网关失败回退直连处调用。详见 [reportGateLoadOutcome]。 */
    override fun onGateLoadOutcome(uri: String, ok: Boolean, reason: String?) =
        reportGateLoadOutcome(uri, ok, reason)
}
