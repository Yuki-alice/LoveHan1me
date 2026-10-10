package lovehan1me.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B5-0 下载限速的确定性用例：只断言"读了多少字节该等多久"的纯算术，
 * 不碰真实时钟（[DownloadThrottle] 的挂起层只是把结果交给 `delay`）。
 */
class DownloadThrottleTest {

    @Test
    fun `不限速恒不等待`() {
        assertEquals(
            0L,
            throttleDelayMillis(transferredBytes = 10_000_000L, elapsedMillis = 0L, bytesPerSecond = 0L),
        )
    }

    @Test
    fun `还没传字节就不等待`() {
        assertEquals(
            0L,
            throttleDelayMillis(transferredBytes = 0L, elapsedMillis = 0L, bytesPerSecond = 1024L),
        )
    }

    @Test
    fun `按速率折算应补的毫秒`() {
        // 1000 B/s 传了 1000 B：该花 1000ms，已过 0 → 补 1000
        assertEquals(1000L, throttleDelayMillis(1000L, 0L, 1000L))
        // 已经挂了 400ms 的墙钟 → 只补余下 600
        assertEquals(600L, throttleDelayMillis(1000L, 400L, 1000L))
    }

    @Test
    fun `已超前不回补`() {
        // 传得比额定慢（或读取本身很慢）时不该反过来惩罚，返回 0
        assertEquals(0L, throttleDelayMillis(1000L, 5_000L, 1000L))
    }

    @Test
    fun `多兆档位折算正确`() {
        // 1 MB/s（1024×1024）传了 4 MB：该花 4000ms
        val oneMb = 1024L * 1024L
        assertEquals(4000L, throttleDelayMillis(4 * oneMb, 0L, oneMb))
    }
}