package lovehan1me.data.network.egress

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 出口归因（[EgressTraceLink]）的回归。
 *
 * 钉住三件"读日志的人会依赖它"的事：
 * 1. **窗口边界**：窗口外的旧事件不得混进来（否则"这段网络"的数字会被上一次会话污染）；
 * 2. **无样本不参与均值**：超时/取消记 rtt = -1，把 -1 平均进去会把 RTT 算成负的；
 * 3. **按域分组**：窗口是全局的，混着看会把"第三方接口慢"读成"站点链路慢"。
 *
 * 事件用 [EgressEvents.emit] 直接造，不经 [EgressReporter] —— 本对象只读事件流，
 * 与域健康度无关，所以用例不必（也不该）动 [RouteRegistry]。
 */
class EgressTraceLinkTest {

    @AfterTest
    fun tearDown() {
        EgressEvents.clear()
    }

    private fun emit(
        atMs: Long,
        domain: DomainClass = DomainClass.Hanime,
        route: RouteId = RouteId.Gate,
        outcome: AttemptOutcome = AttemptOutcome.Success,
        rttMs: Long = 100L,
        budgetMs: Long = 60_000L,
    ) {
        EgressEvents.emit(EgressEvent(atMs, domain, route, outcome, rttMs, budgetMs))
    }

    @Test
    fun `空窗口给出明确提示而不是零值噪声`() {
        val window = EgressTraceLink.since(0L, 1_000L)
        assertTrue(window.isEmpty, "没有任何事件时窗口应为空")
        assertEquals(0, window.attempts)
        assertEquals(-1L, window.avgRttMs, "无样本时均值必须是 -1（-1 是本项目的'无样本'口径）")
        assertTrue(
            window.summary().contains("无出口尝试"),
            "空窗口的摘要应说明原因，而不是打出 '0 次尝试 · 均值 无样本' 这种噪声",
        )
    }

    @Test
    fun `窗口内按路由与域分组统计`() {
        emit(atMs = 100L, domain = DomainClass.Hanime, route = RouteId.Gate, rttMs = 100L)
        emit(atMs = 200L, domain = DomainClass.Hanime, route = RouteId.Gate, rttMs = 200L)
        emit(
            atMs = 300L,
            domain = DomainClass.Getchu,
            route = RouteId.Default,
            outcome = AttemptOutcome.TransportError,
            rttMs = -1L,
        )

        val window = EgressTraceLink.since(0L, 1_000L)

        assertEquals(3, window.attempts)
        assertEquals(2, window.gateAttempts)
        assertEquals(1, window.defaultAttempts)
        assertEquals(1, window.failures)
        assertEquals(150L, window.avgRttMs, "只有两条有样本，均值应为 (100+200)/2")
        assertEquals(60_000L, window.maxBudgetMs)
        assertEquals(mapOf(DomainClass.Hanime to 2, DomainClass.Getchu to 1), window.byDomain)
        assertTrue(window.summary().contains("网关 2 / 当前出口 1"), "摘要要区分两条路：${window.summary()}")
    }

    @Test
    fun `窗口外的事件不计入`() {
        emit(atMs = 50L, route = RouteId.Gate)
        emit(atMs = 500L, route = RouteId.Gate)
        emit(atMs = 2_000L, route = RouteId.Gate)

        val window = EgressTraceLink.since(100L, 1_000L)

        assertEquals(1, window.attempts, "只应算落在 [100,1000] 里的那一条")
        assertFalse(window.isEmpty)
    }

    @Test
    fun `全部无样本时均值保持负一`() {
        emit(atMs = 100L, outcome = AttemptOutcome.TransportError, rttMs = -1L)
        emit(atMs = 200L, outcome = AttemptOutcome.Blocked, rttMs = -1L)

        val window = EgressTraceLink.since(0L, 1_000L)

        assertEquals(2, window.attempts)
        assertEquals(2, window.failures, "Blocked 与 TransportError 都不是 Success")
        assertEquals(-1L, window.avgRttMs, "把 -1 平均进去会把 RTT 算成负的")
    }

    @Test
    fun `阻断类失败也是有样本的往返_不该被排除出均值`() {
        emit(atMs = 100L, outcome = AttemptOutcome.Blocked, rttMs = 80L)

        val window = EgressTraceLink.since(0L, 1_000L)

        assertEquals(1, window.failures)
        assertEquals(80L, window.avgRttMs, "阻断也是有样本的往返，不该被排除出均值")
    }
}
