package lovehan1me.data.network.egress

import lovehan1me.data.network.EchGatePolicy

/**
 * 调度器的共享模型（路由 / 用途 / 强制模式 / 记账口径 / 预算）。
 *
 * 全部纯数据，可进 commonTest；判定在 [EgressScheduler]，记账在 [EgressReporter]。
 */

/**
 * 一条出站路由。
 *
 * ## 为什么没有 UserProxy / SystemProxy / Direct（阶段 0.1 折叠）
 * 那三者在两个执行器里**物理等价**：JVM 侧都只是 `chain.proceed(request)`，出口由
 * client 级 `HanimeProxySelector` 在连接时按设置决定；Darwin 侧都是 `proceed(request)`，
 * 出口由 NSURLSession 决定（手填代理在 Darwin 引擎上根本不生效）。把它们排成三条
 * 只会把**同一个物理出口**的成败记到三个假名字上（审阅 F4）——"设置是代理时，Direct 步
 * 实际走代理，成功却记到 Direct 头上"。
 *
 * 折叠成 [Default]（"客户端当前出口"，代理/直连由设置决定，本层不区分）后，记账字段
 * 说的就是真值；诊断里也诚实地叫"当前网络出口"，不谎称"直连"。
 * 播放层仍需细分（SOCKS 解析不出时退隧道），那份区分改由 `EgressState.proxy` 推导，
 * 不再依赖路由名（见 `PlayerWiring.desktop.kt` 的 `proxyUrlFor`）。
 */
enum class RouteId {
    /** 本地 ECH 网关改写道（唯一真能按请求切换的出口）。 */
    Gate,

    /** 客户端当前出口：代理还是直连由设置决定，HTTP 执行层不区分。 */
    Default,

    /** 网关 CONNECT 隧道（播放器 / CF 验证窗用，非 HTTP 执行器的路由）。 */
    GateTunnel,
}

/** 出站用途：决定预算档位。执行器由建 client 处打标透传，见 Phase 1 方案。 */
enum class EgressPurpose {
    Api,
    Image,
    Video,
    Download,
    Probe,
}

/**
 * 手动强制选路（决策第 7 条逃生舱，Phase 4 由设置页写入）。
 *
 * [Auto] 之外全部短路健康度：ForceGate 无视熔断，ForceDirect 在受限域上预期撞 RST
 * （设置页弹警告），ForceProxy 在无代理时直接诚实失败。
 */
enum class ForceMode {
    Auto,
    ForceGate,
    ForceDirect,
    ForceProxy,
    ;

    companion object {
        /** 未知名一律回 Auto：枚举增删不炸老数据与脏备份。 */
        fun fromName(name: String): ForceMode =
            entries.firstOrNull { it.name == name } ?: Auto
    }
}

/** 单步结局。Blocked = 网关出口被封（403 类），一次即熔该域；其余失败攒够阈值才熔。 */
enum class AttemptOutcome {
    Success,
    TransportError,
    GatewayErrorPage,
    Blocked,
}

/**
 * 调度排好的一步：路由 + 本步预算 + 网关改写（仅 Gate 步携带）。
 *
 * [budgetMs] 是该步网络 IO 的总时间盒（Phase 2/3 由执行器强制执行）；
 * [EgressBudgets.UNLIMITED] = 流式不掐（播放/下载的 read）。
 */
data class RouteAttempt(
    val route: RouteId,
    val budgetMs: Long,
    val rewrite: EchGatePolicy.Rewrite? = null,
)

/** 一次调度的产出。attempts 为空 = 无可用出口，执行器抛 [NoRouteException]，不转圈。 */
data class ScheduledPlan(
    val attempts: List<RouteAttempt>,
    val skipped: GateSkipReason?,
    val domain: DomainClass,
) {
    val isEmpty: Boolean get() = attempts.isEmpty()
}

/**
 * 按用途的预算表（初值，Phase 2 实测调；执行器侧落地前只做断言对象）。
 *
 * ⚠️ **只有 purpose 一维**。原签名带 `route` 参数，但它从未被读过——
 * 预算表在文档里被写成"purpose × route 二维"，实现却是一维，多出来的那个参数
 * 会让读代码的人以为"不同路由有不同预算"。直到真的有路由维度的预算，
 * 不要把它加回来（见 `docs/plan/后期攻坚-网络与全端-审阅与规划.md` 的 F6）。
 */
object EgressBudgets {
    /** 流式不掐：播放/下载的 read 无上界，靠 stall 检测而非超时。 */
    const val UNLIMITED: Long = Long.MAX_VALUE

    fun budgetFor(purpose: EgressPurpose): Long = when (purpose) {
        // 浏览链沿用 hClient 的 call 级 60s。
        EgressPurpose.Api -> 60_000L
        // 图片沿用 connect 5s/15s 分档的上界收敛，补 read/call：30s 还没完就是死了。
        EgressPurpose.Image -> 30_000L
        // 流式：连接阶段由各引擎自己的超时管，调度预算不掐 read。
        EgressPurpose.Video, EgressPurpose.Download -> UNLIMITED
        // 探测沿用 CdnIpProbe 单 IP 量级，整步 10s 上界。
        EgressPurpose.Probe -> 10_000L
    }
}
