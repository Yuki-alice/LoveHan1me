package lovehan1me.data.network

import io.ktor.client.HttpClient

/**
 * P3：Ktor HttpClient 三端工厂（对应旧 Retrofit 的 3 个 OkHttp client）。
 *
 * - android / desktop：Ktor(OkHttp) + preconfigured 复用 jvmMain ServiceCreator 的整条拦截器链
 *   （HDns / HCookieJar / HProxySelector / UserAgent / UrlLogging / Cloudflare / cache），
 *   不再 install HttpCookies，timeout 语义跟随 OkHttp。
 * - ios：Darwin 引擎降级配置（内存 cookie + 15s 请求超时），DNS/DoH/代理不做。
 */
expect fun createHanimeHttpClient(): HttpClient

expect fun createDownloadHttpClient(): HttpClient

expect fun createGetchuHttpClient(): HttpClient

/**
 * 重建底层传输层：JVM 上对应 ServiceCreator.rebuildOkHttpClient()（旧 HanimeNetwork.rebuildNetwork
 * 第一步的语义）；iOS 无 OkHttp 层，no-op。internal，仅 HanimeNetwork 使用。
 */
internal expect fun rebuildHttpClients()
