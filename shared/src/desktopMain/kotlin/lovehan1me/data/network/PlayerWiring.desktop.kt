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

    override fun proxyUrlFor(mediaUri: String): String? {
        if (EchGate.port > 0) return null
        return resolveMediaProxyUrl()
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
