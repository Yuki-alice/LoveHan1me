package lovehan1me.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import lovehan1me.data.network.egress.EgressPurpose

/**
 * iOS Darwin 工厂（Phase 3 超时对齐 JVM 档位）。
 *
 * - 浏览/getchu（Api）：request 60s / connect 15s / socket 30s，对齐
 *   jvmMain `ServiceCreator.hClient` 的 call 60s / connect 15s / read 30s；
 * - 下载走各端专路（Android WorkManager / 桌面与 iOS 自建 client），不在此列；
 * - 第三方干净 client：不动（本来就不该有预算，不同业务）。
 */
actual fun createHanimeHttpClient(): HttpClient = createDarwinHttpClient(EgressPurpose.Api)

actual fun createGetchuHttpClient(): HttpClient = createDarwinHttpClient(EgressPurpose.Api)

actual fun createPlainHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
    }
}

private fun createDarwinHttpClient(purpose: EgressPurpose): HttpClient = HttpClient(Darwin) {
    // ECH 网关插件必须装在 HttpCookies 之前：改写先发生，storage 随后对回环短路。
    installEchGate(defaultPurpose = purpose)
    // M5-5：换 BridgeCookiesStorage——CF 验证产物经 IosCookieBridge 进入 HTTP 层
    install(HttpCookies) {
        storage = BridgeCookiesStorage()
    }
    install(HttpTimeout) {
        // Api 档对齐 JVM；Download 不动（见文件 KDoc）。
        if (purpose == EgressPurpose.Api) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        } else {
            requestTimeoutMillis = 15_000
        }
    }
    // 与 jvm 侧 RetryInterceptor 同语义：只重试幂等方法、只重试传输层异常。
    // 判据两端必须一致，否则同一台设备上"页面能开、视频打不开"这类分叉会换个形式回来。
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
