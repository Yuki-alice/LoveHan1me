package lovehan1me.data.network.egress

/**
 * 两个执行器（OkHttp / Ktor）**逐字重复**的记账与文案 —— F17 收敛的第一步。
 *
 * ## 为什么先做这一步
 * 这三段此前在 `EchGateInterceptor`（jvmMain）与 `EchGateClientPlugin`（commonMain）
 * 里各有一份**完全一样**的拷贝：措辞改一处忘另一处，用户会在同一个设置页上
 * 看到两套说法（Android 一套、iOS 一套）。移到这里之后定义只有一份，
 * 而且能进 `commonTest` —— 重复的文案拦不住人，共用 + 测试才拦得住。
 *
 * ## 范围（刻意保守）
 * 本文件只收**可证同构**的部分。循环顺序、让位定责、502 重试、末步分流
 * 涉及两个引擎的发送原语差异（同步 `chain.proceed` vs 挂起 `proceed`、
 * 请求原地变异 vs 重建），要到 `EgressRunner` 才收 —— 那一步必须两个引擎一起接、一起验收。
 */

/**
 * 单步上报（网关步）。
 *
 * 新注册表（按域）是唯一的记账处（旧全局熔断器已随旧 planner 删除）。
 * 网关出口被封只熔该域，getchu 挂不再连累 hanime。
 */
internal fun reportGateOutcome(
    domain: DomainClass,
    outcome: AttemptOutcome,
    rttMs: Long,
    budgetMs: Long = -1L,
) = EgressReporter.report(domain, RouteId.Gate, outcome, rttMs, budgetMs = budgetMs)

/** 单步上报（任意路由）。 */
internal fun reportRouteOutcome(
    route: RouteId,
    domain: DomainClass,
    outcome: AttemptOutcome,
    rttMs: Long,
    budgetMs: Long = -1L,
) = EgressReporter.report(domain, route, outcome, rttMs, budgetMs = budgetMs)

/**
 * 表空的原因人话（抛给上层前组装，见 [NoRouteException]）。
 *
 * 与调度器同口径：`skipped` 只在"该域受限、网关又没进表"时才有值；
 * `null` 在这里读作"网关不可用"，因为能走到本函数的调用点必然已经排过表了。
 */
internal fun describeEmptyPlan(plan: ScheduledPlan): String {
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

/**
 * 候选出口全部试过仍失败时的原因文案。
 *
 * 措辞固定为"候选出口全部不可用（域）"：它与 [describeEmptyPlan] 的区别是
 * **有路可去、但都试败了**，而不是"表空"。两者不能混为一谈 —— 前者可以重试，
 * 后者要用户去改设置。
 */
internal fun allCandidatesFailedReason(plan: ScheduledPlan): String =
    "候选出口全部不可用（${plan.domain}）"
