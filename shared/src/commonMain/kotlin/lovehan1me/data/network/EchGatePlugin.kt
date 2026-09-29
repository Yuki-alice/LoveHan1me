package lovehan1me.data.network

import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.network.egress.EgressAttempt
import lovehan1me.data.network.egress.EgressPlanner
import lovehan1me.data.network.egress.EgressRequest
import lovehan1me.data.network.egress.GateHealthHolder
import lovehan1me.data.network.egress.currentEgressState
import lovehan1me.data.network.egress.gateBlameAfterYield
import lovehan1me.data.network.egress.isGateBlockedCode
import lovehan1me.data.network.egress.isIdempotent

/**
 * Ktor 侧的 ECH 网关插件（Darwin/iOS 用；JVM 走 OkHttp 拦截器，不装这个）。
 *
 * 与 jvmMain `EchGateInterceptor` 消费**同一个** [EgressPlanner] 计划、同一套记账：
 * 按 `EgressPlan.attempts` 依次尝试，自己不做任何出口判定。此前这里直接调
 * `EchGatePolicy.rewrite` 并自备一份回退，于是"判定分叉"只是换了个地方存在。
 *
 * Cookie 由 [EchGatePluginConfig.cookieHeaderProvider] 按**原域名**取（改写后按
 * 127.0.0.1 匹配域名会拿不到登录态/clearance，与 OkHttp 拦截器手动补 Cookie 是同一个洞）；
 * 调用方若同时装了 `HttpCookies`，其 storage 应对网关回环 host 短路
 * （见 iosMain `BridgeCookiesStorage`），否则同一 Cookie 发两遍。
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

    var logger: ((String) -> Unit)? = null
}

val EchGateClientPlugin = createClientPlugin("EchGate", ::EchGatePluginConfig) {
    on(Send) { request ->
        runCatching { pluginConfig.awaitGateReady?.invoke() }
        val original = request.url.build()
        val intent = EgressRequest(original.toString(), request.method.value)
        val plan = EgressPlanner.plan(intent, currentEgressState())

        var blockedCode = 0
        for (attempt in plan.attempts) {
            when (attempt) {
                is EgressAttempt.Passthrough -> {
                    plan.skipped?.let { pluginConfig.logger?.invoke("EchGate: 不经网关（$it）${original.host}") }
                    return@on proceed(request)
                }

                is EgressAttempt.Gate -> when (val step = tryGate(request, original, attempt, pluginConfig, intent)) {
                    is GateStep.Done -> return@on step.call
                    is GateStep.Blocked -> blockedCode = step.code
                    GateStep.Next -> Unit
                }

                EgressAttempt.Yield -> {
                    pluginConfig.logger?.invoke("EchGate: 网关返回 $blockedCode，经代理路径重试一次 $original")
                    val viaProxy = try {
                        proceed(request)
                    } catch (e: Exception) {
                        GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
                        continue
                    }
                    if (gateBlameAfterYield(viaProxy.response.status.value, blockedCode)) {
                        GateHealthHolder.recordFailure(currentEpochMillis(), blocking = true)
                        pluginConfig.logger?.invoke("EchGate: 代理路径可用，网关退出接管 $original")
                    } else {
                        pluginConfig.logger?.invoke("EchGate: 代理路径同样 $blockedCode，网关无责 $original")
                    }
                    return@on viaProxy
                }
            }
        }
        // 候选全部不可用：恢复成原 URL 再走一遍（Darwin 用自己的 DNS 与系统代理）。
        restoreOriginal(request, original)
        proceed(request)
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
    attempt: EgressAttempt.Gate,
    pluginConfig: EchGatePluginConfig,
    intent: EgressRequest,
): GateStep {
    // 原地变异（path/query 原样保留；3.5.2 的 URLBuilder 无 takeFrom 成员）。
    request.url.protocol = URLProtocol.HTTP
    request.url.host = EchGatePolicy.GATE_HOST
    request.url.port = attempt.rewrite.port
    request.headers.append(EchGatePolicy.TARGET_HEADER, attempt.rewrite.targetHost)
    val cookie = runCatching { pluginConfig.cookieHeaderProvider?.invoke(original) }.getOrNull()
    if (!cookie.isNullOrBlank() && !request.headers.contains("Cookie")) {
        request.headers.append("Cookie", cookie)
    }

    val gateCall = try {
        proceed(request)
    } catch (e: Exception) {
        // 网关异常（进程挂了 / 端口未监听）。同时记一次失败喂给熔断器：iOS 没有
        // "退到代理"这一档，熔断是它唯一的"别再反复撞墙"的手段。
        GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
        pluginConfig.logger?.invoke("EchGate: 网关异常回退直连 $original (${e.message})")
        restoreOriginal(request, original)
        return GateStep.Next
    }

    // 网关回 502 只会是它自己的上游错误页。刻意**不读 body**去核对 `echgate:` 前缀：
    // bodyAsText() 会把响应消费掉，等下返回给调用方的就是个空壳；而网关的 502 恒由
    // 它自己的 onUpstreamError 产生，按状态码判定已经足够准。
    if (gateCall.response.status == HttpStatusCode.BadGateway) {
        if (!intent.isIdempotent) {
            // 非幂等方法既不能重试也不能让位（重发有双提交风险）：把网关这份原样交出去，
            // 但记一次失败 —— 否则"POST 一直撞 502"永远攒不到熔断。
            GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
            return GateStep.Done(gateCall)
        }
        pluginConfig.logger?.invoke("EchGate: 网关上游失败，重试网关一次 $original")
        val retried = try {
            proceed(request)
        } catch (e: Exception) {
            GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
            pluginConfig.logger?.invoke("EchGate: 网关重试异常，回退直连 $original (${e.message})")
            restoreOriginal(request, original)
            return GateStep.Next
        }
        if (retried.response.status != HttpStatusCode.BadGateway) {
            GateHealthHolder.recordSuccess()
            return GateStep.Done(retried)
        }
        GateHealthHolder.recordFailure(currentEpochMillis(), blocking = false)
        pluginConfig.logger?.invoke("EchGate: 网关重试仍失败，回退直连 $original")
        restoreOriginal(request, original)
        return GateStep.Next
    }

    if (attempt.onProbation && intent.isIdempotent && isGateBlockedCode(gateCall.response.status.value)) {
        return GateStep.Blocked(gateCall.response.status.value)
    }

    GateHealthHolder.recordSuccess()
    return GateStep.Done(gateCall)
}

/** 把被改写的 builder 恢复成原请求（URL + 去 Target 头；Cookie 头是幂等的附加）。 */
private fun restoreOriginal(request: HttpRequestBuilder, original: Url) {
    request.url.protocol = original.protocol
    request.url.host = original.host
    request.url.port = original.port
    request.headers.remove(EchGatePolicy.TARGET_HEADER)
}
