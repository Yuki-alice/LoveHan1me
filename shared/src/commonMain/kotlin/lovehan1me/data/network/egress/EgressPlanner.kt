package lovehan1me.data.network.egress

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.HProxyTypes

/**
 * 出口判定的**唯一产出处**。纯函数：给定请求意图与一份不可变的状态快照，算出计划。
 *
 * ## 为什么要有它
 * 此前"这次请求该从哪儿出去"散在 11 个网络栈里各自判断（9 个 OkHttp 配置 +
 * mpv / Exo / CDP 浏览器各一套），彼此不知道对方在做什么。判定分叉正是
 * "页面能开、视频打不开""验证过了还是不行"这类问题的共同根因。
 *
 * ## 网关为什么会被"跳过"
 * 它从"必经的一层"降为"候选出口之一"。以下任意一条成立就不接管，且**原因可查**：
 * 用户关掉、熔断中、进程没跑、非 https、回环/IP 字面量、URL 解析不了。
 *
 * ## 产出的是**有序候选**，不是一个决定
 * [EgressPlan.attempts] 按优先级排好（网关 → 让位给代理 → 原样放行），执行器按序尝试。
 * 于是"失败后下一步走哪"也归这里管 —— 此前这段回退顺序写死在 `EchGateInterceptor`
 * 的三段 if 里，Ktor 侧再抄一遍，图片插件没抄，于是三处行为各不相同。
 */
object EgressPlanner {

    fun plan(request: EgressRequest, state: EgressState): EgressPlan {
        // 有可用代理 ⇒ 网关进入试用期：它仍先试（保住"免梯直连"的体验），
        // 但失败即让位（保住"配了代理就必须能用"的底线）。没有代理时不存在让位，
        // 因为后面没有更好的路可退。
        val probation = state.proxy.isUsable

        if (!state.gate.enabled) return skipped(GateSkipReason.Disabled, probation)
        if (state.gate.circuitOpen) return skipped(GateSkipReason.CircuitOpen, probation)
        if (!state.gate.running) return skipped(GateSkipReason.NotRunning, probation)

        // 改写规则本体仍归 EchGatePolicy —— 它已覆盖非 https / 回环 / IP 字面量 / 非法 URL，
        // 且三端共用同一份。这里不复制它的判定，只包一层"该不该用网关"。
        val rewrite = EchGatePolicy.rewrite(request.url, state.gate.port)
            ?: return skipped(skipReasonFor(request.url), probation)

        return EgressPlan(
            attempts = buildList {
                add(EgressAttempt.Gate(rewrite, probation))
                // 有可用代理 ⇒ 网关只有一次机会：失败即让位给代理，保住
                // "配了代理就必须能用"这条底线。没有代理时不排让位 —— 后面没有更好的路。
                if (probation) add(EgressAttempt.Yield)
            },
            skipped = null,
            proxyUsable = probation,
        )
    }

    /**
     * 执行器用的便捷入口：从一条 URL 直接拿改写结果，判定仍全部走 [plan]。
     *
     * 给那些只关心"要不要改写、改成什么"的调用方（Coil 的 Ktor 插件、播放器的
     * `rewriteForGate`）——它们不需要计划里的其余字段，但**必须**经过同一份判定，
     * 否则又会退化成一堆"各自看端口"的地方。
     */
    fun gateRewriteFor(url: String, state: EgressState = currentEgressState()): EchGatePolicy.Rewrite? =
        plan(EgressRequest(url), state).gate

    /**
     * 播放器的出口：**用户代理优先**（那才是用户已经配好的那条路），
     * 没有用户代理时才考虑网关的 CONNECT 隧道；两者都没有就是直连。
     *
     * 隧道被 [GateState.isCandidate] 门控，于是熔断/关闭期间它也会一起消失 ——
     * 否则"改写道已经退让、隧道却还在劫持媒体流量"会把故障原样留着（媒体没有回退，
     * 撞不通就是直接失败）。
     *
     * @param userProxyUrl 平台侧解析出来的用户/系统代理（`null` = 没有）。
     */
    fun mediaProxyUrl(userProxyUrl: String?, state: EgressState = currentEgressState()): String? =
        userProxyUrl ?: state.gate.connectTunnelUrl()

    /** 网关不接管：候选里只剩原样放行，原因回传给日志与设置页。 */
    private fun skipped(reason: GateSkipReason, proxyUsable: Boolean) =
        EgressPlan(
            attempts = listOf(EgressAttempt.Passthrough),
            skipped = reason,
            proxyUsable = proxyUsable,
        )

    /**
     * 把 [EchGatePolicy] 的"放行"翻译成可读的原因。
     *
     * 只在该 policy 已判定放行后调用，所以正常不会落到最后一个分支；
     * 真落到了说明两边判定不一致，返回 [GateSkipReason.InvalidUrl] 会让日志显得蹊跷 ——
     * 那正是我们想要的信号。
     */
    internal fun skipReasonFor(url: String): GateSkipReason {
        // 空串要先判：Ktor 的 Url("") 能解析出一个"默认 http 主机"的地址，
        // 于是会被解释成 NotHttps —— 那是个误导性的原因，空 URL 就是无效 URL。
        if (url.isBlank()) return GateSkipReason.InvalidUrl
        val parsed = runCatching { Url(url) }.getOrNull() ?: return GateSkipReason.InvalidUrl
        if (parsed.protocol != URLProtocol.HTTPS) return GateSkipReason.NotHttps
        if (EchGatePolicy.isBypassHost(parsed.host)) return GateSkipReason.LoopbackOrLiteral
        return GateSkipReason.InvalidUrl
    }
}

/** 当前平台能否解析出系统代理。iOS 侧应用拿不到，恒 false。 */
expect fun platformSystemProxyUsable(): Boolean

/** 从设置与运行时状态构造一份快照。这是本包唯一的"不纯"处，刻意做得很薄。 */
fun currentProxyState(): ProxyState {
    val type = runCatching { SettingsRepository.proxyType }.getOrNull() ?: return ProxyState.None
    val ip = runCatching { SettingsRepository.proxyIp }.getOrNull().orEmpty()
    val port = runCatching { SettingsRepository.proxyPort }.getOrNull() ?: -1
    val configured = ip.isNotBlank() && port in 1..65535
    return when (type) {
        HProxyTypes.TYPE_HTTP -> if (configured) ProxyState.Explicit(ip, port, socks = false) else ProxyState.None
        HProxyTypes.TYPE_SOCKS -> if (configured) ProxyState.Explicit(ip, port, socks = true) else ProxyState.None
        // System 档无法在公共层判定，交给平台；解析不出来就等于"没有可用代理"。
        HProxyTypes.TYPE_SYSTEM -> if (platformSystemProxyUsable()) ProxyState.SystemResolved else ProxyState.None
        else -> ProxyState.None
    }
}

fun currentEgressState(nowMs: Long = currentEpochMillis()): EgressState = EgressState(
    gate = GateState(
        enabled = runCatching { SettingsRepository.useEchGate }.getOrDefault(false),
        port = EchGate.port,
        circuitOpen = GateHealthHolder.current.isOpen(nowMs),
    ),
    proxy = currentProxyState(),
)
