package io.github.daisukikaffuchino.han1meviewer.logic.network

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
}

/** Darwin HTTP 层的 storage：默认（空）+ 桥接 cookie 合并后按请求 host 附加。 */
class BridgeCookiesStorage : CookiesStorage {

    override suspend fun get(request: Url): List<Cookie> =
        IosCookieBridge.snapshot().map { (name, value) ->
            Cookie(
                name = name,
                value = value,
                domain = request.host,
                path = "/",
            )
        }

    /** HTTP 层自身的 Set-Cookie 由 Darwin 引擎内部处理，桥只读。 */
    override suspend fun addCookie(request: Url, cookie: Cookie) = Unit

    override fun close() = Unit
}
