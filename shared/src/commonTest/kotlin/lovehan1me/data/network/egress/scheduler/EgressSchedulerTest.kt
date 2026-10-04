package lovehan1me.data.network.egress.scheduler

import lovehan1me.data.network.egress.AttemptOutcome
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressBudgets
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.EgressRequest
import lovehan1me.data.network.egress.EgressScheduler
import lovehan1me.data.network.egress.EgressState
import lovehan1me.data.network.egress.ForceMode
import lovehan1me.data.network.egress.GateSkipReason
import lovehan1me.data.network.egress.GateState
import lovehan1me.data.network.egress.ProxyState
import lovehan1me.data.network.egress.RouteHealth
import lovehan1me.data.network.egress.RouteId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 调度器回归（纯函数：健康由调用方传入，不读全局单例，故零 flake）。
 *
 * 这是 Phase 1 的核心防线，钉住决策第 1/3/5/7 条：
 * 网关主力、受限域无直连、按域择优粘滞、强制覆盖。
 */
class EgressSchedulerTest {

    private val now = 1_700_000_000_000L
    private val readyGate = GateState(enabled = true, port = 8080, circuitOpen = false)
    private val hanimeApi = EgressRequest("https://hanime1.me/search?q=abc")

    private fun state(
        gate: GateState = readyGate,
        proxy: ProxyState = ProxyState.None,
    ) = EgressState(gate = gate, proxy = proxy)

    @Test
    fun `受限域无代理时只有网关且无直连`() {
        val plan = EgressScheduler.plan(hanimeApi, state(), RouteHealth(), now)
        assertEquals(listOf(RouteId.Gate), plan.attempts.map { it.route })
        assertEquals(DomainClass.Hanime, plan.domain)
        assertFalse(plan.isEmpty)
        assertNull(plan.skipped)
        assertEquals("http://127.0.0.1:8080/search?q=abc", plan.attempts.single().rewrite?.url)
        assertEquals(60_000L, plan.attempts.single().budgetMs)
    }

    @Test
    fun `第三方域不进网关且不是故障`() {
        val plan = EgressScheduler.plan(
            EgressRequest("https://api.dandanplay.net/api/v2/search/anime"),
            state(),
            RouteHealth(),
            now,
        )
        assertEquals(DomainClass.ThirdParty, plan.domain)
        assertTrue(plan.attempts.none { it.route == RouteId.Gate })
        assertEquals(listOf(RouteId.Direct), plan.attempts.map { it.route })
        assertNull(plan.skipped, "故意不进网关，不是故障，不该报熔断")
    }

    @Test
    fun `网关开着但没跑起来且无代理时受限域表空`() {
        val plan = EgressScheduler.plan(
            hanimeApi,
            state(gate = readyGate.copy(port = -1)),
            RouteHealth(),
            now,
        )
        assertTrue(plan.isEmpty, "直连已知撞 RST，不排就是不浪费时间")
        assertEquals(DomainClass.Hanime, plan.domain)
        assertEquals(GateSkipReason.NotRunning, plan.skipped)
    }

    @Test
    fun `网关关闭时回到旧语义走直连`() {
        val plan = EgressScheduler.plan(
            hanimeApi,
            state(gate = readyGate.copy(enabled = false)),
            RouteHealth(),
            now,
        )
        assertEquals(listOf(RouteId.Direct), plan.attempts.map { it.route })
        assertEquals(GateSkipReason.Disabled, plan.skipped)
    }

    @Test
    fun `有手填代理时网关之后排用户代理`() {
        val plan = EgressScheduler.plan(
            hanimeApi,
            state(proxy = ProxyState.Explicit("203.0.113.7", 7890, socks = false)),
            RouteHealth(),
            now,
        )
        assertEquals(listOf(RouteId.Gate, RouteId.UserProxy), plan.attempts.map { it.route })
    }

    @Test
    fun `旧全局熔断输入被忽略`() {
        val plan = EgressScheduler.plan(
            hanimeApi,
            state(gate = readyGate.copy(circuitOpen = true)),
            RouteHealth(),
            now,
        )
        assertEquals(listOf(RouteId.Gate), plan.attempts.map { it.route })
        assertNull(plan.skipped, "熔断唯一来源是按域健康，旧全局输入不再生效")
    }

    @Test
    fun `本域熔断时网关出表并给出原因`() {
        val health = RouteHealth().onResult(RouteId.Gate, AttemptOutcome.Blocked, 100L, now)
        val plan = EgressScheduler.plan(hanimeApi, state(), health, now)
        assertTrue(plan.attempts.none { it.route == RouteId.Gate })
        assertEquals(GateSkipReason.CircuitOpen, plan.skipped)
    }

    @Test
    fun `隧道与改写道同熔断源`() {
        val domain = DomainClass.Hanime
        assertEquals(
            "http://127.0.0.1:8080",
            EgressScheduler.tunnelUrl(domain, state(), RouteHealth(), ForceMode.Auto, now),
        )
        val melted = RouteHealth().onResult(RouteId.Gate, AttemptOutcome.Blocked, 100L, now)
        assertNull(
            EgressScheduler.tunnelUrl(domain, state(), melted, ForceMode.Auto, now),
            "改写道被熔断摘掉时，隧道必须一起消失，否则半开泄漏",
        )
        assertNull(
            EgressScheduler.tunnelUrl(domain, state(), RouteHealth(), ForceMode.ForceDirect, now),
            "强制直连时隧道不得劫持验证窗与播放器",
        )
        assertNull(
            EgressScheduler.tunnelUrl(domain, state(gate = readyGate.copy(enabled = false)), RouteHealth(), ForceMode.Auto, now),
        )
    }

    @Test
    fun `粘滞优选置顶`() {
        var health = RouteHealth()
        repeat(3) {
            health = health.onResult(
                RouteId.UserProxy, AttemptOutcome.Success, 50L, now,
            )
        }
        val plan = EgressScheduler.plan(
            hanimeApi,
            state(proxy = ProxyState.Explicit("203.0.113.7", 7890, socks = false)),
            health,
            now,
        )
        assertEquals(RouteId.UserProxy, plan.attempts.first().route)
        assertTrue(plan.attempts.map { it.route }.contains(RouteId.Gate))
    }

    @Test
    fun `未锁定时按成功率重排`() {
        var health = RouteHealth()
        repeat(2) {
            health = health.onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, now)
        }
        val plan = EgressScheduler.plan(
            hanimeApi,
            state(proxy = ProxyState.Explicit("203.0.113.7", 7890, socks = false)),
            health,
            now,
        )
        assertEquals(RouteId.UserProxy, plan.attempts.first().route)
        assertEquals(RouteId.Gate, plan.attempts.last().route)
    }

    @Test
    fun `强制网关无视熔断`() {
        val health = RouteHealth().onResult(RouteId.Gate, AttemptOutcome.Blocked, 100L, now)
        val plan = EgressScheduler.plan(hanimeApi.copy(force = ForceMode.ForceGate), state(), health, now)
        assertEquals(listOf(RouteId.Gate), plan.attempts.map { it.route })
    }

    @Test
    fun `强制直连不看域限制`() {
        val plan = EgressScheduler.plan(
            hanimeApi.copy(force = ForceMode.ForceDirect),
            state(),
            RouteHealth(),
            now,
        )
        assertEquals(listOf(RouteId.Direct), plan.attempts.map { it.route })
    }

    @Test
    fun `强制代理无代理时表空`() {
        val plan = EgressScheduler.plan(
            hanimeApi.copy(force = ForceMode.ForceProxy),
            state(),
            RouteHealth(),
            now,
        )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `预算按用途推导`() {
        assertEquals(60_000L, EgressBudgets.budgetFor(EgressPurpose.Api, RouteId.Gate))
        assertEquals(30_000L, EgressBudgets.budgetFor(EgressPurpose.Image, RouteId.Gate))
        assertEquals(EgressBudgets.UNLIMITED, EgressBudgets.budgetFor(EgressPurpose.Video, RouteId.Gate))
        assertEquals(EgressBudgets.UNLIMITED, EgressBudgets.budgetFor(EgressPurpose.Download, RouteId.UserProxy))
        assertEquals(10_000L, EgressBudgets.budgetFor(EgressPurpose.Probe, RouteId.Direct))
    }

    @Test
    fun `图片用途走图片预算`() {
        val plan = EgressScheduler.plan(
            EgressRequest("https://hanime1.me/x.jpg", purpose = EgressPurpose.Image),
            state(),
            RouteHealth(),
            now,
        )
        assertEquals(30_000L, plan.attempts.single().budgetMs)
    }

    @Test
    fun `未运行与非https原因可分辨`() {
        assertEquals(
            GateSkipReason.NotRunning,
            EgressScheduler.plan(hanimeApi, state(gate = readyGate.copy(port = -1)), RouteHealth(), now).skipped,
        )
        assertEquals(
            GateSkipReason.NotHttps,
            EgressScheduler.plan(
                EgressRequest("http://hanime1.me/"),
                state(),
                RouteHealth(),
                now,
            ).skipped,
        )
    }
}
