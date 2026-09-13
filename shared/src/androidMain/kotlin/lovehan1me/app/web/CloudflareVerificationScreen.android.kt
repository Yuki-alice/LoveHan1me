package lovehan1me.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.complete_cloudflare_verification
import lovehan1me.complete_cloudflare_verification_with_warning
import lovehan1me.core.constant.USER_AGENT
import lovehan1me.current_webview_version
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.CloudflareVerificationCoordinator
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.version_check_failed
import lovehan1me.webview_version_too_low
import lovehan1me.webview_version_unknown
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Android 的 Cloudflare 人机验证页（M2 自 `:app` 下沉到 `shared/androidMain`）。
 *
 * ## 为什么能下沉
 * 原实现依赖 `:app` 的 `MainActivity` 只为拿到一个 `Context` 去 `WebView(context)`。
 * 下沉后用 Compose 的 `LocalContext.current` —— 它在本项目的组合树里就是宿主 Activity，
 * 与原来传 `activity` 语义一致，于是 shared 不再需要知道任何 `:app` 类型。
 *
 * ## 与另两端的关系
 * 桌面走 `CloudflareVerificationWindow.desktop`（独立窗口），iOS 走
 * `CloudflareVerificationWebView.ios`（WebView 弹出层）—— 三端形态本就不同，
 * 由 `PlatformScreens.cloudflare` 槽位各自注入，本文件只是把 Android 那一份也挪进 shared。
 *
 * ## 结束时的契约（重要）
 * 无论成功、用户主动关闭、还是组件被销毁，**都必须调用 `CloudflareVerificationCoordinator.complete`**
 * —— 详见 [DisposableEffect] 里的兜底。漏掉会让等待中的网络请求挂满 5 分钟超时。
 */
@Composable
fun CloudflareRouteScreen(
    host: String,
    url: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var progress by remember(host) { mutableIntStateOf(0) }
    // P6d-3-C2：remember 计算 lambda 非 @Composable 上下文，初始串在外层解析后传入
    val tipInitial = stringResource(Res.string.complete_cloudflare_verification_with_warning)
    var tipText by remember(host) {
        mutableStateOf(tipInitial)
    }
    val webViewState = remember(host) { mutableStateOf<WebView?>(null) }
    val finalizedState = remember(host) { mutableStateOf(false) }

    fun finishVerification(succeeded: Boolean) {
        if (finalizedState.value) return
        finalizedState.value = true
        CloudflareVerificationCoordinator.complete(host, succeeded)
        onBack()
    }

    CloudflareScreen(
        progress = progress,
        tipText = tipText,
        onClose = { finishVerification(false) },
        webViewFactory = { context ->
            createCloudflareWebView(
                context = context,
                url = url,
                onCreated = { webViewState.value = it },
                onProgressChanged = { progress = it },
                onUserAgent = { scope.launch { tipText = buildWebViewVersionTip(it) } },
                onVerificationReady = { completedUrl, cookieManager ->
                    scope.launch {
                        if (persistCloudflareCookies(completedUrl, host, cookieManager)) {
                            finishVerification(true)
                        }
                    }
                },
            )
        },
    )

    DisposableEffect(host) {
        onDispose {
            webViewState.value?.run {
                removeAllViews()
                destroy()
            }
            webViewState.value = null
            // 兜底：用户在验证过程中直接返回/导航走了，也要结清等待者
            if (!finalizedState.value) {
                CloudflareVerificationCoordinator.complete(host, succeeded = false)
            }
        }
    }
}

/**
 * 纯展示层：一个铺满的 WebView + 顶部进度条 + 底部提示卡。
 * 不含任何验证逻辑，便于独立修改视觉。
 */
@Composable
private fun CloudflareScreen(
    progress: Int,
    tipText: String,
    onClose: () -> Unit,
    webViewFactory: (Context) -> WebView,
) {
    HanimeScaffold(
        title = stringResource(Res.string.complete_cloudflare_verification),
        onBack = onClose,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context -> webViewFactory(context) },
                modifier = Modifier.fillMaxSize(),
            )

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    strokeCap = StrokeCap.Round,
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = lerp(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.surface,
                        0.6f,
                    )
                ),
            ) {
                Text(
                    text = tipText,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 构造验证用 WebView。
 *
 * 通过 `onProgressChanged` 在进度 ≥90 时**延迟 1 秒回读 `document.head.innerHTML`**，
 * 检查是否还残留任何 challenge 标记元素 —— 三个都找不到才认为验证已通过。
 * 这个判断放在 `onProgressChanged` 而不是 `shouldOverrideUrlLoading`，
 * 是因为 CF 的挑战页是 **JS 原地改写 DOM**，不会发生导航（URL 不变）。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createCloudflareWebView(
    context: Context,
    url: String,
    onCreated: (WebView) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onUserAgent: (String) -> Unit,
    onVerificationReady: (String, CookieManager) -> Unit,
): WebView {
    return WebView(context).apply {
        onCreated(this)
        val cloudflareWebView = this
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = USER_AGENT
        }
        val cookieManager = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(cloudflareWebView, true)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = false
        }
        evaluateJavascript("navigator.userAgent", onUserAgent)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
                if (newProgress >= 90) {
                    view?.postDelayed({
                        view.evaluateJavascript("document.head.innerHTML") { html ->
                            if (!html.contains("#challenge-form") &&
                                !html.contains("#challenge-success-text") &&
                                !html.contains("#challenge-error-text")
                            ) {
                                onVerificationReady(view.url ?: url, cookieManager)
                            }
                        }
                    }, 1000)
                }
            }
        }
        loadUrl(url)
    }
}

/**
 * `webViewFactory` 由 `AndroidView` 的 factory lambda 调用，**factory 本身带一个 Context 参数**
 * （Compose 的 `AndroidView(factory: (Context) -> T)`），所以直接用它即可 ——
 * 不需要 `LocalContext`，也不需要为拿 Context 把展示层签名弄脏。
 */

/**
 * 落 cf_clearance cookie。
 *
 * @return 是否拿到 clearance（false 表示这次"通过"是误判，不要结清协调器）
 */
private suspend fun persistCloudflareCookies(
    completedUrl: String,
    fallbackHost: String,
    cookieManager: CookieManager,
): Boolean {
    val cookies = cookieManager.getCookie(completedUrl).orEmpty()
    if (!cookies.containsCookie("cf_clearance")) return false
    val cookieHost = completedUrl.toUri().host?.lowercase() ?: fallbackHost
    SettingsRepository.setCloudFlareCookie(cookies, cookieHost)
    cookieManager.flush()
    return true
}

// P6d-3-C2：转 suspend + CMP getString（调用方在 scope.launch 内）
private suspend fun buildWebViewVersionTip(output: String): String {
    val userAgent = output
        .removeSurrounding("\"")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
    val versionCode = "Chrome/(\\d+\\.\\d+\\.\\d+\\.\\d+)".toRegex()
        .find(userAgent)
        ?.groupValues
        ?.getOrNull(1)
        ?: userAgent
    var text = getString(Res.string.complete_cloudflare_verification_with_warning)
    text += getString(Res.string.current_webview_version, versionCode)
    text += try {
        val parts = versionCode.split(".").map { it.toIntOrNull() ?: 0 }
        when {
            parts.size < 4 -> getString(Res.string.webview_version_unknown)
            parts[0] < 120 -> getString(Res.string.webview_version_too_low)
            else -> ""
        }
    } catch (_: Exception) {
        getString(Res.string.version_check_failed)
    }
    return text
}

private fun String.containsCookie(name: String): Boolean =
    split(';').any { it.trim().substringBefore('=') == name }
