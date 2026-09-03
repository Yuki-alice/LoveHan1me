package io.github.daisukikaffuchino.han1meviewer.logic.network

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
    // cookie 仅内存态；TODO：后续接 SettingsRepository 持久化
    install(HttpCookies)
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
    }
}
