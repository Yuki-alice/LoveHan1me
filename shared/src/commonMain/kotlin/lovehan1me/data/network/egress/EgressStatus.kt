package lovehan1me.data.network.egress

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGateStatus

/**
 * 设置页"连接三态"的**唯一**计算处（纯函数，commonMain）。
 *
 * ## 为什么要从 Route 里抽出来
 * 这段判定原先内联在 jvmMain `NetworkSettingsRoute` 的 `remember {}` 里，
 * 于是两件事同时成立且都很糟：
 * 1. **iOS 用不了** —— 它不在 commonMain，iOS 网络页只能退化成占位符，
 *    用户看不到"网关是不是被熔断了"，而调度器其实一直在跑；
 * 2. **测不了** —— 内联在 `@Composable` 里的一坨 `when` 没有任何单测，
 *    "熔断域怎么算""无可用出口怎么判"全靠肉眼。
 *
 * 抽出来之后：三端共用同一份判定，判定本身进 commonTest。
 *
 * ## 本地化
 * 本文件**只产出结构，不产文案**。域名的展示名、条目的措辞由各端用自己的
 * `strings.xml` 拼 —— 纯函数里塞 `stringResource` 是它当初逃不出 `@Composable` 的原因。
 */

/** 三态跟踪的域：第三方域永不进网关，熔断对它没有意义。 */
val TRACKED_EGRESS_DOMAINS: List<DomainClass> =
    DomainClass.entries.filter { it != DomainClass.ThirdParty }

/**
 * 一次快照：网关运行态 + 三态成立的域 + 是否有出口。
 *
 * [isClean] 为 true 时设置页**不该**贴任何附加条（正常态不打扰用户）。
 */
data class EgressStatusSnapshot(
    val gate: EchGateStatus,
    /** 熔断中的域（该域网关已被摘掉，暂由备用出口接管）。 */
    val meltedDomains: List<DomainClass>,
    /** 有过失败但还没熔断的域。 */
    val unstableDomains: List<DomainClass>,
    /** [unstableDomains] 里最大的连续失败次数（0 = 没有不稳定域）。 */
    val maxRecentFailures: Int,
    /** 网关开着、没跑起来、也没有可用代理 ⇒ 这个域上无出口可去。 */
    val noRoute: Boolean,
) {
    val isClean: Boolean
        get() = meltedDomains.isEmpty() && unstableDomains.isEmpty() && !noRoute
}

/**
 * 当前快照。全部输入可注入，可离线断言（测试直接传手造的 healthOf）。
 *
 * @param nowMs 熔断冷却的判定基准（[RouteHealth.isOpen] 需要）。
 * @param healthOf 域健康的读取口，默认走进程级 [RouteRegistry]。
 */
fun egressStatusSnapshot(
    nowMs: Long,
    useEchGate: Boolean = runCatching { SettingsRepository.useEchGate }.getOrDefault(false),
    proxyUsable: Boolean = runCatching { currentProxyState() }.getOrDefault(ProxyState.None).isUsable,
    healthOf: (DomainClass) -> RouteHealth = { RouteRegistry.healthOf(it) },
    tracked: List<DomainClass> = TRACKED_EGRESS_DOMAINS,
): EgressStatusSnapshot {
    val melted = tracked.filter { healthOf(it).isOpen(RouteId.Gate, nowMs) }
    val unstable = tracked.filter { domain ->
        domain !in melted && healthOf(domain).routes.values.any { it.consecutiveFailures > 0 }
    }
    val maxFailures = unstable.maxOfOrNull { domain ->
        healthOf(domain).routes.values.maxOf { it.consecutiveFailures }
    } ?: 0
    return EgressStatusSnapshot(
        gate = EchGate.status,
        meltedDomains = melted,
        unstableDomains = unstable,
        maxRecentFailures = maxFailures,
        // 与调度器同口径：`noRoute` 只在"用户想要网关、网关却不在、且没有代理兜底"时成立。
        // 用户主动关掉网关是声明"我的直连可用"，那时不该贴"无可用出口"。
        noRoute = useEchGate && EchGate.port <= 0 && !proxyUsable,
    )
}

/**
 * 三态的本地化模板（各端从自己的 `strings.xml` 取好后传进来）。
 *
 * 占位符沿用 `strings.xml` 的口径：`%1$d` / `%1$s` / `%2$d`。
 */
data class EgressStatusTexts(
    /** `Gateway running (127.0.0.1:%1$d)` */
    val running: String,
    val starting: String,
    val stopped: String,
    /** `Gateway failed: %1$s` */
    val failed: String,
    /** `Circuit open (%1$s), on fallback` */
    val melted: String,
    /** `Unstable (%1$s, %2$d recent failures)` */
    val unstable: String,
    val noRoute: String,
)

/**
 * 把快照排成设置页那几行：第一行是网关运行态，其后是三态附加条。
 * 干净时**只**返回运行态那一行（正常态不打扰用户）。
 */
fun EgressStatusSnapshot.formatLines(texts: EgressStatusTexts): String {
    val base = when (val gate = gate) {
        is EchGateStatus.Running -> texts.running.replace("%1\$d", gate.port.toString())
        is EchGateStatus.Starting -> texts.starting
        // Failed 与 Exited 都给用户看得见的原因（Exited 的文案由状态机自带）。
        is EchGateStatus.Failed -> texts.failed.replace("%1\$s", gate.reason)
        is EchGateStatus.Exited -> texts.failed.replace("%1\$s", gate.lastError.orEmpty())
        else -> texts.stopped
    }
    if (isClean) return base
    val extra = buildList {
        if (meltedDomains.isNotEmpty()) {
            add(texts.melted.replace("%1\$s", meltedDomains.joinToString { it.name }))
        }
        if (unstableDomains.isNotEmpty()) {
            add(
                texts.unstable
                    .replace("%1\$s", unstableDomains.joinToString { it.name })
                    .replace("%2\$d", maxRecentFailures.toString()),
            )
        }
        if (noRoute) add(texts.noRoute)
    }
    return if (extra.isEmpty()) base else base + "\n" + extra.joinToString("\n")
}

/** 最近 [limit] 条事件的导出文本（与诊断窗同源，一键复制用）。 */
fun recentEgressExport(limit: Int = 50): String =
    buildEgressExport(EgressEvents.recent().takeLast(limit))

/**
 * 诊断事件的展示模型（三端共用，避免 iOS 再抄一份行排版）。
 *
 * 时间格式化走 `kotlinx.datetime`（三端都有），不再各端一份
 * （jvm 原先是 `SimpleDateFormat`，iOS 上没有它）。
 */
data class EgressEventRow(
    val domain: DomainClass,
    val route: RouteId,
    val outcome: AttemptOutcome,
    val rttMs: Long,
    val budgetMs: Long,
    val atMs: Long,
)

/** 最近 [limit] 条事件（新在后）。三态与诊断同源，都读 [EgressEvents]。 */
fun recentEgressRows(limit: Int = 50): List<EgressEventRow> =
    EgressEvents.recent().takeLast(limit).map {
        EgressEventRow(it.domain, it.route, it.outcome, it.rttMs, it.budgetMs, it.atMs)
    }

/** 行标题：`域 · 路由 · 结局`（三端同一形状）。路由走 [displayName]，不直接吐枚举名。 */
fun EgressEventRow.title(): String = "$domain · ${route.displayName()} · $outcome"

/**
 * 路由的展示名（诊断窗用；导出文本仍用枚举原名，便于对着复现）。
 *
 * [RouteId.Default] 诚实叫"当前网络出口"而不是"直连"：折叠后它可能就是代理，
 * 也可能就是直连，HTTP 执行层不区分（见 [RouteId] 的 KDoc 与审阅 F4）。
 */
fun RouteId.displayName(): String = when (this) {
    RouteId.Gate -> "网关"
    RouteId.Default -> "当前网络出口"
    RouteId.GateTunnel -> "网关隧道"
}

/** 行详情：本地时间 `HH:mm:ss` + rtt + 本步预算。 */
fun EgressEventRow.detail(): String =
    "${formatEgressEventTime(atMs)} · rtt ${rttMs}ms · budget ${budgetMs}ms"

/** 事件时间戳的展示口径（本机时区，`HH:mm:ss`）。 */
fun formatEgressEventTime(atMs: Long): String {
    val local = runCatching {
        Instant.fromEpochMilliseconds(atMs).toLocalDateTime(TimeZone.currentSystemDefault())
    }.getOrNull() ?: return "--:--:--"
    return "${pad2(local.hour)}:${pad2(local.minute)}:${pad2(local.second)}"
}

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()
