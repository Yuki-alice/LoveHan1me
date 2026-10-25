package lovehan1me.core.util

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
import lovehan1me.data.network.NetworkFailure
import lovehan1me.data.network.toNetworkFailure
import org.jetbrains.compose.resources.StringResource

/**
 * 将首页加载异常映射为对应的错误提示字符串资源（自 `:app` `util/Networks.kt` 下沉）。
 *
 * 网络优化 1.4：内部改走结构化分类（`toNetworkFailure`，类型/错误码判定）——
 * iOS 上不再靠 OkHttp 措辞的关键字匹配猜类型（Darwin 措辞/系统语言会让它失真）。
 * 签名与字符串资源不变，调用方零改动。
 *
 * 兜底语义：分类落 `Other`（含 jvm 泛型 IOException、旧关键字才认得的奇葩消息）时
 * 沿用旧关键词匹配 —— 旧路径零回归，新分类逐步接管。枚举无字符串资源的状态
 * （Offline/Parse/NoRoute）映射到语义最近的既有资源：
 * - Offline / ConnectionReset → 连接中断类（jvm 侧 "connection reset"+socket 的旧细分
 *   由关键词判据保留在 reset 分支内）；
 * - Parse / NoRoute → generic（旧路径里这两类消息本就落 generic，行为不变）。
 */
fun Throwable.toNetworkErrorMessageRes(): StringResource = when (val failure = toNetworkFailure()) {
    NetworkFailure.Dns -> Res.string.home_error_dns
    NetworkFailure.Timeout -> Res.string.home_error_timeout
    NetworkFailure.Ssl -> Res.string.home_error_ssl
    NetworkFailure.Connect -> Res.string.home_error_connect
    NetworkFailure.ConnectionReset -> legacyResetMessageRes()
    NetworkFailure.Offline -> Res.string.home_error_connection_interrupted
    is NetworkFailure.Http -> httpCodeMessageRes(failure.code)
    NetworkFailure.Parse -> Res.string.home_error_generic
    NetworkFailure.NoRoute -> Res.string.home_error_generic
    is NetworkFailure.Other -> legacyKeywordMessageRes()
}

/** HTTP 状态码 → 字符串资源（旧 "403"/"404"/"5xx" 关键字分支的等价迁移）。 */
private fun httpCodeMessageRes(code: Int): StringResource = when (code) {
    403 -> Res.string.home_error_forbidden
    404 -> Res.string.home_error_not_found
    in 500..599 -> Res.string.home_error_server_unavailable
    else -> Res.string.home_error_generic
}

/**
 * 旧连接中断细分：jvm "connection reset by peer (socket write error)" 类消息带 socket 措辞
 * → interrupted，其余 → reset。只在分类已定为 [NetworkFailure.ConnectionReset] 后引用，
 * 作为文案层的细分保留。
 */
private fun Throwable.legacyResetMessageRes(): StringResource {
    val rawMessage = message.orEmpty().lowercase()
    return if (rawMessage.contains("socket")) {
        Res.string.home_error_connection_interrupted
    } else {
        Res.string.home_error_connection_reset
    }
}

/**
 * 旧关键字匹配全表（ Other 兜底，原样保留 —— 判据措辞是 OkHttp 的，在 Darwin 上
 * 失真正是 1.4 要修的事，但没分类可依时它仍是最好的猜测）。
 */
private fun Throwable.legacyKeywordMessageRes(): StringResource {
    val rawMessage = message.orEmpty().lowercase()
    return when {
        rawMessage.contains("unable to resolve host") ||
            rawMessage.contains("no address associated with hostname") -> Res.string.home_error_dns

        rawMessage.contains("timeout") -> Res.string.home_error_timeout

        rawMessage.contains("ssl") || rawMessage.contains("certificate") -> Res.string.home_error_ssl

        rawMessage.contains("failed to connect") -> Res.string.home_error_connect

        rawMessage.contains("connection reset") && rawMessage.contains("socket") ->
            Res.string.home_error_connection_interrupted

        rawMessage.contains("connection reset") -> Res.string.home_error_connection_reset

        rawMessage.contains("403") -> Res.string.home_error_forbidden
        rawMessage.contains("404") -> Res.string.home_error_not_found
        rawMessage.contains("500") || rawMessage.contains("502") ||
            rawMessage.contains("503") || rawMessage.contains("504") ->
            Res.string.home_error_server_unavailable

        rawMessage.contains("unknownhost") || rawMessage.contains("unknown host") ->
            Res.string.home_error_dns

        rawMessage.contains("connect") -> Res.string.home_error_connect

        else -> Res.string.home_error_generic
    }
}
