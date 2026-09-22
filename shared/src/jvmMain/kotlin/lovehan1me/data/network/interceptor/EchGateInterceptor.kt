package lovehan1me.data.network.interceptor

import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.HCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 把站点域名的请求交给本地 ECH 网关出站（见 [EchGate]）。
 *
 * ## 改写约定（与 `echgate/main.go` 配套）
 *
 *	GET https://hanime1.me/path      →  GET http://127.0.0.1:<port>/path
 *	                                     X-Ech-Target: hanime1.me
 *
 * 网关按 `X-Ech-Target` 还原目标，经 ECH TLS 发往它自己的上游 IP 列表。
 *
 * ## 两个必须手动补的洞
 * 1. **Cookie**：改写后 OkHttp 的 CookieJar 按 `127.0.0.1` 匹配域名，登录态与
 *    `cf_clearance` 全部拿不到 ⇒ 这里按**原域名**取出来塞进 `Cookie` 头。
 * 2. **Set-Cookie**：响应按 `127.0.0.1` 存下来，原域名就再也取不到 ⇒ 这里用原 URL
 *    重新解析并存回原域名。
 *
 * ## 不接管什么
 * - 网关没运行（[EchGate.port] <= 0）→ 原样放行；
 * - 非 https、本机地址、IP 字面量 → 原样放行（网关是给"按域名做 SNI 策略"用的，
 *   IP 没有 SNI 可谈）。
 *
 * ## 为什么不再限定站点域名
 * 之前只接管 `HANIME_HOSTNAME`，结果**视频与图片全挂**：它们的直链在
 * `vdownload.hembed.com`（CDN77），既不在站点域名族里、又同样被 SNI 阻断。
 * 现在改成"凡是 https 都交给网关"，由网关按域名决定策略——它对能直连的域名
 * 走普通 TLS，一次探测后就缓存，开销只有首次的一跳本地转发。
 *
 * 仍然保留"网关不在就放行"这条兜底：**网关挂了不该连累正常请求**。
 */
class EchGateInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val port = EchGate.port
        if (port <= 0) return chain.proceed(request)

        val originUrl = request.url
        val host = originUrl.host
        if (originUrl.scheme != "https" || isLocalOrLiteral(host)) return chain.proceed(request)

        val gateUrl = HttpUrl.Builder()
            .scheme("http")
            .host(GATE_HOST)
            .port(port)
            .encodedPath(originUrl.encodedPath)
            .encodedQuery(originUrl.encodedQuery)
            .build()

        val jar = HCookieJar()
        val builder = request.newBuilder()
            .url(gateUrl)
            .header(TARGET_HEADER, host)
            .header("Host", host)

        val cookies = jar.loadForRequest(originUrl)
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
        }

        val response = chain.proceed(builder.build())

        val setCookies = response.headers("Set-Cookie")
        if (setCookies.isNotEmpty()) {
            val parsed = setCookies.mapNotNull { raw ->
                runCatching { Cookie.parse(originUrl, raw) }.getOrNull()
            }
            if (parsed.isNotEmpty()) jar.saveFromResponse(originUrl, parsed)
        }

        LogUtil.d(TAG, "$host -> $GATE_HOST:$port (${response.code})")
        return response
    }

    /** 本机地址与 IP 字面量直接放行：网关按域名做策略，这些没有域名可谈。 */
    private fun isLocalOrLiteral(host: String): Boolean =
        host.isBlank() ||
            host == GATE_HOST || host == "localhost" ||
            host.endsWith(".local") ||
            host.contains(':') ||
            IPV4.matches(host)

    private companion object {
        const val TAG = "EchGate"
        const val GATE_HOST = "127.0.0.1"

        /** 网关侧约定的目标头，见 `echgate/main.go` 的 director。 */
        const val TARGET_HEADER = "X-Ech-Target"

        val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    }
}
