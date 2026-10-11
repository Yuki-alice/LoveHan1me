package lovehan1me.site

import kotlinx.coroutines.withTimeoutOrNull
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.network.HanimeNetwork

/**
 * 镜像探测的真实实现（B3-3b）——[MirrorProbe.probe] 的 `probeOne` 注入源。
 *
 * ## 判定口径：拿到响应即"可达"
 * 只要收到**任何** HTTP 响应（含 403 / CF 挑战页）就算可达并记 RTT，只有超时或传输异常
 * 才算不可达。理由：探测的目的是回答"这个镜像还能不能连上"，而一页 CF 挑战本身就证明
 * 链路是通的 —— 把它判成"不可达"会导致自动切换从一个能连的域逃到一个更糟的域。
 *
 * ## 为什么走 hanimeService
 * 探测必须**复用真实出站判定链**（网关改写 / DNS / 代理），否则测出来的延迟与实际首屏
 * 走的路径不是一回事。`getHomePage(url)` 接受显式 URL，正好可以对任意候选镜像发请求。
 * 本仓在 `HDns.getCDNList()` 上吃过"测试路径与实际路径不一致"的亏，这里不重蹈。
 */
object MirrorProbeClient {

    /** 单次探测上限：启动/换网路径上的探测必须短促，不能拖住后续流程。 */
    private const val PROBE_TIMEOUT_MS = 6_000L

    /** 探测 [host]（形如 `hanime1.me`）的往返毫秒；不可达返回 null。 */
    suspend fun probe(host: String): Long? {
        if (host.isBlank()) return null
        val url = "https://$host/"
        val started = currentEpochMillis()
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            // Ktor 默认不因非 2xx 抛异常：403 / 挑战页同样返回 HttpResponse ⇒ 计为可达。
            runCatching { HanimeNetwork.hanimeService.getHomePage(url) }.getOrNull()
                ?.let { currentEpochMillis() - started }
        }
    }
}
