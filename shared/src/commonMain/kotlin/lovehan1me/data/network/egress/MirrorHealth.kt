package lovehan1me.data.network.egress

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.PlatformLock
import lovehan1me.core.util.withLock
import kotlin.concurrent.Volatile

/**
 * 镜像档案（F12/B3-2）：每个镜像**跨切站**的健康记录。
 *
 * ## 与 [RouteRegistry] 的分工
 * - [RouteRegistry] 管**当前会话**的熔断，切站即清 —— 换镜像不该背着旧账（F22 已保证）；
 * - 本档案**不随切站清除**，于是"哪个镜像一直稳、哪个老是被封"能被记住。
 *
 * 这份档案是 B3-3 自动选优**唯一**的依据：切一次站就把档案清掉，等于每次都瞎选。
 *
 * ## 为什么只有站点镜像进这份档案
 * 记账键只收站点域名（见 [mirrorHostFor]）。图床 / CDN 的 host 千变万化，
 * 逐个记账会把内存撑爆 —— 本仓在 `HCookieJar` 上已经栽过一次（无界累积最终全站 400），
 * 同样的坑不踩第二遍。
 */
object MirrorRegistry {

    private val lock = PlatformLock()

    @Volatile
    private var snapshot: Map<String, RouteHealth> = emptyMap()

    fun healthOf(mirrorHost: String): RouteHealth = snapshot[mirrorHost] ?: RouteHealth()

    fun update(mirrorHost: String, transform: (RouteHealth) -> RouteHealth) {
        lock.withLock {
            snapshot = snapshot + (mirrorHost to transform(healthOf(mirrorHost)))
        }
    }

    /**
     * 传 null = 全清（**换网**：延迟特征变了，旧档案不可比）；
     * 传 host = 只清该镜像。
     *
     * ⚠️ **切站不要调它** —— 切站清档案会让自动选优每次从零开始，那正是本档案存在的反面。
     */
    fun reset(host: String? = null) {
        lock.withLock {
            snapshot = if (host == null) emptyMap() else snapshot - host
        }
    }

    /**
     * 镜像择优（F10/B3-3 的数据源）：未熔断的在前，其次成功率降序、EWMA 升序。
     *
     * 排序口径与 [EgressScheduler.plan] **刻意一致** —— "探测出来的最快"和
     * "档案认为的最好"若用两套排序，用户会看到自动切换挑了一个明显更差的镜像。
     * 熔断维度只看 [RouteId.Gate]：镜像之间的差异主要体现在网关那一跳上。
     *
     * 无样本（EWMA < 0）排最后但**不剔除** —— 没试过的镜像不该被永久判死，
     * 它只是"暂时没有证据"，给一次机会正是自动切换存在的意义。
     */
    fun rankMirrors(
        candidates: List<String>,
        nowMs: Long = currentEpochMillis(),
    ): List<String> = candidates.sortedWith(
        compareBy<String> { healthOf(it).isOpen(RouteId.Gate, nowMs) }
            .thenByDescending { healthOf(it).single(RouteId.Gate).successRate() }
            .thenBy { healthOf(it).single(RouteId.Gate).ewmaRttMs.takeIf { rtt -> rtt >= 0 } ?: Long.MAX_VALUE },
    )
}
