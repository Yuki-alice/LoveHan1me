package lovehan1me.data.network.interceptor

import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGateContract
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.EchGateRuntime
import lovehan1me.data.network.HCookieJar
import lovehan1me.data.network.egress.DefaultResult
import lovehan1me.data.network.egress.EgressBudgets
import lovehan1me.data.network.egress.EgressEngine
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.RouteAttempt
import lovehan1me.data.network.egress.runEgress
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * 调度执行器（OkHttp 侧）。判定在 `EgressScheduler`，循环/让位定责/末步分流/记账在
 * [lovehan1me.data.network.egress.runEgress]；本文件只留 OkHttp 的 `proceed` 原语与三个"洞"：
 * Cookie 按原域名注入、Set-Cookie 按原域名存回（[finishGateResponse]）、
 * 预算兼有外层 `RetryInterceptor` 总预算（[retryBudgetExhausted]）。详见 [EgressRunner]。
 * socket 级硬上限仍是各 client 超时；在途 IO 不能被抢占（OkHttp 同步链的固有限制）。
 */
class EchGateInterceptor(
    /**
     * 是否按原域名注入站点 Cookie。图片/封面链必须传 false：`loadForRequest` 会取出
     * `hanime1_session` 一类登录态，而图床是**第三方**，发过去只有泄漏风险；
     * 需要登录态的链（浏览/评论/我的列表/下载）保持默认 true。
     */
    private val attachSiteCookies: Boolean = true,
    /** 本链的默认用途（决定预算档位）；调用方可用 `request.tag(EgressPurpose::class.java)` 覆盖单次。 */
    private val defaultPurpose: EgressPurpose = EgressPurpose.Api,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val purpose = request.tag(EgressPurpose::class.java) ?: defaultPurpose
        // 冷启动竞态：网关拉起中 LISTENING 未到时首页会抢跑直连撞 RST，拉起中有界等待。
        EchGateRuntime.awaitReadyIfStarting()
        val engine = OkHttpEgressEngine(chain, request, HCookieJar())
        return runBlocking { runEgress(request.url.toString(), request.method, purpose, engine) }
    }

    /** OkHttp 引擎原语：同步 `chain.proceed`，请求**重建**（不改原请求）。 */
    private inner class OkHttpEgressEngine(
        private val chain: Interceptor.Chain,
        private val request: Request,
        private val jar: HCookieJar,
    ) : EgressEngine<Response> {

        override suspend fun sendGate(attempt: RouteAttempt, retry: Boolean): Response? {
            val rewrite = attempt.rewrite ?: return null
            val gateUrl = runCatching { rewrite.url.toHttpUrl() }.getOrNull() ?: return null
            var builder = request.newBuilder()
                .url(gateUrl)
                .header(EchGatePolicy.TARGET_HEADER, rewrite.targetHost)
                .header("Host", rewrite.targetHost)
            if (attachSiteCookies) {
                val cookies = runCatching { jar.loadForRequest(request.url) }.getOrDefault(emptyList())
                if (cookies.isNotEmpty()) {
                    builder = builder.header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
                }
            }
            if (retry) builder = builder.header(RETRY_HEADER, "1")
            return chain.proceed(builder.build())
        }

        override suspend fun proceedDefault(attempt: RouteAttempt): DefaultResult<Response> {
            val startNs = System.nanoTime()
            val response = chain.proceed(request)
            return DefaultResult(response, nanoElapsedMs(startNs))
        }

        override fun isGatewayErrorPage(response: Response): Boolean {
            if (response.code != 502) return false
            return runCatching { EchGateContract.isErrorPage(response.peekBody(PEEK_LIMIT).string()) }
                .getOrDefault(false)
        }

        override fun stamp(): Long = System.nanoTime()

        override fun elapsedMs(since: Long): Long = nanoElapsedMs(since)

        override fun budgetExhausted(startedAt: Long, budgetMs: Long): Boolean =
            retryBudgetExhausted(request) || stepBudgetExhausted(startedAt, budgetMs)

        override fun discard(response: Response) = response.close()

        override fun statusCode(response: Response): Int = response.code

        override fun finish(response: Response): Response = finishGateResponse(response, request.url, jar)

        override fun isCancellation(e: Throwable): Boolean = isCallCanceled(chain)

        override fun isTransportFailure(e: Throwable): Boolean = e is IOException

        override fun logDebug(message: String) = LogUtil.d(TAG, "$message ${request.url.host}")

        override fun logWarn(message: String) = LogUtil.w(TAG, "$message ${request.url.host}")
    }

    /** 调用方是否主动取消了本次呼叫（见 [runEgress] 的取消注释）。 */
    private fun isCallCanceled(chain: Interceptor.Chain): Boolean =
        runCatching { chain.call().isCanceled() }.getOrDefault(false)

    private fun nanoElapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000

    private fun stepBudgetExhausted(startNs: Long, budgetMs: Long): Boolean {
        if (budgetMs == EgressBudgets.UNLIMITED) return false
        return nanoElapsedMs(startNs) >= budgetMs
    }

    /** 外层下传的重试预算是否已耗尽；没有预算标记时视为未耗尽（不装 Retry 的链照旧）。 */
    private fun retryBudgetExhausted(request: Request): Boolean {
        val deadline = request.tag(RetryDeadline::class.java) ?: return false
        return System.nanoTime() >= deadline.deadlineNanos
    }

    /** 网关响应的收尾：Set-Cookie 按原域名存回（见类 KDoc 第 2 点）。 */
    private fun finishGateResponse(response: Response, originUrl: HttpUrl, jar: HCookieJar): Response {
        // 存回与注入必须同一个开关：只关注入不关存回，图床的 Set-Cookie 仍会落进 jar。
        if (attachSiteCookies) {
            val parsed = response.headers("Set-Cookie").mapNotNull { raw ->
                runCatching { Cookie.parse(originUrl, raw) }.getOrNull()
            }
            if (parsed.isNotEmpty()) runCatching { jar.saveFromResponse(originUrl, parsed) }
        }
        LogUtil.d(TAG, "${originUrl.host} -> ${EchGatePolicy.GATE_HOST}:${EchGate.port} (${response.code})")
        return response
    }

    private companion object {
        const val TAG = "EchGate"

        /** 网关重试标记：同一请求只重试一次，防环。 */
        const val RETRY_HEADER = "X-Ech-Retry"

        /** 502 判定只看这么多字节（下载大文件场景下不能整包 peek）。 */
        const val PEEK_LIMIT = 256L
    }
}