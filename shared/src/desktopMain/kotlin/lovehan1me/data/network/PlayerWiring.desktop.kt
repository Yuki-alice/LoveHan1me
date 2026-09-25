package lovehan1me.data.network

import lovehan1me.core.util.LogUtil
import lovehan1me.feature.player.PlayerNetworkConfig
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI

/**
 * 桌面侧网络配置（Gate3-P1）：代理复用 JVM 选择器（原引擎文件内的
 * `resolveMediaProxyUrl` 整段搬入，含 SOCKS 让路注释语义）。
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
     * ## 回退时的取值顺序
     * 用户代理 → 网关 CONNECT 兜底 → null（直连）。
     * 中间那档是给"没配代理"的场景留的：网关的 CONNECT 通道虽然不做 ECH
     * （客户端在隧道内自己 TLS），但会用 **DoH 解析出干净 IP 再逐 IP 拨号**，
     * 至少绕开被污染的 DNS —— 2026-09-25 实测该通道完整拉下 4.3MB 视频。
     */
    override fun proxyUrlFor(mediaUri: String): String? {
        if (mediaUri.isGateLoopback()) return null
        return resolveMediaProxyUrl() ?: gateConnectUrl()
    }

    /**
     * 网关的 CONNECT 兜底地址（`http://127.0.0.1:<port>`）；网关没在跑返回 null。
     *
     * 与主力通道（`X-Ech-Target` 反向代理）共用**同一个端口**，是两条不同的路：
     * 主力通道由网关代为 TLS（能用 ECH / CNAME 真名），CONNECT 只是 DoH 路由隧道。
     * 详见 `echgate/gate/gate.go` 的 `handleConnect` KDoc。
     */
    private fun gateConnectUrl(): String? {
        val port = EchGate.port
        if (port <= 0) return null
        return "http://${EchGatePolicy.GATE_HOST}:$port"
    }

    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? =
        gateRewrite(uri)
}

/**
 * 解析 mpv 该用的 HTTP 代理 URL（null = 直连）。
 *
 * 复用应用自己的 [HanimeProxySelector]：Direct/System/Http/Socks 四种模式与 HTTP 层
 * **同一个判定**（System 模式依赖 JVM 的 `java.net.useSystemProxies`，见 desktopApp 的 jvmArgs）。
 *
 * SOCKS 返回 null 并打日志：FFmpeg 的 `http_proxy` 只支持 HTTP 代理（CONNECT 语义），
 * 把 `socks5://…` 塞进去只会让流更打不开 —— 宁可不设，也不要设错。
 *
 * 设置未就绪（极早的调用/单测）时退回 JVM 默认选择器，再不行就直连。
 */
internal fun resolveMediaProxyUrl(): String? {
    val uri = runCatching { URI("https://hanime1.me/") }.getOrNull() ?: return null
    val selected = runCatching { HanimeProxySelector().select(uri) }
        .recoverCatching { ProxySelector.getDefault()?.select(uri) ?: emptyList() }
        .getOrNull()
    val proxy = selected?.firstOrNull() ?: return null
    return when (proxy.type()) {
        java.net.Proxy.Type.HTTP -> (proxy.address() as? InetSocketAddress)
            ?.let { "http://${it.hostString}:${it.port}" }

        java.net.Proxy.Type.SOCKS -> {
            LogUtil.w("DesktopMpv", "当前是 SOCKS 代理：mpv/ffmpeg 无法透传（只支持 HTTP 代理）")
            null
        }

        else -> null
    }
}

/** 该媒体 URL 是否指向本地 ECH 网关（`http://127.0.0.1:<EchGate.port>/…`）。 */
private fun String.isGateLoopback(): Boolean {
    val port = EchGate.port
    if (port <= 0) return false
    return startsWith("http://${EchGatePolicy.GATE_HOST}:$port/")
}
