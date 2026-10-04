package lovehan1me.data.network.egress

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.EchGatePolicy
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * 连接调度器：出口判定的唯一产出处。
 *
 * 本对象只读快照、不碰任何全局：健康由调用方传入
 * （执行器传 `RouteRegistry.healthOf`，测试传手造状态），
 * 因此"受限域无直连""强制覆盖""预算推导"全都能离线断言。
 *
 * ## 排表规则（Auto 模式）
 * 1. 网关可用（开着、有端口、改写成立、该域未熔断）→ 首位（第三方域永不进网关）；
 * 2. 手填代理 → UserProxy，系统代理可解析 → SystemProxy；
 * 3. 直连只在两种情况下排：非受限域；或用户**关掉了网关**（回到旧语义 ——
 *    关开关等于声明"我的直连可用"，加速项缺席不该连累正常请求；海外用户活在这里）；
 *    网关开着但没跑起来时，受限域不排 Direct（决策第 5 条：已知撞 RST，不浪费时间）；
 * 4. 粘滞优选（未熔断）置顶，其余按成功率降序、EWMA 升序（ties 走默认表序，`sortedWith`
 *    稳定排序保证）；
 * 5. 表空 = 无可用出口：执行器抛 [NoRouteException]，**不转圈**。
 *
 * ## 关于熔断输入
 * 熔断唯一来源是按域健康（见 [RouteHealth]）；`GateState` 只剩存活语义，
 * 旧全局熔断器已删除。
 */

/** 手动强制选路的唯一读取口（设置未就绪回 Auto；调用方不得各写一份 runCatching）。 */
fun currentForceMode(): ForceMode =
    runCatching { SettingsRepository.egressForceMode }.getOrDefault(ForceMode.Auto)
object EgressScheduler {

    fun plan(
        request: EgressRequest,
        state: EgressState,
        health: RouteHealth = RouteRegistry.healthOf(classifyDomain(request.url, request.purpose)),
        nowMs: Long = currentEpochMillis(),
    ): ScheduledPlan {
        val domain = classifyDomain(request.url, request.purpose)
        if (request.force != ForceMode.Auto) {
            return forcedPlan(request, state, domain)
        }

        val rewrite = EchGatePolicy.rewrite(request.url, state.gate.port)
        val gateUsable = state.gate.enabled && state.gate.running &&
            rewrite != null && !health.isOpen(RouteId.Gate, nowMs)

        val table = buildList {
            // 第三方永不进网关（自有 client 本就不装拦截器，排进去也没人执行）。
            if (gateUsable && domain != DomainClass.ThirdParty) add(RouteId.Gate)
            if (state.proxy is ProxyState.Explicit) add(RouteId.UserProxy)
            if (state.proxy is ProxyState.SystemResolved) add(RouteId.SystemProxy)
            // 直连的两种去处：非受限域照常用；受限域只在用户关掉网关时保留
            // （旧语义，加速项缺席=直连）。网关开着但没跑起来 ⇒ 不排（诚实失败）。
            if (!domain.isRestricted || !state.gate.enabled) add(RouteId.Direct)
        }.filter { !health.isOpen(it, nowMs) }

        val locked = health.preferred(nowMs)?.takeIf { it in table }
        val rest = (if (locked == null) table else table - locked).sortedWith(
            compareByDescending<RouteId> { health.single(it).successRate() }
                .thenBy { health.single(it).ewmaRttMs.takeIf { rtt -> rtt >= 0 } ?: Long.MAX_VALUE },
        )
        val ordered = listOfNotNull(locked) + rest
        return ScheduledPlan(
            attempts = ordered.map { route ->
                RouteAttempt(
                    route = route,
                    budgetMs = EgressBudgets.budgetFor(request.purpose, route),
                    rewrite = rewrite.takeIf { route == RouteId.Gate },
                )
            },
            skipped = gateSkipped(domain, ordered, state, request.url, rewrite),
            domain = domain,
        )
    }

    /**
     * 强制模式短路一切（含熔断）：ForceGate 只看"进程在不在"，ForceDirect 不看域限制，
     * ForceProxy 在无代理时表空（诚实失败）。条件不满足同样表空，不降级猜测。
     */
    private fun forcedPlan(request: EgressRequest, state: EgressState, domain: DomainClass): ScheduledPlan {
        val rewrite = EchGatePolicy.rewrite(request.url, state.gate.port)
        val routes = when (request.force) {
            ForceMode.ForceGate ->
                if (state.gate.enabled && state.gate.running && rewrite != null) listOf(RouteId.Gate)
                else emptyList()
            ForceMode.ForceDirect -> listOf(RouteId.Direct)
            ForceMode.ForceProxy -> when (state.proxy) {
                is ProxyState.Explicit -> listOf(RouteId.UserProxy)
                is ProxyState.SystemResolved -> listOf(RouteId.SystemProxy)
                ProxyState.None -> emptyList()
            }
            ForceMode.Auto -> error("unreachable")
        }
        return ScheduledPlan(
            attempts = routes.map { route ->
                RouteAttempt(
                    route = route,
                    budgetMs = EgressBudgets.budgetFor(request.purpose, route),
                    rewrite = rewrite.takeIf { route == RouteId.Gate },
                )
            },
            skipped = gateSkipped(domain, routes, state, request.url, rewrite),
            domain = domain,
        )
    }

    /**
     * 网关没进表的可读原因（与老 planner 同口径，日志与设置页沿用）。
     *
     * 第三方域永不进网关是策略不是故障，返回 null（调用方以 `domain` 区分）；
     * 其余情况网关不在表里只可能是熔断（可用性已在排表时判定）。
     */
    private fun gateSkipped(
        domain: DomainClass,
        ordered: List<RouteId>,
        state: EgressState,
        url: String,
        rewrite: EchGatePolicy.Rewrite?,
    ): GateSkipReason? {
        if (RouteId.Gate in ordered) return null
        if (domain == DomainClass.ThirdParty) return null
        return gateSkipReason(state, url, rewrite)
    }

    /** 网关没进表的可读原因（与老 planner 同口径，日志与设置页沿用）。 */
    private fun gateSkipReason(state: EgressState, url: String, rewrite: EchGatePolicy.Rewrite?): GateSkipReason {
        if (!state.gate.enabled) return GateSkipReason.Disabled
        if (!state.gate.running) return GateSkipReason.NotRunning
        if (rewrite == null) return skipReasonFor(url)
        return GateSkipReason.CircuitOpen
    }

    /**
     * 把 [EchGatePolicy] 的"放行"翻译成可读的原因。
     *
     * 只在该 policy 已判定放行后调用，所以正常不会落到最后一个分支；
     * 真落到了说明两边判定不一致，返回 [GateSkipReason.InvalidUrl] 会让日志显得蹊跷 ——
     * 那正是我们想要的信号。
     */
    private fun skipReasonFor(url: String): GateSkipReason {
        // 空串要先判：Ktor 的 Url("") 能解析出一个"默认 http 主机"的地址，
        // 于是会被解释成 NotHttps —— 那是个误导性的原因，空 URL 就是无效 URL。
        if (url.isBlank()) return GateSkipReason.InvalidUrl
        val parsed = runCatching { Url(url) }.getOrNull() ?: return GateSkipReason.InvalidUrl
        if (parsed.protocol != URLProtocol.HTTPS) return GateSkipReason.NotHttps
        if (EchGatePolicy.isBypassHost(parsed.host)) return GateSkipReason.LoopbackOrLiteral
        return GateSkipReason.InvalidUrl
    }

    /**
     * CONNECT 隧道地址（验证窗 / 播放器兜底用）；网关非优选或被熔断时返回 null。
     *
     * 替代 `GateState.connectTunnelUrl`（随旧 planner 删除）：旧口径只看
     * `enabled && running && !circuitOpen`（全局熔断），新口径看该域健康 ——
     * getchu 熔断不再掐断 hanime 验证窗的隧道。强制直连/强制代理时返回 null
     * （用户已声明出口，隧道不得劫持）。
     */
    fun tunnelUrl(
        domain: DomainClass,
        state: EgressState = currentEgressState(),
        health: RouteHealth = RouteRegistry.healthOf(domain),
        force: ForceMode = currentForceMode(),
        nowMs: Long = currentEpochMillis(),
    ): String? {
        if (force == ForceMode.ForceDirect || force == ForceMode.ForceProxy) return null
        if (!state.gate.enabled || !state.gate.running) return null
        if (health.isOpen(RouteId.Gate, nowMs)) return null
        return "http://${EchGatePolicy.GATE_HOST}:${state.gate.port}"
    }
}
