package lovehan1me.data.network.egress

import lovehan1me.data.network.EchGatePolicy

/**
 * 一次出站的意图与当前运行时状态，以及 planner 算出来的计划。
 *
 * 这三个类型是"出口判定只能有一处产出"这条不变式的载体：执行器（OkHttp 拦截器、mpv、
 * Exo、CDP 浏览器）只消费 [EgressPlan]，自己不做任何判断。判定本身在 [EgressPlanner]，
 * 是纯函数，因此可离线断言。
 */

/** 一次出站的意图。目前只用到 url 与 method；`purpose` 留给"按用途给不同预算"的后续增量。 */
data class EgressRequest(
    val url: String,
    val method: String = "GET",
)

/**
 * 代理状态。planner 只关心"**有没有可用的代理**"，不关心它是手填的还是系统解析出来的 ——
 * 后者决定了网关是否有"试错余地"（见 [EgressPlan.gateOnProbation]）。
 */
sealed interface ProxyState {
    /** 没有可用代理。 */
    data object None : ProxyState

    /** 手填的 HTTP / SOCKS 代理。 */
    data class Explicit(val host: String, val port: Int, val socks: Boolean) : ProxyState

    /** 系统代理可解析（出口由系统决定，应用侧拿不到具体地址）。 */
    data object SystemResolved : ProxyState
}

val ProxyState.isUsable: Boolean get() = this !is ProxyState.None

/** 网关的运行与健康状态。 */
data class GateState(
    /** 用户开关的生效值（已含"条件默认开"）。 */
    val enabled: Boolean,
    /** 监听端口；`<= 0` = 未运行。 */
    val port: Int,
    /** 熔断中（见 [GateHealth]）。 */
    val circuitOpen: Boolean,
) {
    val running: Boolean get() = port > 0
}

/** 出口决策的全部输入。immutable，便于测试与日志复现。 */
data class EgressState(
    val gate: GateState,
    val proxy: ProxyState,
)

/**
 * 网关此刻是否是**可用候选**：用户开着、进程在跑、且未熔断。
 *
 * 这是"网关能不能被考虑"的**唯一**定义。反向改写通道（[EgressPlan.gate]）还要再满足
 * 每条 URL 自己的条件（https、非回环），所以 `usesGate ⇒ isCandidate`，
 * 反过来不成立。隧道通道（[connectTunnelUrl]）与验证浏览器（`--proxy-server`）
 * 用的就是它本身。
 */
val GateState.isCandidate: Boolean get() = enabled && running && !circuitOpen

/**
 * 网关的 CONNECT 隧道地址（`http://127.0.0.1:<port>`）；网关不是可用候选时返回 null。
 *
 * 与主力通道（`X-Ech-Target` 反向代理）共用**同一个端口**，是两条不同的路：
 * 主力通道由网关代为 TLS（能用 ECH / CNAME 真名），CONNECT 只做 DoH 解析 + 裸 TCP。
 * 两者必须由同一份判定放行 —— 此前隧道只判 `port > 0`，于是出现"改写道被熔断摘掉了、
 * 隧道却还在把流量往网关里送"这种半开状态。
 */
fun GateState.connectTunnelUrl(): String? =
    if (isCandidate) "http://${EchGatePolicy.GATE_HOST}:$port" else null

/** 没用网关的原因。回传到日志与设置页，回答"为什么这次没走网关"。 */
enum class GateSkipReason {
    /** 用户关掉了（含"条件默认开"算出来的 false）。 */
    Disabled,
    /** 熔断中，暂由代理/直连接管。 */
    CircuitOpen,
    /** 进程没在跑。 */
    NotRunning,
    /** 非 https —— 网关按域名做 SNI 策略，http 没有 SNI 可谈。 */
    NotHttps,
    /** 本机地址或 IP 字面量（回环走网关等于绕出去再绕回来；IP 没有域名可谈）。 */
    LoopbackOrLiteral,
    /** URL 解析不了。 */
    InvalidUrl,
}

/**
 * 出口计划。
 *
 * @property gate 网关改写结果（[EchGatePolicy.Rewrite] 原样沿用）；null = 本次不经网关。
 * @property gateSkipped 没用网关的原因；走网关时为 null。
 * @property gateOnProbation 网关处于**试用期**：存在可用代理时，它仍先试，
 *   但只有一次机会 —— 一旦失败就让位给代理，并把这次失败计入熔断。
 *   没有可用代理时不存在"让位"这回事，故为 false。
 */
data class EgressPlan(
    val gate: EchGatePolicy.Rewrite?,
    val gateSkipped: GateSkipReason?,
    val gateOnProbation: Boolean,
) {
    val usesGate: Boolean get() = gate != null
}

/** 判定"经代理路径重试后拿到什么算网关无责"时用的口令：只对幂等方法做重试。 */
val EgressRequest.isIdempotent: Boolean get() = method == "GET" || method == "HEAD"
