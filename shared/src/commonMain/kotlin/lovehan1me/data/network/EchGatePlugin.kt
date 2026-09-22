package lovehan1me.data.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Ktor 侧的 ECH 网关插件（Darwin/iOS 用；JVM 走 OkHttp 拦截器，不装这个）。
 *
 * 行为与 [EchGatePolicy] + jvmMain `EchGateInterceptor` 对齐：
 * 1. 网关未运行（port <= 0）→ 零改动放行；
 * 2. 改写 URL + `X-Ech-Target`，Cookie 由 [EchGatePluginConfig.cookieHeaderProvider]
 *    按**原域名**取（改写后按 127.0.0.1 匹配域名会拿不到登录态/clearance，
 *    与 OkHttp 拦截器手动补 Cookie 是同一个洞）；
 * 3. **失败回退**：网关抛异常 → 原样重试一次；网关回 502（`echgate:` 上游错误）
 *    且原方法是 GET/HEAD → 先重试一次网关，还不行才回退直连。
 *    网关永远是加速项，挂了不该连累正常请求（现有代理/直连即兜底）。
 *
 * Cookie 重复附加防护：调用方若同时装了 `HttpCookies`，其 storage 应对网关
 * 回环 host 短路（见 iosMain `BridgeCookiesStorage`），否则同一 Cookie 发两遍。
 */
class EchGatePluginConfig {
    /** 网关端口；`{ EchGate.port }` 这类 provider（`-1` = 未运行）。 */
    var portProvider: () -> Int = { -1 }

    /**
     * 按**原 URL**取 Cookie 请求头（`k=v; k2=v2`），null/blank 则不附加。
     * iOS 传桥接实现（内存桥 + DataStore 登录态 + clearance）；JVM 不用装本插件。
     */
    var cookieHeaderProvider: (suspend (originalUrl: Url) -> String?)? = null

    /**
     * 网关拉起中的有界等待（JVM 侧传 `EchGateProcess::awaitReadyIfStarting` 的
     * 非挂起包装；无运行时传 null）。iOS 暂无运行时，保持 null。
     */
    var awaitGateReady: (suspend () -> Boolean)? = null

    var logger: ((String) -> Unit)? = null
}

/** 网关上游错误页前缀（Go 侧 onUpstreamError 写 `echgate: ...`）。 */
private const val GATEWAY_ERROR_PREFIX = "echgate:"

val EchGateClientPlugin = createClientPlugin("EchGate", ::EchGatePluginConfig) {
    on(Send) { request ->
        runCatching { pluginConfig.awaitGateReady?.invoke() }
        val original = request.url.build()
        val rewrite = EchGatePolicy.rewrite(original.toString(), pluginConfig.portProvider())
        if (rewrite == null) {
            proceed(request)
        } else {
            applyRewrite(request, original, rewrite, pluginConfig)
        }
    }
}

private suspend fun Send.Sender.applyRewrite(
    request: HttpRequestBuilder,
    original: Url,
    rewrite: EchGatePolicy.Rewrite,
    pluginConfig: EchGatePluginConfig,
): io.ktor.client.call.HttpClientCall {
    // 原地变异（path/query 原样保留；3.5.2 的 URLBuilder 无 takeFrom 成员）。
    request.url.protocol = URLProtocol.HTTP
    request.url.host = EchGatePolicy.GATE_HOST
    request.url.port = rewrite.port
    request.headers.append(EchGatePolicy.TARGET_HEADER, rewrite.targetHost)
    val cookie = runCatching {
        pluginConfig.cookieHeaderProvider?.invoke(original)
    }.getOrNull()
    if (!cookie.isNullOrBlank() && !request.headers.contains("Cookie")) {
        request.headers.append("Cookie", cookie)
    }
    val gateCall = try {
        proceed(request)
    } catch (e: Exception) {
        // 网关异常（进程挂了/端口未监听）→ 原样重试一次，现有机制兜底。
        pluginConfig.logger?.invoke("EchGate: 网关异常回退直连 ${original} (${e.message})")
        restoreOriginal(request, original)
        return proceed(request)
    }
    // 网关回 502 且 body 是网关自己的上游错误页（`echgate:` 前缀）→
    // GET/HEAD 先重试一次网关（plan 已被网关侧失效，重试会重新探测），
    // 还不行才回退直连；其它方法直接回退（POST 重发有双提交风险）。
    // bodyAsText 读完即释放连接；502 错误页恒为小包。
    // 同一 on(Send) 内重发不重进本钩子，"只重试一次"是结构保证。
    val response = gateCall.response
    if ((request.method == HttpMethod.Get || request.method == HttpMethod.Head) &&
        response.status == HttpStatusCode.BadGateway &&
        runCatching { response.bodyAsText() }.getOrNull()?.startsWith(GATEWAY_ERROR_PREFIX) == true
    ) {
        pluginConfig.logger?.invoke("EchGate: 网关上游失败，重试网关一次 $original")
        val retried = proceed(request)
        val retriedOk = retried.response.status != HttpStatusCode.BadGateway ||
            runCatching { retried.response.bodyAsText() }.getOrNull()
                ?.startsWith(GATEWAY_ERROR_PREFIX) != true
        if (retriedOk) return retried
        pluginConfig.logger?.invoke("EchGate: 网关重试仍失败，回退直连 $original")
        restoreOriginal(request, original)
        return proceed(request)
    }
    return gateCall
}

/** 把被改写的 builder 恢复成原请求（URL + 去 Target 头；Cookie 头是幂等的附加）。 */
private fun restoreOriginal(request: HttpRequestBuilder, original: Url) {
    request.url.protocol = original.protocol
    request.url.host = original.host
    request.url.port = original.port
    request.headers.remove(EchGatePolicy.TARGET_HEADER)
}
