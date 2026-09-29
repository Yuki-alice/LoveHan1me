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
 * 一个候选出口。**planner 给出的顺序就是尝试顺序**，执行器不再自己编排回退。
 *
 * 这是"新增出口不用改执行器"的载体：加一种出站方式 = 加一个子类 + 在 planner 里排进
 * [EgressPlan.attempts]，五个执行器（OkHttp 链 / Ktor 插件 / 图片插件 / 播放器 / 下载）
 * 一行都不用动。此前回退顺序写死在 `EchGateInterceptor` 的三段 if 里，Ktor 侧再抄一遍，
 * 图片插件干脆没抄 —— "页面能开、图全没了"就是这么来的。
 */
sealed interface EgressAttempt {

    /**
     * 经本地 ECH 网关（反向代理改写道）。
     *
     * @param onProbation 试用期：后面还排着 [Yield]，故网关只有一次机会，失败即让位。
     *   没有让位对象时为 false —— 那时后面没有更好的路，让位只是白跑一趟。
     */
    data class Gate(val rewrite: EchGatePolicy.Rewrite, val onProbation: Boolean) : EgressAttempt

    /**
     * 让位：走传输层自己的出口（用户/系统代理或直连），**不做任何改写**。
     *
     * 与 [Passthrough] 走的是同一条路，区别只在记账：它排在网关之后，成功即构成
     * 对网关的指控（见 [gateBlameAfterYield]）。
     */
    data object Yield : EgressAttempt

    /**
     * 原样放行：网关从未接管（用户关掉 / 熔断中 / 进程没跑 / 这条 URL 不该进网关）。
     * 执行器据此直接把请求交给传输层，并回传 [EgressPlan.skipped] 作为日志原因。
     */
    data object Passthrough : EgressAttempt
}

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
 * 出口计划。**执行器只消费 [attempts]，不做任何判断。**
 *
 * 三个派生属性（[gate] / [gateOnProbation] / [gateSkipped]）是给旧调用点与日志用的
 * 便捷读法，全部由 [attempts] 推导 —— 于是"走网关"这件事仍然只有一个来源，
 * 不存在第二份可以与之分叉的字段。
 *
 * @property attempts 有序候选出口，planner 排好序，执行器按序尝试。
 * @property skipped 网关没被排进候选的原因；走网关时为 null。
 * @property proxyUsable 后面有没有可用代理。与网关是否被跳过无关：它是"用户手里
 *   有没有一条能用的路"这个事实，熔断时同样成立。
 */
data class EgressPlan(
    val attempts: List<EgressAttempt>,
    val skipped: GateSkipReason?,
    val proxyUsable: Boolean,
) {
    val usesGate: Boolean get() = attempts.firstOrNull() is EgressAttempt.Gate

    /** 首个候选是网关时的改写结果；不经过网关时为 null。 */
    val gate: EchGatePolicy.Rewrite? get() = (attempts.firstOrNull() as? EgressAttempt.Gate)?.rewrite

    /**
     * 网关处于**试用期**：存在可用代理时它仍先试，但只有一次机会 —— 失败即让位。
     *
     * 网关不在候选里时恒为 false：那时没有 Gate 可试用，返回 true 是撒谎。
     * "后面有没有退路"这个事实看 [proxyUsable]。
     */
    val gateOnProbation: Boolean get() = (attempts.firstOrNull() as? EgressAttempt.Gate)?.onProbation == true

    /** [skipped] 的原名（日志与设置页沿用旧叫法）。 */
    val gateSkipped: GateSkipReason? get() = skipped
}

/**
 * 让位路径拿到结果后，该怎么给网关记账（纯函数，可离线断言）。
 *
 * - 让位路径与网关**同一个码** ⇒ 封的是用户/站点，不是网关的出口 ⇒ **网关无责，不记账**
 *   （否则用户会因为"站点就是不让看"而被熔断掉一条本来能用的路）；
 * - 让位路径拿到了不同的结果 ⇒ 网关的出口被封 ⇒ 记一次**阻断类**失败，
 *   [lovehan1me.data.network.egress.GateHealth] 一次即熔断，不再反复打扰。
 *
 * @return true = 网关有责，记一次阻断类失败；false = 网关无责，不记账。
 */
fun gateBlameAfterYield(yieldCode: Int, gateCode: Int): Boolean = yieldCode != gateCode

/**
 * 网关返回这个码表示"连上了，但出口被封"。
 *
 * 这类响应看起来像站点问题（`you have been blocked` / `Just a moment`），而用户手里
 * 可能有一条**能用的代理** —— 所以判为被封时不该再试网关，而该让位（[EgressAttempt.Yield]）。
 * 此前这一段缺失，正是"原本系统代理可以访问、却访问不了"的根因。
 */
fun isGateBlockedCode(code: Int): Boolean = code == 403

/** 判定"经代理路径重试后拿到什么算网关无责"时用的口令：只对幂等方法做重试。 */
val EgressRequest.isIdempotent: Boolean get() = method == "GET" || method == "HEAD"
