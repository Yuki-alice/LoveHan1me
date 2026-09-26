package lovehan1me.core.util.gif

import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [GifRecorder] 的端到端测试（M3-b）。
 *
 * 用**假抓帧器**把整条管线跑通，于是路线图要求的
 * 「失败路径（超限/抓帧失败/编码失败）有提示无崩溃」也能在单测里被锁死 ——
 * 不必等真机上抓帧失败一次才发现异常没兜住。
 */
class GifRecorderTest {

    private val w = 320
    private val h = 180

    private fun frame(seed: Int): IntArray {
        val random = Random(seed)
        return IntArray(w * h) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
    }

    // ── 成功路径 ────────────────────────────────────

    @Test
    fun `成功录制出可解析的多帧 GIF`() = runBlocking {
        var call = 0
        val outcome = GifRecorder.record(
            sourceWidth = w,
            sourceHeight = h,
            requestedDurationMs = 500,
            requestedFps = 12,
            startPositionMs = 0,
            captureFrameAt = { _, _, _ -> frame(call++) },
        )

        val success = assertIs<GifRecorder.Outcome.Success>(outcome)
        // 500ms @ 12fps = 6 帧
        assertEquals(6, success.plan.frameCount)

        val gif = success.bytes
        assertEquals("GIF89a", gif.copyOfRange(0, 6).decodeToString())
        assertEquals(0x3B.toByte(), gif.last(), "必须以 Trailer 结尾")

        val frames = MinimalGifDecoder.decodeFrames(gif)
        assertEquals(6, frames.size, "写进去几帧就该解出几帧")
        for (f in frames) assertEquals(w * h, f.size)

        assertEquals(w, gif.readShortLe(6))
        assertEquals(h, gif.readShortLe(8))
    }

    @Test
    fun `每帧延时按夹紧后的帧率写入`() = runBlocking {
        var call = 0
        val outcome = GifRecorder.record(
            sourceWidth = w,
            sourceHeight = h,
            requestedDurationMs = 500,
            requestedFps = 12,
            startPositionMs = 0,
            captureFrameAt = { _, _, _ -> frame(call++) },
        )
        val success = assertIs<GifRecorder.Outcome.Success>(outcome)
        val delays = MinimalGifDecoder.readDelays(success.bytes)
        assertEquals(List(success.plan.frameCount) { 8 }, delays, "12fps → 每帧 8 厘秒")
    }

    @Test
    fun `中位切分被实际使用`() = runBlocking {
        var call = 0
        val outcome = GifRecorder.record(
            sourceWidth = w,
            sourceHeight = h,
            requestedDurationMs = 500,
            requestedFps = 12,
            startPositionMs = 0,
            paletteColors = 64,
            captureFrameAt = { _, _, _ -> frame(call++) },
        )
        val success = assertIs<GifRecorder.Outcome.Success>(outcome)
        assertTrue(success.usedMedianCut, "随机噪声图应当走中位切分")
        assertTrue(success.paletteSize in 2..64, "实际色数 ${success.paletteSize}，应在 2..64")
    }

    @Test
    fun `纯色片段能被精确还原`() = runBlocking {
        val color = 0xFF336699.toInt()
        val outcome = GifRecorder.record(
            sourceWidth = w,
            sourceHeight = h,
            requestedDurationMs = 200,
            requestedFps = 10,
            startPositionMs = 0,
            captureFrameAt = { _, _, _ -> IntArray(w * h) { color } },
        )
        val success = assertIs<GifRecorder.Outcome.Success>(outcome)
        assertEquals(1, success.paletteSize, "纯色片段只需 1 个槽位")

        val table = MinimalGifDecoder.readGlobalColorTable(success.bytes)
        assertEquals(color, table[0], "纯色应被原样写入颜色表")
    }

    @Test
    fun `进度回调覆盖 0 到总帧数`() = runBlocking {
        val seen = mutableListOf<Pair<Int, Int>>()
        var call = 0
        GifRecorder.record(
            sourceWidth = w,
            sourceHeight = h,
            requestedDurationMs = 500,
            requestedFps = 12,
            startPositionMs = 0,
            onProgress = { done, total -> seen.add(done to total) },
            captureFrameAt = { _, _, _ -> frame(call++) },
        )
        assertEquals(7, seen.size, "应为 0..6 共 7 次回调，实际 $seen")
        assertEquals(0 to 6, seen.first())
        assertEquals(6 to 6, seen.last())
    }

    // ── 失败路径（路线图明确要求：有提示、无崩溃）────────────

    @Test
    fun `平台不支持抓帧时返回 CaptureFailed 而不是抛异常`() = runBlocking {
        val outcome = GifRecorder.record(
            sourceWidth = w,
            sourceHeight = h,
            requestedDurationMs = 500,
            requestedFps = 12,
            startPositionMs = 0,
            captureFrameAt = { _, _, _ -> null },
        )
        val failed = assertIs<GifRecorder.Outcome.CaptureFailed>(outcome)
        assertEquals(0, failed.capturedFrames)
        assertEquals(6, failed.expectedFrames)
    }

    @Test
    fun `中途抓帧失败时报出已抓帧数`() = runBlocking {
        var call = 0
        val outcome = GifRecorder.record(
            sourceWidth = w,
            sourceHeight = h,
            requestedDurationMs = 500,
            requestedFps = 12,
            startPositionMs = 0,
            captureFrameAt = { _, _, _ -> if (call++ == 3) null else frame(call) },
        )
        val failed = assertIs<GifRecorder.Outcome.CaptureFailed>(outcome)
        assertEquals(3, failed.capturedFrames, "第 4 帧（index 3）失败，此前成功 3 帧")
        assertEquals(6, failed.expectedFrames)
    }

    @Test
    fun `超内存预算时在抓帧前就被拒绝`() = runBlocking {
        var captured = false
        val outcome = GifRecorder.record(
            sourceWidth = w,
            sourceHeight = h,
            requestedDurationMs = 500,
            requestedFps = 12,
            startPositionMs = 0,
            limits = GifCapturePolicy.Limits(maxFrameBufferBytes = 1024),
            captureFrameAt = { _, _, _ -> captured = true; frame(0) },
        )
        val rejected = assertIs<GifRecorder.Outcome.Rejected>(outcome)
        assertEquals(GifCapturePolicy.RejectReason.FrameBufferTooLarge, rejected.reason)
        assertTrue(!captured, "被拒的请求不该消耗任何一次抓帧")
    }

    @Test
    fun `源尺寸非法时被拒绝`() = runBlocking {
        val outcome = GifRecorder.record(
            sourceWidth = 0,
            sourceHeight = 0,
            requestedDurationMs = 500,
            requestedFps = 12,
            startPositionMs = 0,
            captureFrameAt = { _, _, _ -> null },
        )
        val rejected = assertIs<GifRecorder.Outcome.Rejected>(outcome)
        assertEquals(GifCapturePolicy.RejectReason.InvalidSourceSize, rejected.reason)
    }

    // ── 缩放接入 ────────────────────────────────────

    @Test
    fun `源尺寸大于上限时抓帧后按计划缩放`() = runBlocking {
        val bigW = 1280
        val bigH = 720
        var call = 0
        val outcome = GifRecorder.record(
            sourceWidth = bigW,
            sourceHeight = bigH,
            requestedDurationMs = 300,
            requestedFps = 10,
            startPositionMs = 0,
            captureFrameAt = { _, _, _ ->
                val random = Random(call++)
                IntArray(bigW * bigH) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
            },
        )
        val success = assertIs<GifRecorder.Outcome.Success>(outcome)
        // 1280x720 长边 1280 → 缩到长边 360 = 360x202
        assertEquals(360, success.plan.outputWidth)
        assertEquals(202, success.plan.outputHeight)
        assertEquals(360, success.bytes.readShortLe(6))
        assertEquals(202, success.bytes.readShortLe(8))

        val frames = MinimalGifDecoder.decodeFrames(success.bytes)
        assertEquals(success.plan.frameCount, frames.size)
        for (f in frames) assertEquals(360 * 202, f.size, "缩放后的帧尺寸应一致")
    }

    @Test
    fun `抓帧返回已是目标尺寸时直接采用`() = runBlocking {
        var call = 0
        val outcome = GifRecorder.record(
            sourceWidth = 1280,
            sourceHeight = 720,
            requestedDurationMs = 200,
            requestedFps = 10,
            startPositionMs = 0,
            // 假装平台侧已经按输出尺寸抓好了（360x202）
            captureFrameAt = { _, _, _ ->
                val random = Random(call++)
                IntArray(360 * 202) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
            },
        )
        val success = assertIs<GifRecorder.Outcome.Success>(outcome)
        assertNotNull(success.bytes)
        assertEquals(2, success.plan.frameCount)
    }
}
