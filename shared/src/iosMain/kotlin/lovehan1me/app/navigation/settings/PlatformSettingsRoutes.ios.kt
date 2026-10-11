package lovehan1me.app.navigation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.copy_to_clipboard
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.domain.model.AutoMirrorGuard
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.util.AppToast
import lovehan1me.data.network.ECH_GATE_TIMEOUT_SEC_DEFAULT
import lovehan1me.data.network.ECH_GATE_TIMEOUT_SEC_MAX
import lovehan1me.data.network.ECH_GATE_TIMEOUT_SEC_MIN
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.isPlausibleEchB64
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.EgressDiagnosisTexts
import lovehan1me.data.network.egress.EgressOutletTexts
import lovehan1me.data.network.egress.EgressStatusTexts
import lovehan1me.data.network.egress.ForceMode
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.data.network.egress.detail
import lovehan1me.data.network.egress.egressDiagnosis
import lovehan1me.data.network.egress.egressOutlet
import lovehan1me.data.network.egress.egressStatusSnapshot
import lovehan1me.data.network.egress.formatLine
import lovehan1me.data.network.egress.formatLines
import lovehan1me.data.network.egress.formatReport
import lovehan1me.data.network.egress.recentEgressExport
import lovehan1me.data.network.egress.recentEgressRows
import lovehan1me.data.network.egress.title
// iosMain 必须逐个显式导入资源属性：生成的访问器是 `lovehan1me` 包下的扩展属性，
// Kotlin/Native 侧不会随 `import lovehan1me.Res` 一起带进来
// （既有约定，见 `MainViewController.kt` 的 import 段）。
import lovehan1me.ech_gate_status_failed
import lovehan1me.ech_gate_status_running
import lovehan1me.ech_gate_status_starting
import lovehan1me.ech_gate_status_stopped
import lovehan1me.egress_close
import lovehan1me.egress_copy
import lovehan1me.egress_diagnostics
import lovehan1me.egress_diagnostics_empty
import lovehan1me.egress_ech_acceptance
import lovehan1me.egress_report_status_fail
import lovehan1me.egress_report_status_ok
import lovehan1me.egress_report_status_unknown
import lovehan1me.egress_report_status_warn
import lovehan1me.egress_report_step_domain
import lovehan1me.egress_report_step_gateway
import lovehan1me.egress_report_step_quality
import lovehan1me.egress_report_step_route
import lovehan1me.egress_report_verdict_domains_melted
import lovehan1me.egress_report_verdict_gateway_failed
import lovehan1me.egress_report_verdict_healthy
import lovehan1me.egress_report_verdict_no_data
import lovehan1me.egress_report_verdict_no_route
import lovehan1me.egress_report_verdict_unstable
import lovehan1me.egress_force_auto
import lovehan1me.egress_force_direct
import lovehan1me.egress_force_direct_warning
import lovehan1me.egress_force_gate
import lovehan1me.egress_force_mode
import lovehan1me.egress_force_proxy
import lovehan1me.egress_follow_global
import lovehan1me.egress_per_domain_title
import lovehan1me.egress_per_domain_hanime
import lovehan1me.egress_per_domain_getchu
import lovehan1me.egress_per_domain_cdn
import lovehan1me.egress_per_domain_thirdparty
import lovehan1me.egress_per_domain_override
import lovehan1me.auto_mirror_guard
import lovehan1me.auto_mirror_guard_require_clearance
import lovehan1me.auto_mirror_guard_always
import lovehan1me.gate_args_title
import lovehan1me.gate_args_timeout
import lovehan1me.gate_args_timeout_summary
import lovehan1me.gate_args_doh
import lovehan1me.gate_args_doh_summary
import lovehan1me.gate_args_echb64
import lovehan1me.gate_args_echb64_summary
import lovehan1me.gate_args_default
import lovehan1me.gate_args_overridden
import lovehan1me.gate_args_invalid_doh
import lovehan1me.gate_args_invalid_b64
import lovehan1me.gate_args_ios_restart_note
import lovehan1me.proxy
import lovehan1me.direct
import lovehan1me.system_proxy
import lovehan1me.http
import lovehan1me.socks
import lovehan1me.host_or_ipv4
import lovehan1me.port
import lovehan1me.confirm
import lovehan1me.cancel
import lovehan1me.invalid_ip_or_port
import lovehan1me.egress_outlet_mixed
import lovehan1me.egress_outlet_none
import lovehan1me.egress_outlet_title
import lovehan1me.egress_outlet_via_default
import lovehan1me.egress_outlet_via_gate
import lovehan1me.egress_tri_melted
import lovehan1me.egress_tri_no_route
import lovehan1me.egress_tri_unstable
import lovehan1me.network_diag_section
import lovehan1me.network_gate_section
import lovehan1me.network_platform_limits_ios
import lovehan1me.network_platform_limits_title
import lovehan1me.use_ech_gate
import lovehan1me.use_ech_gate_summary
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * iOS 网络页：只放**本端真的有**的东西。
 *
 * ## 为什么不再是占位符
 * 上一版这里只有两行硬编码英文："Network settings" +
 * "Custom DNS / proxy is not available on iOS"。后半句是假的：同一时刻
 * ECH 网关在跑（Swift `EchGateBootstrap`）、CF 验证窗可达、调度器在按域排表
 * —— 用户却被告知"网络功能不可用"，且看不到任何状态、日志与逃生舱。
 *
 * 现在的原则：**能做的全给，做不到的单独一行说清楚是哪几项**。
 * 自定义 DNS / 自定义 Hosts 在 iOS 确实配不了（跟随系统设置），
 * 这一条如实写；HTTP/SOCKS 代理可在应用内配（B2-4：经本地网关转发出站，
 * 下次启动生效）；其余（网关开关、三态、强制选路、诊断导出）与 jvm 端同源。
 *
 * ## 判定本体不在本文件
 * 三态与事件行由 commonMain `egressStatusSnapshot` / `recentEgressRows` 给出，
 * 与 jvmMain `NetworkSettingsRoute` **共用同一份**——不会出现两端算出来不一样。
 */

private const val REFRESH_TICK_MS = 2_000L

@Composable
actual fun NetworkSettingsRouteScreen(embedded: Boolean) {
    val scope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val copyToClipboard = rememberCopyTextToClipboard()

    // 与 jvm 侧同款 2s 刷新：三态、事件、导出文本都靠它活起来。
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_TICK_MS)
            tick++
        }
    }

    val snapshot = remember(tick, settings) { egressStatusSnapshot(currentEpochMillis()) }
    val rows = remember(tick) { recentEgressRows(50) }
    val exportText = remember(tick) { recentEgressExport(50) }

    val texts = EgressStatusTexts(
        running = stringResource(Res.string.ech_gate_status_running),
        starting = stringResource(Res.string.ech_gate_status_starting),
        stopped = stringResource(Res.string.ech_gate_status_stopped),
        failed = stringResource(Res.string.ech_gate_status_failed),
        melted = stringResource(Res.string.egress_tri_melted),
        unstable = stringResource(Res.string.egress_tri_unstable),
        noRoute = stringResource(Res.string.egress_tri_no_route),
    )
    val statusLine = remember(snapshot, texts) { snapshot.formatLines(texts) }
    // B1-6：与 jvm 侧同源的「当前出口」行（判定/排版都在 commonMain `egressOutlet`/`formatLine`，
    // iOS 只负责取本地化模板并贴结果，保证两端算出来一致）。
    val outletTexts = EgressOutletTexts(
        viaGate = stringResource(Res.string.egress_outlet_via_gate),
        viaDefault = stringResource(Res.string.egress_outlet_via_default),
        mixed = stringResource(Res.string.egress_outlet_mixed),
        none = stringResource(Res.string.egress_outlet_none),
        acceptance = stringResource(Res.string.egress_ech_acceptance),
    )
    val outletLine = remember(tick, outletTexts) { egressOutlet(currentEpochMillis()).formatLine(outletTexts) }
    // B4-4：与 jvm 同源的诊断结论层（四步判定 + 结论全在 commonMain，iOS 只取本地化模板）。
    val diagnosisTexts = EgressDiagnosisTexts(
        stepGateway = stringResource(Res.string.egress_report_step_gateway),
        stepRoute = stringResource(Res.string.egress_report_step_route),
        stepQuality = stringResource(Res.string.egress_report_step_quality),
        stepDomain = stringResource(Res.string.egress_report_step_domain),
        statusOk = stringResource(Res.string.egress_report_status_ok),
        statusWarn = stringResource(Res.string.egress_report_status_warn),
        statusFail = stringResource(Res.string.egress_report_status_fail),
        statusUnknown = stringResource(Res.string.egress_report_status_unknown),
        verdictHealthy = stringResource(Res.string.egress_report_verdict_healthy),
        verdictGatewayFailed = stringResource(Res.string.egress_report_verdict_gateway_failed),
        verdictNoRoute = stringResource(Res.string.egress_report_verdict_no_route),
        verdictDomainsMelted = stringResource(Res.string.egress_report_verdict_domains_melted),
        verdictUnstable = stringResource(Res.string.egress_report_verdict_unstable),
        verdictNoData = stringResource(Res.string.egress_report_verdict_no_data),
    )
    val report = remember(tick, diagnosisTexts) {
        egressDiagnosis(currentEpochMillis()).formatReport(diagnosisTexts)
    }

    var showForceDialog by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showGuardDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showGateArgsDialog by remember { mutableStateOf(false) }
    var perDomainTarget by remember { mutableStateOf<String?>(null) }
    val forceLabel = when (SettingsRepository.egressForceMode) {
        ForceMode.ForceGate -> Res.string.egress_force_gate
        ForceMode.ForceDirect -> Res.string.egress_force_direct
        ForceMode.ForceProxy -> Res.string.egress_force_proxy
        ForceMode.Auto -> Res.string.egress_force_auto
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // 平台限制：只说**具体哪几项**不可用，不说"网络功能不可用"。
            Text(
                text = stringResource(Res.string.network_platform_limits_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.network_platform_limits_ios),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Text(
                text = stringResource(Res.string.network_gate_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.use_ech_gate),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = statusLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.useEchGate,
                            onCheckedChange = { value ->
                                scope.launch {
                                    SettingsRepository.update { it.copy(useEchGate = value) }
                                    // 与 jvm 同语义：用户主动拨开关 = 重新给一次机会，
                                    // 否则刚被熔断过的网关在冷却期内"开了也不管事"。
                                    RouteRegistry.reset()
                                }
                            },
                        )
                    }
                    // B1-6：用户能回答"这次走的是网关还是直连"。空串（无样本）时不占位。
                    if (outletLine.isNotBlank()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(
                                text = stringResource(Res.string.egress_outlet_title),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = outletLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(Res.string.use_ech_gate_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsRow(
                title = stringResource(Res.string.egress_force_mode),
                summary = stringResource(forceLabel),
                onClick = { showForceDialog = true },
            )
        }

        // B3-5：自动镜像切换前置策略。热读、改完下一次评估即生效，无需重启。
        item {
            SettingsRow(
                title = stringResource(Res.string.auto_mirror_guard),
                summary = when (settings.autoMirrorGuard) {
                    AutoMirrorGuard.RequireClearance ->
                        stringResource(Res.string.auto_mirror_guard_require_clearance)
                    AutoMirrorGuard.Always ->
                        stringResource(Res.string.auto_mirror_guard_always)
                },
                onClick = { showGuardDialog = true },
            )
        }

        // B2-4：iOS 应用内代理（经本地网关转发出站）。Darwin 引擎吃不到手填代理，
        // 但网关进程内起服后由它代为出站 —— 开关在这里，生效在网关（下次启动）。
        item {
            SettingsRow(
                title = stringResource(Res.string.proxy),
                summary = iosProxySummary() + "\n" +
                    stringResource(Res.string.gate_args_ios_restart_note),
                onClick = { showProxyDialog = true },
            )
        }

        // B4-2：每域出口覆盖（逻辑三端生效，iOS 同样热读）。默认全部跟随全局。
        item {
            // 与 jvm `SettingsSectionTitle(egress_per_domain_title)` 同信息口径：
            // 没有这行标题，四行「H站/getchu/CDN/第三方」读不出是同一组。
            Text(
                text = stringResource(Res.string.egress_per_domain_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DomainClass.entries.forEach { domain ->
            item {
                SettingsRow(
                    title = stringResource(perDomainLabel(domain)),
                    summary = perDomainSummary(domain),
                    onClick = { perDomainTarget = domain.name },
                )
            }
        }

        // B4-3a：网关启动参数（Swift 经 Kotlin 桥取值，见 EchGateStartArgs）。
        // iOS 网关随启动拉起一次，改动下次启动生效 —— 与代理行共用同一条注记。
        item {
            SettingsRow(
                title = stringResource(Res.string.gate_args_title),
                summary = iosGateArgsSummary() + "\n" +
                    stringResource(Res.string.gate_args_ios_restart_note),
                onClick = { showGateArgsDialog = true },
            )
        }

        item {
            SettingsRow(
                title = stringResource(Res.string.network_diag_section),
                summary = stringResource(
                    if (rows.isEmpty()) Res.string.egress_diagnostics_empty
                    else Res.string.egress_diagnostics,
                ),
                onClick = { showDiagnostics = true },
            )
        }
    }

    if (showForceDialog) {
        ForceModeDialog(
            current = SettingsRepository.egressForceMode,
            onDismiss = { showForceDialog = false },
            onPick = { mode ->
                showForceDialog = false
                scope.launch {
                    SettingsRepository.update { it.copy(egressForceMode = mode.name) }
                    // 强制项是调度器的最高指令：切完即清健康，让新规矩从干净状态开始。
                    RouteRegistry.reset()
                    if (mode == ForceMode.ForceDirect) {
                        AppToast.warning(getString(Res.string.egress_force_direct_warning))
                    }
                }
            },
        )
    }

    if (showGuardDialog) {
        GuardChoiceDialog(
            current = settings.autoMirrorGuard,
            onDismiss = { showGuardDialog = false },
            onPick = { guard ->
                showGuardDialog = false
                // 前置策略每次评估时热读（见 AutoMirrorSwitch），改完即生效，无需重启。
                scope.launch { SettingsRepository.update { it.copy(autoMirrorGuard = guard) } }
            },
        )
    }

    if (showProxyDialog) {
        IosProxyDialog(
            initialType = settings.proxyType,
            initialIp = settings.proxyIp,
            initialPort = settings.proxyPort,
            onDismiss = { showProxyDialog = false },
            onConfirm = { type, ip, port ->
                if ((type == ProxyType.Http || type == ProxyType.Socks) &&
                    (ip.isBlank() || port !in 1..65535)
                ) {
                    scope.launch { AppToast.warning(getString(Res.string.invalid_ip_or_port)) }
                    return@IosProxyDialog
                }
                showProxyDialog = false
                // iOS 无 JVM 系统属性/Darwin 按请求配代理的口子，唯一生效通道是
                // 下次启动的网关（代理模式转交）。Darwin 直发流量不受它影响 ——
                // 行摘要里的下次启动注记说的是同一件事。
                scope.launch {
                    SettingsRepository.update {
                        it.copy(proxyType = type, proxyIp = ip, proxyPort = port)
                    }
                }
            },
        )
    }

    val domainTarget = perDomainTarget?.let { name ->
        DomainClass.entries.firstOrNull { it.name == name }
    }
    if (domainTarget != null) {
        PerDomainChoiceDialog(
            domain = domainTarget,
            current = SettingsRepository.perDomainForceMode[domainTarget],
            globalName = stringResource(forceLabel),
            onDismiss = { perDomainTarget = null },
            onPick = { mode ->
                perDomainTarget = null
                // null = 清除覆盖（跟随全局）。选择器与调度器每请求热读，改完即生效。
                scope.launch {
                    SettingsRepository.update { s ->
                        val next = s.perDomainForceMode.toMutableMap()
                        if (mode == null) next.remove(domainTarget.name)
                        else next[domainTarget.name] = mode.name
                        s.copy(perDomainForceMode = next)
                    }
                }
            },
        )
    }

    if (showGateArgsDialog) {
        IosGateArgsDialog(
            timeoutSec = settings.echGateTimeoutSec,
            dohUrl = settings.echGateDohUrl,
            echB64 = settings.echGateEchB64,
            onDismiss = { showGateArgsDialog = false },
            onConfirm = { timeout, doh, b64 ->
                if (doh.isNotBlank() && !doh.startsWith("https://")) {
                    scope.launch { AppToast.warning(getString(Res.string.gate_args_invalid_doh)) }
                    return@IosGateArgsDialog
                }
                if (b64.isNotBlank() && !isPlausibleEchB64(b64)) {
                    scope.launch { AppToast.warning(getString(Res.string.gate_args_invalid_b64)) }
                    return@IosGateArgsDialog
                }
                showGateArgsDialog = false
                // iOS 网关随启动拉起一次，Kotlin 侧没有重启门面 —— 改动下次启动生效
                // （行摘要已注记）。只做热读部分不需要的复位：超时/DoH/b64 全在起服时收一次。
                scope.launch {
                    SettingsRepository.update {
                        it.copy(
                            echGateTimeoutSec = timeout.coerceIn(
                                ECH_GATE_TIMEOUT_SEC_MIN,
                                ECH_GATE_TIMEOUT_SEC_MAX,
                            ),
                            echGateDohUrl = doh,
                            echGateEchB64 = b64,
                        )
                    }
                }
            },
        )
    }

    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text(stringResource(Res.string.egress_diagnostics)) },
            text = {
                // B4-4：先给结论（问题出在哪 + 下一步），再列原始事件流。
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (report.isNotBlank()) {
                        Text(
                            report,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (rows.isEmpty()) {
                        Text(stringResource(Res.string.egress_diagnostics_empty))
                    } else {
                        LazyColumn {
                            items(rows) { row ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(row.title(), style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        row.detail(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard(exportText)
                        scope.launch { AppToast.success(getString(Res.string.copy_to_clipboard)) }
                    },
                ) { Text(stringResource(Res.string.egress_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text(stringResource(Res.string.egress_close))
                }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForceModeDialog(
    current: ForceMode,
    onDismiss: () -> Unit,
    onPick: (ForceMode) -> Unit,
) {
    val options = listOf(
        ForceMode.Auto to Res.string.egress_force_auto,
        ForceMode.ForceGate to Res.string.egress_force_gate,
        ForceMode.ForceDirect to Res.string.egress_force_direct,
        ForceMode.ForceProxy to Res.string.egress_force_proxy,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.egress_force_mode)) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onPick(mode) })
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.egress_close)) } },
    )
}

/** B3-5：自动镜像切换前置策略两档（与 jvm `NetworkChoiceDialog` 同义，iOS 自绘）。 */
@Composable
private fun GuardChoiceDialog(
    current: AutoMirrorGuard,
    onDismiss: () -> Unit,
    onPick: (AutoMirrorGuard) -> Unit,
) {
    val options = listOf(
        AutoMirrorGuard.RequireClearance to Res.string.auto_mirror_guard_require_clearance,
        AutoMirrorGuard.Always to Res.string.auto_mirror_guard_always,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.auto_mirror_guard)) },
        text = {
            Column {
                options.forEach { (guard, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = guard == current, onClick = { onPick(guard) })
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.egress_close)) } },
    )
}

/** B2-4：iOS 应用内代理（类型 + 地址 + 端口，与 jvm `ProxyDialog` 同形）。 */
@Composable
private fun IosProxyDialog(
    initialType: ProxyType,
    initialIp: String,
    initialPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (ProxyType, String, Int) -> Unit,
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var ip by remember { mutableStateOf(initialIp) }
    var portText by remember { mutableStateOf(initialPort.takeIf { it >= 0 }?.toString().orEmpty()) }
    val options = listOf(
        ProxyType.Direct to Res.string.direct,
        ProxyType.System to Res.string.system_proxy,
        ProxyType.Http to Res.string.http,
        ProxyType.Socks to Res.string.socks,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.proxy)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { (type, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == selectedType, onClick = { selectedType = type })
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                val editable = selectedType == ProxyType.Http || selectedType == ProxyType.Socks
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    enabled = editable,
                    label = { Text(stringResource(Res.string.host_or_ipv4)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    enabled = editable,
                    label = { Text(stringResource(Res.string.port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selectedType, ip.trim(), portText.toIntOrNull() ?: -1)
            }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/** B4-2：单域出口覆盖（含"跟随全局"，null = 清除覆盖）。 */
@Composable
private fun PerDomainChoiceDialog(
    domain: DomainClass,
    current: ForceMode?,
    globalName: String,
    onDismiss: () -> Unit,
    onPick: (ForceMode?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(perDomainLabel(domain))) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current == null, onClick = { onPick(null) })
                    Text(
                        text = stringResource(Res.string.egress_follow_global, globalName),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                listOf(
                    ForceMode.Auto to Res.string.egress_force_auto,
                    ForceMode.ForceGate to Res.string.egress_force_gate,
                    ForceMode.ForceDirect to Res.string.egress_force_direct,
                    ForceMode.ForceProxy to Res.string.egress_force_proxy,
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onPick(mode) })
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.egress_close)) } },
    )
}

/** B4-3a：网关启动参数（与 jvm `GateArgsDialog` 同形；iOS 改动下次启动生效）。 */
@Composable
private fun IosGateArgsDialog(
    timeoutSec: Int,
    dohUrl: String,
    echB64: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, String) -> Unit,
) {
    var timeoutValue by remember { mutableStateOf(timeoutSec.toString()) }
    var dohValue by remember { mutableStateOf(dohUrl) }
    var b64Value by remember { mutableStateOf(echB64) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.gate_args_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = timeoutValue,
                    onValueChange = { timeoutValue = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(Res.string.gate_args_timeout)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(Res.string.gate_args_timeout_summary)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dohValue,
                    onValueChange = { dohValue = it },
                    label = { Text(stringResource(Res.string.gate_args_doh)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(Res.string.gate_args_doh_summary)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = b64Value,
                    onValueChange = { b64Value = it },
                    label = { Text(stringResource(Res.string.gate_args_echb64)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(Res.string.gate_args_echb64_summary)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    timeoutValue.toIntOrNull()?.coerceIn(
                        ECH_GATE_TIMEOUT_SEC_MIN,
                        ECH_GATE_TIMEOUT_SEC_MAX,
                    ) ?: ECH_GATE_TIMEOUT_SEC_DEFAULT,
                    dohValue.trim(),
                    b64Value.trim(),
                )
            }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun iosProxySummary(): String {
    val settings = SettingsRepository.settings.collectAsStateWithLifecycle().value
    return when (settings.proxyType) {
        ProxyType.Direct -> stringResource(Res.string.direct)
        ProxyType.System -> stringResource(Res.string.system_proxy)
        ProxyType.Http -> "HTTP ${settings.proxyIp}:${settings.proxyPort}"
        ProxyType.Socks -> "SOCKS ${settings.proxyIp}:${settings.proxyPort}"
    }
}

private fun perDomainLabel(domain: DomainClass) = when (domain) {
    DomainClass.Hanime -> Res.string.egress_per_domain_hanime
    DomainClass.Getchu -> Res.string.egress_per_domain_getchu
    DomainClass.CdnMedia -> Res.string.egress_per_domain_cdn
    DomainClass.ThirdParty -> Res.string.egress_per_domain_thirdparty
}

@Composable
private fun perDomainSummary(domain: DomainClass): String {
    val override = SettingsRepository.perDomainForceMode[domain]
    val forceName = when (SettingsRepository.egressForceMode) {
        ForceMode.ForceGate -> stringResource(Res.string.egress_force_gate)
        ForceMode.ForceDirect -> stringResource(Res.string.egress_force_direct)
        ForceMode.ForceProxy -> stringResource(Res.string.egress_force_proxy)
        ForceMode.Auto -> stringResource(Res.string.egress_force_auto)
    }
    if (override == null) return stringResource(Res.string.egress_follow_global, forceName)
    val overrideName = when (override) {
        ForceMode.ForceGate -> stringResource(Res.string.egress_force_gate)
        ForceMode.ForceDirect -> stringResource(Res.string.egress_force_direct)
        ForceMode.ForceProxy -> stringResource(Res.string.egress_force_proxy)
        ForceMode.Auto -> stringResource(Res.string.egress_force_auto)
    }
    return stringResource(Res.string.egress_per_domain_override, overrideName)
}

@Composable
private fun iosGateArgsSummary(): String {
    val settings = SettingsRepository.settings.collectAsStateWithLifecycle().value
    return buildString {
        append(stringResource(Res.string.gate_args_timeout))
        append(": ${settings.echGateTimeoutSec}s")
        append(
            if (settings.echGateDohUrl.isBlank() && settings.echGateEchB64.isBlank()) {
                "\n" + stringResource(Res.string.gate_args_default)
            } else {
                "\n" + stringResource(Res.string.gate_args_overridden)
            },
        )
    }
}
