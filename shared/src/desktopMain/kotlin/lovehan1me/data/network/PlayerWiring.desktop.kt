package lovehan1me.data.network

import lovehan1me.data.network.egress.EgressPlanner
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
     * ## 取值顺序（由 [EgressPlanner.mediaProxyUrl] 裁决）
     * 用户代理 → 网关 CONNECT 隧道 → null（直连）。
     * 隧道那一档给"没配代理"的场景留：它不做 ECH（客户端在隧道内自己 TLS），
     * 但会用 **DoH 解析出干净 IP 再逐 IP 拨号**，至少绕开被污染的 DNS ——
     * 2026-09-25 实测该通道完整拉下 4.3MB 视频。
     *
     * 隧道与改写道**同源**：网关熔断/关闭时两者一起消失。此前隧道只判 `port > 0`，
     * 于是"改写道已退让、隧道还在劫持媒体"会把故障原样留着 —— 媒体没有回退，
     * 撞不通就是直接失败。
     */
    override fun proxyUrlFor(mediaUri: String): String? {
        if (mediaUri.isGateLoopback()) return null
        return EgressPlanner.mediaProxyUrl(resolveMediaProxyUrl())
    }

    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? =
        gateRewrite(uri)
}
