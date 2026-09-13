package lovehan1me.data.network

import lovehan1me.core.constant.USER_AGENT
import lovehan1me.data.database.dao.Han1meDatabaseContext
import lovehan1me.data.network.interceptor.CloudflareInterceptor
import okhttp3.Interceptor
import java.io.File

/**
 * Android actual：缓存目录走系统 cacheDir（:app 启动时已通过
 * DataStoreManager.initialize 把 Context 注入 [Han1meDatabaseContext]）。
 * Cloudflare 拦截器真装（M2 起协调器与验证页同在 shared/androidMain，不再需要壳注册回调）。
 */
actual fun httpCacheDirectory(): File =
    File(Han1meDatabaseContext.appContext.cacheDir, "http_cache")

actual fun createCloudflareInterceptor(): Interceptor? =
    CloudflareInterceptor(Han1meDatabaseContext.appContext)

/** Android 用移动 UA；验证页 WebView 的 `userAgentString` 也是它（见 CloudflareVerificationScreen）。 */
actual fun currentHttpUserAgent(): String = USER_AGENT
