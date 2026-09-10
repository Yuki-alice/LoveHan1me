package me.lovehan1me.logic.network

import me.lovehan1me.logic.dao.Han1meDatabaseContext
import me.lovehan1me.logic.network.interceptor.CloudflareInterceptor
import okhttp3.Interceptor
import java.io.File

/**
 * Android actual：缓存目录走系统 cacheDir（:app 启动时已通过
 * DataStoreManager.initialize 把 Context 注入 [Han1meDatabaseContext]）。
 * Cloudflare 拦截器真装（回调经 [CloudflareVerifier] 由 :app 注册）。
 */
actual fun httpCacheDirectory(): File =
    File(Han1meDatabaseContext.appContext.cacheDir, "http_cache")

actual fun createCloudflareInterceptor(): Interceptor? =
    CloudflareInterceptor(Han1meDatabaseContext.appContext)
