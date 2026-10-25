package lovehan1me.data.network

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import lovehan1me.Res
import lovehan1me.core.util.toNetworkErrorMessageRes
import lovehan1me.home_error_connection_interrupted
import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 错误分类 iOS 码分支（网络优化 1.4）：`DarwinHttpRequestException.origin.code`
 * 按 NSURLError* 常量判定 —— 本计划立项的动机用例（旧关键字匹配在 Darwin 上失真）。
 */
class NetworkFailureIosTest {

    private fun darwinError(code: Int): DarwinHttpRequestException {
        val error = NSError(
            domain = "NSURLErrorDomain",
            code = code.toLong(),
            userInfo = mapOf<Any?, Any?>(
                NSLocalizedDescriptionKey to "simulated NSURLError($code)",
            ),
        )
        return DarwinHttpRequestException(origin = error)
    }

    @Test
    fun 找不到主机判为DNS() {
        assertEquals(NetworkFailure.Dns, darwinError(-1003).toNetworkFailure())
        assertEquals(NetworkFailure.Dns, darwinError(-1006).toNetworkFailure())
    }

    @Test
    fun 超时判为Timeout() {
        assertEquals(NetworkFailure.Timeout, darwinError(-1001).toNetworkFailure())
    }

    @Test
    fun 无法建连判为Connect() {
        assertEquals(NetworkFailure.Connect, darwinError(-1004).toNetworkFailure())
    }

    @Test
    fun 连接丢失判为ConnectionReset() {
        assertEquals(NetworkFailure.ConnectionReset, darwinError(-1005).toNetworkFailure())
    }

    @Test
    fun 本机无网络判为Offline() {
        assertEquals(NetworkFailure.Offline, darwinError(-1009).toNetworkFailure())
    }

    @Test
    fun TLS失败判为Ssl() {
        assertEquals(NetworkFailure.Ssl, darwinError(-1200).toNetworkFailure())
        assertEquals(NetworkFailure.Ssl, darwinError(-1202).toNetworkFailure())
    }

    @Test
    fun 非网络面错误码交还兜底() {
        assertNull(darwinError(-999).platformNetworkFailure())
        assertEquals(NetworkFailure.Other::class, darwinError(-999).toNetworkFailure()::class)
    }

    @Test
    fun 非Darwin异常交还兜底() {
        assertNull(Exception("plain").platformNetworkFailure())
    }

    @Test
    fun 断网文案映射为连接中断() {
        assertEquals(
            lovehan1me.Res.string.home_error_connection_interrupted,
            darwinError(-1009).toNetworkErrorMessageRes(),
        )
    }
}
