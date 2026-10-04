package lovehan1me.data.network.egress

import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.HProxyTypes

/**
 * 出口判定的共享类型（意图 / 状态快照 / 记账口径）。
 *
 * Phase 4 起判定唯一产出是 [EgressScheduler]：旧三选一 `EgressPlanner` 与全局
 * `GateHealthHolder` 已删除（熔断按域，见 [RouteHealth]；复位走 [RouteRegistry.reset]）。
 * 本文件只剩被消费的类型；`GateState` 只剩存活语义（开着/端口），健康另由各域持有。
 */

/** 一次出站的意图。`purpose` 决定预算档位（见 [EgressBudgets]），`force` 是手动逃生舱。 */
data class EgressRequest(
    val url: String,
    val method: String = "GET",
    val purpose: EgressPurpose = EgressPurpose.Api,
    val force: ForceMode = ForceMode.Auto,
)

/** 没用网关的原因。回传到日志与设置页，回答"为什么这次没走网关"。 */
enum class GateSkipReason {
    /** 用户关掉了（含"条件默认开"算出来的 false）。 */
    Disabled,
    /** 该域熔断中，暂由代理/直连接管。 */
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
 * 代理状态。调度器只关心"**有没有可用的代理**"，不关心它是手填的还是系统解析出来的 ——
 * 后者决定了网关是否有"试错余地"。
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

/**
 * 网关的运行状态（只回答"开着/在跑"，不回答"健不健康"）。
 *
 * 健康是按域的（见 [RouteHealth]）：getchu 熔断不再掐断 hanime 的隧道与改写。
 */
data class GateState(
    /** 用户开关的生效值（已含"条件默认开"）。 */
    val enabled: Boolean,
    /** 监听端口；`<= 0` = 未运行。 */
    val port: Int,
) {
    val running: Boolean get() = port > 0
}

/** 出口决策的全部输入。immutable，便于测试与日志复现。 */
data class EgressState(
    val gate: GateState,
    val proxy: ProxyState,
)

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

fun currentEgressState(): EgressState = EgressState(
    gate = GateState(
        enabled = runCatching { SettingsRepository.useEchGate }.getOrDefault(false),
        port = EchGate.port,
    ),
    proxy = currentProxyState(),
)

/**
 * 让位路径拿到结果后，该怎么给网关记账（纯函数，可离线断言）。
 *
 * - 让位路径与网关**同一个码** ⇒ 封的是用户/站点，不是网关的出口 ⇒ **网关无责，不记账**
 *   （否则用户会因为"站点就是不让看"而被熔断掉一条本来能用的路）；
 * - 让位路径拿到了不同的结果 ⇒ 网关的出口被封 ⇒ 记一次**阻断类**失败，
 *   [RouteHealth] 一次即熔断，不再反复打扰。
 *
 * @return true = 网关有责，记一次阻断类失败；false = 网关无责，不记账。
 */
fun gateBlameAfterYield(yieldCode: Int, gateCode: Int): Boolean = yieldCode != gateCode

/**
 * 网关返回这个码表示"连上了，但出口被封"。
 *
 * 这类响应看起来像站点问题（`you have been blocked` / `Just a moment`），而用户手里
 * 可能有一条**能用的代理** —— 所以判为被封时不该再试网关，而该让位。
 */
fun isGateBlockedCode(code: Int): Boolean = code == 403

/** 判定"经代理路径重试后拿到什么算网关无责"时用的口令：只对幂等方法做重试。 */
val EgressRequest.isIdempotent: Boolean get() = method == "GET" || method == "HEAD"
