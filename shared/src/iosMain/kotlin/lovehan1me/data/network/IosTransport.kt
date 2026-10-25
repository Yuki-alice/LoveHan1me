package lovehan1me.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import lovehan1me.data.network.egress.EgressPurpose
import platform.Foundation.NSURLSessionConfiguration

/**
 * iOS 统一传输装配器（网络优化 1.1）：Darwin 引擎 + ECH 改写 + 可选 Cookie/超时/重试
 * **一处装配**。此前 5 处生产调用点各写一份 `HttpClient(Darwin) { … }`，超时/重试档位
 * 漂移正是从这里长出来的（图片链漏超时、存相册每请求建/关 client）。规则：
 *
 * - **ECH 网关插件**：默认装；[withCookies] 决定是否挂 Cookie 头提供器 + [HttpCookies]
 *   storage（图片/存相册链传 false，不把登录态发去图床）。`withEchGate=false` 只剩
 *   裸 Darwin + 超时档（第三方干净 client 走这条，本来就不该进网关）。
 * - **超时**：三档独立传值，null = 不设该档（下载链刻意不给 request 档——流式必超限，
 *   靠读空闲档兜底）。档位取值见 [EgressBudgets]（Api 60s / Image 30s）。
 * - **重试**：[retry] = 主栈同款（幂等方法 + 传输异常，`shouldRetryDarwinFailure`）；
 *   与 jvm 侧 `RetryInterceptor` 语义一致，两端判据必须同源。
 * - **[sessionConfiguration]**：NSURLSessionConfiguration 级注入钩子（1.2 应用内代理的
 *   `connectionProxyDictionary` 走这里；1.1 无调用方传值）。
 *
 * 替换的 5 处（验收 = 生产代码无裸 `HttpClient(Darwin)`，plain client 与测试除外）：
 * `HttpClientFactory.ios.kt`（Api/plain）、`HanimeImageLoader.ios.kt`、
 * `GetchuImageLoader.ios.kt`、`HomePageUtils.ios.kt`（存相册，改进程级单例）、
 * `IosDownloadWorkController.kt`。
 */
internal fun createIosTransport(
    purpose: EgressPurpose,
    withEchGate: Boolean = true,
    withCookies: Boolean = true,
    requestTimeoutMillis: Long? = null,
    connectTimeoutMillis: Long? = null,
    socketTimeoutMillis: Long? = null,
    retry: Boolean = false,
    sessionConfiguration: (NSURLSessionConfiguration.() -> Unit)? = null,
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(Darwin) {
    val sessionHook = sessionConfiguration
    engine {
        sessionHook?.let { configureSession(it) }
    }
    if (withEchGate) {
        // ECH 网关插件必须装在 HttpCookies 之前：改写先发生，storage 随后对回环短路。
        installEchGate(defaultPurpose = purpose)
        if (withCookies) {
            install(HttpCookies) {
                storage = BridgeCookiesStorage()
            }
        }
    }
    if (requestTimeoutMillis != null || connectTimeoutMillis != null || socketTimeoutMillis != null) {
        install(HttpTimeout) {
            this.requestTimeoutMillis = requestTimeoutMillis
            this.connectTimeoutMillis = connectTimeoutMillis
            this.socketTimeoutMillis = socketTimeoutMillis
        }
    }
    if (retry) {
        install(HttpRequestRetry) {
            // 状态码一律不重试：网关的 502 重试与 CF 验证后的续跑各自负责那一层，
            // 在这里再叠一层会让两套机制互相看不见。
            noRetry()
            retryOnExceptionIf(maxRetries = 2) { request, cause ->
                shouldRetryDarwinFailure(request.method, cause)
            }
            constantDelay(300, 1_000, true)
        }
    }
    configure()
}
