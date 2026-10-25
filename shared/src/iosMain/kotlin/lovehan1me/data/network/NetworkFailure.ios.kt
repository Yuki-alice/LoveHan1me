package lovehan1me.data.network

import io.ktor.client.engine.darwin.DarwinHttpRequestException

/**
 * iOS 平台网络失败判定（网络优化 1.4）：`DarwinHttpRequestException.origin.code`
 * 按 `NSURLError*` 常量分支（此前全靠 message 关键字，Darwin 措辞是 OkHttp 翻不出来的）。
 *
 * 常量表（Foundation `NSURLError.h`）：
 * -1003 `CannotFindHost` / -1006 `DNSLookupFailed` → DNS；
 * -1001 `TimedOut` → 超时；
 * -1004 `CannotConnectToHost` → 建连失败；
 * -1005 `NetworkConnectionLost` → 连接中断；
 * -1009 `NotConnectedToInternet` → 本机无网络；
 * -1200 `SecureConnectionFailed` / -1202 `ServerCertificateUntrusted` → TLS。
 *
 * 其余 code（-999 取消、-1002 不支持的 URL 等业务面错误）返回 null，
 * 交还 common 关键字兜底 —— 与旧行为一致。
 */
actual fun Throwable.platformNetworkFailure(): NetworkFailure? {
    val error = (this as? DarwinHttpRequestException)?.origin ?: return null
    // NSError.code 的 K/N 绑定是整型位宽敏感的（设备/Simulator 均为 64 位），统一收 Int 比对。
    return when (error.code.toInt()) {
        -1003, -1006 -> NetworkFailure.Dns
        -1001 -> NetworkFailure.Timeout
        -1004 -> NetworkFailure.Connect
        -1005 -> NetworkFailure.ConnectionReset
        -1009 -> NetworkFailure.Offline
        -1200, -1202 -> NetworkFailure.Ssl
        else -> null
    }
}
