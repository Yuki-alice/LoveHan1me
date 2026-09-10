package me.lovehan1me.ui.navigation.main

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.core.net.toUri
import me.lovehan1me.HANIME_LOGIN_URL
import me.lovehan1me.HanimeConstants.HANIME_URL
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.R
import me.lovehan1me.Res
import me.lovehan1me.account_or_password_wrong
import me.lovehan1me.login_failed
import me.lovehan1me.login_success
import me.lovehan1me.complete_cloudflare_verification_with_warning
import me.lovehan1me.current_webview_version
import me.lovehan1me.version_check_failed
import me.lovehan1me.webview_version_too_low
import me.lovehan1me.webview_version_unknown
import me.lovehan1me.USER_AGENT
import me.lovehan1me.logic.NetworkRepo
import me.lovehan1me.logic.network.CloudflareVerificationCoordinator
import me.lovehan1me.logic.state.WebsiteState
import me.lovehan1me.login
import me.lovehan1me.ui.activity.MainActivity
import me.lovehan1me.ui.screen.login.LoginDialog
import me.lovehan1me.ui.screen.login.LoginScreen
import me.lovehan1me.ui.screen.login.ManualInputCookiesScreen
import me.lovehan1me.ui.screen.web.CloudflareScreen
import me.lovehan1me.util.CookieString
import me.lovehan1me.utils.LogUtil
import me.lovehan1me.utils.SonnerToast
import me.lovehan1me.utils.toastText
import kotlinx.coroutines.launch

@Composable
fun LoginRouteScreen(
    activity: MainActivity,
    onBack: () -> Unit,
    onOpenManualCookies: () -> Unit,
    onLoginSucceeded: () -> Unit,
) {
    var isRefreshing by remember { mutableStateOf(true) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var canWebViewGoBack by remember { mutableStateOf(false) }
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()

    fun finishLogin(cookies: String) {
        scope.launch {
            login(cookies)
            onLoginSucceeded()
        }
    }

    fun navigateBack() {
        val webView = webViewState.value
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    BackHandler(enabled = canWebViewGoBack, onBack = ::navigateBack)

    if (showLoginDialog) {
        LoginDialog(
            isLoggingIn = isLoggingIn,
            onDismiss = { showLoginDialog = false },
            onLogin = { username, password ->
                isLoggingIn = true
                scope.launch {
                    NetworkRepo.login(username, password).collect { state ->
                        when (state) {
                            WebsiteState.Loading -> Unit
                            is WebsiteState.Error -> {
                                isLoggingIn = false
                                state.throwable.printStackTrace()
                                if (state.throwable is IllegalStateException) {
                                    SonnerToast.error(getString(Res.string.account_or_password_wrong))
                                } else {
                                    SonnerToast.error(getString(Res.string.login_failed))
                                }
                            }
                            is WebsiteState.Success -> {
                                login(state.info)
                                isLoggingIn = false
                                showLoginDialog = false
                                SonnerToast.success(getString(Res.string.login_success))
                                onLoginSucceeded()
                            }
                        }
                    }
                }
            },
        )
    }

    LoginScreen(
        isRefreshing = isRefreshing,
        onBack = ::navigateBack,
        onRefresh = { webViewState.value?.loadUrl(HANIME_LOGIN_URL) },
        onOpenQrScanner = onOpenManualCookies,
        webViewFactory = {
            createLoginWebView(
                context = activity,
                onCreated = { webViewState.value = it },
                onPageStateChanged = { webView ->
                    isRefreshing = false
                    canWebViewGoBack = webView.canGoBack()
                },
                onLoginSucceeded = ::finishLogin,
                onLoadFailed = {
                    isRefreshing = false
                    showLoginDialog = true
                },
            )
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewState.value?.run {
                removeAllViews()
                destroy()
            }
            webViewState.value = null
        }
    }
}

// M2：ManualCookiesRouteScreen 已下沉 shared（同名；login() 改内联 update + rebuildNetwork，语义一致）。

@Composable
fun CloudflareRouteScreen(
    activity: MainActivity,
    route: CloudflareRoute,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var progress by remember(route.host) { mutableIntStateOf(0) }
    // P6d-3-C2：remember 计算 lambda 非 @Composable 上下文，初始串在外层解析后传入
    val tipInitial = stringResource(Res.string.complete_cloudflare_verification_with_warning)
    var tipText by remember(route.host) {
        mutableStateOf(tipInitial)
    }
    val webViewState = remember(route.host) { mutableStateOf<WebView?>(null) }
    val finalizedState = remember(route.host) { mutableStateOf(false) }

    fun finishVerification(succeeded: Boolean) {
        if (finalizedState.value) return
        finalizedState.value = true
        CloudflareVerificationCoordinator.complete(route.host, succeeded)
        onBack()
    }

    CloudflareScreen(
        progress = progress,
        tipText = tipText,
        onClose = { finishVerification(false) },
        webViewFactory = {
            createCloudflareWebView(
                context = activity,
                url = route.url,
                onCreated = { webViewState.value = it },
                onProgressChanged = { progress = it },
                onUserAgent = { scope.launch { tipText = buildWebViewVersionTip(it) } },
                onVerificationReady = { completedUrl, cookieManager ->
                    scope.launch {
                        if (persistCloudflareCookies(completedUrl, route.host, cookieManager)) {
                            finishVerification(true)
                        }
                    }
                },
            )
        },
    )

    DisposableEffect(route.host) {
        onDispose {
            webViewState.value?.run {
                removeAllViews()
                destroy()
            }
            webViewState.value = null
            if (!finalizedState.value) {
                CloudflareVerificationCoordinator.complete(route.host, succeeded = false)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createLoginWebView(
    context: Context,
    onCreated: (WebView) -> Unit,
    onPageStateChanged: (WebView) -> Unit,
    onLoginSucceeded: (String) -> Unit,
    onLoadFailed: () -> Unit,
): WebView = WebView(context).apply {
    onCreated(this)
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.userAgentString = USER_AGENT
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            onPageStateChanged(view)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            if (request.isRedirect && HANIME_URL.contains(request.url.toString())) {
                val cookies = CookieManager.getInstance().getCookie(request.url.host).orEmpty()
                LogUtil.d("login_cookie", "Captured login cookies: ${cookies.isNotBlank()}")
                onLoginSucceeded(cookies)
                return true
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame == true) onLoadFailed()
        }
    }
    loadUrl(HANIME_LOGIN_URL)
}

@SuppressLint("SetJavaScriptEnabled")
private fun createCloudflareWebView(
    context: Context,
    url: String,
    onCreated: (WebView) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onUserAgent: (String) -> Unit,
    onVerificationReady: (String, CookieManager) -> Unit,
): WebView = WebView(context).apply {
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

// P6d-3-C2：转 suspend + CMP getString（调用方在 scope.launch 内）；context 参数去除
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
