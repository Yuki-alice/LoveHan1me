package lovehan1me.data.network.egress

import lovehan1me.core.platform.currentEpochMillis

/**
 * 两个执行器（OkHttp / Ktor）**逐字重复**的流程收敛 —— F17 的第二步。
 *
 * ## 为什么
 * 排表、循环顺序、让位定责、502 重试门控、末步分流与收尾在 `EchGateInterceptor`（jvmMain）
 * 与 `EchGateClientPlugin`（commonMain）里此前各有一份**逐字拷贝**；改动要改两处，漏一处就
 * 分叉（Android 与 iOS 行为不一致）。这里把循环与记账收成唯一实现，两个引擎只留下
 * **"怎么发请求"** 的原语（同步 `chain.proceed` vs 挂起 `proceed`、请求原地变异 vs 重建、
 * 502 判定口径、计时口径），其余一律不再各写一份。
 *
 * ## 收到的部分
 * - **计划**：意图 + [EgressScheduler.plan] + 表空即 [NoRouteException]；
 * - **GateStep 状态机**：网关步的 502 单次重试、单步预算自查、"连上但被封"的挂起；
 * - **让位定责**：默认出口步的失败分流、[gateBlameAfterYield] 比对、阻断收尾。
 *
 * ## 刻意留在引擎的部分（差异是**真实**的，不是遗漏）
 * - 网关 502 判定：OkHttp 读 256B 前缀核对 `echgate:`，Ktor 只看状态码（读 body 会消费响应）；
 * - 预算门：OkHttp 除单步预算外还有外层 `RetryInterceptor` 下传的总预算；
 * - 计时口径：OkHttp 用 `nanoTime`，Ktor 用 `currentEpochMillis`；
 * - 收尾：OkHttp 要把 `Set-Cookie` 按**原域名**存回（改写后是 127.0.0.1），Ktor 直接返回。
 *
 * ## 旧实现原样保留的三个洞（引擎侧承载，行为不变）
 * 1. **Cookie**：改写后按 `127.0.0.1` 匹配域名拿不到登录态/clearance ⇒ 按原域名取出塞 `Cookie` 头；
 * 2. **Set-Cookie**：按原 URL 重新解析存回；
 * 3. **"连上了但被阻断"**：403 且**有后路**时让位比对；没后路原样交出去
 *    （那多半是 CF 挑战，`NetworkRepo` 要靠它的 body 触发验证窗）。
 */

/** 单个候选出口的结局（引擎无关）。 */
internal sealed interface GateStep<out R> {
    /** 拿到了可用的响应，本次请求结束。 */
    data class Done<R>(val response: R) : GateStep<R>

    /** 网关通了但出口被封（403），有后路时挂起比对定责。 */
    data class Blocked(val code: Int) : GateStep<Nothing>

    /** 这个出口不可用，试下一个候选。 */
    data object Next : GateStep<Nothing>
}

/** 默认（直连/代理）出口步的结果：响应 + 本步 RTT（引擎各自计时）。 */
internal class DefaultResult<R>(val response: R, val rttMs: Long)

/**
 * 执行引擎原语：两个实现只负责"怎么发请求"与"怎么收尾"，
 * 循环、让位定责、末步分流、记账全在 [runEgress]。
 */
internal interface EgressEngine<R> {
    /**
     * 网关候选步：按 [RouteAttempt] 改写并发出。`retry = true` 时带重试标记（一次性）。
     * 返回 null = 该 attempt 不适用本引擎（改写 URL 解析失败等），跳过当步。
     * **异常原样抛出**，由 [runEgress] 依 [isCancellation] / [isTransportFailure] 判定。
     */
    suspend fun sendGate(attempt: RouteAttempt, retry: Boolean): R?

    /** 默认出口步：发出原请求，返回响应与本步 RTT。**异常原样抛出**，判定同上。 */
    suspend fun proceedDefault(attempt: RouteAttempt): DefaultResult<R>

    /** 网关自己的上游错误页？ */
    fun isGatewayErrorPage(response: R): Boolean

    /** 开始计时戳（各引擎自己的 timeBase）。 */
    fun stamp(): Long

    /** 自 [since] 起的毫秒数。 */
    fun elapsedMs(since: Long): Long

    /** 本步预算是否已超支（可含引擎各自的额外门，见实现注释）。 */
    fun budgetExhausted(startedAt: Long, budgetMs: Long): Boolean

    /** 丢弃一个不再需要的响应（关闭 body）。 */
    fun discard(response: R)

    /** 响应状态码（让位定责用）。 */
    fun statusCode(response: R): Int

    /** [GateStep.Done] 前的收尾（OkHttp 存回 Set-Cookie；Ktor 直接返回）。 */
    fun finish(response: R): R

    /** 调用方主动取消？取消不是"路的问题"，不喂熔断，直接抛。 */
    fun isCancellation(e: Throwable): Boolean

    /** 传输层失败？决定是否按"本步失败"记账并让位。 */
    fun isTransportFailure(e: Throwable): Boolean

    fun logDebug(message: String)

    fun logWarn(message: String)
}

/**
 * 走一遍 [EgressScheduler] 排好的计划：引擎无关的协程流程。
 *
 * @return 成功响应；表空或候选全败时抛 [NoRouteException]。
 */
internal suspend fun <R> runEgress(
    url: String,
    method: String,
    purpose: EgressPurpose,
    engine: EgressEngine<R>,
): R {
    val domain = classifyDomain(url, purpose)
    val intent = EgressRequest(url, method, purpose, currentForceMode())
    val plan = EgressScheduler.plan(intent, currentEgressState(), RouteRegistry.healthOf(domain), currentEpochMillis())
    if (plan.isEmpty) throw NoRouteException(domain, describeEmptyPlan(plan))

    // 网关那次拿到的"出口被封"状态码；有后路时拿它跟后路结果比对定责。
    var blockedCode = 0
    var blockedBudgetMs = -1L
    var gateBlamePending = false

    for ((index, attempt) in plan.attempts.withIndex()) {
        // 只有身后还有非网关后路时，403 才按"出口被封"挂起比对；否则原样交出去
        // （多半是 CF 挑战，NetworkRepo 要靠它触发验证窗）。
        val hasLaterRoute = plan.attempts.drop(index + 1).any { it.route != RouteId.Gate }
        when (attempt.route) {
            RouteId.Gate -> when (val step = runGateStep(engine, attempt, domain, intent.isIdempotent, hasLaterRoute)) {
                is GateStep.Done -> return step.response
                is GateStep.Blocked -> {
                    blockedCode = step.code
                    blockedBudgetMs = attempt.budgetMs
                    gateBlamePending = true
                }
                GateStep.Next -> Unit
            }

            RouteId.Default -> {
                val via = try {
                    engine.proceedDefault(attempt)
                } catch (e: Throwable) {
                    // 调用方主动取消（Coil 滑走、关窗 teardown）不是路的问题：
                    // 不喂熔断器，直接抛。否则取消风暴会把好端端的网关熔断，
                    // 下一个请求诚实失败 —— 2026-10-04 桌面崩溃的完整链条。
                    if (engine.isCancellation(e)) throw e
                    if (!engine.isTransportFailure(e)) throw e
                    // 本步记传输失败；挂起的网关指控按普通失败记（非阻断口径）。
                    reportRouteOutcome(attempt.route, domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
                    if (gateBlamePending) {
                        gateBlamePending = false
                        reportGateOutcome(domain, AttemptOutcome.TransportError, -1L, blockedBudgetMs)
                    }
                    // 非幂等方法禁止让位（换一条路重发有双提交风险）：失败即止，抛出真实异常 ——
                    // 身后还有 Gate 也**不能**谎称"候选出口全部不可用"：那是我们主动不走，
                    // 不是它不可用（NoRoute 的语义是"表空 / 无路可去"）。
                    if (!intent.isIdempotent) throw e
                    // 幂等方法但已是最后一步：折叠后计划至多两步，Default 通常就在末位；
                    // 唯一还能让位的形态是 Default 被粘滞锁定置顶时（[Default, Gate]）。
                    if (index == plan.attempts.lastIndex) {
                        if (plan.attempts.any { it.route == RouteId.Gate }) {
                            throw NoRouteException(domain, allCandidatesFailedReason(plan))
                        }
                        // 纯默认出口计划（关网关 / 第三方 / 强制定向）保持旧语义：
                        // 原异常交外层 Retry 按幂等规则处理。
                        throw e
                    }
                    continue
                }
                reportRouteOutcome(attempt.route, domain, AttemptOutcome.Success, via.rttMs, attempt.budgetMs)
                if (gateBlamePending) {
                    gateBlamePending = false
                    if (gateBlameAfterYield(engine.statusCode(via.response), blockedCode)) {
                        reportGateOutcome(domain, AttemptOutcome.Blocked, -1L, blockedBudgetMs)
                        engine.logWarn("代理路径可用（${engine.statusCode(via.response)}），网关退出接管")
                    } else {
                        engine.logDebug("代理路径同样 $blockedCode，网关无责")
                    }
                }
                return via.response
            }

            // HTTP 层没有隧道执行器（隧道是播放器/CF 验证窗的事）：计划里本不该出现，
            // 出现则跳过，不把请求打断在这里。
            RouteId.GateTunnel -> Unit
        }
    }
    if (gateBlamePending) {
        // 防御分支：网关在末位、身后无后路时上游已直接 Done，正常到不了这里。
        // 真到了说明判定与执行脱节，按阻断记并打日志，避免静默漏记。
        engine.logWarn("网关阻断后无后路可比对，按有责记账")
        reportGateOutcome(domain, AttemptOutcome.Blocked, -1L, blockedBudgetMs)
    }
    // 走到这里只有一条原因：候选全是网关步且都返回 Next（502 两次 / 异常），
    // 计划里没有可让位的非网关后路。受限域上不再撞直连，诚实失败。
    // 非网关步失败的两种收尾已在上面的 catch 里分流（见那段注释）：
    // 网关参与过 ⇒ 这里同款 NoRoute；纯直通 ⇒ 原异常交外层 Retry。
    // NoRoute 不是 Retry 认得的连接类异常（见 `NoRouteException`），不重跑，直达 UI。
    throw NoRouteException(domain, allCandidatesFailedReason(plan))
}

/**
 * 单个网关候选步：502 单次重试 + 单步预算自查 + "连上但被封"的挂起。
 *
 * 跨引擎逐字相同的控制流在此收口；各引擎的差异（502 判定口径、预算门、计时、收尾）
 * 由 [EgressEngine] 原语承载。
 */
private suspend fun <R> runGateStep(
    engine: EgressEngine<R>,
    attempt: RouteAttempt,
    domain: DomainClass,
    idempotent: Boolean,
    hasLaterRoute: Boolean,
): GateStep<R> {
    // 计划里 Gate 步必带改写；不带即本引擎不适用（防御，等同旧实现的 `rewrite ?: Next`）。
    if (attempt.rewrite == null) return GateStep.Next

    val start = engine.stamp()

    val gate = try {
        engine.sendGate(attempt, retry = false)
    } catch (e: Throwable) {
        if (engine.isCancellation(e)) throw e
        if (!engine.isTransportFailure(e)) throw e
        reportGateOutcome(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
        engine.logWarn("网关异常，回退下一出口 (${e.message})")
        return GateStep.Next
    }
    // 引擎报"本步不适用"（改写 URL 解析失败等）：跳过，不记账、不打日志（与旧实现一致）。
    if (gate == null) return GateStep.Next

    if (engine.isGatewayErrorPage(gate)) {
        if (!idempotent) {
            // 非幂等方法既不能重试也不能让位（重发有双提交风险）：把网关这份原样交出去，
            // 但记一次失败 —— 否则"POST 一直撞 502"永远攒不到熔断。
            reportGateOutcome(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
            return GateStep.Done(engine.finish(gate))
        }
        engine.discard(gate)
        // 两道预算门：外层 RetryInterceptor 下传的总预算（仅 OkHttp），以及本步的单步预算。
        // 单步预算在此处执行 —— 超支即停，不再重试网关。
        if (engine.budgetExhausted(start, attempt.budgetMs)) {
            reportGateOutcome(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
            engine.logWarn("重试预算已耗尽，不再重试网关")
            return GateStep.Next
        }
        engine.logWarn("网关上游失败，重试网关一次")
        val retried = try {
            engine.sendGate(attempt, retry = true)
        } catch (e: Throwable) {
            if (engine.isCancellation(e)) throw e
            if (!engine.isTransportFailure(e)) throw e
            reportGateOutcome(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
            engine.logWarn("网关重试异常，回退下一出口 (${e.message})")
            return GateStep.Next
        }
        if (retried == null || engine.isGatewayErrorPage(retried)) {
            retried?.let { engine.discard(it) }
            reportGateOutcome(domain, AttemptOutcome.TransportError, -1L, attempt.budgetMs)
            engine.logWarn("网关重试仍失败，回退下一出口")
            return GateStep.Next
        }
        reportGateOutcome(domain, AttemptOutcome.Success, engine.elapsedMs(start), attempt.budgetMs)
        return GateStep.Done(engine.finish(retried))
    }

    // 连上了、但像是"出口被封"：有后路才挂起比对；没后路原样交出去
    // （多半是 CF 挑战，body 必须到达 NetworkRepo）。
    if (hasLaterRoute && idempotent && isGateBlockedCode(engine.statusCode(gate))) {
        engine.discard(gate)
        return GateStep.Blocked(engine.statusCode(gate))
    }

    reportGateOutcome(domain, AttemptOutcome.Success, engine.elapsedMs(start), attempt.budgetMs)
    return GateStep.Done(engine.finish(gate))
}