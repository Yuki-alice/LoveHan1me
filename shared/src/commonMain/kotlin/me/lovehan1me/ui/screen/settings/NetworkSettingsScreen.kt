package me.lovehan1me.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import me.lovehan1me.ui.component.HapticButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import me.lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import me.lovehan1me.Res
import me.lovehan1me.view_node_latency
import me.lovehan1me.use_doh
import me.lovehan1me.use_built_in_hosts_summary
import me.lovehan1me.use_built_in_hosts
import me.lovehan1me.test_doh_summary
import me.lovehan1me.test_doh
import me.lovehan1me.test_connection
import me.lovehan1me.system_proxy
import me.lovehan1me.socks
import me.lovehan1me.proxy
import me.lovehan1me.port
import me.lovehan1me.network_timeout_text
import me.lovehan1me.http
import me.lovehan1me.host_or_ipv4
import me.lovehan1me.enable_custom_mirror_site
import me.lovehan1me.domain_name
import me.lovehan1me.doh_timeout_seconds_summary
import me.lovehan1me.doh_timeout_seconds
import me.lovehan1me.doh_test_result
import me.lovehan1me.doh_settings
import me.lovehan1me.doh_preset
import me.lovehan1me.doh_custom_url
import me.lovehan1me.doh_bootstrap_ips_summary
import me.lovehan1me.doh_bootstrap_ips
import me.lovehan1me.direct
import me.lovehan1me.custom_mirror_site_hint
import me.lovehan1me.custom_mirror_site
import me.lovehan1me.custom_mirror_api_path_root_summary
import me.lovehan1me.custom_mirror_api_path_root
import me.lovehan1me.custom_mirror_api_path_mode
import me.lovehan1me.custom_mirror_api_path_follow_home_summary
import me.lovehan1me.custom_mirror_api_path_follow_home
import me.lovehan1me.custom_hosts_format
import me.lovehan1me.custom_hosts_empty_summary
import me.lovehan1me.custom_hosts
import me.lovehan1me.custom
import me.lovehan1me.current_node_latency
import me.lovehan1me.confirm
import me.lovehan1me.cancel
import me.lovehan1me.network
import me.lovehan1me.debug
import me.lovehan1me.builtin_dns
import me.lovehan1me.ic_delay
import me.lovehan1me.ic_dns
import me.lovehan1me.ic_domain
import me.lovehan1me.ic_edit_square
import me.lovehan1me.ic_hosts
import me.lovehan1me.ic_router
import me.lovehan1me.ic_vpn
import me.lovehan1me.logic.network.DohConfig
import me.lovehan1me.logic.network.HProxyTypes
import me.lovehan1me.ui.component.ChoiceDialog
import me.lovehan1me.ui.component.SettingNavigationItem
import me.lovehan1me.ui.component.SettingSwitchItem
import me.lovehan1me.ui.component.SettingsSectionTitle
import me.lovehan1me.ui.component.SettingsSegmentedGroup
import me.lovehan1me.ui.component.lazy.LazyColumn

data class NetworkSettingsUiState(
    val domainName: String,
    val domainDisplay: String,
    val proxySummary: String,
    val useBuiltInHosts: Boolean,
    val useCustomMirrorSite: Boolean,
    val customMirrorSite: String,
    val appendCustomMirrorPath: Boolean,
    val useDoH: Boolean,
    val dohSummary: String,
    val delaySummary: String,
)

data class DelayResultUi(
    val ip: String,
    val delay: Int,
)

data class DohTestResultUi(
    val host: String,
    val ips: List<String>,
    val delay: Int,
    val message: String,
)

enum class ProxyTypeOption(val value: Int) {
    Direct(HProxyTypes.TYPE_DIRECT),
    System(HProxyTypes.TYPE_SYSTEM),
    Http(HProxyTypes.TYPE_HTTP),
    Socks(HProxyTypes.TYPE_SOCKS),
}

@Composable
fun NetworkSettingsScreen(
    state: NetworkSettingsUiState,
    domainOptions: List<Pair<String, String>>,
    currentHost: String,
    delayResults: List<DelayResultUi>,
    isDelayTesting: Boolean,
    proxyType: Int,
    proxyIp: String,
    proxyPort: Int,
    dohEnabled: Boolean,
    dohPreset: String,
    dohCustomUrl: String,
    dohBootstrapIps: String,
    dohTimeoutSeconds: Int,
    dohTestResults: List<DohTestResultUi>,
    isDohTesting: Boolean,
    useCustomMirrorSite: Boolean,
    customMirrorSite: String,
    appendCustomMirrorPath: Boolean,
    customMirrorTestResult: String?,
    isCustomMirrorTesting: Boolean,
    onDomainChange: (String) -> Unit,
    onSaveCustomMirrorSite: (Boolean, String, Boolean) -> Unit,
    onTestCustomMirrorSite: (String, Boolean) -> Unit,
    onUseBuiltInHostsChange: (Boolean) -> Unit,
    onSaveCustomHosts: (String) -> Unit,
    onSaveDohSettings: (Boolean, String, String, String, Int) -> Unit,
    onOpenDelayTest: () -> Unit,
    customHostsData: String,
    onOpenDohTest: () -> Unit,
    onDismissDelayTest: () -> Unit,
    onDismissDohTest: () -> Unit,
    onApplyProxy: (Int, String, Int) -> Unit,
    embedded: Boolean = false,
) {
    var showDomainDialog by rememberSaveable { mutableStateOf(false) }
    var showProxyDialog by rememberSaveable { mutableStateOf(false) }
    var showDohDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomHostsDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomMirrorSiteDialog by rememberSaveable { mutableStateOf(false) }

    if (showDomainDialog) {
        NetworkChoiceDialog(
            title = stringResource(Res.string.domain_name),
            selectedValue = state.domainName,
            options = domainOptions,
            onDismiss = { showDomainDialog = false },
            onSelect = {
                showDomainDialog = false
                onDomainChange(it)
            },
        )
    }

    if (showProxyDialog) {
        ProxyDialog(
            initialType = proxyType,
            initialIp = proxyIp,
            initialPort = proxyPort,
            onDismiss = { showProxyDialog = false },
            onConfirm = { type, ip, port ->
                showProxyDialog = false
                onApplyProxy(type, ip, port)
            },
        )
    }

    if (showDohDialog) {
        DohDialog(
            enabled = dohEnabled,
            preset = dohPreset,
            customUrl = dohCustomUrl,
            bootstrapIps = dohBootstrapIps,
            timeoutSeconds = dohTimeoutSeconds,
            onDismiss = { showDohDialog = false },
            onConfirm = { enabled, preset, url, bootstrapIps, timeout ->
                showDohDialog = false
                onSaveDohSettings(enabled, preset, url, bootstrapIps, timeout)
            },
        )
    }

    if (showCustomHostsDialog) {
        CustomHostsDialog(
            currentData = customHostsData,
            onDismiss = { showCustomHostsDialog = false },
            onConfirm = { data ->
                showCustomHostsDialog = false
                onSaveCustomHosts(data)
            },
        )
    }

    if (showCustomMirrorSiteDialog) {
        CustomMirrorSiteDialog(
            enabled = useCustomMirrorSite,
            currentUrl = customMirrorSite,
            appendPath = appendCustomMirrorPath,
            testResult = customMirrorTestResult,
            isTesting = isCustomMirrorTesting,
            onDismiss = { showCustomMirrorSiteDialog = false },
            onConfirm = { enabled, url, appendPath ->
                showCustomMirrorSiteDialog = false
                onSaveCustomMirrorSite(enabled, url, appendPath)
            },
            onTest = onTestCustomMirrorSite,
        )
    }

    if (isDelayTesting) {
        DelayTestDialog(
            currentHost = currentHost,
            results = delayResults,
            onDismiss = onDismissDelayTest,
        )
    }

    if (isDohTesting) {
        DohTestDialog(
            currentHost = currentHost,
            results = dohTestResults,
            onDismiss = onDismissDohTest,
        )
    }

    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (embedded) {
                SettingsSectionTitle(titleRes = Res.string.network)
            }
            SettingsSegmentedGroup {
                SettingNavigationItem(
                    title = stringResource(Res.string.domain_name),
                    valueText = state.domainDisplay,
                    iconRes = Res.drawable.ic_domain,
                    onClick = { showDomainDialog = true },
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.custom_mirror_site),
                    summary = if (useCustomMirrorSite && customMirrorSite.isNotBlank()) customMirrorSite else stringResource(Res.string.custom_mirror_site_hint),
                    iconRes = Res.drawable.ic_domain,
                    onClick = { showCustomMirrorSiteDialog = true },
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.proxy),
                    summary = state.proxySummary,
                    iconRes = Res.drawable.ic_vpn,
                    onClick = { showProxyDialog = true },
                )
            }

            SettingsSectionTitle(titleRes = Res.string.builtin_dns)
            SettingsSegmentedGroup {
                SettingSwitchItem(
                    title = stringResource(Res.string.use_built_in_hosts),
                    summary = stringResource(Res.string.use_built_in_hosts_summary),
                    checked = state.useBuiltInHosts,
                    iconRes = Res.drawable.ic_hosts,
                    onCheckedChange = onUseBuiltInHostsChange,
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.custom_hosts),
                    summary = if (customHostsData.isBlank()) stringResource(Res.string.custom_hosts_empty_summary) else customHostsData.take(60),
                    iconRes = Res.drawable.ic_edit_square,
                    onClick = { showCustomHostsDialog = true },
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.use_doh),
                    summary = state.dohSummary,
                    iconRes = Res.drawable.ic_dns,
                    onClick = { showDohDialog = true },
                )
            }

            SettingsSectionTitle(titleRes = Res.string.debug)
            SettingsSegmentedGroup {
                SettingNavigationItem(
                    title = stringResource(Res.string.view_node_latency),
                    summary = state.delaySummary,
                    iconRes = Res.drawable.ic_delay,
                    onClick = onOpenDelayTest,
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.test_doh),
                    summary = stringResource(Res.string.test_doh_summary),
                    iconRes = Res.drawable.ic_router,
                    onClick = onOpenDohTest,
                )
            }
        }
    }
    if (embedded) {
        content()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            enableItemAnimation = false,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { content() }
        }
    }
}

@Composable
private fun NetworkChoiceDialog(
    title: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ChoiceDialog(
        title = title,
        options = options,
        selectedValue = selectedValue,
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun ProxyDialog(
    initialType: Int,
    initialIp: String,
    initialPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Int) -> Unit,
) {
    var selectedType by rememberSaveable(initialType) {
        mutableStateOf(
            ProxyTypeOption.entries.firstOrNull { it.value == initialType }
                ?: ProxyTypeOption.System
        )
    }
    var ip by rememberSaveable(initialIp) { mutableStateOf(initialIp) }
    var portText by rememberSaveable(initialPort) {
        mutableStateOf(initialPort.takeIf { it >= 0 }?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.proxy)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProxyTypeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedType == option,
                                onClick = { selectedType = option },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(
                            checked = selectedType == option,
                            onCheckedChange = null,
                        )
                        Text(
                            when (option) {
                                ProxyTypeOption.Direct -> stringResource(Res.string.direct)
                                ProxyTypeOption.System -> stringResource(Res.string.system_proxy)
                                ProxyTypeOption.Http -> stringResource(Res.string.http)
                                ProxyTypeOption.Socks -> stringResource(Res.string.socks)
                            }
                        )
                    }
                }
                val editable =
                    selectedType == ProxyTypeOption.Http || selectedType == ProxyTypeOption.Socks
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    enabled = editable,
                    label = { Text(stringResource(Res.string.host_or_ipv4)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    enabled = editable,
                    label = { Text(stringResource(Res.string.port)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedType.value, ip, portText.toIntOrNull() ?: -1)
                }
            ) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun DelayTestDialog(
    currentHost: String,
    results: List<DelayResultUi>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.current_node_latency)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = currentHost,
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    enableItemAnimation = false,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.ip }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.ip)
                            Text(
                                text = if (item.delay >= 0) "${item.delay} ms" else stringResource(Res.string.network_timeout_text),
                                color = when (item.delay) {
                                    in 0 until 100 -> Color(0xFF4CAF50)
                                    in 100..500 -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun DohTestDialog(
    currentHost: String,
    results: List<DohTestResultUi>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.doh_test_result)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = currentHost,
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    enableItemAnimation = false,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.host }) { item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(item.host)
                            Text(
                                text = item.message.ifBlank {
                                    if (item.delay >= 0) {
                                        "${item.delay} ms"
                                    } else {
                                        stringResource(Res.string.network_timeout_text)
                                    }
                                },
                                color = when (item.delay) {
                                    in 0 until 100 -> Color(0xFF4CAF50)
                                    in 100..500 -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                },
                            )
                            if (item.ips.isNotEmpty()) {
                                Text(
                                    text = item.ips.joinToString(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun DohDialog(
    enabled: Boolean,
    preset: String,
    customUrl: String,
    bootstrapIps: String,
    timeoutSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String, String, String, Int) -> Unit,
) {
    var dohEnabled by rememberSaveable(enabled) { mutableStateOf(enabled) }
    var presetValue by rememberSaveable(preset) {
        mutableStateOf(preset.ifBlank { DohConfig.presets.first().key })
    }
    var customValue by rememberSaveable(customUrl) { mutableStateOf(customUrl) }
    var bootstrapValue by rememberSaveable(bootstrapIps) { mutableStateOf(bootstrapIps) }
    var timeoutValue by rememberSaveable(timeoutSeconds) { mutableStateOf(timeoutSeconds.toString()) }
    var showPresetDialog by rememberSaveable { mutableStateOf(false) }

    if (showPresetDialog) {
        NetworkChoiceDialog(
            title = stringResource(Res.string.doh_preset),
            selectedValue = presetValue,
            options = DohConfig.presets.map { it.title to it.key } + (stringResource(Res.string.custom) to "custom"),
            onDismiss = { showPresetDialog = false },
            onSelect = {
                presetValue = it
                showPresetDialog = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.doh_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = dohEnabled, onClick = { dohEnabled = !dohEnabled })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(checked = dohEnabled, onCheckedChange = null)
                    Text(stringResource(Res.string.use_doh))
                }

                SettingNavigationItem(
                    title = stringResource(Res.string.doh_preset),
                    valueText = DohConfig.presets.firstOrNull { it.key == presetValue }?.title
                        ?: stringResource(Res.string.custom),
                    iconRes = Res.drawable.ic_domain,
                    onClick = { showPresetDialog = true },
                )

                OutlinedTextField(
                    value = customValue,
                    onValueChange = { customValue = it },
                    label = { Text(stringResource(Res.string.doh_custom_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = bootstrapValue,
                    onValueChange = { bootstrapValue = it },
                    label = { Text(stringResource(Res.string.doh_bootstrap_ips)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(Res.string.doh_bootstrap_ips_summary)) },
                )

                OutlinedTextField(
                    value = timeoutValue,
                    onValueChange = { timeoutValue = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(Res.string.doh_timeout_seconds)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(Res.string.doh_timeout_seconds_summary)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    dohEnabled,
                    presetValue,
                    customValue.trim(),
                    bootstrapValue.trim(),
                    timeoutValue.toIntOrNull()?.coerceIn(1, 60) ?: 10,
                )
            }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun CustomHostsDialog(
    currentData: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable(currentData) { mutableStateOf(currentData) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.custom_hosts)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.host_or_ipv4)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(Res.string.custom_hosts_format)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun CustomMirrorSiteDialog(
    enabled: Boolean,
    currentUrl: String,
    appendPath: Boolean,
    testResult: String?,
    isTesting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String, Boolean) -> Unit,
    onTest: (String, Boolean) -> Unit,
) {
    var customEnabled by rememberSaveable(enabled) { mutableStateOf(enabled) }
    var text by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }
    var appendPathToApi by rememberSaveable(appendPath) { mutableStateOf(appendPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.custom_mirror_site)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = customEnabled, onClick = { customEnabled = !customEnabled })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(checked = customEnabled, onCheckedChange = null)
                    Text(stringResource(Res.string.enable_custom_mirror_site))
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.custom_mirror_site)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(Res.string.custom_mirror_site_hint)) },
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(Res.string.custom_mirror_api_path_mode),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    CustomMirrorPathModeOption(
                        selected = appendPathToApi,
                        title = stringResource(Res.string.custom_mirror_api_path_follow_home),
                        summary = stringResource(Res.string.custom_mirror_api_path_follow_home_summary),
                        onClick = { appendPathToApi = true },
                    )
                    CustomMirrorPathModeOption(
                        selected = !appendPathToApi,
                        title = stringResource(Res.string.custom_mirror_api_path_root),
                        summary = stringResource(Res.string.custom_mirror_api_path_root_summary),
                        onClick = { appendPathToApi = false },
                    )
                }

                Button(
                    enabled = text.isNotBlank() && !isTesting,
                    onClick = { onTest(text.trim(), appendPathToApi) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.test_connection))
                }

                if (isTesting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                testResult?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(customEnabled, text.trim(), appendPathToApi) }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun CustomMirrorPathModeOption(
    selected: Boolean,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
