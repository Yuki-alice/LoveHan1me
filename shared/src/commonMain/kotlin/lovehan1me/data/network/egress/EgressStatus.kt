package lovehan1me.data.network.egress

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.core.platform.currentEpochMillis
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

    /** 网关处于可一键重试的失败态（[EchGateStatus.canRetry]，B1-7）。 */
    val gateRetryable: Boolean get() = gate.canRetry
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

/**
 * 「当前出口」的统计窗口：近 10 分钟（B1-6）。
 *
 * 为什么是滚动窗口而不是"自启动以来"：设置页每 2s 刷新，用户要看的是"现在走哪条路"；
 * 自启动累计会被很久以前的一次失败长期带偏（"明明早恢复了还显示走备用出口"）。
 */
const val EGRESS_OUTLET_WINDOW_MS: Long = 10 * 60 * 1000L

/**
 * 近窗口内的出口分布（B1-6「当前出口」行）。
 *
 * ## 命名口径
 * [RouteId.Default] 折叠后可能就是代理，所以这里叫"当前网络出口"而不是"直连"
 * ——与 [RouteId] / [displayName] 同一口径，不谎称直连。
 *
 * ## [gateSuccessRate] 的诚实边界
 * 它是"**网关口径成功率**"，即网关这一步有多少次拿到成功结局。
 * 这**不等于**服务端接受了 ECH（网关 ECH 未被接受时会降级普通 TLS，仍可能成功）——
 * 但受限域上普通 TLS 的明文 SNI 会被 RST，于是"网关成功"在本项目的主要场景里
 * 就是"ECH 生效"的可观测代理。设置页的措辞据此写成
 * `ECH 接受率 N%（网关成功 a/b）`，把口径摆在明面上，不假装它测的是握手细节。
 */
data class EgressOutlet(
    /** 统计窗口（毫秒）；文案里的"近 N 分钟"由它折算。 */
    val windowMs: Long,
    val attempts: Int,
    val gateAttempts: Int,
    /** [gateAttempts] 里成功的次数。 */
    val gateSuccess: Int,
    val defaultAttempts: Int,
    val failures: Int,
    /** 均值 RTT；-1 = 无样本（与 [EgressEvent] 口径一致）。 */
    val avgRttMs: Long,
) {
    /** 窗口内是否有过实际的 HTTP 出口尝试（兼顾两条路由）。 */
    val hasRouteSample: Boolean get() = gateAttempts + defaultAttempts > 0

    /** 主导出口：窗口内尝试更多的那条路；并列或只有隧道事件时为 null（= 混合）。 */
    val dominant: RouteId?
        get() = when {
            gateAttempts > defaultAttempts -> RouteId.Gate
            defaultAttempts > gateAttempts -> RouteId.Default
            else -> null
        }

    /** 网关口径成功率（见类 KDoc 的边界说明）；无网关样本为 null。 */
    val gateSuccessRate: Double?
        get() = if (gateAttempts == 0) null else gateSuccess.toDouble() / gateAttempts
}

/**
 * 近 [windowMs] 的出口分布。读进程级 [EgressEvents]（与诊断页同源），判定全纯。
 *
 * [nowMs] / [windowMs] / [events] 都可注入，便于离线断言（测试传手造事件与固定时刻）。
 */
fun egressOutlet(
    nowMs: Long = currentEpochMillis(),
    windowMs: Long = EGRESS_OUTLET_WINDOW_MS,
    events: List<EgressEvent> = EgressEvents.recent(),
): EgressOutlet {
    val since = nowMs - windowMs
    var attempts = 0
    var gate = 0
    var gateOk = 0
    var fallback = 0
    var failures = 0
    var rttSum = 0L
    var rttSamples = 0L
    for (event in events) {
        if (event.atMs < since || event.atMs > nowMs) continue
        attempts++
        when (event.route) {
            RouteId.Gate -> {
                gate++
                if (event.outcome == AttemptOutcome.Success) gateOk++
            }

            RouteId.Default -> fallback++
            // HTTP 执行层不产生隧道步（隧道是播放器 / CF 验证窗的事）；真出现了只计入 attempts。
            RouteId.GateTunnel -> Unit
        }
        if (event.outcome != AttemptOutcome.Success) failures++
        if (event.rttMs >= 0) {
            rttSum += event.rttMs
            rttSamples++
        }
    }
    return EgressOutlet(
        windowMs = windowMs,
        attempts = attempts,
        gateAttempts = gate,
        gateSuccess = gateOk,
        defaultAttempts = fallback,
        failures = failures,
        avgRttMs = if (rttSamples > 0) rttSum / rttSamples else -1L,
    )
}

/**
 * 「当前出口」行的本地化模板（各端从自己的 `strings.xml` 取好后传进来）。
 *
 * 占位符沿用 `strings.xml` 口径：`%1$d` / `%1$s` / `%2$d`。注意 [acceptance] 的百分号
 * 由调用方拼进 `%1$s`（形如 `93%`），**不要在资源里写裸 `%`**（会被当成格式符）。
 */
data class EgressOutletTexts(
    /** `网关（近 %1$d 分钟 %2$d 次）` */
    val viaGate: String,
    /** `当前网络出口（近 %1$d 分钟 %2$d 次）` */
    val viaDefault: String,
    /** `网关 %1$d 次 · 当前网络出口 %2$d 次` */
    val mixed: String,
    /** 无样本 */
    val none: String,
    /** `ECH 接受率 %1$s（网关成功 %2$d/%3$d）` */
    val acceptance: String,
)

/**
 * 排成设置页那一行：主导出口 +（有网关样本时）接受率。
 * 无出口样本时只回 [EgressOutletTexts.none]，不硬凑"0 次"。
 */
fun EgressOutlet.formatLine(texts: EgressOutletTexts): String {
    val minutes = (windowMs / 60_000L).toInt().coerceAtLeast(1)
    val head = when {
        !hasRouteSample -> texts.none
        dominant == RouteId.Gate -> texts.viaGate
            .replace("%1\$d", minutes.toString())
            .replace("%2\$d", gateAttempts.toString())

        dominant == RouteId.Default -> texts.viaDefault
            .replace("%1\$d", minutes.toString())
            .replace("%2\$d", defaultAttempts.toString())

        else -> texts.mixed
            .replace("%1\$d", gateAttempts.toString())
            .replace("%2\$d", defaultAttempts.toString())
    }
    val rate = gateSuccessRate ?: return head
    return head + "\n" + texts.acceptance
        .replace("%1\$s", "${(rate * 100).toInt()}%")
        .replace("%2\$d", gateSuccess.toString())
        .replace("%3\$d", gateAttempts.toString())
}

// ---------------------------------------------------------------------------
// B4-4 · 诊断报告结论层（F16）
//
// 数据源全是现成的（[egressStatusSnapshot] + [egressOutlet] + [EchGate.status]），
// 本层只做"**从数字到结论**"：把四步链路各判一个状态，再给出一句"问题出在哪 +
// 下一步做什么"。判定全在 commonMain（三端同源、可离线断言），文案由各端传模板进来。
// ---------------------------------------------------------------------------

/** 诊断报告的四个步骤（按请求实际链路顺序，从"网关能不能用"到"哪个域出了问题"）。 */
enum class DiagnosisStepKind { Gateway, Route, Quality, Domain }

/** 单步结论：够用就好，不引入"严重度分级"这种没人会读的维度。 */
enum class DiagnosisStatus { Ok, Warn, Fail, Unknown }

/** 一步的结论：判定结果 + 证据（数字/枚举名，不做本地化）。 */
data class DiagnosisStep(
    val kind: DiagnosisStepKind,
    val status: DiagnosisStatus,
    /** 人类可读证据（端口 / 原因 / 计数）；空串 = 该步没有可报的数字。 */
    val evidence: String,
)

/**
 * 报告结论：**单一**结论而非"每步各给一句建议"。
 *
 * 多结论会让用户面对"网关失败 + 无出口 + 域熔断"三句话不知从哪下手；这里按
 * 因果优先级取**最上游**那个（网关坏了就别谈出口质量），只给一条下一步动作。
 */
enum class DiagnosisVerdict { Healthy, GatewayFailed, NoRoute, DomainsMelted, Unstable, NoData }

/** 一次诊断：四步结论 + 一条总结论。 */
data class EgressDiagnosis(
    val steps: List<DiagnosisStep>,
    val verdict: DiagnosisVerdict,
)

/**
 * 从现成观测数据得出诊断结论（纯函数，全部输入可注入）。
 *
 * 与设置页三态、B1-6「当前出口」共用同一批数据源 —— 三处口径不会打架。
 *
 * @param gate 网关状态，默认读进程级 [EchGate.status]。
 */
fun egressDiagnosis(
    nowMs: Long = currentEpochMillis(),
    windowMs: Long = EGRESS_OUTLET_WINDOW_MS,
    useEchGate: Boolean = runCatching { SettingsRepository.useEchGate }.getOrDefault(false),
    proxyUsable: Boolean = runCatching { currentProxyState() }.getOrDefault(ProxyState.None).isUsable,
    healthOf: (DomainClass) -> RouteHealth = { RouteRegistry.healthOf(it) },
    gate: EchGateStatus = EchGate.status,
    events: List<EgressEvent> = EgressEvents.recent(),
): EgressDiagnosis {
    val snapshot = egressStatusSnapshot(nowMs, useEchGate, proxyUsable, healthOf)
    val outlet = egressOutlet(nowMs, windowMs, events)

    // 步 1 · 网关：能不能把受限域交给 ECH。
    val gateway = when (gate) {
        is EchGateStatus.Running -> DiagnosisStep(DiagnosisStepKind.Gateway, DiagnosisStatus.Ok, gate.port.toString())
        is EchGateStatus.Starting -> DiagnosisStep(DiagnosisStepKind.Gateway, DiagnosisStatus.Warn, "")
        is EchGateStatus.Failed -> DiagnosisStep(DiagnosisStepKind.Gateway, DiagnosisStatus.Fail, gate.reason)
        is EchGateStatus.Exited -> DiagnosisStep(DiagnosisStepKind.Gateway, DiagnosisStatus.Fail, gate.lastError.orEmpty())
        is EchGateStatus.Stopped -> DiagnosisStep(DiagnosisStepKind.Gateway, DiagnosisStatus.Unknown, "")
        is EchGateStatus.Idle -> DiagnosisStep(DiagnosisStepKind.Gateway, DiagnosisStatus.Unknown, "")
    }

    // 步 2 · 出口：这次走的是网关还是当前网络出口。
    val dominant = outlet.dominant
    val route = when {
        snapshot.noRoute -> DiagnosisStep(DiagnosisStepKind.Route, DiagnosisStatus.Fail, "")
        dominant != null -> DiagnosisStep(DiagnosisStepKind.Route, DiagnosisStatus.Ok, dominant.displayName())
        outlet.hasRouteSample -> DiagnosisStep(DiagnosisStepKind.Route, DiagnosisStatus.Warn, "")
        else -> DiagnosisStep(DiagnosisStepKind.Route, DiagnosisStatus.Unknown, "")
    }

    // 步 3 · 质量：近窗口的成败与耗时。
    val quality = when {
        outlet.attempts == 0 -> DiagnosisStep(DiagnosisStepKind.Quality, DiagnosisStatus.Unknown, "")
        outlet.failures > 0 -> DiagnosisStep(DiagnosisStepKind.Quality, DiagnosisStatus.Warn, qualityEvidence(outlet))
        else -> DiagnosisStep(DiagnosisStepKind.Quality, DiagnosisStatus.Ok, qualityEvidence(outlet))
    }

    // 步 4 · 域健康：哪些域的网关出口被摘掉 / 在抖动。
    val domain = when {
        snapshot.meltedDomains.isNotEmpty() ->
            DiagnosisStep(DiagnosisStepKind.Domain, DiagnosisStatus.Fail, snapshot.meltedDomains.joinToString { it.name })

        snapshot.unstableDomains.isNotEmpty() ->
            DiagnosisStep(DiagnosisStepKind.Domain, DiagnosisStatus.Warn, snapshot.unstableDomains.joinToString { it.name })

        else -> DiagnosisStep(DiagnosisStepKind.Domain, DiagnosisStatus.Ok, "")
    }

    // 结论按因果优先级取最上游那个：网关坏了 → 无出口 → 域熔断 → 抖动 → 无样本 → 健康。
    val verdict = when {
        gate is EchGateStatus.Failed || gate is EchGateStatus.Exited -> DiagnosisVerdict.GatewayFailed
        snapshot.noRoute -> DiagnosisVerdict.NoRoute
        snapshot.meltedDomains.isNotEmpty() -> DiagnosisVerdict.DomainsMelted
        snapshot.unstableDomains.isNotEmpty() -> DiagnosisVerdict.Unstable
        outlet.attempts == 0 -> DiagnosisVerdict.NoData
        else -> DiagnosisVerdict.Healthy
    }

    return EgressDiagnosis(listOf(gateway, route, quality, domain), verdict)
}

/** 质量步的证据串：次数 / 失败 / 均值 RTT / 网关成功率（有样本才附 RTT 与成功率）。 */
private fun qualityEvidence(outlet: EgressOutlet): String = buildString {
    append("${outlet.attempts} · fail ${outlet.failures}")
    if (outlet.avgRttMs >= 0) append(" · rtt ${outlet.avgRttMs}ms")
    outlet.gateSuccessRate?.let { append(" · gate ${(it * 100).toInt()}%") }
}

/**
 * 诊断报告的本地化模板（各端从自己的 `strings.xml` 取好后传进来）。
 *
 * 步标题、状态词、结论句都要本地化；**证据串不本地化**（数字与枚举名，见 [DiagnosisStep.evidence]）。
 */
data class EgressDiagnosisTexts(
    val stepGateway: String,
    val stepRoute: String,
    val stepQuality: String,
    val stepDomain: String,
    val statusOk: String,
    val statusWarn: String,
    val statusFail: String,
    val statusUnknown: String,
    /** `一切正常，无需处理` */
    val verdictHealthy: String,
    /** `网关未运行，受限域会走兜底；点「重试网关」重新拉起` */
    val verdictGatewayFailed: String,
    /** `网关开着却没有可用出口；请开启网关或配置代理 / hosts` */
    val verdictNoRoute: String,
    /** `部分域的网关出口被熔断；等待冷却或切换镜像后重试` */
    val verdictDomainsMelted: String,
    /** `出口有失败但未熔断；若持续失败可临时用「强制选路」换条路` */
    val verdictUnstable: String,
    /** `近窗口没有出口样本（可能全命中缓存）；正常使用一会再看` */
    val verdictNoData: String,
)

/**
 * 排成诊断窗顶部的报告文本：第一行是结论（问题出在哪 + 下一步），其后四步逐行给出状态与证据。
 *
 * 与 [formatLines] 同款：只产出结构，标题词由调用方本地化。
 */
fun EgressDiagnosis.formatReport(texts: EgressDiagnosisTexts): String {
    val verdictLine = when (verdict) {
        DiagnosisVerdict.Healthy -> texts.verdictHealthy
        DiagnosisVerdict.GatewayFailed -> texts.verdictGatewayFailed
        DiagnosisVerdict.NoRoute -> texts.verdictNoRoute
        DiagnosisVerdict.DomainsMelted -> texts.verdictDomainsMelted
        DiagnosisVerdict.Unstable -> texts.verdictUnstable
        DiagnosisVerdict.NoData -> texts.verdictNoData
    }
    val stepLines = steps.joinToString("\n") { step ->
        val title = when (step.kind) {
            DiagnosisStepKind.Gateway -> texts.stepGateway
            DiagnosisStepKind.Route -> texts.stepRoute
            DiagnosisStepKind.Quality -> texts.stepQuality
            DiagnosisStepKind.Domain -> texts.stepDomain
        }
        val status = when (step.status) {
            DiagnosisStatus.Ok -> texts.statusOk
            DiagnosisStatus.Warn -> texts.statusWarn
            DiagnosisStatus.Fail -> texts.statusFail
            DiagnosisStatus.Unknown -> texts.statusUnknown
        }
        val head = "$title · $status"
        if (step.evidence.isBlank()) head else "$head · ${step.evidence}"
    }
    return verdictLine + "\n" + stepLines
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
