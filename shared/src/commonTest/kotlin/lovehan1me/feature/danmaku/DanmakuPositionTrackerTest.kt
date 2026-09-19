package lovehan1me.feature.danmaku

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [DanmakuPositionTracker] 的行为锁定。
 *
 * 它是"稀疏的位置推送"与"每帧都要有位置"之间唯一的桥，所以两个失败方向都得钉住：
 *  - 把正常播放误判成 seek → 每次误判都清空重放屏上弹幕，看起来就是"弹幕集体抽搐"；
 *  - 把真 seek 漏掉 → 用户拖完进度条，屏幕上还在飘上一个位置的弹幕。
 *
 * 时钟全部由测试给定（纳秒 = 毫秒 × 1e6），因此这里断言的是确定性数值，不是"大致连续"。
 */
class DanmakuPositionTrackerTest {

    private fun ms(millis: Long): Long = millis * DanmakuPositionTracker.NANOS_PER_MILLI

    @Test
    fun `采样之间按闭式外推`() {
        val tracker = DanmakuPositionTracker()
        tracker.onSample(positionMs = 0L, playbackSpeed = 1f)
        assertEquals(0L, tracker.positionAt(ms(0L)))

        // 下一个采样到达之前，位置由外推给出：每帧 +16ms，无台阶
        assertEquals(16L, tracker.positionAt(ms(16L)))
        assertEquals(132L, tracker.positionAt(ms(132L)))

        // 采样与外推一致时重锚定不产生跳变
        tracker.onSample(positionMs = 250L, playbackSpeed = 1f)
        assertEquals(250L, tracker.positionAt(ms(250L)))
        assertEquals(266L, tracker.positionAt(ms(266L)))
    }

    @Test
    fun `倍速只取引擎回显的速度`() {
        val tracker = DanmakuPositionTracker()
        tracker.onSample(positionMs = 0L, playbackSpeed = 2.5f)
        assertEquals(0L, tracker.positionAt(ms(0L)))
        // 墙钟 100ms → 视频时间 250ms
        assertEquals(250L, tracker.positionAt(ms(100L)))
    }

    @Test
    fun `非法速度退回常速`() {
        val tracker = DanmakuPositionTracker()
        tracker.onSample(positionMs = 0L, playbackSpeed = Float.NaN)
        assertEquals(0L, tracker.positionAt(ms(0L)))
        assertEquals(100L, tracker.positionAt(ms(100L)))

        tracker.onSample(positionMs = 100L, playbackSpeed = 0f)
        assertEquals(100L, tracker.positionAt(ms(100L)))
        assertEquals(200L, tracker.positionAt(ms(200L)))
    }

    @Test
    fun `拖动进度条判为seek`() {
        val tracker = DanmakuPositionTracker()
        tracker.onSample(positionMs = 0L, playbackSpeed = 1f)
        tracker.positionAt(ms(0L))
        val before = tracker.seekGeneration

        tracker.onSample(positionMs = 60_000L, playbackSpeed = 1f)
        assertEquals(60_000L, tracker.positionAt(ms(10L)))
        assertEquals(before + 1L, tracker.seekGeneration)
    }

    @Test
    fun `循环播放回绕判为seek`() {
        val tracker = DanmakuPositionTracker()
        tracker.onSample(positionMs = 30_000L, playbackSpeed = 1f)
        tracker.positionAt(ms(0L))
        val before = tracker.seekGeneration

        // 单片循环：位置从末尾跳回 0，是个负向大跳变
        tracker.onSample(positionMs = 0L, playbackSpeed = 1f)
        tracker.positionAt(ms(50L))
        assertEquals(before + 1L, tracker.seekGeneration)
    }

    @Test
    fun `采样间隔抖动不误判为seek`() {
        val tracker = DanmakuPositionTracker()
        var position = 0L
        var nowMs = 0L
        var generation = 0L
        tracker.onSample(position, 1f)
        tracker.positionAt(ms(nowMs))
        repeat(20) {
            // 最粗的一路：桌面事件驱动 + 主线程卡顿，间隔远大于安卓的 250ms
            nowMs += 900L
            position += 900L
            generation = tracker.seekGeneration
            tracker.onSample(position, 1f)
            tracker.positionAt(ms(nowMs))
        }
        assertEquals(generation, tracker.seekGeneration)
    }

    @Test
    fun `冻结期间不漂移解冻后原地继续`() {
        val tracker = DanmakuPositionTracker()
        tracker.onSample(positionMs = 10_000L, playbackSpeed = 1f)
        assertEquals(10_000L, tracker.positionAt(ms(10_000L)))

        tracker.setFrozen(true)
        // 卡顿 30 秒：期间位置纹丝不动
        repeat(30) { index -> assertEquals(10_000L, tracker.positionAt(ms(10_000L + (index + 1) * 1_000L))) }

        tracker.setFrozen(false)
        // 解冻后从原处续上，不会一次跳出冻结的 30 秒
        assertEquals(11_000L, tracker.positionAt(ms(41_000L)))
    }

    @Test
    fun `长时间没有采样时外推有上界`() {
        val tracker = DanmakuPositionTracker()
        tracker.onSample(positionMs = 0L, playbackSpeed = 1f)
        tracker.positionAt(ms(0L))
        // 后台挂 5 分钟再回前台：宁可停在原地等下一次采样，也不要一口气跑飞
        assertEquals(
            DanmakuPositionTracker.MAX_LOOKAHEAD_MS,
            tracker.positionAt(ms(300_000L)),
        )
    }

    @Test
    fun `位置钳在片尾以内`() {
        val tracker = DanmakuPositionTracker()
        // 4 倍速：500ms 墙钟就该越过 60s 的片尾
        tracker.onSample(positionMs = 59_000L, playbackSpeed = 4f, durationMs = 60_000L)
        assertEquals(59_000L, tracker.positionAt(ms(0L)))
        assertEquals(60_000L, tracker.positionAt(ms(500L)))
    }

    @Test
    fun `reset后没有位置`() {
        val tracker = DanmakuPositionTracker()
        tracker.onSample(positionMs = 5_000L, playbackSpeed = 1f)
        assertNotNull(tracker.positionAt(ms(0L)))
        tracker.reset()
        assertNull(tracker.positionAt(ms(1_000L)))
        tracker.onSample(positionMs = 7_000L, playbackSpeed = 1f)
        assertNotNull(tracker.positionAt(ms(2_000L)))
        // reset 会让消费者重建可见集（换片子时旧弹幕不能留）
        assertNotEquals(0L, tracker.seekGeneration)
    }
}
