package lovehan1me.logic

/**
 * SSL 异常判定（P4b：NetworkRepo.handleException 的 catch-rethrow 用）。
 * jvmMain（android+desktop）用真实 javax.net.ssl.SSLHandshakeException；
 * iosMain 无此异常类型，用 RuntimeException 替代且 is 判定恒 false。
 */
internal expect fun sslHandshakeException(message: String): Exception
internal expect fun Throwable.isSslHandshakeException(): Boolean
