package lovehan1me.data.network

import lovehan1me.core.constant.DESKTOP_USER_AGENT
import okhttp3.Interceptor
import java.io.File

/**
 * Desktop actual：缓存目录固定在用户主目录下；拦截器保持 null——CF 恢复不走
 * OkHttp 拦截器，改由共享 [CloudflareChallenges] 总线触发验证窗口
 * （`platformScreens.cloudflare` 槽位 → `CloudflareVerificationWindow`，
 * 求解走 `CloudflareCdp` 驱动本机 Chrome/Edge）。
 */
actual fun httpCacheDirectory(): File {
    val dir = File(System.getProperty("user.home"), ".lovehan1me/http_cache")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual fun createCloudflareInterceptor(): Interceptor? = null

/**
 * 桌面用**桌面** UA。
 *
 * ⚠️ 这里返回的字符串必须与 `CloudflareCdp` 传给验证浏览器的 `--user-agent` 完全一致
 * （两者都取 [DESKTOP_USER_AGENT]），否则 cf_clearance 因 UA 不匹配而永久失效——
 * 详见 [currentHttpUserAgent] 的 KDoc。`CloudflareCdpTest` 有一条测试把这个不变量钉住。
 */
actual fun currentHttpUserAgent(): String = DESKTOP_USER_AGENT
