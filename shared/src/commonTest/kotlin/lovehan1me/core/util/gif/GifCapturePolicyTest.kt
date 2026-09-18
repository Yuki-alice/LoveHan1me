package lovehan1me.core.util.gif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [GifCapturePolicy] 的测试（M3-b）。
 *
 * 这里覆盖的是路线图里"**三档封顶 + 超了直接拒绝**"那条要求 ——
 * 它是防 OOM 的闸门，最该被单测锁死，而不是等真机上崩一次再补。
 */
class GifCapturePolicyTest {

    private val limits = GifCapturePolicy.Limits()

    // ── 档位夹紧 ────────────────────────────────────

    @Test
    fun `时长超过上限被夹紧`() {
        val ok = GifCapturePolicy.plan(1920, 1080, requestedDurationMs = 30_000, requestedFps = 12)
        val plan = assertIs<GifCapturePolicy.Result.Ok>(ok).plan
        // 5s @ 12fps = 60 帧
        assertEquals(60, plan.frameCount)
    }

    @Test
    fun `帧率超过上限被夹紧`() {
        val ok = GifCapturePolicy.plan(1920, 1080, requestedDurationMs = 5_000, requestedFps = 60)
        val plan = assertIs<GifCapturePolicy.Result.Ok>(ok).plan
        assertEquals(60, plan.frameCount, "60fps 请求应被夹到 12fps，即 5s → 60 帧")
        assertEquals(8, plan.delayCentis)
    }

    @Test
    fun `不到上限的请求按原样执行`() {
        val ok = GifCapturePolicy.plan(640, 360, requestedDurationMs = 2_000, requestedFps = 10)
        val plan = assertIs<GifCapturePolicy.Result.Ok>(ok).plan
        assertEquals(20, plan.frameCount, "2s @ 10fps = 20 帧")
        assertEquals(10, plan.delayCentis)
    }

    // ── 延时的 GIF 经典坑 ────────────────────────────

    @Test
    fun `延时下限为 2 厘秒`() {
        // 规范里 0/1 厘秒会被浏览器当"未指定"按 100ms 渲染 → 必须夹到 2
        assertEquals(2, GifCapturePolicy.delayCentisFor(100))
        assertEquals(2, GifCapturePolicy.delayCentisFor(60))
    }

    @Test
    fun `常规帧率的延时换算正确`() {
        assertEquals(100, GifCapturePolicy.delayCentisFor(1))
        assertEquals(20, GifCapturePolicy.delayCentisFor(5))
        assertEquals(10, GifCapturePolicy.delayCentisFor(10))
        assertEquals(8, GifCapturePolicy.delayCentisFor(12))
    }

    // ── 分辨率 ──────────────────────────────────────

    @Test
    fun `横屏超限时按长边等比缩小`() {
        assertEquals(360 to 202, GifCapturePolicy.scaleToLongEdge(1920, 1080, 360))
    }

    @Test
    fun `竖屏按长边缩小（不是按宽）`() {
        assertEquals(202 to 360, GifCapturePolicy.scaleToLongEdge(1080, 1920, 360))
    }

    @Test
    fun `源小于上限时不放大`() {
        assertEquals(320 to 180, GifCapturePolicy.scaleToLongEdge(320, 180, 360))
        assertEquals(360 to 360, GifCapturePolicy.scaleToLongEdge(360, 360, 360))
    }

    @Test
    fun `极端宽高比也保证两边至少 1 像素`() {
        val (w, h) = GifCapturePolicy.scaleToLongEdge(4000, 3, 360)
        assertTrue(w >= 1 && h >= 1, "得到 ${w}x$h")
        assertEquals(360, w)
    }

    // ── 拒绝路径（防 OOM）────────────────────────────

    @Test
    fun `超出内存预算时拒绝`() {
        val tiny = GifCapturePolicy.Limits(maxFrameBufferBytes = 1024)
        val result = GifCapturePolicy.plan(1920, 1080, 5_000, 12, tiny)
        val rejected = assertIs<GifCapturePolicy.Result.Rejected>(result)
        assertEquals(GifCapturePolicy.RejectReason.FrameBufferTooLarge, rejected.reason)
    }

    @Test
    fun `源尺寸非法时拒绝`() {
        for (bad in listOf(0 to 1080, 1920 to 0, -1 to 100)) {
            val result = GifCapturePolicy.plan(bad.first, bad.second, 5_000, 12)
            val rejected = assertIs<GifCapturePolicy.Result.Rejected>(result)
            assertEquals(GifCapturePolicy.RejectReason.InvalidSourceSize, rejected.reason)
        }
    }

    @Test
    fun `时长或帧率非正时拒绝`() {
        for (d in listOf(0, -100)) {
            val r = GifCapturePolicy.plan(1920, 1080, d, 12)
            assertEquals(
                GifCapturePolicy.RejectReason.InvalidRequest,
                assertIs<GifCapturePolicy.Result.Rejected>(r).reason,
            )
        }
        for (f in listOf(0, -12)) {
            val r = GifCapturePolicy.plan(1920, 1080, 5_000, f)
            assertEquals(
                GifCapturePolicy.RejectReason.InvalidRequest,
                assertIs<GifCapturePolicy.Result.Rejected>(r).reason,
            )
        }
    }

    // ── 计划自洽 ────────────────────────────────────

    @Test
    fun `估算内存与尺寸帧数一致`() {
        val ok = GifCapturePolicy.plan(1920, 1080, 5_000, 12)
        val plan = assertIs<GifCapturePolicy.Result.Ok>(ok).plan
        val expected = plan.frameCount.toLong() * plan.outputWidth * plan.outputHeight *
            GifCapturePolicy.BYTES_PER_PIXEL
        assertEquals(expected, plan.estimatedFrameBytes)
        assertTrue(
            plan.estimatedFrameBytes <= limits.maxFrameBufferBytes,
            "通过的方案必须落在预算内",
        )
    }

    @Test
    fun `默认上限录满 5 秒的内存占用远低于预算`() {
        val ok = GifCapturePolicy.plan(3840, 2160, 5_000, 12)
        val plan = assertIs<GifCapturePolicy.Result.Ok>(ok).plan
        // 60 帧 × 360×202 × 4B ≈ 16.6 MB，预算 48 MiB → 约 2.9 倍余量
        assertTrue(
            plan.estimatedFrameBytes < limits.maxFrameBufferBytes / 2,
            "实际估算 ${plan.estimatedFrameBytes} 字节，预算 ${limits.maxFrameBufferBytes}",
        )
    }

    @Test
    fun `整数运算不会因大尺寸溢出为负`() {
        val huge = GifCapturePolicy.Limits(
            maxDurationMs = 60_000,
            maxFps = 60,
            maxLongEdgePx = 4096,
            maxFrameBufferBytes = Long.MAX_VALUE,
        )
        val ok = GifCapturePolicy.plan(7680, 4320, 60_000, 60, huge)
        val plan = assertIs<GifCapturePolicy.Result.Ok>(ok).plan
        assertTrue(plan.estimatedFrameBytes > 0, "溢出成了 ${plan.estimatedFrameBytes}")
    }
}
