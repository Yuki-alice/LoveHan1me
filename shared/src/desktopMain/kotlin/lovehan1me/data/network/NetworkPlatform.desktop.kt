package lovehan1me.data.network

import lovehan1me.core.constant.DESKTOP_USER_AGENT
import lovehan1me.data.SettingsRepository
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
 * 桌面 UA：**优先用验证浏览器采集到的真实 UA**，未采集时退回 [DESKTOP_USER_AGENT]。
 *
 * 为什么必须跟随浏览器：`cf_clearance` 绑定 UA；而浏览器 UA **不能伪造**
 * （实测：把真实 Chrome 153 强制成常量里的 Chrome/149，CF 会永远停在挑战页），
 * 所以唯一正确的组合是"浏览器用它自己的 UA，HTTP 层发同一个字符串"。
 * 真实值由 `CloudflareCdp` 采集后经 `SettingsRepository.setDesktopBrowserUserAgent` 落盘。
 *
 * runCatching 兜底：极早的请求可能早于 DataStore 就绪，那时也还没有 clearance 要匹配。
 */
actual fun currentHttpUserAgent(): String =
    runCatching { SettingsRepository.desktopBrowserUserAgent }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: DESKTOP_USER_AGENT
