package lovehan1me.data.network

import lovehan1me.core.util.LogUtil
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI

/**
 * 播放器出口判定的**单份实现**。
 *
 * 放 jvmMain 而不是 desktopMain：android 与 desktop 在自定义层级里同属 jvm 中间源集，
 * 判定收在这里，两端就不可能各写一份。此前 `resolveMediaProxyUrl` 只在 desktopMain，
 * android 侧因此写了第二份**判据不同**的实现（判"网关有没有在跑"而不是"这个 URL 是不是
 * 网关地址"），在"网关在跑但 URL 是真实源站"的回退分支上两者会给出相反结论。
 *
 * `:video:engine` 只认 [lovehan1me.feature.player.PlayerNetworkConfig]，
 * 不直读网关与选择器；实现由 :shared 的 data 层提供。
 */

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
    val selected = runCatching { HanimeProxySelector.SHARED.select(uri) }
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
internal fun String.isGateLoopback(): Boolean {
    val port = EchGate.port
    if (port <= 0) return false
    return startsWith("http://${EchGatePolicy.GATE_HOST}:$port/")
}
