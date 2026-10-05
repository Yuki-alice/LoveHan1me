package lovehan1me.data.network.egress.scheduler

import lovehan1me.data.network.egress.AttemptOutcome
import lovehan1me.data.network.egress.RouteHealth
import lovehan1me.data.network.egress.RouteId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 按域健康状态机回归（纯函数，时间由调用方传入，故零 flake）。
 *
 * 熔断语义：阻断类一次即熔、其余 3 次、冷却 5 分钟半开，外加粘滞锁定。
 */
class RouteHealthTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `fresh状态成功率视为全好且无RTT样本`() {
        val single = RouteHealth().single(RouteId.Gate)
        assertEquals(1.0, single.successRate())
        assertEquals(-1L, single.ewmaRttMs)
        assertNull(RouteHealth().lockedRoute)
    }

    @Test
    fun `阻断类一次即熔`() {
        val health = RouteHealth().onResult(RouteId.Gate, AttemptOutcome.Blocked, 100L, now)
        assertTrue(health.isOpen(RouteId.Gate, now))
        // 别家不受影响：按域独立的地基。
        assertFalse(health.isOpen(RouteId.Default, now))
    }

    @Test
    fun `普通失败攒够3次才熔`() {
        var health = RouteHealth()
        repeat(2) {
            health = health.onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, now)
            assertFalse(health.isOpen(RouteId.Gate, now), "第 ${it + 1} 次")
        }
        health = health.onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, now)
        assertTrue(health.isOpen(RouteId.Gate, now))
    }

    @Test
    fun `成功清零并闭合`() {
        var health = RouteHealth()
            .onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, now)
            .onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, now)
        health = health.onResult(RouteId.Gate, AttemptOutcome.Success, 120L, now)
        assertFalse(health.isOpen(RouteId.Gate, now))
        assertEquals(0, health.single(RouteId.Gate).consecutiveFailures)
    }

    @Test
    fun `连续3次成功锁定优选`() {
        var health = RouteHealth()
        repeat(2) {
            health = health.onResult(RouteId.Gate, AttemptOutcome.Success, 100L, now)
            assertNull(health.lockedRoute, "第 ${it + 1} 次还不够")
        }
        health = health.onResult(RouteId.Gate, AttemptOutcome.Success, 100L, now)
        assertEquals(RouteId.Gate, health.lockedRoute)
        assertEquals(RouteId.Gate, health.preferred(now))
    }

    @Test
    fun `锁定路由失败即解锁`() {
        var health = RouteHealth()
        repeat(3) { health = health.onResult(RouteId.Gate, AttemptOutcome.Success, 100L, now) }
        health = health.onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, now)
        assertNull(health.lockedRoute)
        assertNull(health.preferred(now))
    }

    @Test
    fun `别家成功不抢锁`() {
        var health = RouteHealth()
        repeat(3) { health = health.onResult(RouteId.Gate, AttemptOutcome.Success, 100L, now) }
        repeat(5) { health = health.onResult(RouteId.Default, AttemptOutcome.Success, 50L, now) }
        assertEquals(RouteId.Gate, health.lockedRoute, "锁定的才置顶，快的别来抢")
    }

    @Test
    fun `冷却到期半开且失败立刻重熔`() {
        // 先攒够阈值再熔：计数到 3 才开，冷却后一次失败（3→4）无需再攒。
        var health = RouteHealth()
        repeat(3) { health = health.onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, now) }
        assertTrue(health.isOpen(RouteId.Gate, now))
        val halfOpenAt = now + RouteHealth.COOLDOWN_MS
        assertFalse(health.isOpen(RouteId.Gate, halfOpenAt), "到期后半开")
        assertNull(health.preferred(halfOpenAt))
        health = health.onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, halfOpenAt)
        assertTrue(health.isOpen(RouteId.Gate, halfOpenAt), "半开失败无需再攒阈值")
    }

    @Test
    fun `EWMA按3比7收敛且负样本不污染`() {
        var health = RouteHealth().onResult(RouteId.Gate, AttemptOutcome.Success, 100L, now)
        assertEquals(100L, health.single(RouteId.Gate).ewmaRttMs)
        health = health.onResult(RouteId.Gate, AttemptOutcome.Success, 200L, now)
        assertEquals(90L, health.single(RouteId.Gate).ewmaRttMs)
        health = health.onResult(RouteId.Gate, AttemptOutcome.Success, -1L, now)
        assertEquals(90L, health.single(RouteId.Gate).ewmaRttMs, "超时/取消的无样本不该拉低 EWMA")
    }

    @Test
    fun `成功率窗口只看近10次`() {
        var health = RouteHealth()
        repeat(10) { health = health.onResult(RouteId.Gate, AttemptOutcome.Success, 100L, now) }
        assertEquals(1.0, health.single(RouteId.Gate).successRate())
        health = health.onResult(RouteId.Gate, AttemptOutcome.TransportError, 100L, now)
        assertEquals(0.9, health.single(RouteId.Gate).successRate())
    }
}
