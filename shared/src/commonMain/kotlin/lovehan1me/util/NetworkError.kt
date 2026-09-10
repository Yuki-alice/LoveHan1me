package lovehan1me.util

import lovehan1me.Res
import lovehan1me.home_error_connect
import lovehan1me.home_error_connection_interrupted
import lovehan1me.home_error_connection_reset
import lovehan1me.home_error_dns
import lovehan1me.home_error_forbidden
import lovehan1me.home_error_generic
import lovehan1me.home_error_not_found
import lovehan1me.home_error_server_unavailable
import lovehan1me.home_error_ssl
import lovehan1me.home_error_timeout
import org.jetbrains.compose.resources.StringResource

/**
 * M2：将首页加载异常映射为对应的错误提示字符串资源（自 `:app` `util/Networks.kt` 下沉）。
 *
 * 与 `:app` 版的差异：commonMain 没有 `java.net.*`（UnknownHostException /
 * SocketTimeoutException / ConnectException / SocketException）与
 * `javax.net.ssl.SSLHandshakeException` 类型可用，故去掉 `is` 类型分支，
 * 只保留消息关键字匹配——Ktor 在 Darwin/OkHttp 抛出的异常消息仍含同样关键字
 *（"unable to resolve host" / "timeout" / "ssl" / "failed to connect" 等），
 * 行为基本等价。包名函数名不变，`:app` 调用方零改动。
 */
fun Throwable.toNetworkErrorMessageRes(): StringResource {
    val rawMessage = message.orEmpty().lowercase()
    return when {
        rawMessage.contains("unable to resolve host") ||
            rawMessage.contains("no address associated with hostname") -> {
            Res.string.home_error_dns
        }

        rawMessage.contains("timeout") -> {
            Res.string.home_error_timeout
        }

        rawMessage.contains("ssl") ||
            rawMessage.contains("certificate") -> {
            Res.string.home_error_ssl
        }

        rawMessage.contains("failed to connect") -> {
            Res.string.home_error_connect
        }

        rawMessage.contains("connection reset") &&
            rawMessage.contains("socket") -> {
            Res.string.home_error_connection_interrupted
        }

        rawMessage.contains("connection reset") -> {
            Res.string.home_error_connection_reset
        }

        rawMessage.contains("403") -> {
            Res.string.home_error_forbidden
        }

        rawMessage.contains("404") -> {
            Res.string.home_error_not_found
        }

        rawMessage.contains("500") || rawMessage.contains("502") ||
            rawMessage.contains("503") || rawMessage.contains("504") -> {
            Res.string.home_error_server_unavailable
        }

        rawMessage.contains("unknownhost") ||
            rawMessage.contains("unknown host") -> {
            Res.string.home_error_dns
        }

        rawMessage.contains("connect") -> {
            Res.string.home_error_connect
        }

        else -> {
            Res.string.home_error_generic
        }
    }
}
