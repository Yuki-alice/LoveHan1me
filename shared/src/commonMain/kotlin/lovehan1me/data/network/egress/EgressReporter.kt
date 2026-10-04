package lovehan1me.data.network.egress

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.LogUtil
import kotlin.concurrent.Volatile

/** 诊断事件：与上报同源，用户侧三态与诊断页都从这里读（决策第 2 条）。 */
data class EgressEvent(
    val atMs: Long,
    val domain: DomainClass,
    val route: RouteId,
    val outcome: AttemptOutcome,
    val rttMs: Long,
    /** 本步预算（毫秒）；-1 = 上报方不知道（旧调用点兼容）。诊断页靠它回答"为什么这次慢"。 */
    val budgetMs: Long = -1L,
)

/**
 * 诊断事件流：ring buffer，近 200 条。只追加、不修改，上限截断。
 *
 * 与 [RouteRegistry] 同一并发策略：`@Volatile` + 不可变拷贝，执行器线程直接调，
 * 不进锁、不落盘（导出是 Phase 4 诊断页的事）。
 */
object EgressEvents {

    const val CAPACITY = 200

    @Volatile
    private var buffer: List<EgressEvent> = emptyList()

    fun emit(event: EgressEvent) {
        buffer = (buffer + event).takeLast(CAPACITY)
    }

    fun recent(): List<EgressEvent> = buffer

    internal fun clear() {
        buffer = emptyList()
    }
}

/**
 * 两执行器共用的上报入口（Phase 2/3 由 OkHttp 拦截器与 Darwin 插件调用）。
 *
 * 一次上报做两件事：喂域健康（动态择优的数据源）+ 记诊断事件（用户三态与诊断页的数据源）。
 * 超时/取消不记 RTT：`rttMs < 0` 表示无样本，只记成败（窗口照推，EWMA 不动）。
 */
object EgressReporter {

    fun report(
        domain: DomainClass,
        route: RouteId,
        outcome: AttemptOutcome,
        rttMs: Long,
        nowMs: Long = currentEpochMillis(),
        budgetMs: Long = -1L,
    ) {
        val before = RouteRegistry.healthOf(domain)
        val after = before.onResult(route, outcome, rttMs, nowMs)
        RouteRegistry.update(domain) { after }
        EgressEvents.emit(EgressEvent(nowMs, domain, route, outcome, rttMs, budgetMs))
        // 熔断/恢复跳变打一行：自动化验收（tools/phase5）与人工排障都靠它断言，
        // UI 三态读不到的地方（iOS 占位页、后台）也有据可查。只看 Gate 步。
        if (route == RouteId.Gate) {
            val wasOpen = before.isOpen(route, nowMs)
            val isOpen = after.isOpen(route, nowMs)
            if (!wasOpen && isOpen) {
                LogUtil.w("Egress", "网关熔断（$domain）：暂由备用出口接管")
            } else if (wasOpen && !isOpen) {
                LogUtil.i("Egress", "网关恢复（$domain）：半开试探成功")
            }
        }
    }
}
