package lovehan1me.data.network.egress

import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGateStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B4-4 诊断报告结论层的守卫测试。
 *
 * 结论层只做"从现成数字到一句话"：四步各判一个状态，再按**因果优先级**取最上游那条结论。
 * 本文件把每种"人为制造"的失败都钉一遍 —— 网关挂 / 无出口 / 域熔断 / 抖动 / 无样本 / 正常，
 * 确保结论不会指错方向（这是"用户能看懂问题出在哪"的唯一保证）。
 *
 * 反向验证：把 [egressDiagnosis] 里 verdict 的优先级顺序打乱（如把 `noRoute` 提到网关失败之前），
 * `网关失败时结论指向网关而非出口` 会转红。
 */
class EgressDiagnosisTest {

    private val now = 1_700_000_000_000L

    @BeforeTest
    fun setUp() {
        EchGate.publish(EchGateStatus.Idle)
    }

    @AfterTest
    fun tearDown() {
        EchGate.publish(EchGateStatus.Idle)
    }

    private fun texts() = EgressDiagnosisTexts(
        stepGateway = "G",
        stepRoute = "R",
        stepQuality = "Q",
        stepDomain = "D",
        statusOk = "ok",
        statusWarn = "warn",
        statusFail = "fail",
        statusUnknown = "?",
        verdictHealthy = "HEALTHY",
        verdictGatewayFailed = "GATE_DOWN",
        verdictNoRoute = "NO_ROUTE",
        verdictDomainsMelted = "MELTED",
        verdictUnstable = "UNSTABLE",
        verdictNoData = "NO_DATA",
    )

    private fun healthOf(vararg pairs: Pair<DomainClass, RouteHealth>): (DomainClass) -> RouteHealth {
        val map = mapOf(*pairs)
        return { map[it] ?: RouteHealth() }
    }

    private fun event(route: RouteId, outcome: AttemptOutcome, rttMs: Long = -1L) =
        EgressEvent(now - 1_000, DomainClass.Hanime, route, outcome, rttMs)

    private fun stepOf(diagnosis: EgressDiagnosis, kind: DiagnosisStepKind) =
        diagnosis.steps.first { it.kind == kind }

    @Test
    fun `网关失败时结论指向网关而非出口`() {
        EchGate.publish(EchGateStatus.Failed("产物缺失"))
        val d = egressDiagnosis(
            nowMs = now, useEchGate = true, proxyUsable = false,
            healthOf = healthOf(), gate = EchGateStatus.Failed("产物缺失"), events = emptyList(),
        )
        assertEquals(DiagnosisVerdict.GatewayFailed, d.verdict)
        val gateway = stepOf(d, DiagnosisStepKind.Gateway)
        assertEquals(DiagnosisStatus.Fail, gateway.status)
        assertEquals("产物缺失", gateway.evidence, "失败原因要原样带出来")
    }

    @Test
    fun `意外退出也算网关失败`() {
        val d = egressDiagnosis(
            nowMs = now, useEchGate = true, proxyUsable = false,
            healthOf = healthOf(), gate = EchGateStatus.Exited, events = emptyList(),
        )
        assertEquals(DiagnosisVerdict.GatewayFailed, d.verdict)
    }

    @Test
    fun `网关不在且无代理时结论指向无出口`() {
        EchGate.publish(EchGateStatus.Stopped)
        val d = egressDiagnosis(
            nowMs = now, useEchGate = true, proxyUsable = false,
            healthOf = healthOf(), gate = EchGateStatus.Stopped, events = emptyList(),
        )
        assertEquals(DiagnosisVerdict.NoRoute, d.verdict, "想要网关但网关不在、又没有代理兜底")
        assertEquals(DiagnosisStatus.Fail, stepOf(d, DiagnosisStepKind.Route).status)
    }

    @Test
    fun `域被熔断时结论指向域健康`() {
        EchGate.publish(EchGateStatus.Running(18080))
        val melted = RouteHealth().onResult(RouteId.Gate, AttemptOutcome.Blocked, -1L, now)
        val d = egressDiagnosis(
            nowMs = now, useEchGate = true, proxyUsable = false,
            healthOf = healthOf(DomainClass.Hanime to melted),
            gate = EchGateStatus.Running(18080), events = emptyList(),
        )
        assertEquals(DiagnosisVerdict.DomainsMelted, d.verdict)
        val domain = stepOf(d, DiagnosisStepKind.Domain)
        assertEquals(DiagnosisStatus.Fail, domain.status)
        assertTrue(domain.evidence.contains("Hanime"), "证据要指出是哪个域：${domain.evidence}")
    }

    @Test
    fun `未熔断但有失败时结论提示抖动`() {
        EchGate.publish(EchGateStatus.Running(18080))
        val unstable = RouteHealth().onResult(RouteId.Gate, AttemptOutcome.TransportError, -1L, now)
        val d = egressDiagnosis(
            nowMs = now, useEchGate = true, proxyUsable = false,
            healthOf = healthOf(DomainClass.Hanime to unstable),
            gate = EchGateStatus.Running(18080), events = emptyList(),
        )
        assertEquals(DiagnosisVerdict.Unstable, d.verdict)
        assertEquals(DiagnosisStatus.Warn, stepOf(d, DiagnosisStepKind.Domain).status)
    }

    @Test
    fun `干净但近窗口无样本时结论提示稍后再看`() {
        EchGate.publish(EchGateStatus.Running(18080))
        val d = egressDiagnosis(
            nowMs = now, useEchGate = true, proxyUsable = false,
            healthOf = healthOf(), gate = EchGateStatus.Running(18080), events = emptyList(),
        )
        assertEquals(DiagnosisVerdict.NoData, d.verdict)
        assertEquals(DiagnosisStatus.Unknown, stepOf(d, DiagnosisStepKind.Quality).status)
    }

    @Test
    fun `有成功样本且无异常时结论为正常`() {
        EchGate.publish(EchGateStatus.Running(18080))
        val d = egressDiagnosis(
            nowMs = now, useEchGate = true, proxyUsable = false,
            healthOf = healthOf(), gate = EchGateStatus.Running(18080),
            events = listOf(event(RouteId.Gate, AttemptOutcome.Success, rttMs = 120)),
        )
        assertEquals(DiagnosisVerdict.Healthy, d.verdict)
        val route = stepOf(d, DiagnosisStepKind.Route)
        assertEquals(DiagnosisStatus.Ok, route.status)
        assertEquals(RouteId.Gate.displayName(), route.evidence, "出口步要说清走的是哪条路")
        assertEquals(DiagnosisStatus.Ok, stepOf(d, DiagnosisStepKind.Quality).status)
    }

    @Test
    fun `结论排在第一行 四步随后逐行给出`() {
        EchGate.publish(EchGateStatus.Running(18080))
        val report = egressDiagnosis(
            nowMs = now, useEchGate = true, proxyUsable = false,
            healthOf = healthOf(), gate = EchGateStatus.Running(18080),
            events = listOf(event(RouteId.Gate, AttemptOutcome.Success, rttMs = 120)),
        ).formatReport(texts())

        val lines = report.split("\n")
        assertEquals("HEALTHY", lines.first(), "第一行必须是结论（问题出在哪 + 下一步）")
        assertEquals(5, lines.size, "结论 1 行 + 四步 4 行")
        assertEquals("G · ok · 18080", lines[1], "网关步带端口证据")
        assertEquals("R · ok · 网关", lines[2], "出口步带路由名证据")
        assertTrue(lines[3].startsWith("Q · ok · "), "质量步带计数证据：${lines[3]}")
        assertEquals("D · ok", lines[4], "域健康步无证据时不硬凑分隔符")
    }
}