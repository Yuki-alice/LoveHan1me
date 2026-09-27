package lovehan1me.feature.danmaku

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DanmakuClock] 的读数规则。
 *
 * 它是弹幕飞行位置的唯一自变量，所以两条硬要求：
 *  - 冻结（暂停 / 卡顿）期间不许走，解冻后也不许一次性补出冻结的时长；
 *  - 读数只由帧时刻决定，**不由调用次数决定** —— 空屏时帧循环节流到 20Hz，
 *    "每次调用累加"那种写法会把弹幕飞行拖慢一半以上。
 */
class DanmakuClockTest {

    private fun millis(ms: Long) = ms * 1_000_000L

    @Test
    fun `读数与帧时刻同速`() {
        val clock = DanmakuClock()
        assertEquals(1_000L, clock.nowMs(millis(1_000), frozen = false))
        assertEquals(1_016L, clock.nowMs(millis(1_016), frozen = false))
    }

    @Test
    fun `冻结期间不走且解冻后从原处继续`() {
        val clock = DanmakuClock()
        assertEquals(1_016L, clock.nowMs(millis(1_016), frozen = false))
        // 暂停是在这一帧才发现的：读数停在这一帧，不多走也不回退
        assertEquals(1_032L, clock.nowMs(millis(1_032), frozen = true))
        assertEquals(1_032L, clock.nowMs(millis(1_176), frozen = true))
        // 解冻的那一帧先把冻结时长扣掉，于是原地续上
        assertEquals(1_032L, clock.nowMs(millis(1_192), frozen = false))
        assertEquals(1_112L, clock.nowMs(millis(1_272), frozen = false))
    }

    @Test
    fun `同一帧时刻的读数与调用密度无关`() {
        // 800ms 同时被 16 和 50 整除：两种节拍都停在同一个帧时刻上
        fun readAt(stepMs: Long): Long {
            val clock = DanmakuClock()
            var read = 0L
            for (at in 0L..800L step stepMs) read = clock.nowMs(millis(at), frozen = false)
            return read
        }
        assertEquals(800L, readAt(16L))
        assertEquals(readAt(16L), readAt(50L))
    }
}
