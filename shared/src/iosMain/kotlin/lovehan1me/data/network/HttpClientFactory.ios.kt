package lovehan1me.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpMethod
import kotlinx.io.IOException

actual fun createHanimeHttpClient(): HttpClient = createDarwinHttpClient()

actual fun createDownloadHttpClient(): HttpClient = createDarwinHttpClient()

actual fun createGetchuHttpClient(): HttpClient = createDarwinHttpClient()

actual fun createPlainHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
    }
}

internal actual fun rebuildHttpClients() {
    // iOS 无 ServiceCreator/OkHttp 层，重建即重建上面的 Darwin client，无需额外动作
}

private fun createDarwinHttpClient(): HttpClient = HttpClient(Darwin) {
    // ECH 网关插件必须装在 HttpCookies 之前：改写先发生，storage 随后对回环短路。
    installEchGate()
    // M5-5：换 BridgeCookiesStorage——CF 验证产物经 IosCookieBridge 进入 HTTP 层
    install(HttpCookies) {
        storage = BridgeCookiesStorage()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
    }
    // 与 jvm 侧 RetryInterceptor 同语义：只重试幂等方法、只重试传输层异常。
    // 判据两端必须一致，否则同一台设备上"页面能开、视频打不开"这类分叉会换个形式回来。
    install(HttpRequestRetry) {
        // 状态码一律不重试：网关的 502 重试与 CF 验证后的续跑各自负责那一层，
        // 在这里再叠一层会让两套机制互相看不见。
        noRetry()
        retryOnExceptionIf(maxRetries = 2) { request, cause ->
            val idempotent = request.method == HttpMethod.Get || request.method == HttpMethod.Head
            idempotent && (cause is IOException || cause is HttpRequestTimeoutException)
        }
        constantDelay(300, 1_000, true)
    }
}
