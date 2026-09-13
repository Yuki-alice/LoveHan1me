package lovehan1me.core.util.gif

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [MedianCutPalette] 的测试（M3-b）。
 *
 * 关键立场：调色板的优劣**不能靠肉眼看**，必须量化。
 * 所以这里除了行为断言（尺寸/范围/确定性），还有一条
 * [中位切分的还原误差低于固定 3 3 2] —— 用均方误差直接比出画质差，
 * 否则"引入中位切分"这件事就没有可验证的收益。
 */
class MedianCutPaletteTest {

    // ── 行为 ────────────────────────────────────────

    @Test
    fun `颜色数少于上限时不虚增调色板`() {
        val frame = IntArray(64) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val palette = MedianCutPalette.build(listOf(frame), maxColors = 256)
        assertEquals(2, palette.size, "只有黑白两色，调色板应恰好 2 项")
    }

    @Test
    fun `高频色被精确保留`() {
        // 全 0 / 全 255 的通道能被 5bit 精确还原（255 shr 3 = 31，expand(31) = 255）
        val frame = IntArray(32) { if (it < 24) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val palette = MedianCutPalette.build(listOf(frame), maxColors = 256)
        val decodedColors = (0 until palette.size).map { palette.colorAt(it) }.toSet()
        assertTrue(
            decodedColors.containsAll(setOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())),
            "黑白两色必须原样出现在调色板里，实际 $decodedColors",
        )
    }

    @Test
    fun `色数超过上限时收敛到上限`() {
        val w = 128; val h = 128
        val gradient = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            0xFF000000.toInt() or ((x * 2) shl 16) or ((y * 2) shl 8) or ((x + y) and 0xFF)
        }
        val palette = MedianCutPalette.build(listOf(gradient), maxColors = 8)
        assertTrue(palette.size <= 8, "调色板不应超过 maxColors，实际 ${palette.size}")
        assertTrue(palette.size >= 2, "渐变图至少该分出 2 色，实际 ${palette.size}")
    }

    @Test
    fun `索引始终落在调色板范围内`() {
        val random = Random(1234)
        val frame = IntArray(4096) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val palette = MedianCutPalette.build(listOf(frame), maxColors = 16)
        for (px in frame) {
            val idx = palette.indexOf(px)
            assertTrue(idx in 0 until palette.size, "索引 $idx 越界（size=${palette.size}）")
        }
    }

    @Test
    fun `同一输入产出同一调色板（确定性）`() {
        val random = Random(99)
        val frames = List(3) { IntArray(1024) { 0xFF000000.toInt() or random.nextInt(0x1000000) } }
        val a = MedianCutPalette.build(frames, maxColors = 32)
        val b = MedianCutPalette.build(frames, maxColors = 32)
        assertEquals(a.size, b.size, "调色板项数应可复现")
        assertContentEquals(
            IntArray(a.size) { a.colorAt(it) },
            IntArray(b.size) { b.colorAt(it) },
            "调色板颜色应可复现",
        )
    }

    @Test
    fun `空输入返回可用的单色调色板`() {
        val palette = MedianCutPalette.build(emptyList())
        assertEquals(1, palette.size)
        assertEquals(0, palette.indexOf(0xFF123456.toInt()))
    }

    @Test
    fun `全同色画面只占一个槽位`() {
        val frame = IntArray(256) { 0xFF336699.toInt() }
        val palette = MedianCutPalette.build(listOf(frame))
        assertEquals(1, palette.size)
    }

    // ── 画质（这是引入中位切分的全部理由）────────────────

    @Test
    fun `中位切分的还原误差低于固定 3 3 2`() {
        // 造一张"暗部连续渐变"图：这正是 3:3:2 最容易出色带的场景
        val w = 256; val h = 128
        val frame = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            // R/G 在 0..127 的暗区慢速爬升，B 固定 → 3:3:2 在暗区只有 4 个 R 档位
            val r = x / 2
            val g = y
            0xFF000000.toInt() or (r shl 16) or (g shl 8)
        }
        val frames = listOf(frame)

        val medianCut = MedianCutPalette.build(frames, maxColors = 256)
        val fixed = Fixed332Palette

        val mcError = meanSquaredError(frame, medianCut)
        val fixedError = meanSquaredError(frame, fixed)

        println("MSE  medianCut=$mcError  fixed332=$fixedError  (size=${medianCut.size})")
        assertTrue(
            mcError < fixedError,
            "中位切分的还原误差应低于固定 3:3:2：medianCut=$mcError fixed332=$fixedError",
        )
    }

    @Test
    fun `中位切分色数越少误差越大（单调性）`() {
        val w = 128; val h = 128
        val frame = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            0xFF000000.toInt() or ((x * 2) shl 16) or ((y * 2) shl 8) or ((x * y / 64) and 0xFF)
        }
        val frames = listOf(frame)
        val coarse = meanSquaredError(frame, MedianCutPalette.build(frames, maxColors = 4))
        val fine = meanSquaredError(frame, MedianCutPalette.build(frames, maxColors = 128))
        assertTrue(fine < coarse, "128 色的误差（$fine）应小于 4 色（$coarse）")
    }

    /** 把整帧按调色板量化后与原始像素的均方误差（按 8bit RGB 算，忽略 alpha）。 */
    private fun meanSquaredError(frame: IntArray, palette: GifPalette): Double {
        var sum = 0.0
        for (px in frame) {
            val c = palette.colorAt(palette.indexOf(px))
            val dr = ((px shr 16) and 0xFF) - ((c shr 16) and 0xFF)
            val dg = ((px shr 8) and 0xFF) - ((c shr 8) and 0xFF)
            val db = (px and 0xFF) - (c and 0xFF)
            sum += (dr * dr + dg * dg + db * db).toDouble()
        }
        return sum / frame.size
    }
}
