package lovehan1me.core.util.gif

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Buffer
import java.io.File
import kotlin.random.Random

/**
 * [Gif89aEncoder] 的往返测试：编码 → 用极简解码器解回索引 → 逐像素比对。
 *
 * 之所以要自己写解码器而不是「肉眼打开文件看」：GIF 的坑几乎全在 LZW 的
 * 位序（LSB first）和码长递增时机上，肉眼完全看不出来，只有往返比对能证明。
 *
 * 解码器只覆盖本编码器产出的子集（全局颜色表、无透明、非隔行），
 * 不是通用 GIF 解码器。
 */
class Gif89aEncoderTest {

    // ── 容器层 ────────────────────────────────────

    @Test
    fun `文件头是 GIF89a`() {
        val gif = Gif89aEncoder.encode(2, 2, listOf(solidFrame(2, 2, 0xFFFF0000.toInt())), 10)
        assertEquals("GIF89a", gif.copyOfRange(0, 6).decodeToString())
        assertEquals(0x3B.toByte(), gif.last())
    }

    @Test
    fun `逻辑屏幕尺寸写入正确`() {
        val gif = Gif89aEncoder.encode(7, 5, listOf(solidFrame(7, 5, 0xFF00FF00.toInt())), 10)
        assertEquals(7, gif.readShortLe(6))
        assertEquals(5, gif.readShortLe(8))
    }

    @Test
    fun `多帧写入 NETSCAPE 循环扩展`() {
        val frames = listOf(
            solidFrame(4, 4, 0xFFFF0000.toInt()),
            solidFrame(4, 4, 0xFF0000FF.toInt()),
        )
        val gif = Gif89aEncoder.encode(4, 4, frames, 10, loop = 0)
        assertTrue(gif.decodeToString().contains("NETSCAPE2.0"))
        assertEquals(2, MinimalGifDecoder.decodeFrames(gif).size)
    }

    // ── 像素往返 ────────────────────────────────────

    @Test
    fun `单帧纯色往返一致`() {
        val w = 16
        val h = 9
        val frame = solidFrame(w, h, 0xFF123456.toInt())
        assertRoundTrip(w, h, listOf(frame))
    }

    @Test
    fun `渐变往返一致`() {
        val w = 64
        val h = 48
        val frame = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            0xFF000000.toInt() or (x * 4 shl 16) or (y * 5 shl 8) or ((x + y) * 2)
        }
        assertRoundTrip(w, h, listOf(frame))
    }

    @Test
    fun `随机噪声往返一致（覆盖码长递增）`() {
        val w = 64
        val h = 64
        val random = Random(20260913)
        val frame = IntArray(w * h) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        assertRoundTrip(w, h, listOf(frame))
    }

    @Test
    fun `大图高噪声往返一致（覆盖字典满与 clear）`() {
        // 256×256×3 通道的高噪声足以把 LZW 字典撑到 4095，触发 clear 重置分支。
        val w = 200
        val h = 200
        val random = Random(42)
        val frame = IntArray(w * h) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        assertRoundTrip(w, h, listOf(frame))
    }

    @Test
    fun `多帧每帧都往返一致`() {
        val w = 32
        val h = 32
        val random = Random(7)
        val frames = List(3) { IntArray(w * h) { 0xFF000000.toInt() or random.nextInt(0x1000000) } }
        assertRoundTrip(w, h, frames)
    }

    /**
     * 落一个真实 GIF 到 build 目录，供外部看图工具打开核对。
     *
     * 往返测试只能证明「按我们自己的解码器解回来是对的」，不能证明「标准解码器认」——
     * 这个产物就是用来过外部工具这一关的。
     */
    @Test
    fun `产出示例 GIF 供外部工具核对`() {
        val w = 160
        val h = 120
        val frameCount = 12
        val frames = List(frameCount) { f ->
            val phase = f.toFloat() / frameCount
            IntArray(w * h) { i ->
                val x = i % w
                val y = i / w
                val r = ((x.toFloat() / w + phase) * 255f) % 256f
                val g = (y.toFloat() / h) * 255f
                val b = phase * 255f
                0xFF000000.toInt() or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            }
        }
        val gif = Gif89aEncoder.encode(w, h, frames, delayCentis = 10, loop = 0)
        val out = File("build/gif-spike/sample.gif")
        out.parentFile?.mkdirs()
        out.writeBytes(gif)
        println("sample gif -> ${out.absolutePath} (${gif.size} bytes)")
        assertTrue(out.length() > 0)
    }

    // ── 断言行数少但关键 ────────────────────────────────────

    private fun assertRoundTrip(w: Int, h: Int, frames: List<IntArray>) {
        val gif = Gif89aEncoder.encode(w, h, frames, delayCentis = 5)
        val decoded = MinimalGifDecoder.decodeFrames(gif)
        assertEquals(frames.size, decoded.size, "帧数不符")
        frames.forEachIndexed { index, frame ->
            val expected = IntArray(frame.size) { Gif89aEncoder.quantize(frame[it]) }
            assertEquals(w * h, decoded[index].size, "第 $index 帧像素数不符")
            assertContentEquals(expected, decoded[index], "第 $index 帧像素往返不一致")
        }
    }

    private fun solidFrame(w: Int, h: Int, argb: Int) = IntArray(w * h) { argb }
}
