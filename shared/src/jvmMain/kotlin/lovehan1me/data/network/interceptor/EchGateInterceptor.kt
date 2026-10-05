package lovehan1me.data.network.interceptor

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGateContract
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.EchGateRuntime
import lovehan1me.data.network.HCookieJar
import lovehan1me.data.network.egress.AttemptOutcome
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressBudgets
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.EgressReporter
import lovehan1me.data.network.egress.EgressRequest
import lovehan1me.data.network.egress.EgressScheduler
import lovehan1me.data.network.egress.GateSkipReason
import lovehan1me.data.network.egress.NoRouteException
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.RouteAttempt
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.data.network.egress.ScheduledPlan
import lovehan1me.data.network.egress.classifyDomain
import lovehan1me.data.network.egress.currentEgressState
import lovehan1me.data.network.egress.currentForceMode
import lovehan1me.data.network.egress.gateBlameAfterYield
import lovehan1me.data.network.egress.isGateBlockedCode
import lovehan1me.data.network.egress.isIdempotent
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 调度执行器（原网关拦截器，Phase 2 改写为通用执行器，类名保留以收敛改动面）。
 *
 * ## 本类不做任何出口判定
 * 判定（排哪几条路、什么顺序、每步多少预算）全在 [EgressScheduler]；改写规则在
 * [EchGatePolicy]。本类只把 `ScheduledPlan.attempts` 走一遍 —— 与 Darwin 插件（Phase 3 切流）
 * 共用同一份计划与同一套记账。
 *
 * ## 从旧实现原样保留的三个洞
 * 1. **Cookie**：改写后按 `127.0.0.1` 匹配域名，登录态与 `cf_clearance` 拿不到 ⇒
 *    按**原域名**取出来塞进 `Cookie` 头；
 * 2. **Set-Cookie**：响应按 `127.0.0.1` 存下来，原域名就再也取不到 ⇒ 用原 URL 重新解析存回去；
 * 3. **"连上了但被阻断"**：网关出口被封时返回 403 而不是异常。有后路（代理/直连）时让位，
 *    由 [gateBlameAfterYield] 判定网关是否有责；**没后路时原样交出去** —— 那多半是 CF 挑战，
 *    `NetworkRepo` 要靠它的 body 触发验证窗，吃掉它等于把验证链掐断。
 *
 * ## 记账
 * 每步结局上报 [EgressReporter]（按域健康 + 诊断事件）：网关出口被封只熔该域，
 * getchu 挂不再连累 hanime。旧全局熔断器已随旧 planner 删除。
 *
 * ## 预算的执行边界（诚实说明）
 * [attempt.budgetMs] 在 attempt 边界与重试门控处执行（502 重试前自查超支即停）；
 * socket 级硬上限仍是各 client 的 connect/read/call 超时（Phase 2 已按用途补齐）。
 * 在途 IO 不能被抢占 —— 这是 OkHttp 同步链的固有限制，不撒谎。
 */
class EchGateInterceptor(
    /**
     * 是否按原域名注入站点 Cookie（见类 KDoc 第一个洞）。
     *
     * 图片/封面链必须传 false：`loadForRequest` 会把 `hanime1_session` 一类的登录态
     * 一并取出，而那些图床是**第三方**——把会话凭据发过去没有任何用途，只有泄漏风险。
     * 需要登录态的链（浏览 / 评论 / 我的列表 / 下载）保持默认 true。
     */
    private val attachSiteCookies: Boolean = true,
    /**
     * 本链的默认用途（决定预算档位）。各 client 按职责传入
     * （浏览/ getchu = Api，下载 = Download，图片 = Image）；调用方可用
     * `request.tag(EgressPurpose::class.java)` 覆盖单次请求。
     */
    private val defaultPurpose: EgressPurpose = EgressPurpose.Api,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val purpose = request.tag(EgressPurpose::class.java) ?: defaultPurpose
        val url = request.url.toString()
        val domain = classifyDomain(url, purpose)

        // 冷启动竞态：网关被要求启动但 LISTENING 未到时，首页请求会抢跑直连撞 RST，
        // 表现为"封面首刷失败、再滑回来又好"。拉起中有界等待，图片与列表统一处理。
        // （位置不变，仍在计划前；计划内各步预算另计。）
        EchGateRuntime.awaitReadyIfStarting()

        val now = currentEpochMillis()
        val intent = EgressRequest(url, request.method, purpose, currentForceMode())
        val plan = EgressScheduler.plan(intent, currentEgressState(), RouteRegistry.healthOf(domain), now)
        if (plan.isEmpty) throw NoRouteException(domain, describeEmpty(plan))
        val jar = HCookieJar()

        // 网关那次拿到的"出口被封"状态码；有后路时拿它跟后路结果比对定责。
        var blockedCode = 0
        var blockedBudgetMs = -1L
        var gateBlamePending = false

        for ((index, attempt) in plan.attempts.withIndex()) {
            // 只有身后还有非网关后路时，403 才按"出口被封"挂起比对；否则原样交出去
            // （多半是 CF 挑战，NetworkRepo 要靠它触发验证窗）。
            val hasLaterRoute = plan.attempts.drop(index + 1).any { it.route != RouteId.Gate }
            when (attempt.route) {
                RouteId.Gate -> when (val step = tryGate(chain, request, attempt, jar, intent, domain, hasLaterRoute)) {
                    is GateStep.Done -> return step.response
                    is GateStep.Blocked -> {
                        blockedCode = step.code
                        blockedBudgetMs = attempt.budgetMs
                        gateBlamePending = true
                    }
                    GateStep.Next -> Unit
                }

                RouteId.Default -> {
                    val startNs = System.nanoTime()
                    val via = try {
                        chain.proceed(request)
                    } catch (e: IOException) {
                        // 调用方主动取消（Coil 滑走、关窗 teardown）不是路的问题：
                        // 不喂熔断器，直接抛。否则取消风暴会把好端端的网关熔断，
                        // 下一个请求诚实失败 —— 2026-10-04 桌面崩溃的完整链条。
                        if (isCallCanceled(chain)) throw e
                        // 本步记传输失败；挂起的网关指控按普通失败记（非阻断口径）。
                        report(attempt.route, domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
                        if (gateBlamePending) {
                            gateBlamePending = false
                            reportGate(domain, AttemptOutcome.TransportError, -1L, blockedBudgetMs)
                        }
                        // 非幂等方法禁止让位（换一条路重发有双提交风险）：失败即止，抛出真实异常 ——
                        // 身后还有 Gate 也**不能**谎称"候选出口全部不可用"：那是我们主动不走，
                        // 不是它不可用（NoRoute 的语义是"表空 / 无路可去"）。
                        if (!intent.isIdempotent) throw e
                        // 幂等方法但已是最后一步：折叠后计划至多两步，Default 通常就在末位；
                        // 唯一还能让位的形态是 Default 被粘滞锁定置顶时（[Default, Gate]）。
                        if (index == plan.attempts.lastIndex) {
                            if (plan.attempts.any { it.route == RouteId.Gate }) {
                                throw NoRouteException(domain, "候选出口全部不可用（${plan.domain}）")
                            }
                            // 纯默认出口计划（关网关 / 第三方 / 强制定向）保持旧语义：
                            // 原异常交外层 Retry 按幂等规则处理。
                            throw e
                        }
                        continue
                    }
                    val rttMs = (System.nanoTime() - startNs) / 1_000_000
                    report(attempt.route, domain, AttemptOutcome.Success, rttMs, attempt.budgetMs)
                    if (gateBlamePending) {
                        gateBlamePending = false
                        if (gateBlameAfterYield(via.code, blockedCode)) {
                            reportGate(domain, AttemptOutcome.Blocked, -1L, blockedBudgetMs)
                            LogUtil.w(TAG, "代理路径可用（${via.code}），网关退出接管")
                        } else {
                            LogUtil.d(TAG, "代理路径同样 $blockedCode，网关无责 ${request.url.host}")
                        }
                    }
                    return finishGateResponse(via, request.url, jar)
                }

                // HTTP 层没有隧道执行器（隧道是播放器/CF 验证窗的事）：计划里本不该出现，
                // 出现则跳过，不把请求打断在这里。
                RouteId.GateTunnel -> Unit
            }
        }
        if (gateBlamePending) {
            // 防御分支：网关在末位、身后无后路时上游已直接 Done，正常到不了这里。
            // 真到了说明判定与执行脱节，按阻断记并打日志，避免静默漏记。
            LogUtil.w(TAG, "网关阻断后无后路可比对，按有责记账 ${request.url.host}")
            reportGate(domain, AttemptOutcome.Blocked, -1L, blockedBudgetMs)
        }
        // 走到这里只有一条原因：候选全是网关步且都返回 Next（502 两次 / 异常），
        // 计划里没有可让位的非网关后路。受限域上不再撞直连，诚实失败。
        // 非网关步失败的两种收尾已在上面的 catch 里分流（见那段注释）：
        // 网关参与过 ⇒ 这里同款 NoRoute；纯直通 ⇒ 原异常交外层 Retry。
        // NoRoute 不是 Retry 认得的连接类异常（见 `NoRouteException`），不重跑，直达 UI。
        throw NoRouteException(domain, "候选出口全部不可用（${plan.domain}）")
    }

    /** 单个候选出口的结局。 */
    private sealed interface GateStep {
        /** 拿到了可用的响应，本次请求结束。 */
        data class Done(val response: Response) : GateStep

        /** 网关通了但出口被封（403），有后路时挂起比对定责。 */
        data class Blocked(val code: Int) : GateStep

        /** 这个出口不可用，试下一个候选。 */
        data object Next : GateStep
    }

    private fun tryGate(
        chain: Interceptor.Chain,
        request: okhttp3.Request,
        attempt: RouteAttempt,
        jar: HCookieJar,
        intent: EgressRequest,
        domain: DomainClass,
        hasLaterRoute: Boolean,
    ): GateStep {
        val originUrl = request.url
        val rewrite = attempt.rewrite ?: return GateStep.Next
        val gateUrl = runCatching { rewrite.url.toHttpUrl() }.getOrNull() ?: return GateStep.Next
        val startNs = System.nanoTime()

        val builder = request.newBuilder()
            .url(gateUrl)
            .header(EchGatePolicy.TARGET_HEADER, rewrite.targetHost)
            .header("Host", rewrite.targetHost)

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
            // 同上：取消不喂熔断。
            if (isCallCanceled(chain)) throw e
            reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
            LogUtil.w(TAG, "网关异常，回退下一出口 ${originUrl.host} (${e.message})")
            return GateStep.Next
        }

        // 网关自己的上游错误页
        if (isGatewayErrorPage(gateResponse)) {
            if (!intent.isIdempotent) {
                // 非幂等方法既不能重试也不能让位（重发有双提交风险）：把网关这份原样交出去，
                // 但记一次失败 —— 否则"POST 一直撞 502"永远攒不到熔断。
                reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
                return GateStep.Done(finishGateResponse(gateResponse, originUrl, jar))
            }
            gateResponse.close()
            // 两道预算门：外层 RetryInterceptor 下传的总预算，以及本步的单步预算。
            // 单步预算在此处执行 —— 超支即停，不再重试网关。
            if (budgetExhausted(request) || stepBudgetExhausted(startNs, attempt.budgetMs)) {
                reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
                LogUtil.w(TAG, "重试预算已耗尽，不再重试网关 ${originUrl.host}")
                return GateStep.Next
            }
            LogUtil.w(TAG, "网关上游失败，重试网关一次 ${originUrl.host}")
            val retried = try {
                chain.proceed(builder.header(RETRY_HEADER, "1").build())
            } catch (e: IOException) {
                if (isCallCanceled(chain)) throw e
                reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
                LogUtil.w(TAG, "网关重试异常，回退下一出口 ${originUrl.host} (${e.message})")
                return GateStep.Next
            }
            if (isGatewayErrorPage(retried)) {
                retried.close()
                reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
                LogUtil.w(TAG, "网关重试仍失败，回退下一出口 ${originUrl.host}")
                return GateStep.Next
            }
            reportGate(domain, AttemptOutcome.Success, elapsedMs(startNs), attempt.budgetMs)
            return GateStep.Done(finishGateResponse(retried, originUrl, jar))
        }

        // 连上了、但像是"出口被封"：有后路才挂起比对；没后路原样交出去
        // （多半是 CF 挑战，body 必须到达 NetworkRepo）。
        if (hasLaterRoute && intent.isIdempotent && isGateBlockedCode(gateResponse.code)) {
            gateResponse.close()
            return GateStep.Blocked(gateResponse.code)
        }

        reportGate(domain, AttemptOutcome.Success, elapsedMs(startNs), attempt.budgetMs)
        return GateStep.Done(finishGateResponse(gateResponse, originUrl, jar))
    }

    /** 调用方是否主动取消了本次呼叫（见 intercept 的取消注释）。 */
    private fun isCallCanceled(chain: Interceptor.Chain): Boolean =
        runCatching { chain.call().isCanceled() }.getOrDefault(false)

    /** 本步上报：新注册表（按域）是唯一的记账处（旧全局熔断器已随旧 planner 删除）。 */
    private fun reportGate(domain: DomainClass, outcome: AttemptOutcome, rttMs: Long, budgetMs: Long = -1L) {
        report(RouteId.Gate, domain, outcome, rttMs, budgetMs)
    }

    private fun report(route: RouteId, domain: DomainClass, outcome: AttemptOutcome, rttMs: Long, budgetMs: Long = -1L) {
        EgressReporter.report(domain, route, outcome, rttMs, budgetMs = budgetMs)
    }

    private fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000

    private fun stepBudgetExhausted(startNs: Long, budgetMs: Long): Boolean {
        if (budgetMs == EgressBudgets.UNLIMITED) return false
        return elapsedMs(startNs) >= budgetMs
    }

    /** 表空的原因人话（抛给上层前组装，见 [NoRouteException]）。 */
    private fun describeEmpty(plan: ScheduledPlan): String {
        val gatePart = when (plan.skipped) {
            GateSkipReason.Disabled -> "网关已关闭"
            GateSkipReason.NotRunning -> "网关未运行"
            GateSkipReason.CircuitOpen -> "网关熔断中"
            GateSkipReason.NotHttps, GateSkipReason.LoopbackOrLiteral, GateSkipReason.InvalidUrl ->
                "该 URL 不适用网关"
            null -> "网关不可用"
        }
        return "$gatePart，且未配置可用代理（${plan.domain}）"
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
