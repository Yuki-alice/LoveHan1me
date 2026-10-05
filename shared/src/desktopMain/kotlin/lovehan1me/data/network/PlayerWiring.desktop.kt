package lovehan1me.data.network

import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.EgressRequest
import lovehan1me.data.network.egress.EgressScheduler
import lovehan1me.data.network.egress.ForceMode
import lovehan1me.data.network.egress.ProxyState
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.data.network.egress.classifyDomain
import lovehan1me.data.network.egress.currentEgressState
import lovehan1me.data.network.egress.currentForceMode
import lovehan1me.feature.player.PlayerNetworkConfig

/**
 * 桌面侧网络配置（Gate3-P1）。
 *
 * 两条判定本体都在 commonMain 纯函数里：网关改写道走 [gateRewrite]，媒体是否让位代理见
 * [preferProxyOverGateForMedia]，播放层代理落点见 [mediaProxyUrlFor]（具体地址的解析在
 * jvmMain 的 [resolveMediaProxyUrl]）。本文件只把它们串起来。
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
     * 到这里是引擎"没走改写"的分支：可能是 [rewriteForGate] 把媒体让给了代理
     * （有可用代理时优先），也可能是网关链路失败后的回退重试 —— 两种都拿真实源站 URL。
     * Default 在 HTTP 层把用户代理/系统代理/直连合成了一个名字，但**播放层这条细分是真的**，
     * 故按 `state.proxy` + 强制模式还原（本体见 [mediaProxyUrlFor]）：
     * - 配了代理（手填或系统解析）→ 先解析成具体 HTTP 代理（见 [resolveMediaProxyUrl]，
     *   System 档靠 `java.net.useSystemProxies` 拿到系统代理地址）；SOCKS 或解析不出
     *   才退到网关 CONNECT 隧道兜底；
     * - 无代理 / 强制直连 / 强制网关 → null（引擎裸直连，或按用户声明走网关改写）。
     *
     * ⚠️ 系统代理**不能只给隧道**：隧道与改写道同源（见
     *   [EgressScheduler.tunnelUrl]），用户关掉网关后它也变 null，mpv 就只剩裸直连 ——
     *   受限网下正是 `[ffmpeg] tls: IO error -10054` / `mpv_error=-13` 的成因
     *   （此时系统代理明明可用，却一点没交给 mpv）。
     *
     * 隧道那一档是兜底（解析不出具体 HTTP 代理时）：它不做 ECH（客户端在隧道内自己 TLS），
     * 但会用 **DoH 解析出干净 IP 再逐 IP 拨号**，至少绕开被污染的 DNS。
     *
     * ⚠️ 隧道**救不了视频 CDN**：`vdownload.hembed.com` 的原 SNI 被重置（2026-10-05 实测
     *   `curl: (35) Recv failure: Connection was reset`），而隧道里客户端自带 TLS、SNI
     *   对网关不可控 —— 这条 CDN 只有网关反向代理（CNAME 降级成 `*.rsc.cdn77.org`）一条活路；
     *   且该通道本机实测仅 ~23–33 KB/s，远不够视频码率（所以有代理时媒体优先让位给代理）。
     *   隧道仍对原 SNI 未被封的域名有效。
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
        // 空表 = 无可用出口：不编造代理，让引擎裸直连（与 HTTP 层的诚实失败一致）。
        if (plan.attempts.isEmpty()) return null
        // 用户已声明出口：强制直连/强制网关时播放层不得改道（否则"强制"是假动作）。
        if (force == ForceMode.ForceDirect || force == ForceMode.ForceGate) return null
        // 到这里首位只可能是 Default，或 Gate + 真实源站 URL 的两种情形：
        // [rewriteForGate] 主动把媒体让给了代理（有可用代理），或网关链路失败后的回退重试。
        // 两种都必须给具体代理，否则 mpv 只剩裸直连 —— 受限网下必失败。
        return mediaProxyUrlFor(
            proxy = state.proxy,
            resolvedProxyUrl = resolveMediaProxyUrl(),
            tunnelUrl = EgressScheduler.tunnelUrl(domain),
        )
    }

    /**
     * 有可用代理时，媒体**不让网关改写**（判据见 [preferProxyOverGateForMedia]）。
     *
     * 网关是绕 SNI 阻断的通道，不是带宽通道：2026-10-05 本机实测同一 1080p 直链，
     * 网关 CNAME 降级通道（`vdownload.hembed.com → *.rsc.cdn77.org`）只有
     * ~23–33 KB/s，而系统代理 `127.0.0.1:7897` 有 ~235 KB/s（约 8 倍）。
     * 视频是带宽敏感型：走网关只会"一直缓冲"，有代理就必须让位（代理出口见 [proxyUrlFor]）。
     *
     * 返回 null 后引擎用**真实源站 URL** 加载，[proxyUrlFor] 随即把具体代理交给 mpv。
     */
    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? {
        val proxy = runCatching { currentEgressState() }.getOrNull()?.proxy ?: ProxyState.None
        if (preferProxyOverGateForMedia(currentForceMode(), proxy, resolveMediaProxyUrl())) return null
        return gateRewrite(uri)
    }

    /** 网关链路结局回 [EgressReporter]（F9）：桌面 `DesktopMpvPlaybackEngine` 在
     *  网关失败回退直连处调用。详见 [reportGateLoadOutcome]。 */
    override fun onGateLoadOutcome(uri: String, ok: Boolean, reason: String?) =
        reportGateLoadOutcome(uri, ok, reason)
}

/**
 * 播放层的代理落点（纯判定，离线可断言）。
 *
 * 配了代理（手填或系统解析）→ 优先给**具体 HTTP 代理地址**（ffmpeg 的 `http_proxy`
 * 只认 HTTP 代理）；`resolvedProxyUrl` 为 null（SOCKS、或系统代理解析不出具体地址）时
 * 才退到网关 CONNECT 隧道兜底。无代理 → null（引擎裸直连）。
 *
 * 手填与系统代理走**同一条路**是刻意的：隧道与改写道同源（见
 * [EgressScheduler.tunnelUrl]），用户关掉网关后隧道也变 null —— 若系统代理只给隧道，
 * mpv 就拿到 null 去裸直连，受限网下正是 `tls: IO error -10054` / `mpv_error=-13`。
 */
internal fun mediaProxyUrlFor(
    proxy: ProxyState,
    resolvedProxyUrl: String?,
    tunnelUrl: String?,
): String? = when (proxy) {
    is ProxyState.Explicit, is ProxyState.SystemResolved -> resolvedProxyUrl ?: tunnelUrl
    ProxyState.None -> null
}

/**
 * 媒体是否该**让位给代理**而不是走网关改写（纯判定，离线可断言）。
 *
 * 只有拿到**具体 HTTP 代理地址**才算让位：ffmpeg 的 `http_proxy` 只认 HTTP 代理，
 * SOCKS 或系统代理解析不出地址时（[resolvedProxyUrl] 为 null）仍应走网关 ——
 * 那时网关是唯一还能绕开 SNI 阻断的通道。强制网关是用户的显式声明，不改道。
 */
internal fun preferProxyOverGateForMedia(
    force: ForceMode,
    proxy: ProxyState,
    resolvedProxyUrl: String?,
): Boolean = force != ForceMode.ForceGate && proxy !is ProxyState.None && resolvedProxyUrl != null
