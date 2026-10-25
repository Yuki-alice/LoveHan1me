package lovehan1me.data.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import lovehan1me.Res
import lovehan1me.core.domain.exception.ParseException
import lovehan1me.core.util.toNetworkErrorMessageRes
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.NoRouteException
import lovehan1me.home_error_connect
import lovehan1me.home_error_connection_interrupted
import lovehan1me.home_error_connection_reset
import lovehan1me.home_error_dns
import lovehan1me.home_error_forbidden
import lovehan1me.home_error_generic
import lovehan1me.home_error_server_unavailable
import lovehan1me.home_error_ssl
import lovehan1me.home_error_timeout
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 错误分类 common 分支（网络优化 1.4）：Ktor/业务异常的 common 判定 +
 * 文案层映射（含 Other → 旧关键词兜底的零回归路径）。
 *
 * jvm 类型分支见 desktopTest `NetworkFailureJvmTest`；iOS NSURLError 码分支见
 * iosTest `NetworkFailureIosTest`。
 */
class NetworkFailureTest {

    private fun timeoutException() = HttpRequestTimeoutException(HttpRequestBuilder())

    // ── toNetworkFailure：common 类型分支 ─────────────────────────────

    @Test
    fun ktor请求超时判为Timeout() {
        assertEquals(NetworkFailure.Timeout, timeoutException().toNetworkFailure())
    }

    @Test
    fun 页面解析异常判为Parse() {
        assertEquals(NetworkFailure.Parse, ParseException("bad html").toNetworkFailure())
    }

    @Test
    fun 无可用出口判为NoRoute() {
        val e = NoRouteException(DomainClass.Hanime, "test")
        assertEquals(NetworkFailure.NoRoute, e.toNetworkFailure())
    }

    @Test
    fun 取消异常不误判为超时() {
        val e = CancellationException("timed out job was cancelled")
        assertEquals(NetworkFailure.Other::class, e.toNetworkFailure()::class)
    }

    @Test
    fun 无分类信息落Other() {
        assertEquals(NetworkFailure.Other::class, Exception("???").toNetworkFailure()::class)
    }

    // ── toNetworkErrorMessageRes：分类 → 字符串资源 ───────────────────

    @Test
    fun 超时异常文案() {
        assertEquals(Res.string.home_error_timeout, timeoutException().toNetworkErrorMessageRes())
    }

    /** 模拟 iOS 断网消息：含 "connect"，旧关键词路径误判为建连失败 —— 1.4 立项动机的现场证据。 */
    private class DarwinNotConnectedSim : Exception("The Internet connection appears to be offline.")

    @Test
    fun iOS断网消息在旧关键词路径失真_由平台分类接住() {
        // 无平台分支的 common 环境（= 旧 world）：落 Other → 关键词 "connect" → 建连失败。
        // 这里钉住旧路径的失真本身；iOS 上 platformNetworkFailure 先判 -1009 → 连接中断，
        // 见 iosTest `断网文案映射为连接中断`。桌面 JVM 同样由类型分支先接管，不走这里。
        assertEquals(Res.string.home_error_connect, DarwinNotConnectedSim().toNetworkErrorMessageRes())
    }

    // ── Other → 旧关键词兜底（零回归哨兵） ────────────────────────────

    @Test
    fun Other兜底_DNS关键词() {
        assertEquals(Res.string.home_error_dns, Exception("unable to resolve host hanime1.me").toNetworkErrorMessageRes())
    }

    @Test
    fun Other兜底_建连关键词() {
        assertEquals(Res.string.home_error_connect, Exception("failed to connect to /1.2.3.4").toNetworkErrorMessageRes())
    }

    @Test
    fun Other兜底_SSL关键词() {
        assertEquals(Res.string.home_error_ssl, Exception("certificate verify failed").toNetworkErrorMessageRes())
    }

    @Test
    fun Other兜底_403() {
        assertEquals(Res.string.home_error_forbidden, Exception("http 403").toNetworkErrorMessageRes())
    }

    @Test
    fun Other兜底_502() {
        assertEquals(Res.string.home_error_server_unavailable, Exception("http 502").toNetworkErrorMessageRes())
    }

    @Test
    fun Other兜底_连接重置() {
        assertEquals(Res.string.home_error_connection_reset, Exception("connection reset").toNetworkErrorMessageRes())
    }

    @Test
    fun Other兜底_连接中断细分() {
        assertEquals(
            Res.string.home_error_connection_interrupted,
            Exception("connection reset, socket write error").toNetworkErrorMessageRes(),
        )
    }

    @Test
    fun Other兜底_未识别消息落generic() {
        assertEquals(Res.string.home_error_generic, Exception("weird failure").toNetworkErrorMessageRes())
    }
}
