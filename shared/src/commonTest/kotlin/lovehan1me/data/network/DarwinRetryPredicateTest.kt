package lovehan1me.data.network

import io.ktor.http.HttpMethod
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.NoRouteException
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Darwin 重试谓词回归（纯函数，零 flake）。
 *
 * 钉住两条：诚实失败永不重试（重排也变不出路，只加延迟）；
 * 其余与 jvm `RetryInterceptor` 同语义（幂等 + 传输层异常）。
 */
class DarwinRetryPredicateTest {

    private fun noRoute() = NoRouteException(DomainClass.Hanime, "test")

    @Test
    fun `诚实失败不重试`() {
        assertFalse(shouldRetryDarwinFailure(HttpMethod.Get, noRoute()))
        assertFalse(shouldRetryDarwinFailure(HttpMethod.Post, noRoute()))
    }

    @Test
    fun `幂等传输异常才重试`() {
        assertTrue(shouldRetryDarwinFailure(HttpMethod.Get, IOException("reset")))
        assertTrue(shouldRetryDarwinFailure(HttpMethod.Head, IOException("reset")))
        assertFalse(shouldRetryDarwinFailure(HttpMethod.Post, IOException("reset")))
        assertFalse(shouldRetryDarwinFailure(HttpMethod.Get, IllegalStateException("boom")))
    }

    @Test
    fun `NoRoute向上是IO异常`() {
        // 与 okio 在各平台的 actual 一致：JVM 上它就是 java.io.IOException，
        // OkHttp 才肯把它交进 Callback 而不是抛到裸线程。
        val failure: Exception = noRoute()
        assertTrue(failure is IOException)
    }
}
