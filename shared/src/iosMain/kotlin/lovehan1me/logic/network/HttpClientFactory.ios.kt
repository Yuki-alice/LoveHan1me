package lovehan1me.logic.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies

actual fun createHanimeHttpClient(): HttpClient = createDarwinHttpClient()

actual fun createDownloadHttpClient(): HttpClient = createDarwinHttpClient()

actual fun createGetchuHttpClient(): HttpClient = createDarwinHttpClient()

internal actual fun rebuildHttpClients() {
    // iOS 无 ServiceCreator/OkHttp 层，重建即重建上面的 Darwin client，无需额外动作
}

private fun createDarwinHttpClient(): HttpClient = HttpClient(Darwin) {
    // M5-5：换 BridgeCookiesStorage——CF 验证产物经 IosCookieBridge 进入 HTTP 层
    install(HttpCookies) {
        storage = BridgeCookiesStorage()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
    }
}
