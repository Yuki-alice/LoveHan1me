@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.ui.screen.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import lovehan1me.USER_AGENT
import lovehan1me.logic.network.IosCookieBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebsiteDataStore

/**
 * M5-5：iOS 的 CF 人机验证视图（系统 WebKit 直嵌）。
 *
 * - `UIKitView` 嵌 `WKWebView`（与 `PlatformVideoSurface.ios` 嵌 `AVPlayerLayer`
 *   同一机制），加载挑战 URL；
 * - `customUserAgent` 固定为 HTTP 层的 [USER_AGENT]——`cf_clearance` 绑定 UA，
 *   两者不一致则验证通过后 HTTP 层携带仍会被拒；
 * - 后台协程每 1.5s 从 `WKHTTPCookieStore` 轮询 cookie，把验证产物写入
 *   [IosCookieBridge]（Darwin HTTP 层的 BridgeCookiesStorage 据此附加请求头），
 *   一旦发现 `cf_clearance` 即回调完成。
 */
@Composable
fun CloudflareVerificationWebView(
    url: String,
    onVerificationPassed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WKWebView?>(null) }

    Box(modifier) {
        UIKitView(
            factory = {
                val wv = WKWebView(frame = CGRectZero.readValue())
                wv.customUserAgent = USER_AGENT
                NSURL.URLWithString(url)?.let { nsUrl ->
                    wv.loadRequest(NSURLRequest.requestWithURL(nsUrl))
                }
                webView = wv
                wv
            },
            onRelease = { it.stopLoading() },
            modifier = Modifier.fillMaxSize(),
        )
    }

    LaunchedEffect(url) {
        while (isActive) {
            delay(1_500)
            val extracted = extractCookies()
            IosCookieBridge.put(extracted)
            val verified = IosCookieBridge.snapshot().keys.any { it.startsWith("cf_") }
            if (verified) {
                // M7-2：验证产物落盘 DataStore，跨进程重启后由
                // BridgeCookiesStorage 从持久化层恢复注入（对齐 jvm 端语义）。
                NSURL.URLWithString(url)?.host?.let { host ->
                    IosCookieBridge.persist(host, IosCookieBridge.snapshot())
                }
                onVerificationPassed()
                break
            }
        }
    }
}

/** 从默认 WKHTTPCookieStore 枚举全部 cookie（cinterop 回调转挂起）。 */
private suspend fun extractCookies(): Map<String, String> {
    val store = WKWebsiteDataStore.defaultDataStore().httpCookieStore
    return suspendCancellableCoroutine { cont ->
        store.getAllCookies { cookies ->
            val map = cookies.orEmpty()
                .filterIsInstance<NSHTTPCookie>()
                .associate { it.name to it.value }
            cont.resumeWith(Result.success(map))
        }
    }
}
