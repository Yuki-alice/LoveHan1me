package lovehan1me.feature.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PlaybackStallDetector] 的行为锁定（M5-3「卡顿可见」）。
 *
 * 它代替三端语义不一致的 `isBuffering` 来回答"画面是不是卡住了"，
 * 误报会让用户在正常播放时看到转圈，漏报则让卡死看起来像正常播放 —— 两个方向都要钉住。
 */
class PlaybackStallDetectorTest {

    private class FakeClock(var now: Long = 1_000L) {
        fun advance(ms: Long) { now += ms }
    }

    @Test
    fun `位置持续前进时不判卡顿`() {
        val clock = FakeClock()
        val detector = PlaybackStallDetector(thresholdMs = 2_500L, clockMillis = { clock.now })
        var position = 0L
        repeat(20) {
            clock.advance(500L)
            position += 500L
            assertFalse(detector.update(position, isPlaying = true, isBuffering = false))
        }
    }

    @Test
    fun `位置停滞超过阈值后判卡顿`() {
        val clock = FakeClock()
        val detector = PlaybackStallDetector(thresholdMs = 2_500L, clockMillis = { clock.now })
        // 先建立基线（首帧不判）
        assertFalse(detector.update(1_000L, isPlaying = true, isBuffering = false))
        clock.advance(1_000L)
        assertFalse(detector.update(1_000L, isPlaying = true, isBuffering = false))
        clock.advance(2_000L)
        assertTrue(detector.update(1_000L, isPlaying = true, isBuffering = false))
    }

    @Test
    fun `恢复前进后立刻解除卡顿`() {
        val clock = FakeClock()
        val detector = PlaybackStallDetector(thresholdMs = 2_500L, clockMillis = { clock.now })
        detector.update(1_000L, isPlaying = true, isBuffering = false)
        clock.advance(5_000L)
        assertTrue(detector.update(1_000L, isPlaying = true, isBuffering = false))
        clock.advance(500L)
        assertFalse(detector.update(1_500L, isPlaying = true, isBuffering = false))
    }

    @Test
    fun `暂停时不判卡顿`() {
        val clock = FakeClock()
        val detector = PlaybackStallDetector(thresholdMs = 2_500L, clockMillis = { clock.now })
        detector.update(1_000L, isPlaying = false, isBuffering = false)
        clock.advance(60_000L)
        // 暂停一分钟也不算卡顿（用户自己按的暂停）
        assertFalse(detector.update(1_000L, isPlaying = false, isBuffering = false))
    }

    @Test
    fun `引擎已报缓冲时不叠加卡顿语义`() {
        val clock = FakeClock()
        val detector = PlaybackStallDetector(thresholdMs = 2_500L, clockMillis = { clock.now })
        detector.update(1_000L, isPlaying = true, isBuffering = true)
        clock.advance(10_000L)
        // 缓冲由引擎的 isBuffering 表达，看门狗不重复报（否则 UI 会同时出现两种反馈）
        assertFalse(detector.update(1_000L, isPlaying = true, isBuffering = true))
    }

    @Test
    fun `seek 造成的位置跳变不会误判`() {
        val clock = FakeClock()
        val detector = PlaybackStallDetector(thresholdMs = 2_500L, clockMillis = { clock.now })
        detector.update(1_000L, isPlaying = true, isBuffering = false)
        clock.advance(2_000L)
        // 跳到远处：位置变化即重建基线
        assertFalse(detector.update(900_000L, isPlaying = true, isBuffering = false))
        clock.advance(2_000L)
        assertFalse(detector.update(900_500L, isPlaying = true, isBuffering = false))
    }
}
