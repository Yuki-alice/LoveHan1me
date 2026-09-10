package lovehan1me.logic

// iosMain：无 javax.net.ssl.SSLHandshakeException，用 RuntimeException 兜底；
// 判定恒 false —— iOS 上 SSL 握手错误会走 handleException 的 else 分支原样抛出。
internal actual fun sslHandshakeException(message: String): Exception = RuntimeException(message)
internal actual fun Throwable.isSslHandshakeException(): Boolean = false
