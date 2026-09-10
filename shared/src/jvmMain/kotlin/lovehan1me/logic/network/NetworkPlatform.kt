package lovehan1me.logic.network

import okhttp3.Interceptor
import java.io.File

/**
 * jvmMain（android + desktop 共享）平台差异点，P3 新增：
 *  - [httpCacheDirectory]：OkHttp 磁盘缓存目录（android = cacheDir/http_cache；desktop = ~/.han1meviewer/http_cache）
 *  - [createCloudflareInterceptor]：Cloudflare 拦截器仅 Android 安装（依赖 WebView 验证），desktop 返回 null 不装
 */
expect fun httpCacheDirectory(): File

expect fun createCloudflareInterceptor(): Interceptor?
