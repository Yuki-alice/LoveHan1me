package lovehan1me.app.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lovehan1me.Res
import lovehan1me.cf_cdp_failed_title
import lovehan1me.cf_cdp_locating
import lovehan1me.cf_cdp_waiting
import lovehan1me.cf_manual_cookie_hint
import lovehan1me.cf_manual_open_browser
import lovehan1me.cf_manual_retry
import lovehan1me.cf_manual_submit
import lovehan1me.complete_cloudflare_verification
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI

/**
 * 阶段一⑩：桌面 CF 人机验证——独立弹窗 + CDP 无头浏览器自动验证。
 *
 * 形态（用户要的"独立窗口弹窗"）：Compose Desktop 在 App 内再开 `Window`，
 * 与主窗口互不干扰；验证结束关窗，调用方回退路由重试原请求。
 *
 * 自动验证走 [CloudflareCdp]：探活本机 Chrome/Edge → 临时 profile 无头启动 →
 * CDP 建页导航 → 轮询 `Network.getAllCookies` 收割 `cf_*` → 写回
 * CookieJar + DataStore。与旧 KCEF 窗同语义，但**零下载**（KCEF 200MB
 * 运行时已删除）：本机无浏览器 / 启动失败 / 120 秒未通过 → 切手动兜底面板。
 *
 * 手动兜底（与旧版同）：① 用系统浏览器打开验证页；② 粘贴 `cf_clearance`
 * 写回 DataStore（`HCookieJar.loadForRequest` 在 host 匹配时叠加，
 * 后续请求自动携带）。
 */
@Composable
fun CloudflareVerificationWindow(
    url: String,
    host: String,
    onPassed: () -> Unit,
) {
    var open by remember { mutableStateOf(true) }
    var phase by remember { mutableStateOf<CdpWindowPhase>(CdpWindowPhase.Locating) }
    var waitText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val locatingText = stringResource(Res.string.cf_cdp_locating)
    val waitingText = stringResource(Res.string.cf_cdp_waiting)

    fun runSolve() {
        scope.launch {
            phase = CdpWindowPhase.Locating
            // 探活是同步文件检查，仍放后台线程做，避免卡 UI
            val browser = withContext(Dispatchers.IO) {
                CloudflareCdp.findBrowser()
            }
            if (browser == null) {
                phase = CdpWindowPhase.Manual("本机未找到 Chrome / Edge 浏览器")
                return@launch
            }
            phase = CdpWindowPhase.Verifying
            waitText = null
            when (val result = CloudflareCdp.solve(url, onStage = { stage ->
                waitText = when (stage) {
                    "starting" -> locatingText
                    else -> waitingText
                }
            })) {
                is CloudflareCdp.SolveResult.Solved -> {
                    CloudflareCdp.persistSolvedCookies(host, result.cookies)
                    open = false
                    onPassed()
                }

                is CloudflareCdp.SolveResult.NoBrowser ->
                    phase = CdpWindowPhase.Manual("本机未找到 Chrome / Edge 浏览器")

                is CloudflareCdp.SolveResult.Timeout ->
                    phase = CdpWindowPhase.Manual("120 秒内未通过验证（可能需要人工点选）")

                is CloudflareCdp.SolveResult.Failed ->
                    phase = CdpWindowPhase.Manual(result.reason)
            }
        }
    }

    LaunchedEffect(url) {
        runSolve()
    }

    if (open) {
        androidx.compose.ui.window.Window(
            onCloseRequest = { open = false },
            title = "Cloudflare 人机验证",
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (val current = phase) {
                    is CdpWindowPhase.Locating -> {
                        StatusPane(
                            title = stringResource(Res.string.complete_cloudflare_verification),
                            body = locatingText,
                            onUseManual = { phase = CdpWindowPhase.Manual("已切换为手动方式") },
                        )
                    }

                    is CdpWindowPhase.Verifying -> {
                        StatusPane(
                            title = stringResource(Res.string.complete_cloudflare_verification),
                            body = waitText ?: waitingText,
                            onUseManual = { phase = CdpWindowPhase.Manual("已切换为手动方式") },
                        )
                    }

                    is CdpWindowPhase.Manual -> {
                        ManualFallbackPane(
                            url = url,
                            host = host,
                            reason = current.reason,
                            onRetry = { runSolve() },
                            onPassed = {
                                open = false
                                onPassed()
                            },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface CdpWindowPhase {
    data object Locating : CdpWindowPhase
    data object Verifying : CdpWindowPhase
    data class Manual(val reason: String) : CdpWindowPhase
}

/** 自动验证进行中：给出状态，并允许随时改走手动兜底。 */
@Composable
private fun StatusPane(
    title: String,
    body: String,
    onUseManual: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onUseManual) {
            Text(stringResource(Res.string.cf_manual_open_browser))
        }
    }
}

/**
 * 手动兜底：自动验证不可用时的自救路径。
 *
 * 写回通道只需 DataStore —— `HCookieJar.loadForRequest` 在
 * `cloudFlareCookieHost == host` 时会叠加该 Cookie，后续请求自动携带。
 */
@Composable
private fun ManualFallbackPane(
    url: String,
    host: String,
    reason: String,
    onRetry: () -> Unit,
    onPassed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var browserOpened by remember { mutableStateOf(false) }
    var browserError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.cf_cdp_failed_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = reason, style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = {
                val ok = runCatching {
                    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        false
                    } else {
                        Desktop.getDesktop().browse(URI(url)); true
                    }
                }.getOrDefault(false)
                browserOpened = ok
                browserError = if (ok) null else "无法调起系统浏览器，请手动复制上方网址打开"
            },
        ) {
            Text(stringResource(Res.string.cf_manual_open_browser))
        }

        if (browserOpened) {
            Text(url, style = MaterialTheme.typography.bodySmall)
        }
        browserError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Text(
            text = stringResource(Res.string.cf_manual_cookie_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = false,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 200.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))

        Button(
            enabled = input.contains('='),
            onClick = {
                val cookie = input.trim()
                scope.launch {
                    lovehan1me.data.SettingsRepository.setCloudFlareCookie(cookie, host)
                    onPassed()
                }
            },
        ) {
            Text(stringResource(Res.string.cf_manual_submit))
        }

        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.cf_manual_retry))
        }
    }
}
