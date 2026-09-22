package lovehan1me.data.network

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * 本地 ECH 网关的改写策略（三端共享的唯一判定）。
 *
 * 约定与 `echgate` Go 网关配套：
 * `GET https://host/path?q` → `GET http://127.0.0.1:<port>/path?q` + `X-Ech-Target: host`。
 *
 * 放行（返回 null = 不经网关，原样直连），网关没运行（port <= 0）自然全放行：
 * - 非 https（网关按域名做 SNI 策略，http 没有 SNI 可谈）；
 * - 本机地址与 IP 字面量（回环走网关等于绕出去再绕回来；IP 没有域名可谈）。
 *
 * OkHttp 拦截器（jvmMain）、Ktor 插件（Darwin）、mpv/Exo/AVPlayer 的改写
 * 全部调这里——判定分叉正是"页面能开、视频打不开"类问题的根因。
 */
object EchGatePolicy {

    const val GATE_HOST = "127.0.0.1"
    const val TARGET_HEADER = "X-Ech-Target"

    /** 改写结果：发往网关的 URL + 网关还原目标用的原域名 + 网关端口。 */
    data class Rewrite(val url: String, val targetHost: String, val port: Int)

    /**
     * 对 [rawUrl] 做网关改写；null = 放行。
     *
     * 纯函数、无平台相关，可进 commonTest。解析失败同样放行——
     * 改写层永远不能成为请求失败的原因，兜底在调用方。
     */
    fun rewrite(rawUrl: String, port: Int): Rewrite? {
        if (port <= 0) return null
        val url = runCatching { Url(rawUrl) }.getOrNull() ?: return null
        if (url.protocol != URLProtocol.HTTPS) return null
        val host = url.host
        if (isBypassHost(host)) return null
        val gateUrl = URLBuilder(url).apply {
            protocol = URLProtocol.HTTP
            this.host = GATE_HOST
            this.port = port
        }.buildString()
        return Rewrite(gateUrl, host, port)
    }

    /** 本机地址与 IP 字面量直接放行（与 jvmMain 旧 EchGateInterceptor 同语义，收敛到此）。 */
    fun isBypassHost(host: String): Boolean {
        if (host.isBlank()) return true
        val lower = host.lowercase()
        if (lower == GATE_HOST || lower == "localhost" || lower.endsWith(".local")) return true
        if (host.contains(':')) return true // IPv6 字面量
        if (IPV4.matches(host)) return true
        return false
    }

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
}
