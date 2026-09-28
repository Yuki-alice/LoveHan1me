package lovehan1me.data.network.egress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [EgressPlanner] 的回归（纯函数，不碰全局单例、不联网，故零 flake）。
 *
 * 这是本轮重构的核心防线：**"该不该用网关"只能有一个地方回答**。
 * 尤其钉住"有代理 ⇒ 网关进入试用期"——那是这次故障的正面修复：
 * 网关仍先试（保住免梯直连），但只有一次机会，失败即让位给代理
 * （保住"配了代理就必须能用"）。
 */
class EgressPlannerTest {

    private val readyGate = GateState(enabled = true, port = 8080, circuitOpen = false)

    private val browseRequest = EgressRequest("https://hanime1.me/search?q=abc")

    private fun state(
        gate: GateState = readyGate,
        proxy: ProxyState = ProxyState.None,
    ) = EgressState(gate = gate, proxy = proxy)

    @Test
    fun `无代理时网关优先且不设试用期`() {
        val plan = EgressPlanner.plan(browseRequest, state())
        assertTrue(plan.usesGate)
        assertFalse(plan.gateOnProbation, "后面没有更好的路可退，就不存在「让位」这回事")
        assertNull(plan.gateSkipped)
        val rewrite = assertNotNull(plan.gate)
        assertEquals("http://127.0.0.1:8080/search?q=abc", rewrite.url)
        assertEquals("hanime1.me", rewrite.targetHost)
    }

    @Test
    fun `有手填代理时网关进入试用期`() {
        val proxy = ProxyState.Explicit(host = "203.0.113.7", port = 7890, socks = false)
        val plan = EgressPlanner.plan(browseRequest, state(proxy = proxy))
        assertTrue(plan.usesGate, "仍然先试网关，保住免梯直连的体验")
        assertTrue(plan.gateOnProbation, "但只有一次机会，失败即让位给代理")
    }

    @Test
    fun `系统代理可解析时同样进入试用期`() {
        val plan = EgressPlanner.plan(browseRequest, state(proxy = ProxyState.SystemResolved))
        assertTrue(plan.gateOnProbation)
    }

    @Test
    fun `熔断时不接管并给出原因`() {
        val plan = EgressPlanner.plan(browseRequest, state(gate = readyGate.copy(circuitOpen = true)))
        assertFalse(plan.usesGate)
        assertEquals(GateSkipReason.CircuitOpen, plan.gateSkipped)
        assertNull(plan.gate)
    }

    @Test
    fun `关闭与未运行各自给出原因`() {
        assertEquals(
            GateSkipReason.Disabled,
            EgressPlanner.plan(browseRequest, state(gate = readyGate.copy(enabled = false))).gateSkipped,
        )
        assertEquals(
            GateSkipReason.NotRunning,
            EgressPlanner.plan(browseRequest, state(gate = readyGate.copy(port = -1))).gateSkipped,
        )
    }

    @Test
    fun `非 https 与回环字面量不接管且原因可分辨`() {
        assertEquals(
            GateSkipReason.NotHttps,
            EgressPlanner.plan(EgressRequest("http://hanime1.me/"), state()).gateSkipped,
        )
        assertEquals(
            GateSkipReason.LoopbackOrLiteral,
            EgressPlanner.plan(EgressRequest("https://127.0.0.1/x"), state()).gateSkipped,
        )
        assertEquals(
            GateSkipReason.LoopbackOrLiteral,
            EgressPlanner.plan(EgressRequest("https://localhost/x"), state()).gateSkipped,
        )
        assertEquals(
            GateSkipReason.LoopbackOrLiteral,
            EgressPlanner.plan(EgressRequest("https://192.168.1.10/x"), state()).gateSkipped,
        )
    }

    @Test
    fun `视频 CDN 域名同样接管`() {
        val plan = EgressPlanner.plan(EgressRequest("https://vdownload.hembed.com/f/a.mp4"), state())
        assertTrue(plan.usesGate, "视频直链同样被 SNI 阻断，必须与浏览同一条出口")
        assertEquals("vdownload.hembed.com", plan.gate?.targetHost)
    }

    @Test
    fun `非法 URL 给得出原因而不是抛`() {
        assertEquals(GateSkipReason.InvalidUrl, EgressPlanner.plan(EgressRequest("::::"), state()).gateSkipped)
        assertEquals(GateSkipReason.InvalidUrl, EgressPlanner.plan(EgressRequest(""), state()).gateSkipped)
    }

    @Test
    fun `跳过原因不影响试用期判定`() {
        // 熔断期间如果同时有代理，"让位"的对象是存在的；试用期标记照旧表达这个事实。
        val plan = EgressPlanner.plan(
            browseRequest,
            state(gate = readyGate.copy(circuitOpen = true), proxy = ProxyState.Explicit("p", 1, false)),
        )
        assertTrue(plan.gateOnProbation)
    }

    // ── 增量二：隧道通道与播放器出口 ──

    @Test
    fun `可用候选的三个条件缺一不可`() {
        assertTrue(readyGate.isCandidate)
        assertFalse(readyGate.copy(enabled = false).isCandidate)
        assertFalse(readyGate.copy(port = -1).isCandidate)
        assertFalse(readyGate.copy(circuitOpen = true).isCandidate)
    }

    @Test
    fun `隧道通道与改写道同源`() {
        assertEquals("http://127.0.0.1:8080", readyGate.connectTunnelUrl())
        // 熔断/关闭/未运行时两条道一起消失。此前隧道只判 port > 0，
        // 于是"改写道已退让、隧道还在劫持媒体"——媒体没有回退，撞不通就是直接失败。
        assertNull(readyGate.copy(circuitOpen = true).connectTunnelUrl())
        assertNull(readyGate.copy(enabled = false).connectTunnelUrl())
        assertNull(readyGate.copy(port = -1).connectTunnelUrl())
    }

    @Test
    fun `播放器出口用户代理优先于隧道`() {
        val explicit = ProxyState.Explicit("203.0.113.7", 7890, socks = false)
        assertEquals(
            "http://203.0.113.7:7890",
            EgressPlanner.mediaProxyUrl("http://203.0.113.7:7890", state(proxy = explicit)),
            "用户已经配好的那条路不该被隧道顶掉",
        )
        assertEquals(
            "http://127.0.0.1:8080",
            EgressPlanner.mediaProxyUrl(userProxyUrl = null, state = state()),
            "没配代理时才轮到网关的 DoH 路由隧道",
        )
        assertEquals(
            null,
            EgressPlanner.mediaProxyUrl(
                userProxyUrl = null,
                state = state(gate = readyGate.copy(circuitOpen = true)),
            ),
            "熔断期间隧道也要跟着消失，否则媒体仍被劫持且没有回退",
        )
    }

    @Test
    fun `便捷入口与计划同源`() {
        // 执行器（Coil 的 Ktor 插件、播放器的 rewriteForGate）走的是这个入口，
        // 它必须与 plan() 给出同一个结论，否则又会分叉。
        val viaConvenience = EgressPlanner.gateRewriteFor(browseRequest.url, state())
        val viaPlan = EgressPlanner.plan(browseRequest, state()).gate
        assertEquals(viaPlan, viaConvenience)
        assertNull(EgressPlanner.gateRewriteFor(browseRequest.url, state(gate = readyGate.copy(circuitOpen = true))))
    }
}
