package io.github.daisukikaffuchino.han1meviewer.logic.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHanimeHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = ServiceCreator.hClient }
}

actual fun createDownloadHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = ServiceCreator.downloadClient }
}

actual fun createGetchuHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = ServiceCreator.getchuClient }
}

internal actual fun rebuildHttpClients() {
    ServiceCreator.rebuildOkHttpClient()
}
