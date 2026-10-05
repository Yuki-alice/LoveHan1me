package lovehan1me.data.network.egress

import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGateStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
