package lovehan1me.data.network.egress

import lovehan1me.core.platform.currentEpochMillis

/**
 * **出口归因**：把 [EgressEvents] 的尝试记录折算成"这段网络走的是什么出口"。
 *
 * ## 为什么是"拉"而不是"推"（B0-3 的设计约束）
 * [EgressReporter] 的职责被刻意压在两件事上（喂域健康 + 记诊断事件），
 * 埋点**不能渗进那条热路径** —— 所以本对象不订阅、不注册回调，只在需要时按时间窗拉一次。
 * [EgressScheduler]（判定）与 [EgressReporter]（记账）都不认识本文件。
 *
 * ## 为什么按时间窗而不是按请求
 * [EgressEvents] 是进程级 ring buffer，一次首屏会并发几十个请求，
 * 事件里也没有请求 id —— 逐个请求归因既做不到也没必要。
 * 首屏要回答的问题本来就是"这一屏的网络走了什么路、失败过几次"，
 * 窗口内的并发正是答案（"30 个资源里 28 个走网关"）。
 *
 * ## 已知边界（诚实记录）
 * 窗口是**全局**的：同一窗口内别的域（如弹弹play、更新检查）的事件也会被算进来。
 * 所以统计**按 [DomainClass] 分组**输出，而不是给一个混合的平均值 ——
 * 混着看会把"第三方接口慢"读成"站点链路慢"。
 *
 * ## 与两套追踪器的关系
 * `StartupTrace` / `PlayerTrace` 各自提供 `egress(label, sinceMs)` 调本对象，
 * 把归因打到**同一条时间线**上。本文件不反向依赖它们。
 */
object EgressTraceLink {

    /**
     * 一次窗口聚合。全零 = 窗口内没有任何出口尝试（例如命中缓存、或请求还没发出）。
     *
     * [avgRttMs] 与 [maxBudgetMs] 用 -1 表示"无样本"，与 [EgressEvent] 的口径一致
     * （超时/取消不记 RTT，只记成败）。
     */
    data class EgressWindow(
        val attempts: Int = 0,
        val gateAttempts: Int = 0,
        val defaultAttempts: Int = 0,
        val failures: Int = 0,
        val avgRttMs: Long = -1L,
        val maxBudgetMs: Long = -1L,
        val byDomain: Map<DomainClass, Int> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = attempts == 0

        /**
         * 一行摘要。日志与诊断页共用同一口径，避免两边各写一套措辞。
         *
         * `RouteId.Default` 按它自己的诚实命名写作"当前出口"，不谎称"直连"——
         * 折叠后它可能就是代理（见 [RouteId] 的 KDoc）。
         */
        fun summary(): String {
            if (isEmpty) return "无出口尝试（多来自缓存命中）"
            val rtt = if (avgRttMs >= 0) "${avgRttMs}ms" else "无样本"
            val domains = byDomain.entries.joinToString("+") { "${it.key}=${it.value}" }
            return "$attempts 次尝试（网关 $gateAttempts / 当前出口 $defaultAttempts）" +
                " · 失败 $failures · 均值 $rtt · 域 $domains"
        }
    }

    /**
     * 取 `[sinceMs, untilMs]` 窗口内的事件聚合。
     *
     * 时间基准是 [currentEpochMillis]，与 [EgressEvent.atMs] 同源 ——
     * 调用方用同一次 [currentEpochMillis] 取起点即可，不需要自己做时间换算。
     *
     * `untilMs` 可显式传入供离线断言（测试用手造事件与固定时刻）。
     */
    fun since(sinceMs: Long, untilMs: Long = currentEpochMillis()): EgressWindow {
        var attempts = 0
        var gate = 0
        var fallback = 0
        var failures = 0
        var rttSum = 0L
        var rttSamples = 0L
        var maxBudget = -1L
        val byDomain = mutableMapOf<DomainClass, Int>()

        for (event in EgressEvents.recent()) {
            if (event.atMs < sinceMs || event.atMs > untilMs) continue
            attempts++
            when (event.route) {
                RouteId.Gate -> gate++
                RouteId.Default -> fallback++
                // HTTP 执行层不产生隧道步（隧道是播放器 / CF 验证窗的事）；
                // 真出现了也计入 attempts 与健康，只是不归到任一"路由"上。
                RouteId.GateTunnel -> Unit
            }
            if (event.outcome != AttemptOutcome.Success) failures++
            if (event.rttMs >= 0) {
                rttSum += event.rttMs
                rttSamples++
            }
            if (event.budgetMs > maxBudget) maxBudget = event.budgetMs
            byDomain[event.domain] = (byDomain[event.domain] ?: 0) + 1
        }

        return EgressWindow(
            attempts = attempts,
            gateAttempts = gate,
            defaultAttempts = fallback,
            failures = failures,
            avgRttMs = if (rttSamples > 0) rttSum / rttSamples else -1L,
            maxBudgetMs = maxBudget,
            byDomain = byDomain,
        )
    }
}
