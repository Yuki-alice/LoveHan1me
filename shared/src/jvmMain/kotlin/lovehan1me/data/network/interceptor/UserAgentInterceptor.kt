package lovehan1me.data.network.interceptor

import lovehan1me.data.network.currentHttpUserAgent
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 给每个请求盖上 HTTP 层的统一 UA（Android = 移动 UA，桌面 = 桌面 UA）。
 *
 * 用 [currentHttpUserAgent] 而不是直接引常量：桌面端必须与 CF 验证浏览器同一 UA，
 * 否则 `cf_clearance` 因 UA 不匹配而失效（见该函数的 KDoc）。
 *
 * 用 `header()` 而不是 `addHeader()`：后者会**再追加**一个 User-Agent 头，
 * 于是请求里出现两个 UA（服务端取哪个不确定，CF 会因此判定 UA 不一致）。
 * `header()` 是替换语义，保证全链只有一个 UA。
 */
object UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().header(
            "User-Agent", currentHttpUserAgent()
        ).build()
        return chain.proceed(request)
    }
}