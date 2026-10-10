package lovehan1me.data.network.egress

import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGateStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 三态判定的守卫测试。
 *
 * 这段判定原先内联在 jvmMain `NetworkSettingsRoute` 的 `remember {}` 里，
 * 一条测试都没有 —— "熔断域怎么算""无可用出口怎么判"全靠肉眼。
 * 上提到 commonMain 之后补上本文件，同时它也是 iOS 网络页能复用同一份判定的前提。
 *
 * 反向验证：撤掉 `noRoute` 里 `useEchGate &&` 这一项，`用户主动关网关不贴无出口条` 转红；
 * 撤掉 `melted` 的 `!in melted` 过滤，不稳定条会跟着熔断条一起出现。
 */
class EgressStatusTest {

    private val now = 1_700_000_000_000L

    @BeforeTest
    fun setUp() {
        EchGate.publish(EchGateStatus.Idle)
    }

    @AfterTest
    fun tearDown() {
        EchGate.publish(EchGateStatus.Idle)
    }

    private fun texts() = EgressStatusTexts(
        running = "R:%1\$d",
        starting = "S",
        stopped = "X",
        failed = "F:%1\$s",
        melted = "M:%1\$s",
        unstable = "U:%1\$s/%2\$d",
        noRoute = "N",
    )

    private fun healthOf(vararg pairs: Pair<DomainClass, RouteHealth>): (DomainClass) -> RouteHealth {
        val map = mapOf(*pairs)
        return { map[it] ?: RouteHealth() }
    }

    private fun meltedHanime(): RouteHealth =
        RouteHealth().onResult(RouteId.Gate, AttemptOutcome.Blocked, -1L, now)

    private fun unstableHanime(): RouteHealth =
        RouteHealth().onResult(RouteId.Gate, AttemptOutcome.TransportError, -1L, now)

    @Test
    fun `无健康数据时三态干净且只排运行态一行`() {
        EchGate.publish(EchGateStatus.Running(18080))
        val snapshot = egressStatusSnapshot(nowMs = now, useEchGate = true, proxyUsable = false)
        assertTrue(snapshot.isClean, "没有失败就不该贴任何附加条")
        assertEquals("R:18080", snapshot.formatLines(texts()), "干净时只有运行态那一行")
    }

    @Test
    fun `本域熔断时贴熔断条`() {
        EchGate.publish(EchGateStatus.Running(18080))
        val snapshot = egressStatusSnapshot(
            nowMs = now,
            useEchGate = true,
            proxyUsable = false,
            healthOf = healthOf(DomainClass.Hanime to meltedHanime()),
        )
        assertEquals(listOf(DomainClass.Hanime), snapshot.meltedDomains)
        assertFalse(snapshot.isClean)
        val line = snapshot.formatLines(texts())
        assertTrue(line.startsWith("R:18080"), "运行态行仍在：$line")
        assertTrue(line.contains("M:Hanime"), "熔断条应出现：$line")
    }

    @Test
    fun `未熔断的失败计为不稳定并带上最大连续失败数`() {
        EchGate.publish(EchGateStatus.Running(18080))
        val snapshot = egressStatusSnapshot(
            nowMs = now,
            useEchGate = true,
            proxyUsable = false,
            healthOf = healthOf(DomainClass.Getchu to unstableHanime()),
        )
        assertTrue(snapshot.meltedDomains.isEmpty())
        assertEquals(listOf(DomainClass.Getchu), snapshot.unstableDomains)
        assertEquals(1, snapshot.maxRecentFailures)
        assertTrue(snapshot.formatLines(texts()).contains("U:Getchu/1"))
    }

    @Test
    fun `同一个域不会同时被算成熔断和不稳定`() {
        val snapshot = egressStatusSnapshot(
            nowMs = now,
            useEchGate = true,
            proxyUsable = false,
            healthOf = healthOf(DomainClass.Hanime to meltedHanime()),
        )
        assertTrue(snapshot.unstableDomains.isEmpty(), "熔断域不该再进不稳定列表")
    }

    @Test
    fun `网关不在且无代理时贴无出口条`() {
        // port <= 0（Idle）
        val snapshot = egressStatusSnapshot(nowMs = now, useEchGate = true, proxyUsable = false)
        assertTrue(snapshot.noRoute)
        assertTrue(snapshot.formatLines(texts()).contains("N"))
    }

    @Test
    fun `用户主动关掉网关不贴无出口条`() {
        // 关开关 = 声明"我的直连可用"，那时不该报无出口（与调度器同口径）。
        val snapshot = egressStatusSnapshot(nowMs = now, useEchGate = false, proxyUsable = false)
        assertFalse(snapshot.noRoute)
        assertEquals("X", snapshot.formatLines(texts()))
    }

    @Test
    fun `有可用代理时不贴无出口条`() {
        val snapshot = egressStatusSnapshot(nowMs = now, useEchGate = true, proxyUsable = true)
        assertFalse(snapshot.noRoute, "有代理兜底就不是无出口")
    }

    @Test
    fun `第三方域不参与三态跟踪`() {
        // proxyUsable = true 是为了把 noRoute 摘掉，让本条只考察"域跟踪范围"。
        val snapshot = egressStatusSnapshot(
            nowMs = now,
            useEchGate = true,
            proxyUsable = true,
            healthOf = healthOf(DomainClass.ThirdParty to meltedHanime()),
        )
        assertTrue(snapshot.meltedDomains.isEmpty(), "第三方域永不进网关，熔断对它没有意义")
        assertTrue(snapshot.isClean)
    }

    @Test
    fun `失败态带上原因文案`() {
        EchGate.publish(EchGateStatus.Failed("boom"))
        // 网关不在 + 无代理 ⇒ 还会再贴一条无出口条，故这里断言"以失败行开头"而非整行相等。
        val line = egressStatusSnapshot(nowMs = now, useEchGate = true, proxyUsable = false)
            .formatLines(texts())
        assertTrue(line.startsWith("F:boom"), "失败原因要给用户看得见：$line")
        assertTrue(line.contains("N"), "网关不在且无代理，应同时提示无出口：$line")
    }

    // ---- B1-6「当前出口」行 / B1-7 可重试判定 ----

    private fun event(
        atMs: Long,
        route: RouteId,
        outcome: AttemptOutcome,
        rttMs: Long = -1L,
    ) = EgressEvent(atMs, DomainClass.Hanime, route, outcome, rttMs)

    private fun outletTexts() = EgressOutletTexts(
        viaGate = "VG:%1\$d/%2\$d",
        viaDefault = "VD:%1\$d/%2\$d",
        mixed = "MX:%1\$d/%2\$d",
        none = "none",
        acceptance = "ACC:%1\$s/%2\$d/%3\$d",
    )

    @Test
    fun `出口分布按窗口统计并给出网关成功率`() {
        val events = listOf(
            event(now - 1_000, RouteId.Gate, AttemptOutcome.Success, 100),
            event(now - 2_000, RouteId.Gate, AttemptOutcome.Blocked, 50),
            event(now - 3_000, RouteId.Default, AttemptOutcome.Success, 200),
            // 窗口外：滚动窗口之外的旧事件不该把"现在走哪条路"带偏。
            event(now - EGRESS_OUTLET_WINDOW_MS - 1, RouteId.Gate, AttemptOutcome.Success),
        )
        val outlet = egressOutlet(nowMs = now, windowMs = EGRESS_OUTLET_WINDOW_MS, events = events)
        assertEquals(3, outlet.attempts, "窗口外那条不计数")
        assertEquals(2, outlet.gateAttempts)
        assertEquals(1, outlet.gateSuccess)
        assertEquals(1, outlet.defaultAttempts)
        assertEquals(1, outlet.failures, "只有 Blocked 那次不是成功")
        assertEquals(116L, outlet.avgRttMs, "350/3 向下取整")
        assertEquals(RouteId.Gate, outlet.dominant)
        assertEquals(0.5, outlet.gateSuccessRate)
    }

    @Test
    fun `默认出口更多时主导为当前网络出口`() {
        val events = listOf(
            event(now - 1_000, RouteId.Default, AttemptOutcome.Success),
            event(now - 2_000, RouteId.Default, AttemptOutcome.Success),
            event(now - 3_000, RouteId.Gate, AttemptOutcome.Success),
        )
        val outlet = egressOutlet(nowMs = now, windowMs = EGRESS_OUTLET_WINDOW_MS, events = events)
        assertEquals(RouteId.Default, outlet.dominant)
    }

    @Test
    fun `两条出口次数并列时视作混合`() {
        val events = listOf(
            event(now - 1_000, RouteId.Gate, AttemptOutcome.Success),
            event(now - 2_000, RouteId.Default, AttemptOutcome.Success),
        )
        val outlet = egressOutlet(nowMs = now, windowMs = EGRESS_OUTLET_WINDOW_MS, events = events)
        assertNull(outlet.dominant, "并列 = 混合，不硬选一边")
    }

    @Test
    fun `无出口样本时给无记录文案`() {
        val outlet = egressOutlet(nowMs = now, windowMs = EGRESS_OUTLET_WINDOW_MS, events = emptyList())
        assertFalse(outlet.hasRouteSample)
        assertEquals("none", outlet.formatLine(outletTexts()), "没样本就不硬凑 0 次")
    }

    @Test
    fun `主导出口与接受率排进同一行`() {
        val events = listOf(
            event(now - 1_000, RouteId.Gate, AttemptOutcome.Success),
            event(now - 2_000, RouteId.Gate, AttemptOutcome.Blocked),
            event(now - 3_000, RouteId.Default, AttemptOutcome.Success),
        )
        val line = egressOutlet(nowMs = now, windowMs = EGRESS_OUTLET_WINDOW_MS, events = events)
            .formatLine(outletTexts())
        assertTrue(line.startsWith("VG:10/2"), "近 10 分钟 2 次走网关：$line")
        assertTrue(line.contains("ACC:50%/1/2"), "接受率 50%（网关成功 1/2）：$line")
    }

    @Test
    fun `失败或意外退出才可一键重试`() {
        EchGate.publish(EchGateStatus.Failed("boom"))
        assertTrue(
            egressStatusSnapshot(nowMs = now, useEchGate = true, proxyUsable = true).gateRetryable,
            "启动失败还值得再拉一次",
        )
        EchGate.publish(EchGateStatus.Exited)
        assertTrue(
            egressStatusSnapshot(nowMs = now, useEchGate = true, proxyUsable = true).gateRetryable,
            "进程意外退出也能手动催一次",
        )
        EchGate.publish(EchGateStatus.Running(18080))
        assertFalse(
            egressStatusSnapshot(nowMs = now, useEchGate = true, proxyUsable = true).gateRetryable,
            "运行中无重试可说",
        )
        EchGate.publish(EchGateStatus.Stopped)
        assertFalse(
            egressStatusSnapshot(nowMs = now, useEchGate = true, proxyUsable = true).gateRetryable,
            "用户主动关：重试没有意义",
        )
    }
}
