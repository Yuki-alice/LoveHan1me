package lovehan1me.data.network.egress.scheduler

import lovehan1me.data.network.egress.AttemptOutcome
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressEvents
import lovehan1me.data.network.egress.EgressReporter
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.RouteRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 上报 + 注册表 + 事件流回归。
 *
 * 全局对象用完即清（`@AfterTest`），杜绝"整跑全红、单跑全过"。
 */
class ReporterRegistryTest {

    private val now = 1_700_000_000_000L

    @AfterTest
    fun tearDown() {
        RouteRegistry.reset()
        EgressEvents.clear()
    }

    @Test
    fun `上报喂健康并记事件`() {
        EgressReporter.report(DomainClass.Hanime, RouteId.Gate, AttemptOutcome.Success, 100L, now)
        assertEquals(1, RouteRegistry.healthOf(DomainClass.Hanime).single(RouteId.Gate).consecutiveSuccesses)
        val event = EgressEvents.recent().single()
        assertEquals(DomainClass.Hanime, event.domain)
        assertEquals(RouteId.Gate, event.route)
        assertEquals(AttemptOutcome.Success, event.outcome)
        assertEquals(100L, event.rttMs)
    }

    @Test
    fun `阻断上报熔断本域且别域干净`() {
        EgressReporter.report(DomainClass.Getchu, RouteId.Gate, AttemptOutcome.Blocked, 100L, now)
        assertTrue(RouteRegistry.healthOf(DomainClass.Getchu).isOpen(RouteId.Gate, now))
        assertEquals(0, RouteRegistry.healthOf(DomainClass.Hanime).single(RouteId.Gate).consecutiveFailures)
    }

    @Test
    fun `事件流只留近200条`() {
        repeat(210) { i ->
            EgressReporter.report(DomainClass.Hanime, RouteId.Gate, AttemptOutcome.Success, i.toLong(), now)
        }
        val recent = EgressEvents.recent()
        assertEquals(EgressEvents.CAPACITY, recent.size)
        assertEquals(10L, recent.first().rttMs)
        assertEquals(209L, recent.last().rttMs)
    }

    @Test
    fun `复位按域与全局`() {
        EgressReporter.report(DomainClass.Hanime, RouteId.Gate, AttemptOutcome.Blocked, 100L, now)
        EgressReporter.report(DomainClass.Getchu, RouteId.Gate, AttemptOutcome.Blocked, 100L, now)
        RouteRegistry.reset(DomainClass.Hanime)
        assertTrue(RouteRegistry.healthOf(DomainClass.Getchu).isOpen(RouteId.Gate, now))
        assertEquals(0, RouteRegistry.healthOf(DomainClass.Hanime).single(RouteId.Gate).consecutiveFailures)
        RouteRegistry.reset()
        assertEquals(0, RouteRegistry.healthOf(DomainClass.Getchu).single(RouteId.Gate).consecutiveFailures)
    }
}
