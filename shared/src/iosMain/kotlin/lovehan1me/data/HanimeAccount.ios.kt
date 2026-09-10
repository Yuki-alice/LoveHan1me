package lovehan1me.data

// M5：iOS 平台 actual。
actual fun clearMemoryCookies() {
    // M7-2：登出时清空内存桥，同时清掉 DataStore 中已持久化的 CF cookie，
    // 避免登出后旧 cf_clearance 继续随请求注入（对齐 Android 端登出语义）。
    lovehan1me.data.network.IosCookieBridge.clear()
    kotlinx.coroutines.runBlocking {
        lovehan1me.data.SettingsRepository.setCloudFlareCookie("", "")
    }
}

actual fun clearWebCookies() {
    // iOS 无 WebView 登录路径（表单登录直连 HTTP）。
    // 若后续接 WKWebView 登录，改用 WKWebsiteDataStore 清理。
}
