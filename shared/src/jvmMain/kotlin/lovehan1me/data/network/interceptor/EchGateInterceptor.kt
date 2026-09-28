package lovehan1me.data.network.interceptor

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.EchGateProcess
import lovehan1me.data.network.HCookieJar
import lovehan1me.data.network.egress.EgressPlanner
import lovehan1me.data.network.egress.EgressRequest
import lovehan1me.data.network.egress.GateHealthHolder
import lovehan1me.data.network.egress.currentEgressState
import lovehan1me.data.network.egress.isIdempotent
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 把 https 请求交给本地 ECH 网关出站（见 [EchGate]）。
 *
 * ## 该不该用网关由 [EgressPlanner] 一处产出
 * 本类不再自己判断。改写规则仍是 commonMain 的 [EchGatePolicy]（三端唯一一份），
 * 它之上加了"该不该用网关"（用户开关 / 熔断 / 进程没跑 / 非 https / 回环与字面量）
 * 与"网关是否处于试用期"（存在可用代理时，它只有一次机会）。
 *
 * ## 三个必须手动补的洞
 * 1. **Cookie**：改写后 OkHttp 的 CookieJar 按 `127.0.0.1` 匹配域名，登录态与
 *    `cf_clearance` 全部拿不到 ⇒ 这里按**原域名**取出来塞进 `Cookie` 头。
 * 2. **Set-Cookie**：响应按 `127.0.0.1` 存下来，原域名就再也取不到 ⇒ 用原 URL 重新解析存回去。
 * 3. **"连上了但被阻断"**：网关的出口被封时返回的是 403 而不是异常。这类响应看起来像
 *    站点问题（`you have been blocked` / `Just a moment`），而用户手里可能有一条**能用的代理**
 *    —— 见下面第 ④ 段。**这一段此前缺失，正是"原本系统代理可以访问、却访问不了"的根因。**
 *
 * ## 四条失败路径（都不让网关成为单点）
 * - ① 网关抛 [IOException]（进程挂了 / 端口没监听）→ 记失败，原样走原出口；
 * - ② 网关回自己的上游错误页（502 + `echgate:` 前缀）→ 幂等方法先重试网关一次，再不行回退；
 *      非幂等方法直接回退（POST 重发有双提交风险）。重试只做一次（`X-Ech-Retry` 防环），
 *      且只 peek 前 256 字节 —— 本拦截器也装在下载客户端上，整包 peek 会把大文件读进内存。
 * - ③ 网关正常响应 → 记一次成功并放行。
 * - ④ 网关回 **403** 且处于试用期且方法幂等 → **经代理路径重试一次**：
 *      重试拿到非 403 ⇒ 判网关有责，记一次**阻断类**失败（熔断器一次即开，不再反复打扰）；
 *      重试同样 403 ⇒ 不是网关的锅，不计失败，把代理那份原样交出去（状态码一致，
 *      `NetworkRepo` 的 CF / IP 封判定照常生效）。
 */
class EchGateInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 熔断中：不接管、也不为它等待。等价于网关不存在，本次直接走原出口。
        // （顺带省掉下面那笔 8s 就绪等待 —— 熔断期间每个请求都白等是纯亏。）
        val now = currentEpochMillis()
        if (GateHealthHolder.current.isOpen(now)) {
            return chain.proceed(request)
        }

        // 冷启动竞态：网关被要求启动但 LISTENING 未到时，首页请求会抢跑直连撞 RST，
        // 表现为"封面首刷失败、再滑回来又好"。拉起中有界等待，图片与列表统一处理。
        EchGateProcess.awaitReadyIfStarting()

        val intent = EgressRequest(request.url.toString(), request.method)
        val plan = EgressPlanner.plan(intent, currentEgressState(now))
        val rewrite = plan.gate ?: run {
            plan.gateSkipped?.let { LogUtil.d(TAG, "不经网关（$it）${request.url.host}") }
            return chain.proceed(request)
        }
        val originUrl = request.url

        val gateUrl = runCatching { rewrite.url.toHttpUrl() }
            .getOrNull() ?: return chain.proceed(request)

        val jar = HCookieJar()
        val builder = request.newBuilder()
            .url(gateUrl)
            .header(EchGatePolicy.TARGET_HEADER, rewrite.targetHost)
            .header("Host", rewrite.targetHost)

        val cookies = runCatching { jar.loadForRequest(originUrl) }.getOrDefault(emptyList())
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
        }

        // ① 网关异常
        val gateResponse = try {
            chain.proceed(builder.build())
        } catch (e: IOException) {
            GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
            LogUtil.w(TAG, "网关异常，回退直连 ${originUrl.host} (${e.message})")
            return chain.proceed(request)
        }

        // ② 网关自己的上游错误页
        if (isGatewayErrorPage(gateResponse) && intent.isIdempotent) {
            gateResponse.close()
            LogUtil.w(TAG, "网关上游失败，重试网关一次 ${originUrl.host}")
            val retried = try {
                chain.proceed(builder.header(RETRY_HEADER, "1").build())
            } catch (e: IOException) {
                GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                LogUtil.w(TAG, "网关重试异常，回退直连 ${originUrl.host} (${e.message})")
                return chain.proceed(request)
            }
            if (isGatewayErrorPage(retried)) {
                retried.close()
                GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                LogUtil.w(TAG, "网关重试仍失败，回退直连 ${originUrl.host}")
                return chain.proceed(request)
            }
            GateHealthHolder.recordSuccess()
            return finishGateResponse(retried, originUrl, jar)
        }

        // ④ 连上了、但像是"出口被封"
        if (plan.gateOnProbation && intent.isIdempotent && gateResponse.code == 403) {
            gateResponse.close()
            LogUtil.w(TAG, "网关返回 403，经代理路径重试一次 ${originUrl.host}")
            val viaProxy = runCatching { chain.proceed(request) }.getOrNull()
            if (viaProxy == null) {
                // 代理路径直接抛了：按普通网关失败处理（非阻断，累计口径）
                GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                return chain.proceed(request)
            }
            if (viaProxy.code != 403) {
                GateHealthHolder.recordFailure(currentEpochMillis(), blocking = true)
                LogUtil.w(TAG, "代理路径可用（${viaProxy.code}），网关退出接管")
            } else {
                LogUtil.d(TAG, "代理路径同样 403，网关无责 ${originUrl.host}")
            }
            return finishGateResponse(viaProxy, originUrl, jar)
        }

        // ③ 正常
        GateHealthHolder.recordSuccess()
        return finishGateResponse(gateResponse, originUrl, jar)
    }

    /** 网关响应的收尾：Set-Cookie 按原域名存回（见类 KDoc 第二个洞）。 */
    private fun finishGateResponse(response: Response, originUrl: okhttp3.HttpUrl, jar: HCookieJar): Response {
        val setCookies = response.headers("Set-Cookie")
        if (setCookies.isNotEmpty()) {
            val parsed = setCookies.mapNotNull { raw ->
                runCatching { Cookie.parse(originUrl, raw) }.getOrNull()
            }
            if (parsed.isNotEmpty()) {
                runCatching { jar.saveFromResponse(originUrl, parsed) }
            }
        }

        LogUtil.d(TAG, "${originUrl.host} -> ${EchGatePolicy.GATE_HOST}:${EchGate.port} (${response.code})")
        return response
    }

    private fun isGatewayErrorPage(response: Response): Boolean {
        if (response.code != 502) return false
        return runCatching {
            response.peekBody(PEEK_LIMIT).string().startsWith(GATEWAY_ERROR_PREFIX)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "EchGate"

        /** 网关上游错误页的前缀（见 `echgate` 的 onUpstreamError）。 */
        const val GATEWAY_ERROR_PREFIX = "echgate:"

        /** 网关重试标记：同一请求只重试一次，防环。 */
        const val RETRY_HEADER = "X-Ech-Retry"

        /** 502 判定只看这么多字节（下载大文件场景下不能整包 peek）。 */
        const val PEEK_LIMIT = 256L
    }
}
