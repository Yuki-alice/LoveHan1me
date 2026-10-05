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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Ktor 侧的调度执行器（Darwin/iOS 用；JVM 走 OkHttp 拦截器，不装这个）。
 *
 * Phase 3 改写为通用执行器：与 jvmMain `EchGateInterceptor` 消费**同一个**
 * [EgressScheduler] 计划、同一套记账（[EgressReporter]），只把"怎么发请求"换成
 * Ktor 写法。此前这里调老 `EgressPlanner` 并自备回退，判定分叉只是换了个地方存在。
 *
 * ## 与 OkHttp 执行器的三处差异（刻意，非遗漏）
 * 1. **502 不验错误页前缀**：`bodyAsText()` 会把响应消费掉，返回给调用方的就是空壳；
 *    网关的 502 恒由它自己的 `onUpstreamError` 产生，按状态码判定已经足够准。
 * 2. **代理/直连步完全交给 NSURLSession**：恢复原 URL 后直接发，系统代理自动生效；
 *    用户手填的显式代理**不会**被应用（Darwin 引擎无按请求配代理的口子，见
 *    `ProxyCapability.ios`）—— 已知缺口，Phase 4 处理，计划里照排不撒谎。
 * 3. **无就绪等待**：iOS 起服由 Swift 壳管、`EchGatePortReporter` 被动回填，
 *    Kotlin 侧没有"拉起中"状态可等；端口没到就是没跑，按计划诚实失败。
 *
 * Cookie 由 [EchGatePluginConfig.cookieHeaderProvider] 按**原域名**取（改写后按
 * 127.0.0.1 匹配域名会拿不到登录态/clearance，与 OkHttp 拦截器手动补 Cookie 是同一个洞）；
 * 调用方若同时装了 `HttpCookies`，其 storage 应对网关回环 host 短路
 * （见 iosMain `BridgeCookiesStorage`），否则同一 Cookie 发两遍。
 *
 * ## 记账
 * 每步结局上报 [EgressReporter]（按域健康 + 诊断事件），与 OkHttp 执行器同一套。
 * 旧全局熔断器已随旧 planner 删除。
 */
class EchGatePluginConfig {
    /**
     * 按**原 URL**取 Cookie 请求头（`k=v; k2=v2`），null/blank 则不附加。
     * iOS 传桥接实现（内存桥 + DataStore 登录态 + clearance）；JVM 不用装本插件。
     */
    var cookieHeaderProvider: (suspend (originalUrl: Url) -> String?)? = null

    /**
     * 网关拉起中的有界等待（无运行时传 null）。iOS 暂无运行时，保持 null。
     */
    var awaitGateReady: (suspend () -> Boolean)? = null

    /**
     * 本 client 的默认用途（决定预算档位）。各工厂按职责传入
     * （浏览/getchu = Api，下载 = Download）；调用方可用
     * `request.attributes[EgressPurposeAttributeKey]` 覆盖单次。
     */
    var defaultPurpose: EgressPurpose = EgressPurpose.Api

    var logger: ((String) -> Unit)? = null
}

/** 单次请求覆盖用途的 attribute key（对齐 OkHttp 侧 `request.tag`）。 */
val EgressPurposeAttributeKey = AttributeKey<EgressPurpose>("EgressPurpose")

/**
 * Darwin 传输层重试谓词（与 jvm `RetryInterceptor` 同语义：只重试幂等方法、只重试传输层异常）。
 *
 * 诚实失败（[NoRouteException]）不重试：重排一次计划也不会多出路来，只会给失败加延迟。
 * 抽成函数只为可单测，调用方是 iosMain 工厂的 `HttpRequestRetry`。
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
        val domain = classifyDomain(url, purpose)
        val now = currentEpochMillis()
        val plan = EgressScheduler.plan(
            EgressRequest(url, request.method.value, purpose, currentForceMode()),
            currentEgressState(),
            RouteRegistry.healthOf(domain),
            now,
        )
        if (plan.isEmpty) throw NoRouteException(domain, describeEmpty(plan))

        var blockedCode = 0
        var blockedBudgetMs = -1L
        var gateBlamePending = false
        for ((index, attempt) in plan.attempts.withIndex()) {
            // 只有身后还有非网关后路时，403 才按"出口被封"挂起比对；否则原样交出去
            // （多半是 CF 挑战，NetworkRepo 要靠它触发验证窗）。
            val hasLaterRoute = plan.attempts.drop(index + 1).any { it.route != RouteId.Gate }
            when (attempt.route) {
                RouteId.Gate -> when (val step = tryGate(request, original, attempt, pluginConfig, url, domain, hasLaterRoute)) {
                    is GateStep.Done -> return@on step.call
                    is GateStep.Blocked -> {
                        blockedCode = step.code
                        blockedBudgetMs = attempt.budgetMs
                        gateBlamePending = true
                    }
                    GateStep.Next -> Unit
                }

                RouteId.Default -> {
                    restoreOriginal(request, original)
                    val startMs = currentEpochMillis()
                    val idempotent = request.method.value == "GET" || request.method.value == "HEAD"
                    val viaProxy = try {
                        proceed(request)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        report(attempt.route, domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
                        if (gateBlamePending) {
                            gateBlamePending = false
                            reportGate(domain, AttemptOutcome.TransportError, -1L, blockedBudgetMs)
                        }
                        // 与 OkHttp 执行器同款分流（见 `EchGateInterceptor` 内注释）：
                        // 非幂等方法失败即止、抛真实异常（不谎称 NoRoute）；幂等但已是最后一步时，
                        // 网关参与过的计划全败走 NoRoute，纯默认出口计划把原异常交出去。
                        // 折叠后唯一能让位的形态是 Default 被粘滞锁定置顶时的 [Default, Gate]。
                        if (!idempotent) throw e
                        // 幂等但已是最后一步：网关参与过的计划全败走 NoRoute，
                        // 纯默认出口计划把原异常交出去。
                        // 折叠后唯一能让位的形态是 Default 被粘滞锁定置顶时的 [Default, Gate]。
                        if (index == plan.attempts.lastIndex) {
                            if (plan.attempts.any { it.route == RouteId.Gate }) {
                                throw NoRouteException(domain, "候选出口全部不可用（${plan.domain}）")
                            }
                            throw e
                        }
                        continue
                    }
                    val rttMs = currentEpochMillis() - startMs
                    report(attempt.route, domain, AttemptOutcome.Success, rttMs, attempt.budgetMs)
                    if (gateBlamePending) {
                        gateBlamePending = false
                        if (gateBlameAfterYield(viaProxy.response.status.value, blockedCode)) {
                            reportGate(domain, AttemptOutcome.Blocked, -1L, blockedBudgetMs)
                            pluginConfig.logger?.invoke("EchGate: 代理路径可用，网关退出接管 $original")
                        } else {
                            pluginConfig.logger?.invoke("EchGate: 代理路径同样 $blockedCode，网关无责 $original")
                        }
                    }
                    return@on viaProxy
                }

                // HTTP 层没有隧道执行器（隧道是播放器/CF 验证窗的事）：计划里本不该出现，
                // 出现则跳过，不把请求打断在这里。
                RouteId.GateTunnel -> Unit
            }
        }
        if (gateBlamePending) {
            pluginConfig.logger?.invoke("EchGate: 网关阻断后无后路可比对，按有责记账 $original")
            reportGate(domain, AttemptOutcome.Blocked, -1L, blockedBudgetMs)
        }
        // 走到这里只有一条原因：候选全是网关步且都返回 Next（502 两次 / 异常）。
        // 非网关步失败的收尾已在上面的 catch 里分流（网关参与过 ⇒ NoRoute；纯直通 ⇒ 原异常）。
        throw NoRouteException(domain, "候选出口全部不可用（${plan.domain}）")
    }
}

/** 单个候选出口的结局。 */
private sealed interface GateStep {
    data class Done(val call: HttpClientCall) : GateStep
    data class Blocked(val code: Int) : GateStep
    data object Next : GateStep
}

private suspend fun Send.Sender.tryGate(
    request: HttpRequestBuilder,
    original: Url,
    attempt: RouteAttempt,
    pluginConfig: EchGatePluginConfig,
    url: String,
    domain: DomainClass,
    hasLaterRoute: Boolean,
): GateStep {
    val rewrite = attempt.rewrite ?: return GateStep.Next
    val startMs = currentEpochMillis()
    // 原地变异（path/query 原样保留；3.5.2 的 URLBuilder 无 takeFrom 成员）。
    request.url.protocol = URLProtocol.HTTP
    request.url.host = EchGatePolicy.GATE_HOST
    request.url.port = rewrite.port
    request.headers.append(EchGatePolicy.TARGET_HEADER, rewrite.targetHost)
    val cookie = runCatching { pluginConfig.cookieHeaderProvider?.invoke(original) }.getOrNull()
    if (!cookie.isNullOrBlank() && !request.headers.contains("Cookie")) {
        request.headers.append("Cookie", cookie)
    }

    val intentIdempotent = request.method.value == "GET" || request.method.value == "HEAD"
    val gateCall = try {
        proceed(request)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 网关异常（进程挂了 / 端口未监听）。
        reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
        pluginConfig.logger?.invoke("EchGate: 网关异常回退下一出口 $original (${e.message})")
        restoreOriginal(request, original)
        return GateStep.Next
    }

    // 网关回 502 只会是它自己的上游错误页。刻意**不读 body**去核对 `echgate:` 前缀：
    // bodyAsText() 会把响应消费掉，等下返回给调用方的就是个空壳；而网关的 502 恒由
    // 它自己的 onUpstreamError 产生，按状态码判定已经足够准。
    if (gateCall.response.status == HttpStatusCode.BadGateway) {
        if (!intentIdempotent) {
            // 非幂等方法既不能重试也不能让位（重发有双提交风险）：把网关这份原样交出去，
            // 但记一次失败 —— 否则"POST 一直撞 502"永远攒不到熔断。
            reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
            return GateStep.Done(gateCall)
        }
        // 单步预算在此处执行：超支即停，不再重试网关（与 OkHttp 执行器同语义）。
        if (currentEpochMillis() - startMs >= attempt.budgetMs && attempt.budgetMs != EgressBudgets.UNLIMITED) {
            reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
            pluginConfig.logger?.invoke("EchGate: 重试预算已耗尽，不再重试网关 $original")
            restoreOriginal(request, original)
            return GateStep.Next
        }
        pluginConfig.logger?.invoke("EchGate: 网关上游失败，重试网关一次 $original")
        val retried = try {
            proceed(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
            pluginConfig.logger?.invoke("EchGate: 网关重试异常，回退下一出口 $original (${e.message})")
            restoreOriginal(request, original)
            return GateStep.Next
        }
        if (retried.response.status != HttpStatusCode.BadGateway) {
            reportGate(domain, AttemptOutcome.Success, currentEpochMillis() - startMs, attempt.budgetMs)
            return GateStep.Done(retried)
        }
        reportGate(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
        pluginConfig.logger?.invoke("EchGate: 网关重试仍失败，回退下一出口 $original")
        restoreOriginal(request, original)
        return GateStep.Next
    }

    if (hasLaterRoute && intentIdempotent && isGateBlockedCode(gateCall.response.status.value)) {
        return GateStep.Blocked(gateCall.response.status.value)
    }

    reportGate(domain, AttemptOutcome.Success, currentEpochMillis() - startMs, attempt.budgetMs)
    return GateStep.Done(gateCall)
}

/** 本步上报：新注册表（按域）是唯一的记账处（旧全局熔断器已随旧 planner 删除）。 */
private fun reportGate(domain: DomainClass, outcome: AttemptOutcome, rttMs: Long, budgetMs: Long = -1L) {
    report(RouteId.Gate, domain, outcome, rttMs, budgetMs)
}

private fun report(route: RouteId, domain: DomainClass, outcome: AttemptOutcome, rttMs: Long, budgetMs: Long = -1L) {
    EgressReporter.report(domain, route, outcome, rttMs, budgetMs = budgetMs)
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

/** 把被改写的 builder 恢复成原请求（URL + 去 Target 头；Cookie 头是幂等的附加）。 */
private fun restoreOriginal(request: HttpRequestBuilder, original: Url) {
    request.url.protocol = original.protocol
    request.url.host = original.host
    request.url.port = original.port
    request.headers.remove(EchGatePolicy.TARGET_HEADER)
}
