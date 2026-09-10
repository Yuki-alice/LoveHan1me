package me.lovehan1me.logic.network

import me.lovehan1me.logic.SettingsRepository
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * M5-5：iOS 的 Cookie 桥。
 *
 * Darwin 引擎的 Ktor `HttpCookies` 默认是进程内存 storage，CF 验证在
 * WKWebView 中完成、cookie 无法自动到达 HTTP 层。本桥承担中转：
 * `CloudflareVerificationWebView` 从 `WKHTTPCookieStore` 轮询提取验证产物
 * （`cf_clearance` / 会话 cookie）写入这里，HTTP 层经 [BridgeCookiesStorage]
 * 在每次请求时附加。
 *
 * 注意 `cf_clearance` 与 UA/IP 绑定：WKWebView 的 `customUserAgent` 已固定为
 * 与 HTTP 层一致的 `USER_AGENT`，同机同网下匹配成立。
 *
 * M7-2 修复：cf_clearance 除写入内存桥外，同步写入 DataStore（`cf_cookie` /
 * `cf_cookie_host`），与 jvm 端 `persistCloudflareCookies` 语义对齐——跨进程
 * 重启后 CF 验证态可由 [BridgeCookiesStorage] 从持久化层恢复注入。
 */
object IosCookieBridge {

    private val cookies = MutableStateFlow<Map<String, String>>(emptyMap())

    fun put(extra: Map<String, String>) {
        cookies.update { it + extra }
    }

    fun snapshot(): Map<String, String> = cookies.value

    fun clear() {
        cookies.update { emptyMap() }
    }

    /**
     * M7-2：把 WKWebView 提取的 cookie 持久化到 DataStore（登录/CF 验证态跨重启保留）。
     * 只在提取到 `cf_clearance` 时落盘，避免把无意义的中间态写进设置。
     */
    suspend fun persist(host: String, extra: Map<String, String>) {
        val hasClearance = extra.keys.any { it == "cf_clearance" }
        if (hasClearance && host.isNotBlank()) {
            val cookieHeader = extra.entries.joinToString("; ") { "${it.key}=${it.value}" }
            SettingsRepository.setCloudFlareCookie(cookieHeader, host)
        }
    }

    /** 解析 `k=v; k2=v2` 形式的 Cookie 字符串（DataStore 持久化值）。 */
    internal fun parseCookieString(cookie: String): List<Pair<String, String>> =
        cookie.split(';')
            .mapNotNull { part ->
                val name = part.substringBefore('=').trim()
                val value = part.substringAfter('=').trim()
                if (name.isNotEmpty()) name to value else null
            }

    /** M7-2：从 DataStore loginCookie 构造请求 Cookie（对齐 HCookieJar 叠加逻辑）。 */
    internal fun loginCookiesFor(host: String): List<Cookie> {
        val loginCookie = SettingsRepository.current.loginCookie
        if (loginCookie.isBlank()) return emptyList()
        return parseCookieString(loginCookie).map { (name, value) ->
            Cookie(name = name, value = value, domain = host, path = "/")
        }
    }

    /** M7-2：从 DataStore cloudFlareCookie 构造请求 Cookie（host 匹配由调用方判定）。 */
    internal fun cloudFlareCookiesFor(host: String): List<Cookie> {
        val cfCookie = SettingsRepository.current.cloudFlareCookie
        if (cfCookie.isBlank()) return emptyList()
        return parseCookieString(cfCookie).map { (name, value) ->
            Cookie(name = name, value = value, domain = host, path = "/")
        }
    }
}

/**
 * Darwin HTTP 层的 storage。
 *
 * M7-2 修复：对齐 jvm 端 `HCookieJar.loadForRequest` 的语义——每次请求把
 * DataStore 持久化的登录 Cookie（`loginCookie`）叠加到桥接 cookie 上，
 * CF Cookie（`cloudFlareCookie`）在 host 匹配时叠加。此前这里只读内存桥，
 * 导致 iOS 端已持久化的登录态（表单登录/手动填 Cookie）重启后不再注入请求，
 * 且 cf_clearance 只存活于内存、进程重启即丢失。
 */
class BridgeCookiesStorage : CookiesStorage {

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val host = requestUrl.host
        val cookies = IosCookieBridge.snapshot().mapTo(mutableListOf()) { (name, value) ->
            Cookie(
                name = name,
                value = value,
                domain = host,
                path = "/",
            )
        }
        cookies += IosCookieBridge.loginCookiesFor(host)
        if (SettingsRepository.cloudFlareCookieHost == host) {
            cookies += IosCookieBridge.cloudFlareCookiesFor(host)
        }
        return cookies
    }

    /** HTTP 层自身的 Set-Cookie 由 Darwin 引擎内部处理，桥只读。 */
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = Unit

    override fun close() = Unit
}
