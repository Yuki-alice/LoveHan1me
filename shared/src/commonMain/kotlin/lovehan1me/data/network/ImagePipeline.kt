package lovehan1me.data.network

import io.ktor.client.plugins.api.createClientPlugin
import lovehan1me.core.constant.DESKTOP_USER_AGENT

/**
 * 图片管线的 Ktor 侧配置（引擎无关）。
 *
 * 这里只剩 getchu 域名特化一件事了。
 *
 * ECH 改写与失败回退已收敛到 `EchGateClientPlugin` —— 图片与 API **共用同一个插件**，
 * 只是装配时关掉站点 Cookie（图片不该把登录态发给图床）。此前图片自己有一份
 * "只改写、不回退"的实现：网关一出问题整页封面全空，而同一时刻 API 链路靠
 * `EchGateInterceptor` 的回退照常工作，"页面能开、图全没了"就是这么来的。
 *
 * 插件挂在 `onRequest`（早于网关插件的 `on(Send)`），因此它看到的仍是**原始** host——
 * 若装在同一个 hook 上，后装的会看到改写后的 127.0.0.1，特化头恒不命中。
 */
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
