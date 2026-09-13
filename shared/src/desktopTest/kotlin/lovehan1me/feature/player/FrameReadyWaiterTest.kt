package lovehan1me.feature.player

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [FrameReadyWaiter] 的测试。
 *
 * 这里覆盖的是"抓帧时序"这条最容易写成"睡个魔法值了事"的逻辑：
 * 注入假时钟后可以精确断言"轮询了几次、等了多久、超时后是否还继续等"。
 */
class FrameReadyWaiterTest {

    private class FakeClock {
        var now = 0L
        val delays = mutableListOf<Long>()

        fun nowMs(): Long = now

        suspend fun delayMs(ms: Long) {
            delays.add(ms)
            now += ms
        }
    }

    private fun waiter(clock: FakeClock, settleMs: Long = 120L) = FrameReadyWaiter(
        toleranceMs = 250L,
        timeoutMs = 2_000L,
        pollMs = 40L,
        settleMs = settleMs,
        nowMs = clock::nowMs,
        delayMs = clock::delayMs,
    )

    @Test
    fun `位置已就绪时立即通过并只等 settle`() = runBlocking {
        val clock = FakeClock()
        val ok = waiter(clock).await(targetMs = 1_000L, positionProvider = { 1_000L })
        assertTrue(ok)
        assertEquals(listOf(120L), clock.delays, "只需 settle 一次，不该有多余轮询")
    }

    @Test
    fun `位置逐步到位时会轮询等待`() = runBlocking {
        val clock = FakeClock()
        // 1000 的目标，容差 250 → 900 即命中；前两次读都太靠前
        val samples = ArrayDeque(listOf(0L, 100L, 900L))
        val ok = waiter(clock).await(targetMs = 1_000L, positionProvider = { samples.removeFirst() })
        assertTrue(ok)
        assertEquals(listOf(40L, 40L, 120L), clock.delays, "两次轮询 + 一次 settle")
        assertEquals(200L, clock.now, "假时钟推进应为 40+40+120")
    }

    @Test
    fun `容差边界之内都算就绪`() = runBlocking {
        for (pos in listOf(750L, 1_000L, 1_250L)) {
            val clock = FakeClock()
            assertTrue(
                waiter(clock).await(targetMs = 1_000L, positionProvider = { pos }),
                "位置 $pos 应落在容差 [750,1250] 内",
            )
        }
    }

    @Test
    fun `容差之外不算就绪`() = runBlocking {
        for (pos in listOf(749L, 1_251L)) {
            val clock = FakeClock()
            assertFalse(
                waiter(clock).await(targetMs = 1_000L, positionProvider = { pos }),
                "位置 $pos 应落在容差外并最终超时",
            )
        }
    }

    @Test
    fun `位置对但仍在上次 seek 中时不通过`() = runBlocking {
        val clock = FakeClock()
        var seeking = true
        val ok = waiter(clock).await(
            targetMs = 1_000L,
            positionProvider = { 1_000L },
            seekingProvider = { seeking },
        )
        // 位置一直"正确"但 seeking 恒 true → 必须超时，绝不能抓帧
        assertFalse(ok, "引擎报告仍在 seek 时不能抓帧（否则拿到旧帧）")
        assertTrue(clock.now >= 2_000L, "应当等到超时，实际推进 ${clock.now}ms")
    }

    @Test
    fun `seeking 转为 false 后可以继续成功`() = runBlocking {
        val clock = FakeClock()
        var reads = 0
        val ok = waiter(clock).await(
            targetMs = 1_000L,
            positionProvider = { 1_000L },
            seekingProvider = {
                reads++
                reads <= 2 // 前两次仍在 seek
            },
        )
        assertTrue(ok)
        assertEquals(listOf(40L, 40L, 120L), clock.delays)
    }

    @Test
    fun `超时后不再继续轮询`() = runBlocking {
        val clock = FakeClock()
        val ok = waiter(clock).await(targetMs = 5_000L, positionProvider = { 0L })
        assertFalse(ok)
        // 40ms 一次、2000ms 上限 → 50 次轮询，且不含 settle
        assertTrue(clock.delays.isNotEmpty())
        assertTrue(clock.delays.all { it == 40L }, "超时路径不该出现 settle：${clock.delays.distinct()}")
        assertTrue(clock.now >= 2_000L && clock.now < 2_200L, "实际推进 ${clock.now}ms")
    }

    @Test
    fun `settle 为 0 时不产生额外等待`() = runBlocking {
        val clock = FakeClock()
        val ok = waiter(clock, settleMs = 0L).await(targetMs = 1_000L, positionProvider = { 1_000L })
        assertTrue(ok)
        assertTrue(clock.delays.isEmpty(), "settle=0 时不该有任何 delay")
    }
}
