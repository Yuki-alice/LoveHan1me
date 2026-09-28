package lovehan1me.data.network.egress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [GateHealth] 的回归（纯状态机，不碰时钟也不碰全局）。
 *
 * 钉的是用户能感知的那条线：网关被阻断后**必须**被摘掉，而不是每个请求都再撞一遍。
 * 两条口径的差别是有意的 —— 阻断类一次即熔断（出口被封不是抖动，重试只是消耗耐心），
 * 其余累计到阈值（连接被重置可能只是抽风）。
 *
 * 时间基准统一取 0，于是"熔断发生在 t=0"⇒ 半开点恰是 [GateHealth.COOLDOWN_MS]。
 */
class GateHealthTest {

    private fun failures(n: Int, blocking: Boolean = false): GateHealth =
        (1..n).fold(GateHealth.CLOSED) { acc, _ -> acc.onFailure(nowMs = 0L, blocking = blocking) }

    @Test
    fun `普通失败攒够阈值才熔断`() {
        assertFalse(failures(1).isOpen(60_000L), "一次抖动就熔断会让网关形同虚设")
        assertFalse(failures(2).isOpen(60_000L))
        assertTrue(failures(3).isOpen(60_000L), "第 3 次失败应当熔断")
    }

    @Test
    fun `阻断类失败一次即熔断`() {
        val health = GateHealth.CLOSED.onFailure(nowMs = 0L, blocking = true)
        assertTrue(health.isOpen(1L), "网关出口被封时，再试只是继续消耗用户的耐心")
    }

    @Test
    fun `冷却期内不放行`() {
        assertTrue(failures(3).isOpen(GateHealth.COOLDOWN_MS - 1))
    }

    @Test
    fun `冷却到期后半开`() {
        assertFalse(failures(3).isOpen(GateHealth.COOLDOWN_MS), "到期应当允许再试一次")
    }

    @Test
    fun `半开后再失败立刻重新熔断`() {
        val opened = failures(3)
        assertFalse(opened.isOpen(GateHealth.COOLDOWN_MS), "此刻是半开")
        // 阈值已经攒够，半开期间的任何一次失败都应立刻重新打开，不用再攒两轮。
        val afterFailure = opened.onFailure(nowMs = GateHealth.COOLDOWN_MS, blocking = false)
        assertTrue(afterFailure.isOpen(GateHealth.COOLDOWN_MS + 1))
    }

    @Test
    fun `成功即闭合并把计数归零`() {
        val health = failures(2).onSuccess()
        assertEquals(0, health.consecutiveFailures)
        assertFalse(health.isOpen(60_000L))
        assertNull(health.describe(60_000L), "闭合后不该再对外报状态")
    }

    @Test
    fun `状态描述能区分熔断与半开`() {
        val opened = failures(3)
        assertTrue(opened.describe(0L)?.contains("熔断") == true)
        assertTrue(opened.describe(GateHealth.COOLDOWN_MS)?.contains("半开") == true)
    }
}
