package io.github.daisukikaffuchino.han1meviewer.logic.network

import okhttp3.Interceptor
import java.io.File

/**
 * Desktop actual：缓存目录固定在用户主目录下；拦截器保持 null——CF 恢复不走
 * OkHttp 拦截器，改由共享 [CloudflareChallenges] 总线触发 KCEF 弹窗
 * （`Main.kt` 的 `platformScreens.cloudflare` 槽位）。
 */
actual fun httpCacheDirectory(): File {
    val dir = File(System.getProperty("user.home"), ".han1meviewer/http_cache")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual fun createCloudflareInterceptor(): Interceptor? = null
