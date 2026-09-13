package lovehan1me.core.util.gif

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [FrameScaler] 的测试（M3-b）。
 *
 * 缩放是"录 GIF 前把 1080p 压到 360p"的那一步，错了会让整段 GIF 颜色偏，
 * 所以这里对**已知输入**断言精确像素值，而不是只看尺寸对不对。
 */
class FrameScalerTest {

    @Test
    fun `尺寸相同时返回内容相同的副本而非同一引用`() {
        val src = IntArray(4) { 0xFF000000.toInt() or it }
        val out = FrameScaler.scale(src, 2, 2, 2, 2)
        assertContentEquals(src, out)
        assertTrue(src !== out, "必须返回新数组，避免调用方被意外共享")
    }

    @Test
    fun `2x2 缩到 1x1 是四像素面积平均`() {
        val src = intArrayOf(
            0xFFFF0000.toInt(), // 红
            0xFF00FF00.toInt(), // 绿
            0xFF0000FF.toInt(), // 蓝
            0xFFFFFFFF.toInt(), // 白
        )
        val out = FrameScaler.scale(src, 2, 2, 1, 1)
        // R=(255+0+0+255)/4=127  G=(0+255+0+255)/4=127  B=(0+0+255+255)/4=127
        assertEquals(0xFF7F7F7F.toInt(), out[0])
    }

    @Test
    fun `纯色图缩放后颜色不变`() {
        val color = 0xFF336699.toInt()
        val src = IntArray(16) { color }
        val out = FrameScaler.scale(src, 4, 4, 2, 2)
        assertEquals(4, out.size)
        for (px in out) assertEquals(color, px, "纯色图不该因平均而偏色")
    }

    @Test
    fun `输出尺寸与像素数一致`() {
        val src = IntArray(1920 * 1080) { 0xFF101010.toInt() }
        val out = FrameScaler.scale(src, 1920, 1080, 360, 202)
        assertEquals(360 * 202, out.size)
    }

    @Test
    fun `输出 alpha 恒为不透明`() {
        val random = Random(7)
        val src = IntArray(64) { random.nextInt() } // alpha 随机
        val out = FrameScaler.scale(src, 8, 8, 4, 4)
        for (px in out) {
            assertEquals(0xFF, (px ushr 24) and 0xFF, "GIF 无透明，输出必须不透明")
        }
    }

    @Test
    fun `非整数倍缩放不丢行也不越界`() {
        // 7x5 → 3x2：区间边界要覆盖到每一行每一列，且不能越界
        val src = IntArray(35) { 0xFF000000.toInt() or (it and 0xFF) }
        val out = FrameScaler.scale(src, 7, 5, 3, 2)
        assertEquals(6, out.size)
        for (px in out) {
            assertTrue((px and 0xFF) in 0..255, "越界读了源数据：$px")
        }
    }

    @Test
    fun `放大走最近邻且不崩`() {
        val src = intArrayOf(0xFFAA0000.toInt(), 0xFF00BB00.toInt())
        val out = FrameScaler.scale(src, 2, 1, 4, 2)
        assertEquals(8, out.size)
        for (px in out) assertEquals(0xFF, (px ushr 24) and 0xFF)
    }

    @Test
    fun `源长度与尺寸不符时抛异常`() {
        assertFailsWith<IllegalArgumentException> {
            FrameScaler.scale(IntArray(3), 2, 2, 1, 1)
        }
    }

    @Test
    fun `目标尺寸非正时抛异常`() {
        assertFailsWith<IllegalArgumentException> {
            FrameScaler.scale(IntArray(4), 2, 2, 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            FrameScaler.scale(IntArray(4), 2, 2, 1, -1)
        }
    }
}
