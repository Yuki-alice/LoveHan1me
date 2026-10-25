package lovehan1me.data.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import lovehan1me.core.domain.exception.ParseException
import lovehan1me.data.network.egress.NoRouteException
import kotlinx.coroutines.CancellationException

/**
 * 结构化网络失败分类（网络优化 1.4）：把"错误文案关键词猜类型"换成"类型/错误码判定"。
 *
 * 背景：`core/util/NetworkError.kt` 的旧映射全靠 message 关键字（关键字是 OkHttp 措辞），
 * 在 iOS 上因 Darwin 的 NSSuperURL 错误措辞/系统语言本地化而失真 —— 同样断网，
 * 桌面显示 DNS、iOS 显示 generic。现在三层判定：
 *
 * 1. **common 类型分支**（[toNetworkFailure]）：Ktor 公共异常 + 本项目自抛的业务异常；
 * 2. **平台分支**（[platformNetworkFailure]）：jvm 按 `java.net.*`/SSL 类型，
 *    iOS 按 `DarwinHttpRequestException.origin.code`（NSURLError* 常量）；
 * 3. **关键字兜底**：以上都不中时落 [Other]，文案层沿用旧关键词匹配 ——
 *    旧路径零回归，新分类逐步接管。
 *
 * 分类只用于展示层映射（`toNetworkErrorMessageRes`）与逃生舱（3.3 的强制直连/网关），
 * 不参与重试/熔断决策（那是调度器与执行器的事，别把两套判据搅在一起）。
 */
sealed class NetworkFailure {

    /** DNS 解析失败（jvm `UnknownHostException`；iOS -1003/-1006）。 */
    data object Dns : NetworkFailure()

    /** 超时（请求/连接/读空闲任一档；iOS -1001）。 */
    data object Timeout : NetworkFailure()

    /** TLS 证书/握手失败（jvm `SSLException`；iOS -1200/-1202）。 */
    data object Ssl : NetworkFailure()

    /** 建连失败（TCP 层拒绝/不可达；iOS -1004）。 */
    data object Connect : NetworkFailure()

    /** 连接被重置/中途断流（jvm "connection reset"；iOS -1005）。 */
    data object ConnectionReset : NetworkFailure()

    /** 本机无网络（iOS -1009 `NotConnectedToInternet`；jvm 没有对等类型，靠兜底）。 */
    data object Offline : NetworkFailure()

    /** HTTP 状态码错误（Ktor `ResponseException`；网关 502 等）。 */
    data class Http(val code: Int) : NetworkFailure()

    /** 页面解析失败（本项目 `ParseException`）。 */
    data object Parse : NetworkFailure()

    /** 出站计划空/所有候选失败（调度器 `NoRouteException`）。 */
    data object NoRoute : NetworkFailure()

    /** 以上皆非 —— 文案层退回旧关键词匹配，保证既有映射零回归。 */
    data class Other(val cause: Throwable) : NetworkFailure()
}

/**
 * 异常 → 结构化分类。先走 common 类型分支，再问平台分支（[platformNetworkFailure]），
 * 都不中落 [NetworkFailure.Other]。
 *
 * `CancellationException` 刻意不参与分类（取消不是网络失败）—— 落 [NetworkFailure.Other]
 * 而不是误判成超时（消息里可能带 "timed out" 的协程取消措辞）。
 */
fun Throwable.toNetworkFailure(): NetworkFailure = when {
    this is CancellationException -> NetworkFailure.Other(this)
    this is HttpRequestTimeoutException -> NetworkFailure.Timeout
    this is ConnectTimeoutException -> NetworkFailure.Timeout
    this is SocketTimeoutException -> NetworkFailure.Timeout
    this is ParseException -> NetworkFailure.Parse
    this is NoRouteException -> NetworkFailure.NoRoute
    this is ResponseException -> NetworkFailure.Http(response.status.value)
    else -> platformNetworkFailure() ?: NetworkFailure.Other(this)
}

/**
 * 平台专属判定；无法判定返回 null（交还 common 兜底）。
 * expect/actual 而非 open 类：分类是封闭集合，平台只补"各自引擎的异常面"。
 */
expect fun Throwable.platformNetworkFailure(): NetworkFailure?
