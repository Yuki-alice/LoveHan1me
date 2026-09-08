package io.github.daisukikaffuchino.han1meviewer

// M5：iOS 平台 actual。
actual fun clearMemoryCookies() {
    // iOS 网络层 Cookie 存于 Ktor HttpCookies（内存态，随 HttpClient 实例）。
    // TODO(M-后续)：抽共享 CookiesStorage 注入引擎，登出时同步清除，
    //  否则登出后内存中的旧 session cookie 会残留至进程结束。
}

actual fun clearWebCookies() {
    // iOS 无 WebView 登录路径（表单登录直连 HTTP）。
    // 若后续接 WKWebView 登录，改用 WKWebsiteDataStore 清理。
}
