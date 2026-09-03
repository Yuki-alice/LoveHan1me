package io.github.daisukikaffuchino.han1meviewer.logic.network

import okhttp3.Interceptor
import java.io.File

/**
 * Desktop actual：缓存目录固定在用户主目录下；无 WebView/Cloudflare 验证能力，不装拦截器。
 */
actual fun httpCacheDirectory(): File {
    val dir = File(System.getProperty("user.home"), ".han1meviewer/http_cache")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual fun createCloudflareInterceptor(): Interceptor? = null
