package lovehan1me.data.network

import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.util.AttributeKey
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.network.egress.DefaultResult
import lovehan1me.data.network.egress.EgressBudgets
import lovehan1me.data.network.egress.EgressEngine
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.NoRouteException
import lovehan1me.data.network.egress.RouteAttempt
import lovehan1me.data.network.egress.runEgress
import kotlin.coroutines.cancellation.CancellationException

/**
 * Ktor 侧的调度执行器（Darwin/iOS 用；JVM 走 OkHttp 拦截器，不装这个）。
 * 循环（排表/让位定责/末步分流/记账）全在 [lovehan1me.data.network.egress.runEgress]。
 *
 * ## 与 OkHttp 执行器的三处差异（刻意，非遗漏）
 * 1. **502 不验错误页前缀**：读 body 会消费响应，返回给调用方的就是空壳；网关 502 恒由
 *    自身 `onUpstreamError` 产生，按状态码判定已足够准。
 * 2. **代理/直连步交给 NSURLSession**：恢复原 URL 直接发，系统代理生效；手填代理**不**生效
 *    （Darwin 无按请求配代理的口子，见 `ProxyCapability.ios`）—— 已知缺口，Phase 4 处理。
 * 3. **无就绪等待**：iOS 起服由 Swift 壳管，Kotlin 侧没有"拉起中"可等；没跑就诚实失败。
 *
 * Cookie 由 [EchGatePluginConfig.cookieHeaderProvider] 按**原域名**取（与 OkHttp 同一个洞）；
 * 同时装 `HttpCookies` 时其 storage 应对网关回环 host 短路（见 iosMain `BridgeCookiesStorage`）。
 */
class EchGatePluginConfig {
    /** 按**原 URL**取 Cookie 请求头（`k=v; k2=v2`），null/blank 不附加；iOS 传桥接实现。 */
    var cookieHeaderProvider: (suspend (originalUrl: Url) -> String?)? = null

    /** 网关拉起中的有界等待（无运行时传 null）。iOS 暂无运行时，保持 null。 */
    var awaitGateReady: (suspend () -> Boolean)? = null

    /** 本 client 的默认用途（决定预算档位）；各工厂按职责传，单次可用 attribute 覆盖。 */
    var defaultPurpose: EgressPurpose = EgressPurpose.Api

    var logger: ((String) -> Unit)? = null
}

/** 单次请求覆盖用途的 attribute key（对齐 OkHttp 侧 `request.tag`）。 */
val EgressPurposeAttributeKey = AttributeKey<EgressPurpose>("EgressPurpose")

/**
 * Darwin 传输层重试谓词（与 jvm `RetryInterceptor` 同语义：只重试幂等方法与传输层异常）。
 * 诚实失败（[NoRouteException]）不重试——重排也不会多出路，只给失败加延迟。
 */
internal fun shouldRetryDarwinFailure(method: HttpMethod, cause: Throwable): Boolean {
    if (cause is NoRouteException) return false
    val idempotent = method == HttpMethod.Get || method == HttpMethod.Head
    return idempotent && (cause is okio.IOException || cause is HttpRequestTimeoutException)
}

val EchGateClientPlugin = createClientPlugin("EchGate", ::EchGatePluginConfig) {
    on(Send) { request ->
        runCatching { pluginConfig.awaitGateReady?.invoke() }
        val original = request.url.build()
        val url = original.toString()
        val purpose = request.attributes.getOrNull(EgressPurposeAttributeKey)
            ?: pluginConfig.defaultPurpose
        return@on runEgress(url, request.method.value, purpose, KtorEgressEngine(this, request, original, url, pluginConfig))
    }
}

/**
 * Ktor 引擎原语：网关步**原地变异** builder（只变异一次），默认步先 [restoreOriginal] 再发。
 */
private class KtorEgressEngine(
    private val sender: Send.Sender,
    private val request: HttpRequestBuilder,
    private val original: Url,
    private val url: String,
    private val pluginConfig: EchGatePluginConfig,
) : EgressEngine<HttpClientCall> {

    private var prepared = false

    override suspend fun sendGate(attempt: RouteAttempt, retry: Boolean): HttpClientCall? {
        val rewrite = attempt.rewrite ?: return null
        if (!prepared) {
            request.url.protocol = URLProtocol.HTTP
            request.url.host = EchGatePolicy.GATE_HOST
            request.url.port = rewrite.port
            request.headers.append(EchGatePolicy.TARGET_HEADER, rewrite.targetHost)
            val cookie = runCatching { pluginConfig.cookieHeaderProvider?.invoke(original) }.getOrNull()
            if (!cookie.isNullOrBlank() && !request.headers.contains("Cookie")) {
                request.headers.append("Cookie", cookie)
            }
            prepared = true
        }
        return sender.proceed(request)
    }

    override suspend fun proceedDefault(attempt: RouteAttempt): DefaultResult<HttpClientCall> {
        restoreOriginal(request, original)
        val start = currentEpochMillis()
        val call = sender.proceed(request)
        return DefaultResult(call, currentEpochMillis() - start)
    }

    override fun isGatewayErrorPage(response: HttpClientCall): Boolean =
        response.response.status == HttpStatusCode.BadGateway

    override fun stamp(): Long = currentEpochMillis()

    override fun elapsedMs(since: Long): Long = currentEpochMillis() - since

    override fun budgetExhausted(startedAt: Long, budgetMs: Long): Boolean =
        budgetMs != EgressBudgets.UNLIMITED && elapsedMs(startedAt) >= budgetMs

    /** Ktor 的响应体由框架管，丢弃即不再引用；不需要显式关闭。 */
    override fun discard(response: HttpClientCall) = Unit

    override fun statusCode(response: HttpClientCall): Int = response.response.status.value

    override fun finish(response: HttpClientCall): HttpClientCall = response

    override fun isCancellation(e: Throwable): Boolean = e is CancellationException

    override fun isTransportFailure(e: Throwable): Boolean = e is Exception

    override fun logDebug(message: String) {
        pluginConfig.logger?.invoke("EchGate: $message $url")
    }

    override fun logWarn(message: String) {
        pluginConfig.logger?.invoke("EchGate: $message $url")
    }
}

/** 把被改写的 builder 恢复成原请求（URL + 去 Target 头；Cookie 头是幂等的附加）。 */
private fun restoreOriginal(request: HttpRequestBuilder, original: Url) {
    request.url.protocol = original.protocol
    request.url.host = original.host
    request.url.port = original.port
    request.headers.remove(EchGatePolicy.TARGET_HEADER)
}