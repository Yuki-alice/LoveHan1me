package lovehan1me.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import lovehan1me.Res
import lovehan1me.core.util.toNetworkErrorMessageRes
import lovehan1me.home_error_forbidden
import lovehan1me.home_error_generic
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 错误分类 jvm 类型分支（网络优化 1.4）：`java.net.*` / SSL 类型判定 +
 * Ktor `ResponseException`（MockEngine 真发）。
 *
 * common 分支见 commonTest `NetworkFailureTest`；iOS 码分支见 iosTest。
 */
class NetworkFailureJvmTest {

    @Test
    fun 未知主机判为DNS() {
        assertEquals(NetworkFailure.Dns, UnknownHostException("hanime1.me").toNetworkFailure())
    }

    @Test
    fun socket读超时判为Timeout() {
        assertEquals(NetworkFailure.Timeout, SocketTimeoutException("Read timed out").toNetworkFailure())
    }

    @Test
    fun 建连拒绝判为Connect() {
        assertEquals(NetworkFailure.Connect, ConnectException("Connection refused").toNetworkFailure())
    }

    @Test
    fun TLS握手失败判为Ssl() {
        assertEquals(NetworkFailure.Ssl, SSLHandshakeException("PKIX path validation failed").toNetworkFailure())
    }

    @Test
    fun HTTP403经ResponseException判为Http() = runBlocking {
        val client = HttpClient(MockEngine) {
            expectSuccess = true
            engine { addHandler { respondError(HttpStatusCode.Forbidden) } }
        }
        try {
            client.get("https://hanime1.me")
            throw AssertionError("expected ClientRequestException")
        } catch (e: io.ktor.client.plugins.ResponseException) {
            assertEquals(NetworkFailure.Http(403), e.toNetworkFailure())
            assertEquals(Res.string.home_error_forbidden, e.toNetworkErrorMessageRes())
        } finally {
            client.close()
        }
    }

    @Test
    fun 泛型IOException落Other走关键词兜底() {
        val e = IOException("unexpected end of stream on https://cdn.example.com")
        assertEquals(NetworkFailure.Other::class, e.toNetworkFailure()::class)
        assertEquals(Res.string.home_error_generic, e.toNetworkErrorMessageRes())
    }
}
