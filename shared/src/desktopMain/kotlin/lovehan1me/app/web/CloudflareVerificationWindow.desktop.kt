package lovehan1me.app.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFCookieManager
import lovehan1me.core.constant.DESKTOP_USER_AGENT
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.HCookieJar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.cef.network.CefCookieManager
import java.io.File

/**
 * M5-5：桌面 CF 人机验证——独立弹窗 + 内嵌 WebView（KCEF/Chromium）。
 *
 * 用户要求的"独立 WebView 窗口弹窗"形态：Compose Desktop 支持在 App 内再开
 * `Window`，引擎随验证窗口按需初始化、关窗即弃，主窗口不受影响。
 *
 * UA 关键：`cf_clearance` 绑定 UA——WebView 的 UA 必须与 HTTP 层（OkHttp 引擎
 * 的 HCookieJar / 拦截器链）完全一致，统一用 [DESKTOP_USER_AGENT]。
 *
 * 验证判定：后台轮询 KCEF 全局 CookieManager，出现 `cf_*` cookie 即视为通过，
 * 全量转成 okhttp3.Cookie 写入 [HCookieJar.cookieMap]（HTTP 层 loadForRequest
 * 自动携带），随后关闭弹窗、由调用方回退路由以重试原请求。
 *
 * 运行前提：[CloudflareKcef.ensureInit] 首次会下载/解包 CEF 运行时（约 200MB，
 * 落在工作目录 kcef-bins/），期间弹窗内 WebView 处于等待。
 */
@Composable
fun CloudflareVerificationWindow(
    url: String,
    host: String,
    onPassed: () -> Unit,
) {
    var open by remember { mutableStateOf(true) }
    val state = rememberWebViewState(url)
    val navigator = rememberWebViewNavigator()
    state.webSettings.customUserAgentString = DESKTOP_USER_AGENT

    if (open) {
        androidx.compose.ui.window.Window(
            onCloseRequest = { open = false },
            title = "Cloudflare 人机验证",
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "请在窗口内完成人机验证，通过后本窗口自动关闭并重试请求。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                )
                WebView(
                    state = state,
                    navigator = navigator,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
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

/** KCEF 引擎初始化（同步阻塞版放后台线程调用；幂等；首次下载 CEF 运行时）。 */
object CloudflareKcef {

    @Volatile
    private var started = false

    fun ensureInit() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            runCatching {
                KCEF.initBlocking(
                    {
                        installDir(File("kcef-bins"))
                    },
                    onError = { it?.printStackTrace() },
                    onRestartRequired = {},
                )
            }.onFailure { it.printStackTrace() }
        }
    }
}
