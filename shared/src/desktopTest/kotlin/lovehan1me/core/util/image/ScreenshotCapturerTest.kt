package lovehan1me.core.util.image

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import lovehan1me.core.platform.encodePngArgb

/**
 * [ScreenshotCapturer] 的测试（M3-c）。
 *
 * 两条主线：
 * 1. **尺寸反推是纯逻辑，在这里钉死** —— 它是"图不错位"的唯一保证。
 *    真机上三端引擎返回的短边正好差 1 像素这种事很难复现定位（图会整体错位），
 *    而单测里一行断言就锁住了。
 * 2. **PNG 走真实编码实现**（desktopTest 里 expect/actual 解析到桌面 actual = ImageIO），
 *    再用 ImageIO 解码回来逐点比对 —— 与 `Gif89aEncoderTest` 用独立解码器做往返验证同一思路。
 */
class ScreenshotCapturerTest {

    private val w = 64
    private val h = 36

    private fun solid(argb: Int, width: Int = w, height: Int = h) =
        IntArray(width * height) { argb }

    private fun decode(bytes: ByteArray) =
        assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)), "PNG 应当能被解码")

    // ── 尺寸反推 ──────────────────────────────────────

    @Test
    fun `像素数等于请求尺寸时直接用请求尺寸`() {
        assertEquals(1920 to 1080, ScreenshotCapturer.resolveActualSize(1920 * 1080, 1920, 1080))
    }

    @Test
    fun `短边取整差一像素时按长边反推`() {
        // 引擎按长边 1920 缩，短边算成 1081（截断 vs 四舍五入的差异）
        assertEquals(1920 to 1081, ScreenshotCapturer.resolveActualSize(1920 * 1081, 1920, 1080))
    }

    @Test
    fun `竖屏片段同样按长边反推`() {
        assertEquals(1081 to 1920, ScreenshotCapturer.resolveActualSize(1081 * 1920, 1080, 1920))
    }

    @Test
    fun `容差窗口外的尺寸一律拒绝`() {
        // 1080p 源、请求 360 长边时，2073600 / 360 = 5760 也是整数：
        // 没有容差检查就会把 1080p 的图当成 360x5760 去编码。这条用例是那个守卫。
        assertNull(ScreenshotCapturer.resolveActualSize(1920 * 1080, 360, 202))
    }

    @Test
    fun `完全对不上的像素数返回 null`() {
        assertNull(ScreenshotCapturer.resolveActualSize(1000, 1920, 1080))
    }

    @Test
    fun `反推的非法入参返回 null`() {
        assertNull(ScreenshotCapturer.resolveActualSize(0, 1920, 1080))
        assertNull(ScreenshotCapturer.resolveActualSize(-1, 1920, 1080))
        assertNull(ScreenshotCapturer.resolveActualSize(1920 * 1080, 0, 1080))
        assertNull(ScreenshotCapturer.resolveActualSize(1920 * 1080, 1920, 0))
    }

    // ── 目标尺寸 ──────────────────────────────────────

    @Test
    fun `长边超限时等比缩到上限`() {
        assertEquals(1920 to 1080, ScreenshotCapturer.targetSize(3840, 2160, 1920))
        assertEquals(1080 to 1920, ScreenshotCapturer.targetSize(2160, 3840, 1920))
    }

    @Test
    fun `源本就小于上限时不放大`() {
        assertEquals(1280 to 720, ScreenshotCapturer.targetSize(1280, 720, 1920))
    }

    @Test
    fun `上限非正时不做缩放`() {
        assertEquals(3840 to 2160, ScreenshotCapturer.targetSize(3840, 2160, 0))
    }

    // ── 端到端（假抓帧器 + 真 PNG 编码）──────────────

    @Test
    fun `抓到目标尺寸的帧时编码出同尺寸 PNG 且像素一致`() = runBlocking {
        val outcome = ScreenshotCapturer.capture(
            sourceWidth = w,
            sourceHeight = h,
            positionMs = 1234L,
            captureFrameArgb = { _, _, _ -> solid(0xFF3366CC.toInt()) },
        )

        val success = assertIs<ScreenshotCapturer.Outcome.Success>(outcome)
        assertEquals(w, success.width)
        assertEquals(h, success.height)

        val image = decode(success.bytes)
        assertEquals(w, image.width)
        assertEquals(h, image.height)
        // PNG 无损：应当逐点一致（含 alpha）
        assertEquals(0xFF3366CC.toInt(), image.getRGB(0, 0))
        assertEquals(0xFF3366CC.toInt(), image.getRGB(w - 1, h - 1))
    }

    @Test
    fun `抓帧把位置与目标尺寸原样传给回调`() = runBlocking {
        var seen: Triple<Long, Int, Int>? = null
        ScreenshotCapturer.capture(
            sourceWidth = 3840,
            sourceHeight = 2160,
            positionMs = 4200L,
            captureFrameArgb = { pos, tw, th ->
                seen = Triple(pos, tw, th)
                solid(0xFF102030.toInt(), 1920, 1080)
            },
        )
        assertEquals(Triple(4200L, 1920, 1080), seen)
    }

    @Test
    fun `引擎只给源尺寸时补一次缩放`() = runBlocking {
        val outcome = ScreenshotCapturer.capture(
            sourceWidth = 3840,
            sourceHeight = 2160,
            positionMs = 0L,
            captureFrameArgb = { _, _, _ -> solid(0xFF102030.toInt(), 3840, 2160) },
        )

        val success = assertIs<ScreenshotCapturer.Outcome.Success>(outcome)
        assertEquals(1920, success.width)
        assertEquals(1080, success.height)
        // 纯色在面积平均下不会变（这正是它比最近邻适合视频降采样的原因）
        assertEquals(0xFF102030.toInt(), decode(success.bytes).getRGB(0, 0))
    }

    @Test
    fun `引擎自己缩过且短边差一像素时按反推尺寸编码`() = runBlocking {
        val outcome = ScreenshotCapturer.capture(
            sourceWidth = 3840,
            sourceHeight = 2160,
            positionMs = 0L,
            captureFrameArgb = { _, _, _ -> solid(0xFF00CC00.toInt(), 1920, 1081) },
        )

        val success = assertIs<ScreenshotCapturer.Outcome.Success>(outcome)
        assertEquals(1920, success.width)
        assertEquals(1081, success.height)
        assertEquals(0xFF00CC00.toInt(), decode(success.bytes).getRGB(0, 0))
    }

    @Test
    fun `alpha 为 0 的帧会被归一成不透明`() = runBlocking {
        val outcome = ScreenshotCapturer.capture(
            sourceWidth = w,
            sourceHeight = h,
            positionMs = 0L,
            captureFrameArgb = { _, _, _ -> IntArray(w * h) { 0x00336699 } },
        )

        val success = assertIs<ScreenshotCapturer.Outcome.Success>(outcome)
        val argb = decode(success.bytes).getRGB(0, 0)
        assertEquals(0xFF, (argb ushr 24) and 0xFF)
        assertEquals(0xFF336699.toInt(), argb)
    }

    @Test
    fun `已是不透明的帧不做多余拷贝`() = runBlocking {
        // 归一化是"全不透明就原样返回"，故这里断言编码结果仍是原值而非被改写
        val frame = solid(0xFF445566.toInt())
        val outcome = ScreenshotCapturer.capture(w, h, 0L) { _, _, _ -> frame }
        val success = assertIs<ScreenshotCapturer.Outcome.Success>(outcome)
        assertEquals(0xFF445566.toInt(), decode(success.bytes).getRGB(0, 0))
    }

    @Test
    fun `抓帧返回 null 时报 NoFrame`() {
        // ⚠️ 这里刻意用块体而不是 `= runBlocking { ... }`：
        // 表达式体 + 以 assertIs 结尾会让函数**返回 assertIs 的值**，
        // JUnit4 随即报 "Method ... should be void" 的初始化错误（整类测试都跑不了）。
        val outcome = runBlocking { ScreenshotCapturer.capture(w, h, 0L) { _, _, _ -> null } }
        assertIs<ScreenshotCapturer.Outcome.NoFrame>(outcome)
    }

    @Test
    fun `源尺寸未上报时报 NoFrame 且不去抓帧`() = runBlocking {
        var called = false
        val outcome = ScreenshotCapturer.capture(0, 0, 0L) { _, _, _ ->
            called = true
            null
        }
        assertIs<ScreenshotCapturer.Outcome.NoFrame>(outcome)
        assertFalse(called, "尺寸都没上报就不该白抓一帧")
    }

    @Test
    fun `像素数与任何尺寸都对不上时报 NoFrame`() {
        val outcome = runBlocking {
            ScreenshotCapturer.capture(w, h, 0L) { _, _, _ -> IntArray(w * h + 7) }
        }
        assertIs<ScreenshotCapturer.Outcome.NoFrame>(outcome)
    }

    // ── PNG 编码器本身 ────────────────────────────────

    @Test
    fun `PNG 签名与 IHDR 尺寸正确`() {
        val bytes = assertNotNull(encodePngArgb(solid(0xFFAABBCC.toInt()), w, h))

        val signature = bytes.take(8).joinToString("") { "%02X".format(it) }
        assertEquals("89504E470D0A1A0A", signature)
        // IHDR：8 字节签名 + 4 字节块长 + 4 字节类型，随后是宽、高（各 4 字节大端）
        assertEquals(w, readBigEndianInt(bytes, 16))
        assertEquals(h, readBigEndianInt(bytes, 20))
    }

    @Test
    fun `编码器对尺寸与像素数不符的入参返回 null`() {
        assertNull(encodePngArgb(IntArray(10), 4, 3))
        assertNull(encodePngArgb(IntArray(0), 0, 0))
        assertNull(encodePngArgb(IntArray(12), 4, -3))
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
