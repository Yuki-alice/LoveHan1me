package lovehan1me.data.network.egress

import kotlin.concurrent.Volatile

/**
 * 各域健康度的唯一持有者。
 *
 * 与旧全局熔断器同一处理方式：读写在网络线程上，`@Volatile` 保证跨线程可见，
 * 更新走不可变拷贝 —— 并发下丢一次上报不影响结论（"够不够糟糕"的启发式），不值得加锁。
 *
 * 换网/切站时调 [reset]（挂进既有 `onNetworkChanged` 复位入口，见 Phase 2）；
 * 旧网络上的结论全部失效，不带到新网络。
 */
object RouteRegistry {

    @Volatile
    private var snapshot: Map<DomainClass, RouteHealth> = emptyMap()

    fun healthOf(domain: DomainClass): RouteHealth = snapshot[domain] ?: RouteHealth()

    fun update(domain: DomainClass, transform: (RouteHealth) -> RouteHealth) {
        snapshot = snapshot + (domain to transform(healthOf(domain)))
    }

    /** 传 null = 全清（换网）；传域 = 只清该域（切站/单域自愈）。测试直接用它还原。 */
    fun reset(domain: DomainClass? = null) {
        snapshot = if (domain == null) emptyMap() else snapshot - domain
    }
}
