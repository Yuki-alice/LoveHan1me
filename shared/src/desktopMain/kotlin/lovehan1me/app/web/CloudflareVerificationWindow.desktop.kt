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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFCookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lovehan1me.Res
import lovehan1me.cf_kcef_failed_title
import lovehan1me.cf_kcef_preparing
import lovehan1me.cf_manual_cookie_hint
import lovehan1me.cf_manual_open_browser
import lovehan1me.cf_manual_retry
import lovehan1me.cf_manual_submit
import lovehan1me.complete_cloudflare_verification
import lovehan1me.core.constant.DESKTOP_USER_AGENT
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.HCookieJar
import org.cef.network.CefCookieManager
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File
import java.net.URI

/** KCEF 运行时初始化状态。 */
sealed interface CloudflareKcefStatus {
    data object Idle : CloudflareKcefStatus
    data object Initializing : CloudflareKcefStatus
    data object Ready : CloudflareKcefStatus
    data class Failed(val reason: String) : CloudflareKcefStatus
}

/**
 * M5-5 / 阶段一⑩：桌面 CF 人机验证——独立弹窗 + 内嵌 WebView（KCEF/Chromium）。
 *
 * 用户要求的"独立 WebView 窗口弹窗"形态：Compose Desktop 支持在 App 内再开
 * `Window`，引擎随验证窗口按需初始化、关窗即弃，主窗口不受影响。
 *
 * UA 关键：`cf_clearance` 绑定 UA——WebView 的 UA 必须与 HTTP 层（OkHttp 引擎
 * 的 HCookieJar / 拦截器链）完全一致，统一用 [DESKTOP_USER_AGENT]。
 *
 * 验证判定：后台轮询 KCEF 全局 CookieManager，出现 `cf_*` cookie 即视为通过，
 * 全量转成 okhttp3.Cookie 写入 [HCookieJar.cookieMap] 与 DataStore
 * （`HCookieJar.loadForRequest` 会从 DataStore 叠加，故后续请求自动携带），
 * 随后关闭弹窗、由调用方回退路由以重试原请求。
 *
 * **自救降级（阶段一⑩核心）**：首次使用需下载/解包约 200MB 的 CEF 运行时
 * （落在工作目录 `kcef-bins/`），国内网络常失败。原实现在失败时只是
 * `printStackTrace`，用户会看到永远加载不出来的窗口 = 桌面端打不开应用。
 * 现在初始化的成功/失败/超时都会反馈到 UI，失败时提供两条自救路径：
 *   ① 用系统浏览器打开验证页；② 手动粘贴 `cf_clearance` 后写回 DataStore。
 */
@Composable
fun CloudflareVerificationWindow(
    url: String,
    host: String,
    onPassed: () -> Unit,
) {
    var open by remember { mutableStateOf(true) }
    var timedOut by remember { mutableStateOf(false) }
    var forceManual by remember { mutableStateOf(false) }
    val status by CloudflareKcef.status.collectAsState()
    val scope = rememberCoroutineScope()

    // 状态提升到条件分支之外：KCEF 尚未就绪时不组合 WebView，但状态对象始终存在。
    val webViewState = rememberWebViewState(url)
    val webViewNavigator = rememberWebViewNavigator()
    webViewState.webSettings.customUserAgentString = DESKTOP_USER_AGENT

    val ready = status == CloudflareKcefStatus.Ready
    val failureReason: String? = when {
        status is CloudflareKcefStatus.Failed -> (status as CloudflareKcefStatus.Failed).reason
        !ready && timedOut -> "初始化超时"
        else -> null
    }

    // 超时闸门：KCEF.initBlocking 是阻塞调用，无法 withTimeout 打断；
    // 故超时只影响 UI 呈现（转手动兜底），后台初始化继续跑，成功后会自行切回 WebView。
    LaunchedEffect(Unit) {
        delay(KCEF_INIT_TIMEOUT_MS)
        if (CloudflareKcef.status.value != CloudflareKcefStatus.Ready) timedOut = true
    }

    if (open) {
        androidx.compose.ui.window.Window(
            onCloseRequest = { open = false },
            title = "Cloudflare 人机验证",
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    ready -> {
                        Text(
                            text = "请在窗口内完成人机验证，通过后本窗口自动关闭并重试请求。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        )
                        WebView(
                            state = webViewState,
                            navigator = webViewNavigator,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    failureReason == null && !forceManual -> {
                        PreparingPane(onUseManual = { forceManual = true })
                    }

                    else -> ManualFallbackPane(
                        url = url,
                        host = host,
                        reason = failureReason ?: "已切换为手动方式",
                        onRetry = {
                            timedOut = false
                            forceManual = false
                            // 重置状态并重新跑一次初始化（首次失败多为网络问题，值得重试）
                            scope.launch { CloudflareKcef.retry() }
                        },
                        onPassed = {
                            open = false
                            onPassed()
                        },
                    )
                }
            }
        }
    }

    if (ready) {
        LaunchedEffect(url, host) {
            while (isActive) {
                delay(2_500)
                val manager = KCEFCookieManager(CefCookieManager.getGlobalManager())
                val cefCookies = runCatching {
                    manager.getCookiesWhileBlocking(url, true, 5_000L) { _, _ -> true }
                }.getOrNull() ?: continue
                if (cefCookies.any { it.name.startsWith("cf_") }) {
                    val okhttpCookies = cefCookies.mapNotNull { c ->
                        runCatching {
                            okhttp3.Cookie.Builder()
                                .name(c.name)
                                .value(c.value)
                                .domain(host)
                                .path("/")
                                .build()
                        }.getOrNull()
                    }
                    HCookieJar.cookieMap[host] = okhttpCookies.toMutableList()
                    // M7-2：验证产物落盘 DataStore，跨进程重启后由 loadForRequest 从
                    // 持久化层恢复注入（对齐 iOS 端 IosCookieBridge.persist 语义）。
                    val cookieHeader = cefCookies.joinToString("; ") { "${it.name}=${it.value}" }
                    SettingsRepository.setCloudFlareCookie(cookieHeader, host)
                    open = false
                    onPassed()
                    break
                }
            }
        }
    }
}

/** 正在初始化：给出预期时长，并允许随时改走手动兜底。 */
@Composable
private fun PreparingPane(onUseManual: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.complete_cloudflare_verification),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.cf_kcef_preparing),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onUseManual) {
            Text(stringResource(Res.string.cf_manual_open_browser))
        }
    }
}

/**
 * 手动兜底：自动验证组件不可用时的自救路径。
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
            text = stringResource(Res.string.cf_kcef_failed_title),
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
                    SettingsRepository.setCloudFlareCookie(cookie, host)
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

private const val KCEF_INIT_TIMEOUT_MS = 120_000L

/** KCEF 引擎初始化（同步阻塞；放 IO 线程调用；幂等；首次下载 CEF 运行时）。 */
object CloudflareKcef {

    private val _status = MutableStateFlow<CloudflareKcefStatus>(CloudflareKcefStatus.Idle)
    val status: StateFlow<CloudflareKcefStatus> = _status.asStateFlow()

    /**
     * 确保引擎可用，返回是否成功。
     *
     * 阶段一⑩：失败不再静默——状态经 [status] 反馈到验证窗口，
     * 由 UI 提供「浏览器 + 手动粘贴 Cookie」的自救路径。
     */
    suspend fun ensureInit(): Boolean {
        if (_status.value == CloudflareKcefStatus.Ready) return true
        return withContext(Dispatchers.IO) {
            synchronized(this@CloudflareKcef) {
                if (_status.value == CloudflareKcefStatus.Ready) return@withContext true
                _status.value = CloudflareKcefStatus.Initializing
                val outcome = runCatching {
                    KCEF.initBlocking(
                        { installDir(File("kcef-bins")) },
                        onError = { it?.printStackTrace() },
                        onRestartRequired = {},
                    )
                }
                outcome.exceptionOrNull()?.printStackTrace()
                if (outcome.isSuccess) {
                    _status.value = CloudflareKcefStatus.Ready
                    true
                } else {
                    _status.value = CloudflareKcefStatus.Failed(
                        outcome.exceptionOrNull()?.message
                            ?: "Chromium 运行时初始化失败（首次需联网下载约 200MB）",
                    )
                    false
                }
            }
        }
    }

    /** 手动兜底面板的「重试」：重置状态并重新初始化。 */
    suspend fun retry(): Boolean {
        synchronized(this) {
            if (_status.value != CloudflareKcefStatus.Ready) {
                _status.value = CloudflareKcefStatus.Idle
            }
        }
        return ensureInit()
    }
}
