package lovehan1me.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHanimeHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = ServiceCreator.hClient }
}

actual fun createGetchuHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = ServiceCreator.getchuClient }
}

actual fun createPlainHttpClient(): HttpClient = HttpClient(OkHttp)
