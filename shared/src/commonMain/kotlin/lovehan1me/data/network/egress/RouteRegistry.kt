package lovehan1me.data.network.egress

import lovehan1me.core.util.PlatformLock
import lovehan1me.core.util.withLock
import kotlin.concurrent.Volatile

/**
 * 各域健康度的唯一持有者。
 *
 * 换网/切站时调 [reset]（挂进既有 `onNetworkChanged` 复位入口，见 Phase 2）；
 * 旧网络上的结论全部失效，不带到新网络。
 *
 * ## 为什么 [update] 要加锁
 * 原先它是"读快照 → 算新值 → 写回"三步，靠 `@Volatile` 保证可见性但不保证原子性：
 * 图片链几十个请求并发上报时，两次更新会互相覆盖，**熔断计数少算一次**。
 *
 * 成功率排序少一次无所谓，但熔断计数少一次意味着本该在第 [RouteHealth.FAILURE_THRESHOLD]
 * 次熔断的路由要拖到下一次，而 [RouteHealth.COOLDOWN_MS] 是 5 分钟 ——
 * 代价不是"少一条上报"，是"多 5 分钟坏路由"。为此值得加一把锁。
 *
 * 锁内只做一次纯函数变换（[RouteHealth.onResult]），不回调、不阻塞 IO，无死锁面。
 */
object RouteRegistry {

    private val lock = PlatformLock()

    @Volatile
    private var snapshot: Map<DomainClass, RouteHealth> = emptyMap()

    fun healthOf(domain: DomainClass): RouteHealth = snapshot[domain] ?: RouteHealth()

    fun update(domain: DomainClass, transform: (RouteHealth) -> RouteHealth) {
        lock.withLock {
            snapshot = snapshot + (domain to transform(healthOf(domain)))
        }
    }

    /** 传 null = 全清（换网）；传域 = 只清该域（切站/单域自愈）。测试直接用它还原。 */
    fun reset(domain: DomainClass? = null) {
        lock.withLock {
            snapshot = if (domain == null) emptyMap() else snapshot - domain
        }
    }
}
