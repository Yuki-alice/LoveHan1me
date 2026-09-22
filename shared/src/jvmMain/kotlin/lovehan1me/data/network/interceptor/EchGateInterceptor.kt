package lovehan1me.data.network.interceptor

import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.EchGateProcess
import lovehan1me.data.network.HCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 把 https 请求交给本地 ECH 网关出站（见 [EchGate]）。
 *
 * 改写判定收敛到 commonMain [EchGatePolicy]（与 Ktor 插件 / 播放器改写同一份逻辑）。
 *
 * ## 两个必须手动补的洞
 * 1. **Cookie**：改写后 OkHttp 的 CookieJar 按 `127.0.0.1` 匹配域名，登录态与
 *    `cf_clearance` 全部拿不到 ⇒ 这里按**原域名**取出来塞进 `Cookie` 头。
 * 2. **Set-Cookie**：响应按 `127.0.0.1` 存下来，原域名就再也取不到 ⇒ 这里用原 URL
 *    重新解析并存回原域名。
 *
 * ## 失败回退（网关是加速项，不是单点）
 * - 网关抛 [IOException]（进程挂了/端口未监听）→ 原样重试一次；
 * - 网关回 502 且 body 是 `echgate:` 开头（网关自己的上游错误页，
 *   见 `echgate` 的 onUpstreamError；源站的 502  body 不会是这个前缀）→
 *   GET/HEAD 先重试一次**网关**（plan 已被网关侧失效，重试会重新探测），
 *   还不行才回退直连；其它方法直接回退（POST 重发有双提交风险）。
 *   重试只做一次（`X-Ech-Retry` 防环）。
 * 502 判定只 peek 前 256 字节——本拦截器同样装在下载客户端上，
 * 整包 peek 会把大文件读进内存。
 * 回退后走的即现有机制（代理选择器 + 内置 hosts/DoH），与网关存在前完全一致。
 *
 * ## 不接管什么
 * - 网关没运行（[EchGate.port] <= 0）→ 原样放行；
 * - 非 https、本机地址、IP 字面量 → 原样放行（[EchGatePolicy] 的判定）。
 */
class EchGateInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 冷启动竞态：网关被要求启动但 LISTENING 未到时，首页请求会抢跑直连撞 RST，
        // 表现为"封面首刷失败、再滑回来又好"。拉起中有界等待，图片与列表统一处理；
        // 等不到（或根本没启动）就按原逻辑走直连/代理兜底。
        EchGateProcess.awaitReadyIfStarting()
        val rewrite = EchGatePolicy.rewrite(request.url.toString(), EchGate.port)
            ?: return chain.proceed(request)
        val originUrl = request.url

        val gateUrl = runCatching { rewrite.url.toHttpUrl() }
            .getOrNull() ?: return chain.proceed(request)

        val jar = HCookieJar()
        val builder = request.newBuilder()
            .url(gateUrl)
            .header(EchGatePolicy.TARGET_HEADER, rewrite.targetHost)
            .header("Host", rewrite.targetHost)

        val cookies = runCatching { jar.loadForRequest(originUrl) }.getOrDefault(emptyList())
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
        }

        val gateResponse = try {
            chain.proceed(builder.build())
        } catch (e: IOException) {
            LogUtil.w(TAG, "网关异常，回退直连 ${originUrl.host} (${e.message})")
            return chain.proceed(request)
        }

        // 网关 502 且是它自己的上游错误页：GET/HEAD 先重试一次网关
        // （plan 已被网关侧失效，重试会重新探测，常能切到可用 IP——DPI 间歇性
        // RST 时"首刷失败、再滑回来又好"的根因）；其它方法直接回退直连
        // （POST 重发有双提交风险）。重试只做一次（`X-Ech-Retry` 防环）。
        if (gateResponse.code == 502 && isGatewayErrorPage(gateResponse) &&
            (request.method == "GET" || request.method == "HEAD")
        ) {
            gateResponse.close()
            LogUtil.w(TAG, "网关上游失败，重试网关一次 ${originUrl.host}")
            val retried = try {
                chain.proceed(builder.header(RETRY_HEADER, "1").build())
            } catch (e: IOException) {
                LogUtil.w(TAG, "网关重试异常，回退直连 ${originUrl.host} (${e.message})")
                return chain.proceed(request)
            }
            if (retried.code == 502 && isGatewayErrorPage(retried)) {
                retried.close()
                LogUtil.w(TAG, "网关重试仍失败，回退直连 ${originUrl.host}")
                return chain.proceed(request)
            }
            return finishGateResponse(retried, originUrl, jar)
        }

        return finishGateResponse(gateResponse, originUrl, jar)
    }

    /** 网关响应的收尾：Set-Cookie 按原域名存回（见类 KDoc 第二个洞）。 */
    private fun finishGateResponse(response: Response, originUrl: okhttp3.HttpUrl, jar: HCookieJar): Response {
        val setCookies = response.headers("Set-Cookie")
        if (setCookies.isNotEmpty()) {
            val parsed = setCookies.mapNotNull { raw ->
                runCatching { Cookie.parse(originUrl, raw) }.getOrNull()
            }
            if (parsed.isNotEmpty()) {
                runCatching { jar.saveFromResponse(originUrl, parsed) }
            }
        }

        LogUtil.d(TAG, "${originUrl.host} -> ${EchGatePolicy.GATE_HOST}:${EchGate.port} (${response.code})")
        return response
    }

    private fun isGatewayErrorPage(response: Response): Boolean {
        return runCatching {
            val peek = response.peekBody(PEEK_LIMIT).string()
            peek.startsWith(GATEWAY_ERROR_PREFIX)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "EchGate"

        /** 网关上游错误页的前缀（见 `echgate` 的 onUpstreamError）。 */
        const val GATEWAY_ERROR_PREFIX = "echgate:"

        /** 网关重试标记：同一请求只重试一次，防环。 */
        const val RETRY_HEADER = "X-Ech-Retry"

        /** 502 判定只看这么多字节（下载大文件场景下不能整包 peek）。 */
        const val PEEK_LIMIT = 256L
    }
}
