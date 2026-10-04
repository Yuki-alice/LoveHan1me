package lovehan1me.data.network

import lovehan1me.core.constant.USER_AGENT
import lovehan1me.data.database.dao.Han1meDatabaseContext
import okhttp3.Interceptor
import java.io.File

/**
 * Android actual：缓存目录走系统 cacheDir（:app 启动时已通过
 * DataStoreManager.initialize 把 Context 注入 [HanimeDatabaseContext]）。
 * Cloudflare 拦截器不再安装：验证触发已收敛到 `NetworkRepo` 单触发点
 * （header 路径与 body 路径同一等待 + 续跑），拦截器内阻塞验证 + inline 重试已删除。
 */
actual fun httpCacheDirectory(): File =
    File(Han1meDatabaseContext.appContext.cacheDir, "http_cache")

actual fun createCloudflareInterceptor(): Interceptor? = null

/** Android 用移动 UA；验证页 WebView 的 `userAgentString` 也是它（见 CloudflareVerificationScreen）。 */
actual fun currentHttpUserAgent(): String = USER_AGENT
