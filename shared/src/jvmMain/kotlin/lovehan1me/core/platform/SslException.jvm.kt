package lovehan1me.core.platform

import javax.net.ssl.SSLHandshakeException

// jvmMain（android + desktop 共享）：真实 SSL 异常类型
internal actual fun sslHandshakeException(message: String): Exception = SSLHandshakeException(message)
internal actual fun Throwable.isSslHandshakeException(): Boolean = this is SSLHandshakeException
