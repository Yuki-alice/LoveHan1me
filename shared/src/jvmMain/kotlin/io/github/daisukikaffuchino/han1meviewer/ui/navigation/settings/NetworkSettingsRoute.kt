package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import io.github.daisukikaffuchino.utils.LogUtil
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.daisukikaffuchino.han1meviewer.EMPTY_STRING
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.alternative
import io.github.daisukikaffuchino.han1meviewer.custom
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_test_failed
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_test_failed_http
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_test_parse_failed
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_test_partial_success
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_test_success
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_watch_test_failed
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_watch_test_failed_http
import io.github.daisukikaffuchino.han1meviewer.default_
import io.github.daisukikaffuchino.han1meviewer.direct
import io.github.daisukikaffuchino.han1meviewer.doh_conflict_message
import io.github.daisukikaffuchino.han1meviewer.doh_disabled_summary
import io.github.daisukikaffuchino.han1meviewer.http_proxy
import io.github.daisukikaffuchino.han1meviewer.invalid_ip_or_port
import io.github.daisukikaffuchino.han1meviewer.loading
import io.github.daisukikaffuchino.han1meviewer.node_latency_sum
import io.github.daisukikaffuchino.han1meviewer.socks_proxy
import io.github.daisukikaffuchino.han1meviewer.system_proxy
import io.github.daisukikaffuchino.han1meviewer.unknow
import io.github.daisukikaffuchino.han1meviewer.warning
import io.github.daisukikaffuchino.han1meviewer.restart_or_not_working
import io.github.daisukikaffuchino.han1meviewer.network_timeout_text
import io.github.daisukikaffuchino.han1meviewer.mpv_socks5_warning
import io.github.daisukikaffuchino.han1meviewer.domain_change_tips
import io.github.daisukikaffuchino.han1meviewer.doh_conflict_message
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_warning
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_testing
import io.github.daisukikaffuchino.han1meviewer.custom_mirror_site_invalid
import io.github.daisukikaffuchino.han1meviewer.confirm
import io.github.daisukikaffuchino.han1meviewer.cancel
import io.github.daisukikaffuchino.han1meviewer.attention
import io.github.daisukikaffuchino.han1meviewer.logic.Parser
import io.github.daisukikaffuchino.han1meviewer.logic.network.DohConfig
import io.github.daisukikaffuchino.han1meviewer.logic.network.HDns
import io.github.daisukikaffuchino.han1meviewer.logic.network.HProxySelector
import io.github.daisukikaffuchino.han1meviewer.logic.network.HanimeNetwork
import io.github.daisukikaffuchino.han1meviewer.logic.network.ServiceCreator
import io.github.daisukikaffuchino.han1meviewer.logic.state.WebsiteState
import io.github.daisukikaffuchino.han1meviewer.logout
import io.github.daisukikaffuchino.han1meviewer.ui.component.ConfirmDialog
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.DelayResultUi
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.DohTestResultUi
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.NetworkSettingsScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.NetworkSettingsUiState
import io.github.daisukikaffuchino.utils.SonnerToast
import okhttp3.Request
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.daisukikaffuchino.han1meviewer.logic.currentEpochMillis
import io.github.daisukikaffuchino.han1meviewer.logic.platform.restartApp
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.runBlocking

private enum class DohConflictTarget {
    EnableDoH,
    EnableBuiltInHosts,
}

@Composable
actual fun NetworkSettingsRouteScreen(embedded: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    var currentHost by remember { mutableStateOf(SettingsRepository.baseUrl) }
    var isDelayTesting by remember { mutableStateOf(false) }
    var isDohTesting by remember { mutableStateOf(false) }
    var isCustomMirrorTesting by remember { mutableStateOf(false) }
    var customMirrorTestResult by remember { mutableStateOf<String?>(null) }
    var showDomainRestartConfirm by remember { mutableStateOf(false) }
    var showHostsRestartConfirm by remember { mutableStateOf(false) }
    var showCustomHostsValidationError by remember { mutableStateOf<List<String>?>(null) }
    var showCustomMirrorValidationError by remember { mutableStateOf(false) }
    var showCustomMirrorWarningConfirm by remember { mutableStateOf(false) }
    var showDohConflictConfirm by remember { mutableStateOf(false) }
    var showSocksWarning by remember { mutableStateOf(false) }
    var pendingDomainValue by remember { mutableStateOf("") }
    var pendingUseCustomMirrorSite by remember { mutableStateOf(SettingsRepository.useCustomMirrorSite) }
    var pendingCustomMirrorSite by remember { mutableStateOf(SettingsRepository.customMirrorSite) }
    var pendingAppendCustomMirrorPath by remember { mutableStateOf(SettingsRepository.appendCustomMirrorPath) }
    var pendingDohConflictTarget by remember { mutableStateOf(DohConflictTarget.EnableDoH) }
    var pendingDohEnabled by remember { mutableStateOf(SettingsRepository.useDoH) }
    var pendingDohPreset by remember { mutableStateOf(SettingsRepository.dohPreset) }
    var pendingDohCustomUrl by remember { mutableStateOf(SettingsRepository.dohCustomUrl) }
    var pendingDohBootstrapIps by remember { mutableStateOf(SettingsRepository.dohBootstrapIps) }
    var pendingDohTimeoutSeconds by remember { mutableIntStateOf(SettingsRepository.dohTimeoutSeconds) }
    val delayResults = remember { mutableStateListOf<DelayResultUi>() }
    val dohTestResults = remember { mutableStateListOf<DohTestResultUi>() }
    val networkTimeoutText = stringResource(Res.string.network_timeout_text)
    // P6d-3-C2：以下 builder/后台回调在非 @Composable 上下文（remember{}/executor），字符串在此预解析后传入
    val unknownText = stringResource(Res.string.unknow)
    val domainDefaultText = stringResource(Res.string.default_)
    val domainAlternativeText = stringResource(Res.string.alternative)
    val directText = stringResource(Res.string.direct)
    val systemProxyText = stringResource(Res.string.system_proxy)
    val httpProxyTemplate = stringResource(Res.string.http_proxy)
    val socksProxyTemplate = stringResource(Res.string.socks_proxy)
    val nodeLatencyText = stringResource(Res.string.node_latency_sum)
    val dohDisabledText = stringResource(Res.string.doh_disabled_summary)
    val dohConflictText = stringResource(Res.string.doh_conflict_message)
    val customText = stringResource(Res.string.custom)
    val failedHttpTemplate = stringResource(Res.string.custom_mirror_site_test_failed_http)
    val successTemplate = stringResource(Res.string.custom_mirror_site_test_success)
    val partialTemplate = stringResource(Res.string.custom_mirror_site_test_partial_success)
    val parseFailedTemplate = stringResource(Res.string.custom_mirror_site_test_parse_failed)
    val failedTemplate = stringResource(Res.string.custom_mirror_site_test_failed)
    val watchFailedHttpTemplate = stringResource(Res.string.custom_mirror_site_watch_test_failed_http)
    val watchFailedTemplate = stringResource(Res.string.custom_mirror_site_watch_test_failed)
    val loadingText = stringResource(Res.string.loading)
    val uiState = remember(
        settings, unknownText, domainDefaultText, domainAlternativeText, directText,
        systemProxyText, httpProxyTemplate, socksProxyTemplate, nodeLatencyText,
        dohDisabledText, dohConflictText, customText,
    ) {
        buildNetworkSettingsUiState(
            domainDefaultText, domainAlternativeText, directText, systemProxyText,
            httpProxyTemplate, socksProxyTemplate, nodeLatencyText,
            dohDisabledText, dohConflictText, customText,
        )
    }
    val customMirrorInvalidText = stringResource(Res.string.custom_mirror_site_invalid)
    val customMirrorTestingText = stringResource(Res.string.custom_mirror_site_testing)
    fun stopDelayTest() {
        isDelayTesting = false
    }

    fun stopDohTest() {
        isDohTesting = false
    }

    fun measureDelay(ip: String): Int {
        return try {
            val start = currentEpochMillis()
            val address = InetAddress.getByName(ip)
            val reachable = address.isReachable(2000)
            if (reachable) (currentEpochMillis() - start).toInt() else -1
        } catch (_: Exception) {
            -1
        }
    }

    fun scheduleNextTest(ipList: List<String>) {
        // P6d-4：原 Handler.postDelayed 轮询链改协程循环；IO 线程直写 Compose 快照状态（合法）
        if (!isDelayTesting) return
        coroutineScope.launch(Dispatchers.IO) {
            while (isDelayTesting) {
                for (ip in ipList) {
                    if (!isDelayTesting) return@launch
                    val delay = measureDelay(ip)
                    val index = delayResults.indexOfFirst { it.ip == ip }
                    if (index >= 0) {
                        delayResults[index] = DelayResultUi(ip, delay)
                    }
                }
                delay(2000L)
            }
        }
    }

    fun runDohTest() {
        if (isDohTesting) return
        val host = SimpleUri(SettingsRepository.baseUrl).host ?: unknownText
        currentHost = SettingsRepository.baseUrl
        dohTestResults.clear()
        isDohTesting = true
        coroutineScope.launch(Dispatchers.IO) {
            val start = currentEpochMillis()
            val result = runCatching { HDns().lookupByDoHOnly(host) }
            val delay = (currentEpochMillis() - start).toInt()
            dohTestResults.clear()
            result.onSuccess { list ->
                    dohTestResults.add(
                        DohTestResultUi(
                            host = host,
                            ips = list.mapNotNull { it.hostAddress }.distinct(),
                            delay = delay,
                            message = "",
                        )
                    )
                }.onFailure { throwable ->
                    LogUtil.w("DOH_TEST", "lookup failed for $host: ${throwable.message}")
                    dohTestResults.add(
                        DohTestResultUi(
                            host = host,
                            ips = emptyList(),
                            delay = -1,
                            message = throwable.message?.ifBlank { networkTimeoutText }
                                ?: networkTimeoutText,
                        )
                    )
                }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopDelayTest()
            stopDohTest()
        }
    }

    NetworkSettingsScreen(
        state = uiState,
        domainOptions = buildDomainOptions(domainDefaultText, domainAlternativeText),
        currentHost = currentHost,
        delayResults = delayResults,
        dohTestResults = dohTestResults,
        isDelayTesting = isDelayTesting,
        isDohTesting = isDohTesting,
        proxyType = SettingsRepository.proxyType,
        proxyIp = SettingsRepository.proxyIp,
        proxyPort = SettingsRepository.proxyPort,
        dohEnabled = SettingsRepository.useDoH,
        dohPreset = SettingsRepository.dohPreset,
        dohCustomUrl = SettingsRepository.dohCustomUrl,
        dohBootstrapIps = SettingsRepository.dohBootstrapIps,
        dohTimeoutSeconds = SettingsRepository.dohTimeoutSeconds,
        useCustomMirrorSite = SettingsRepository.useCustomMirrorSite,
        customMirrorSite = SettingsRepository.customMirrorSite,
        appendCustomMirrorPath = SettingsRepository.appendCustomMirrorPath,
        customMirrorTestResult = customMirrorTestResult,
        isCustomMirrorTesting = isCustomMirrorTesting,
        onDomainChange = { newValue ->
            val origin = SettingsRepository.baseUrl
            if (newValue != origin) {
                pendingDomainValue = newValue
                pendingUseCustomMirrorSite = false
                pendingCustomMirrorSite = SettingsRepository.customMirrorSite
                pendingAppendCustomMirrorPath = SettingsRepository.appendCustomMirrorPath
                showDomainRestartConfirm = true
            }
        },
        onSaveCustomMirrorSite = { enabled, url, appendPath ->
            val normalizedUrl = normalizeCustomMirrorSite(url)
            if (enabled && normalizedUrl == null) {
                showCustomMirrorValidationError = true
                return@NetworkSettingsScreen
            }
            val customMirrorSite = normalizedUrl.orEmpty()
            if (enabled != SettingsRepository.useCustomMirrorSite ||
                customMirrorSite != SettingsRepository.customMirrorSite ||
                appendPath != SettingsRepository.appendCustomMirrorPath
            ) {
                pendingUseCustomMirrorSite = enabled
                pendingCustomMirrorSite = customMirrorSite
                pendingAppendCustomMirrorPath = appendPath
                if (enabled) {
                    showCustomMirrorWarningConfirm = true
                } else {
                    showDomainRestartConfirm = true
                }
            }
        },
        onTestCustomMirrorSite = { url, appendPath ->
            val normalizedUrl = normalizeCustomMirrorSite(url)
            if (normalizedUrl == null) {
                customMirrorTestResult = customMirrorInvalidText
                return@NetworkSettingsScreen
            }
            if (isCustomMirrorTesting) return@NetworkSettingsScreen
            isCustomMirrorTesting = true
            customMirrorTestResult = customMirrorTestingText
            coroutineScope.launch(Dispatchers.IO) {
                val result = testCustomMirrorSite(
                    normalizedUrl,
                    appendPath,
                    failedHttpTemplate,
                    successTemplate,
                    partialTemplate,
                    parseFailedTemplate,
                    failedTemplate,
                    watchFailedHttpTemplate,
                    watchFailedTemplate,
                    loadingText,
                )
                customMirrorTestResult = result
                isCustomMirrorTesting = false
            }
        },
        onUseBuiltInHostsChange = { value ->
            if (value && SettingsRepository.useDoH) {
                showDohConflictConfirm = true
                pendingDohConflictTarget = DohConflictTarget.EnableBuiltInHosts
                return@NetworkSettingsScreen
            }
            coroutineScope.launch {
                SettingsRepository.update { it.copy(useBuiltInHosts = value) }
                showHostsRestartConfirm = true
            }
        },
        onSaveCustomHosts = { data ->
            val errors = HDns.validateCustomHosts(data)
            if (errors.isNotEmpty()) {
                showCustomHostsValidationError = errors
                return@NetworkSettingsScreen
            }
            coroutineScope.launch {
                SettingsRepository.update { it.copy(customHostsData = data) }
                if (SettingsRepository.useBuiltInHosts) HanimeNetwork.rebuildNetwork()
            }
        },
        customHostsData = SettingsRepository.customHostsData,
        onSaveDohSettings = { enabled, preset, url, bootstrapIps, timeoutSeconds ->
            pendingDohEnabled = enabled
            pendingDohPreset = preset
            pendingDohCustomUrl = url
            pendingDohBootstrapIps = bootstrapIps
            pendingDohTimeoutSeconds = timeoutSeconds
            if (enabled && SettingsRepository.useBuiltInHosts) {
                showDohConflictConfirm = true
                pendingDohConflictTarget = DohConflictTarget.EnableDoH
                return@NetworkSettingsScreen
            }
            coroutineScope.launch {
                SettingsRepository.update { it.copy(useDoH = enabled, dohPreset = preset, dohCustomUrl = url, dohBootstrapIps = bootstrapIps, dohTimeoutSeconds = timeoutSeconds.coerceIn(1, 60)) }
                currentHost = SettingsRepository.baseUrl
                HanimeNetwork.rebuildNetwork()
            }
        },
        onOpenDelayTest = {
            val host = SimpleUri(SettingsRepository.baseUrl).host ?: unknownText
            currentHost = SettingsRepository.baseUrl
            delayResults.clear()
            isDelayTesting = true
            coroutineScope.launch(Dispatchers.IO) {
                val ipList = HDns().getCDNList(host)
                LogUtil.i("delayTest", ipList.toString())
                delayResults.clear()
                delayResults.addAll(ipList.map { DelayResultUi(it, -1) })
                scheduleNextTest(ipList)
            }
        },
        onOpenDohTest = { runDohTest() },
        onDismissDelayTest = { stopDelayTest() },
        onDismissDohTest = { stopDohTest() },
        onApplyProxy = { type, ip, port ->
            val valid = when (type) {
                HProxySelector.TYPE_DIRECT, HProxySelector.TYPE_SYSTEM -> true
                HProxySelector.TYPE_HTTP, HProxySelector.TYPE_SOCKS -> HProxySelector.validateIp(ip) && HProxySelector.validatePort(
                    port
                )

                else -> false
            }
            if (!valid) {
                // P6d-3-C2 附带：回调内非 suspend，用 scope 桥 CMP getString（C3 同模式先行一处）
                coroutineScope.launch { SonnerToast.warning(getString(Res.string.invalid_ip_or_port)) }
                return@NetworkSettingsScreen
            }
            if (type == HProxySelector.TYPE_SOCKS) {
                showSocksWarning = true
            }
            coroutineScope.launch {
                SettingsRepository.update { it.copy(proxyType = io.github.daisukikaffuchino.han1meviewer.logic.model.ProxyType.fromId(type), proxyIp = ip, proxyPort = port) }
                HProxySelector.rebuildNetwork()
                HanimeNetwork.rebuildNetwork()
            }
        },
        embedded = embedded,
    )

    ConfirmDialog(
        visible = showDomainRestartConfirm,
        title = stringResource(Res.string.attention),
        message = stringResource(Res.string.domain_change_tips).trimIndent(),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        cancelable = false,
        onConfirm = {
            coroutineScope.launch {
                SettingsRepository.update {
                    it.copy(
                        domainName = pendingDomainValue.ifEmpty { it.domainName },
                        selectedBaseUrl = pendingDomainValue.ifEmpty { it.selectedBaseUrl },
                        useCustomMirrorSite = pendingUseCustomMirrorSite,
                        customMirrorSite = pendingCustomMirrorSite,
                        appendCustomMirrorPath = pendingAppendCustomMirrorPath,
                    )
                }
                logout()
                restartApp(killProcess = true)
            }
        },
        onDismiss = {
            pendingDomainValue = ""
            pendingUseCustomMirrorSite = SettingsRepository.useCustomMirrorSite
            pendingCustomMirrorSite = SettingsRepository.customMirrorSite
            pendingAppendCustomMirrorPath = SettingsRepository.appendCustomMirrorPath
            showDomainRestartConfirm = false
        },
    )

    if (showCustomMirrorValidationError) {
        AlertDialog(
            onDismissRequest = { showCustomMirrorValidationError = false },
            title = { Text(stringResource(Res.string.attention)) },
            text = { Text(stringResource(Res.string.custom_mirror_site_invalid)) },
            confirmButton = {
                TextButton(onClick = { showCustomMirrorValidationError = false }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
        )
    }

    ConfirmDialog(
        visible = showHostsRestartConfirm,
        title = stringResource(Res.string.attention),
        message = stringResource(Res.string.restart_or_not_working, EMPTY_STRING),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        cancelable = false,
        onConfirm = { restartApp(killProcess = true) },
        onDismiss = { showHostsRestartConfirm = false },
    )

    val validationErrors = showCustomHostsValidationError
    if (validationErrors != null) {
        AlertDialog(
            onDismissRequest = { showCustomHostsValidationError = null },
            title = { Text(stringResource(Res.string.attention)) },
            text = { Text(validationErrors.joinToString("\n")) },
            confirmButton = {
                TextButton(onClick = { showCustomHostsValidationError = null }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
        )
    }

    ConfirmDialog(
        visible = showCustomMirrorWarningConfirm,
        title = stringResource(Res.string.attention),
        message = stringResource(Res.string.custom_mirror_site_warning),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        cancelable = false,
        onConfirm = {
            showCustomMirrorWarningConfirm = false
            showDomainRestartConfirm = true
        },
        onDismiss = {
            pendingUseCustomMirrorSite = SettingsRepository.useCustomMirrorSite
            pendingCustomMirrorSite = SettingsRepository.customMirrorSite
            pendingAppendCustomMirrorPath = SettingsRepository.appendCustomMirrorPath
            showCustomMirrorWarningConfirm = false
        },
    )

    ConfirmDialog(
        visible = showDohConflictConfirm,
        title = stringResource(Res.string.attention),
        message = stringResource(Res.string.doh_conflict_message),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        cancelable = false,
        onConfirm = {
            coroutineScope.launch {
                SettingsRepository.update {
                    when (pendingDohConflictTarget) {
                        DohConflictTarget.EnableDoH -> it.copy(useBuiltInHosts = false, useDoH = pendingDohEnabled, dohPreset = pendingDohPreset, dohCustomUrl = pendingDohCustomUrl, dohBootstrapIps = pendingDohBootstrapIps, dohTimeoutSeconds = pendingDohTimeoutSeconds.coerceIn(1, 60))
                        DohConflictTarget.EnableBuiltInHosts -> it.copy(useDoH = false, useBuiltInHosts = true)
                    }
                }
                showDohConflictConfirm = false
                HanimeNetwork.rebuildNetwork()
            }
        },
        onDismiss = { showDohConflictConfirm = false },
    )

    ConfirmDialog(
        visible = showSocksWarning,
        title = stringResource(Res.string.warning),
        message = stringResource(Res.string.mpv_socks5_warning),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = { showSocksWarning = false },
        onDismiss = { showSocksWarning = false },
    )
}

private fun buildNetworkSettingsUiState(
    domainDefault: String,
    domainAlternative: String,
    direct: String,
    systemProxy: String,
    httpProxyTemplate: String,
    socksProxyTemplate: String,
    nodeLatency: String,
    dohDisabled: String,
    dohConflict: String,
    custom: String,
): NetworkSettingsUiState {
    return NetworkSettingsUiState(
        domainName = SettingsRepository.baseUrl,
        domainDisplay = buildDomainOptions(domainDefault, domainAlternative).firstOrNull { it.second == SettingsRepository.baseUrl }?.first
            ?: SettingsRepository.baseUrl,
        proxySummary = when (SettingsRepository.proxyType) {
            HProxySelector.TYPE_DIRECT -> direct
            HProxySelector.TYPE_SYSTEM -> systemProxy
            HProxySelector.TYPE_HTTP -> httpProxyTemplate
                .replace("%1\$s", SettingsRepository.proxyIp)
                .replace("%2\$d", SettingsRepository.proxyPort.toString())

            HProxySelector.TYPE_SOCKS -> socksProxyTemplate
                .replace("%1\$s", SettingsRepository.proxyIp)
                .replace("%2\$d", SettingsRepository.proxyPort.toString())

            else -> direct
        },
        useBuiltInHosts = SettingsRepository.useBuiltInHosts,
        useCustomMirrorSite = SettingsRepository.useCustomMirrorSite,
        customMirrorSite = SettingsRepository.customMirrorSite,
        appendCustomMirrorPath = SettingsRepository.appendCustomMirrorPath,
        useDoH = SettingsRepository.useDoH,
        dohSummary = buildDohSummary(dohDisabled, dohConflict, custom),
        delaySummary = nodeLatency,
    )
}

private fun normalizeCustomMirrorSite(url: String): String? {
    val trimmed = url.trim().trimEnd('/')
    val uri = SimpleUri(trimmed)
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
    if (uri.query.isNotEmpty() || uri.fragment.isNotEmpty()) return null
    return url.trim()
}

/**
 * P6d-4：本文件对 android.net.Uri 的用法子集（scheme/authority/host/query/fragment）的
 * 纯字符串切片等价实现——宽容解析、字段缺失返回空串/null、不抛异常。
 */
private class SimpleUri(url: String) {
    private val beforeFragment = url.substringBefore('#')
    val fragment: String = url.substringAfter('#', "")
    val query: String = beforeFragment.substringAfter('?', "")
    private val beforeQuery = beforeFragment.substringBefore('?')
    private val schemeCandidate = beforeQuery.substringBefore("://", missingDelimiterValue = "")
    val scheme: String? = schemeCandidate.takeIf { it.isNotEmpty() && it.length < beforeQuery.length }?.lowercase()
    private val afterScheme = if (scheme != null) beforeQuery.substringAfter("://") else beforeQuery
    val authority: String = afterScheme.substringBefore('/')
    val host: String? = authority
        .substringAfterLast('@', authority)
        .substringBefore(':')
        .takeIf { it.isNotBlank() }
        ?.lowercase()
}

private fun testCustomMirrorSite(
    homeUrl: String,
    appendPath: Boolean,
    failedHttpTemplate: String,
    successTemplate: String,
    partialTemplate: String,
    parseFailedTemplate: String,
    failedTemplate: String,
    watchFailedHttpTemplate: String,
    watchFailedTemplate: String,
    loadingText: String,
): String {
    return runCatching {
        val request = Request.Builder().url(homeUrl).get().build()
        ServiceCreator.hClient.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val body = response.body.string()
            if (!response.isSuccessful) {
                return failedHttpTemplate
                    .replace("%1\$d", response.code.toString())
                    .replace("%2\$s", finalUrl)
            }

            val apiBaseUrl = buildCustomMirrorApiBaseUrl(homeUrl, appendPath)
            val watchTestResult = testCustomMirrorWatchUrl(apiBaseUrl, watchFailedHttpTemplate, watchFailedTemplate)
            // P4：homePageVer2 因 composeResources getString 变为 suspend，此处同步桥接
            val parseResult = runBlocking { Parser.homePageVer2(body) }
            when (parseResult) {
                is WebsiteState.Success -> if (watchTestResult == null) {
                    successTemplate
                        .replace("%1\$s", finalUrl)
                        .replace("%2\$s", apiBaseUrl)
                } else {
                    partialTemplate
                        .replace("%1\$s", finalUrl)
                        .replace("%2\$s", apiBaseUrl)
                        .replace("%3\$s", watchTestResult)
                }

                is WebsiteState.Error -> parseFailedTemplate
                    .replace("%1\$s", finalUrl)
                    .replace(
                        "%2\$s",
                        parseResult.throwable.message ?: parseResult.throwable::class.java.simpleName,
                    )

                WebsiteState.Loading -> parseFailedTemplate
                    .replace("%1\$s", finalUrl)
                    .replace("%2\$s", loadingText)
            }
        }
    }.getOrElse { throwable ->
        failedTemplate
            .replace("%1\$s", throwable.message ?: throwable::class.java.simpleName)
    }
}

private fun testCustomMirrorWatchUrl(
    apiBaseUrl: String,
    failedHttpTemplate: String,
    failedTemplate: String,
): String? {
    return runCatching {
        val url = apiBaseUrl + "search"
        val request = Request.Builder().url(url).get().build()
        ServiceCreator.hClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                null
            } else {
                failedHttpTemplate
                    .replace("%1\$d", response.code.toString())
                    .replace("%2\$s", response.request.url.toString())
            }
        }
    }.getOrElse { throwable ->
        failedTemplate
            .replace("%1\$s", throwable.message ?: throwable::class.java.simpleName)
    }
}

private fun buildCustomMirrorApiBaseUrl(homeUrl: String, appendPath: Boolean): String {
    val url = if (appendPath) homeUrl else {
        val uri = SimpleUri(homeUrl)
        "${uri.scheme}://${uri.authority}"
    }
    return if (url.endsWith('/')) url else "$url/"
}

private fun buildDohSummary(dohDisabled: String, dohConflict: String, custom: String): String {
    if (!SettingsRepository.useDoH) return dohDisabled
    if (SettingsRepository.useBuiltInHosts) return dohConflict
    val core = if (SettingsRepository.dohPreset == "custom") {
        SettingsRepository.dohCustomUrl.ifBlank { custom }
    } else {
        DohConfig.selectedPreset().title
    }
    val bootstrap = DohConfig.bootstrapIps().takeIf { it.isNotEmpty() }?.joinToString()
    return if (bootstrap != null) "$core\nBootstrap: $bootstrap" else core
}
