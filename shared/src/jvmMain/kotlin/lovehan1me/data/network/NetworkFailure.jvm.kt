package lovehan1me.data.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * jvm 平台网络失败判定（网络优化 1.4）：按 `java.net.*` / SSL 类型分支。
 *
 * OkHttp 会把底层异常原样抛出（Dns 解析 → [UnknownHostException]、读超时 →
 * [SocketTimeoutException]、建连拒绝 → [ConnectException]），类型分支在这里就能定性；
 * 泛型 `IOException`（"unexpected end of stream" 之类）交还 common 关键字兜底，
 * 保持既有文案映射零回归。
 */
actual fun Throwable.platformNetworkFailure(): NetworkFailure? = when (this) {
    is UnknownHostException -> NetworkFailure.Dns
    is SocketTimeoutException -> NetworkFailure.Timeout
    is SSLException -> NetworkFailure.Ssl
    is ConnectException -> NetworkFailure.Connect
    else -> null
}
