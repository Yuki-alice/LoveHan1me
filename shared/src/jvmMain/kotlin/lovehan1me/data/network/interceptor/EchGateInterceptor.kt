package lovehan1me.data.network.interceptor

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGateContract
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.EchGateRuntime
import lovehan1me.data.network.HCookieJar
import lovehan1me.data.network.egress.EgressAttempt
import lovehan1me.data.network.egress.EgressPlanner
import lovehan1me.data.network.egress.EgressRequest
import lovehan1me.data.network.egress.GateHealthHolder
import lovehan1me.data.network.egress.currentEgressState
import lovehan1me.data.network.egress.gateBlameAfterYield
import lovehan1me.data.network.egress.isGateBlockedCode
import lovehan1me.data.network.egress.isIdempotent
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 按 [EgressPlanner] 给出的**有序候选**依次尝试出口。
 *
 * ## 本类不做任何出口判定
 * 判定（该不该用网关、失败了下一步走哪）全在 [EgressPlanner]；改写规则在
 * [EchGatePolicy]。本类只把 `EgressPlan.attempts` 走一遍 —— 与 Ktor 插件、图片插件、
 * 播放器共用同一份计划与同一套记账。
 * 此前"失败后下一步走哪"写死在下面三段 if 里，另外两处执行器各抄一份且抄得不一样。
 *
 * ## 三个必须手动补的洞
 * 1. **Cookie**：改写后 OkHttp 的 CookieJar 按 `127.0.0.1` 匹配域名，登录态与
 *    `cf_clearance` 全部拿不到 ⇒ 这里按**原域名**取出来塞进 `Cookie` 头。
 * 2. **Set-Cookie**：响应按 `127.0.0.1` 存下来，原域名就再也取不到 ⇒ 用原 URL 重新解析存回去。
 * 3. **"连上了但被阻断"**：网关的出口被封时返回的是 403 而不是异常。这类响应看起来像
 *    站点问题（`you have been blocked` / `Just a moment`），而用户手里可能有一条**能用的代理**
 *    —— 这时让位给 [EgressAttempt.Yield]，由 [gateBlameAfterYield] 判定网关是否有责。
 *
 * ## 网关的失败分两类，都不让它成为单点
 * - 网关抛 [IOException]（进程挂了 / 端口没监听）、或回自己的上游错误页（502 + `echgate:` 前缀）
 *   ⇒ 记一次普通失败，试下一个候选；
 * - 网关回 403（出口被封）⇒ 让位，不再反复撞它。
 */
class EchGateInterceptor(
    /**
     * 是否按原域名注入站点 Cookie。
     *
     * 图片/封面链必须传 false：`loadForRequest` 会把 `hanime1_session` 一类的登录态
     * 一并取出，而那些图床（`vdownload.hembed.com` → CDN77）是**第三方**——
     * 把会话凭据发过去没有任何用途，只有泄漏风险。
     * 需要登录态的链（浏览 / 评论 / 我的列表 / 下载）保持默认 true。
     */
    private val attachSiteCookies: Boolean = true,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 熔断中：不接管、也不为它等待。等价于网关不存在，本次直接走原出口。
        // （顺带省掉下面那笔 8s 就绪等待 —— 熔断期间每个请求都白等是纯亏。）
        val now = currentEpochMillis()
        if (GateHealthHolder.current.isOpen(now)) return chain.proceed(request)

        // 冷启动竞态：网关被要求启动但 LISTENING 未到时，首页请求会抢跑直连撞 RST，
        // 表现为"封面首刷失败、再滑回来又好"。拉起中有界等待，图片与列表统一处理。
        EchGateRuntime.awaitReadyIfStarting()

        val intent = EgressRequest(request.url.toString(), request.method)
        val plan = EgressPlanner.plan(intent, currentEgressState(now))
        val jar = HCookieJar()

        // 网关那次拿到的"出口被封"状态码；让位时要拿它跟让位结果比对。
        var blockedCode = 0

        for (attempt in plan.attempts) {
            when (attempt) {
                is EgressAttempt.Passthrough -> {
                    plan.skipped?.let { LogUtil.d(TAG, "不经网关（$it）${request.url.host}") }
                    return chain.proceed(request)
                }

                is EgressAttempt.Gate -> when (val step = tryGate(chain, request, attempt, jar, intent)) {
                    is GateStep.Done -> return step.response
                    is GateStep.Blocked -> blockedCode = step.code
                    GateStep.Next -> Unit
                }

                EgressAttempt.Yield -> {
                    LogUtil.w(TAG, "网关返回 $blockedCode，经代理路径重试一次 ${request.url.host}")
                    val viaProxy = runCatching { chain.proceed(request) }.getOrNull()
                    if (viaProxy == null) {
                        // 让位路径直接抛了：按普通网关失败处理（非阻断，累计口径），
                        // 循环走到头后由下面那句兜底再走一遍原路。
                        GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                        continue
                    }
                    if (gateBlameAfterYield(viaProxy.code, blockedCode)) {
                        GateHealthHolder.recordFailure(currentEpochMillis(), blocking = true)
                        LogUtil.w(TAG, "代理路径可用（${viaProxy.code}），网关退出接管")
                    } else {
                        LogUtil.d(TAG, "代理路径同样 $blockedCode，网关无责 ${request.url.host}")
                    }
                    return finishGateResponse(viaProxy, request.url, jar)
                }
            }
        }
        // 候选全部不可用：交给传输层自己再走一遍原路（DNS / 代理选择器该怎样就怎样）。
        return chain.proceed(request)
    }

    /** 单个候选出口的结局。 */
    private sealed interface GateStep {
        /** 拿到了可用的响应，本次请求结束。 */
        data class Done(val response: Response) : GateStep

        /** 网关通了但出口被封（403），该让位 —— 不再重试网关。 */
        data class Blocked(val code: Int) : GateStep

        /** 这个出口不可用，试下一个候选。 */
        data object Next : GateStep
    }

    private fun tryGate(
        chain: Interceptor.Chain,
        request: okhttp3.Request,
        attempt: EgressAttempt.Gate,
        jar: HCookieJar,
        intent: EgressRequest,
    ): GateStep {
        val originUrl = request.url
        val gateUrl = runCatching { attempt.rewrite.url.toHttpUrl() }.getOrNull() ?: return GateStep.Next

        val builder = request.newBuilder()
            .url(gateUrl)
            .header(EchGatePolicy.TARGET_HEADER, attempt.rewrite.targetHost)
            .header("Host", attempt.rewrite.targetHost)

        if (attachSiteCookies) {
            val cookies = runCatching { jar.loadForRequest(originUrl) }.getOrDefault(emptyList())
            if (cookies.isNotEmpty()) {
                builder.header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
            }
        }

        // 网关异常（进程挂了 / 端口没监听）
        val gateResponse = try {
            chain.proceed(builder.build())
        } catch (e: IOException) {
            GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
            LogUtil.w(TAG, "网关异常，回退直连 ${originUrl.host} (${e.message})")
            return GateStep.Next
        }

        // 网关自己的上游错误页
        if (isGatewayErrorPage(gateResponse)) {
            if (!intent.isIdempotent) {
                // 非幂等方法既不能重试也不能让位（重发有双提交风险）：把网关这份原样交出去，
                // 但记一次失败 —— 否则"POST 一直撞 502"永远攒不到熔断。
                GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                return GateStep.Done(finishGateResponse(gateResponse, originUrl, jar))
            }
            gateResponse.close()
            // 预算由外层 RetryInterceptor 下传：它只管 attempt 之间，管不到这里
            // （单次 attempt 内最多三次往返 × 15s 连接超时 = 分钟级转圈）。
            if (budgetExhausted(request)) {
                GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                LogUtil.w(TAG, "重试预算已耗尽，不再重试网关 ${originUrl.host}")
                return GateStep.Next
            }
            LogUtil.w(TAG, "网关上游失败，重试网关一次 ${originUrl.host}")
            val retried = try {
                chain.proceed(builder.header(RETRY_HEADER, "1").build())
            } catch (e: IOException) {
                GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                LogUtil.w(TAG, "网关重试异常，回退直连 ${originUrl.host} (${e.message})")
                return GateStep.Next
            }
            if (isGatewayErrorPage(retried)) {
                retried.close()
                GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                LogUtil.w(TAG, "网关重试仍失败，回退直连 ${originUrl.host}")
                return GateStep.Next
            }
            GateHealthHolder.recordSuccess()
            return GateStep.Done(finishGateResponse(retried, originUrl, jar))
        }

        // 连上了、但像是"出口被封"
        if (attempt.onProbation && intent.isIdempotent && isGateBlockedCode(gateResponse.code)) {
            gateResponse.close()
            return GateStep.Blocked(gateResponse.code)
        }

        GateHealthHolder.recordSuccess()
        return GateStep.Done(finishGateResponse(gateResponse, originUrl, jar))
    }

    /** 网关响应的收尾：Set-Cookie 按原域名存回（见类 KDoc 第二个洞）。 */
    private fun finishGateResponse(response: Response, originUrl: okhttp3.HttpUrl, jar: HCookieJar): Response {
        // 存回与注入必须同一个开关：只关注入不关存回，图床的 Set-Cookie 仍会落进 jar。
        if (attachSiteCookies) {
            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                val parsed = setCookies.mapNotNull { raw ->
                    runCatching { Cookie.parse(originUrl, raw) }.getOrNull()
                }
                if (parsed.isNotEmpty()) {
                    runCatching { jar.saveFromResponse(originUrl, parsed) }
                }
            }
        }

        LogUtil.d(TAG, "${originUrl.host} -> ${EchGatePolicy.GATE_HOST}:${EchGate.port} (${response.code})")
        return response
    }

    /** 外层下传的重试预算是否已耗尽；没有预算标记时视为未耗尽（不装 Retry 的链照旧）。 */
    private fun budgetExhausted(request: okhttp3.Request): Boolean {
        val deadline = request.tag(RetryDeadline::class.java) ?: return false
        return System.nanoTime() >= deadline.deadlineNanos
    }

    private fun isGatewayErrorPage(response: Response): Boolean {
        if (response.code != 502) return false
        return runCatching {
            EchGateContract.isErrorPage(response.peekBody(PEEK_LIMIT).string())
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "EchGate"

        /** 网关重试标记：同一请求只重试一次，防环。 */
        const val RETRY_HEADER = "X-Ech-Retry"

        /** 502 判定只看这么多字节（下载大文件场景下不能整包 peek）。 */
        const val PEEK_LIMIT = 256L
    }
}
