package lovehan1me.data.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLProtocol
import lovehan1me.core.constant.DESKTOP_USER_AGENT

/**
 * 图片管线的 Ktor 侧通用配置（Gate4-1，common，引擎无关）。
 *
 * 对齐 jvmMain OkHttp 链（`GetchuImageLoader.jvm` + `HanimeImageLoader.jvm`）里与引擎
 * 无关的那两件事，iOS 的 Darwin 引擎同样需要：
 * 1. **ECH 改写**：图片同样在 CDN 上，和视频一样被 SNI 阻断；网关运行时把 URL 改写到
 *    本地回环 + 补目标头（判定收敛到 [EchGatePolicy]，与 OkHttp 的 EchGateInterceptor 同语义）。
 *    网关未运行（port <= 0）零改动透传。回环走明文 HTTP，iOS 侧 Info.plist 已开
 *    `NSAllowsLocalNetworking`，ATS 不拦截。
 * 2. **getchu 域名特化**（仅 `getchu = true`）：`/brandnew/` 路径补 UA/Referer/Cookie，
 *    与 jvm 拦截器逐字一致。
 *
 * 实现为 `createClientPlugin` 的 `onRequest` 改写（只改请求，不接管执行），
 * 不碰 `HttpSend` 的 DSL 形态。DNS/代理走各引擎原生能力（Darwin 用系统解析，
 * jvm 用 OkHttp 拦截器链），不在这里统一。
 */
val HanimeImageHeaders = createClientPlugin("HanimeImageHeaders") {
    onRequest { request, _ ->
        // ECH 改写只动三要素（scheme/host/port），path/query 原样保留——
        // 与 EchGatePolicy.rewrite 的变换逐字等价（它就是这么拼的），故无需解析重建。
        val rewritten = EchGatePolicy.rewrite(request.url.toString(), EchGate.port)
        if (rewritten != null) {
            request.url.protocol = URLProtocol.HTTP
            request.url.host = EchGatePolicy.GATE_HOST
            request.url.port = EchGate.port
            request.headers.append(EchGatePolicy.TARGET_HEADER, rewritten.targetHost)
        }
    }
}

// getchu 特化是第二插件（与 ECH 改写正交，可单独开关；`getchu = false` 时不装）。
val GetchuBrandHeaders = createClientPlugin("GetchuBrandHeaders") {
    onRequest { request, _ ->
        val host = request.url.host
        val path = "/" + request.url.encodedPathSegments.joinToString("/")
        if (host == "www.getchu.com" && path.startsWith("/brandnew/")) {
            request.headers.append("User-Agent", DESKTOP_USER_AGENT)
            request.headers.append("Referer", "https://www.getchu.com/")
            request.headers.append("Cookie", "getchu_adalt_flag=getchu.com; gc=gc")
        }
    }
}
