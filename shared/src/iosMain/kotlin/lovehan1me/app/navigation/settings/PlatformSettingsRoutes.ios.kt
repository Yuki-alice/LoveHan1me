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
import lovehan1me.core.util.AppToast
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.EgressOutletTexts
import lovehan1me.data.network.egress.EgressStatusTexts
import lovehan1me.data.network.egress.ForceMode
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.data.network.egress.detail
import lovehan1me.data.network.egress.egressOutlet
import lovehan1me.data.network.egress.egressStatusSnapshot
import lovehan1me.data.network.egress.formatLine
import lovehan1me.data.network.egress.formatLines
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
import lovehan1me.egress_force_auto
import lovehan1me.egress_force_direct
import lovehan1me.egress_force_direct_warning
import lovehan1me.egress_force_gate
import lovehan1me.egress_force_mode
import lovehan1me.egress_force_proxy
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
 * 自定义 DNS / HTTP·SOCKS 代理 / 自定义 Hosts 在 iOS 确实配不了（跟随系统设置），
 * 这一条如实写；其余（网关开关、三态、强制选路、诊断导出）与 jvm 端同源。
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

    var showForceDialog by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
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

    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text(stringResource(Res.string.egress_diagnostics)) },
            text = {
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
