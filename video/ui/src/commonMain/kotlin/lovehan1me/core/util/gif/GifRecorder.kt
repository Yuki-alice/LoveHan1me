package lovehan1me.core.util.gif

/**
 * 片段转 GIF 的录制管线（M3-b）。
 *
 * ## 为什么把"抓帧"做成注入的回调
 * 三端抓帧方式完全不同（Android 从渲染面取、桌面走 mpv、iOS 走 AVPlayer 输出），
 * 但**编排逻辑是一样的**：算计划 → 按间隔抓帧 → 缩放 → 建调色板 → 编码。
 * 把差异收进 `captureFrameAt` 这一个挂起回调后，整条管线（含超限/抓帧失败/编码失败
 * 这些失败路径）都能在桌面单测里用假抓帧器跑完 —— 这正是路线图要求
 * 「失败路径有提示无崩溃」最需要的可验证性。
 *
 * ## 为什么按"定位取帧"而不是"实时连拍"
 * `captureFrameAt(positionMs)` 的语义是**把播放器定位到该时刻再取一帧**，
 * 于是同一片段每次录制结果一致、不依赖抓帧速度（实时连拍在低端机上必然掉帧，
 * 掉帧后 GIF 节奏就毁了）。代价是 60 帧要定位 60 次，比实时录制慢，
 * 故 [record] 提供 `onProgress` 供 UI 显示进度。
 *
 * 内存被 [GifCapturePolicy.Limits.maxFrameBufferBytes] 硬闸守住：
 * 计划阶段就拒绝超预算的请求，所以这里可以安全地把帧攒在内存里。
 */
object GifRecorder {

    /** 抓帧进度回调：(已完成帧数, 总帧数)。 */
    fun interface ProgressListener {
        fun onProgress(done: Int, total: Int)
    }

    /** 录制结果。UI 应当对每个分支给出不同提示，不允许静默失败。 */
    sealed interface Outcome {
        /** 成功，[bytes] 即 GIF89a 字节流。 */
        data class Success(
            val bytes: ByteArray,
            val plan: GifCapturePolicy.Plan,
            /** 实际用到的颜色数（中位切分算出来的）。 */
            val paletteSize: Int,
            /** false 表示中位切分不可用、退化成了固定 3:3:2。 */
            val usedMedianCut: Boolean,
        ) : Outcome {
            // ByteArray 是引用比较，data class 需手写 equals/hashCode 才符合直觉
            override fun equals(other: Any?): Boolean =
                this === other || (other is Success &&
                    plan == other.plan &&
                    paletteSize == other.paletteSize &&
                    usedMedianCut == other.usedMedianCut &&
                    bytes.contentEquals(other.bytes))

            override fun hashCode(): Int {
                var result = bytes.contentHashCode()
                result = 31 * result + plan.hashCode()
                result = 31 * result + paletteSize
                result = 31 * result + usedMedianCut.hashCode()
                return result
            }
        }

        /** 计划阶段就被拒（超时长/帧率/分辨率上限，或超内存预算）。 */
        data class Rejected(val reason: GifCapturePolicy.RejectReason) : Outcome

        /** 抓帧失败：平台不支持抓帧，或中途某帧取不到。 */
        data class CaptureFailed(val capturedFrames: Int, val expectedFrames: Int) : Outcome

        /** 编码失败（异常已捕获，不会崩）。 */
        data class EncodeFailed(val message: String) : Outcome
    }

    /**
     * 录制一段 GIF。
     *
     * @param sourceWidth / [sourceHeight] 视频源尺寸（来自播放器 state）
     * @param requestedDurationMs 想录多久（会被上限夹紧）
     * @param requestedFps 想要的帧率（会被上限夹紧）
     * @param startPositionMs 起始播放位置
     * @param captureFrameAt 把播放器定位到给定毫秒并返回该帧的 ARGB 像素
     *        （长度必须为 `宽*高`）；返回 null 表示抓帧失败/平台不支持。
     *        **同时传入目标输出尺寸**：桌面端 mediamp 的 `FramePreview.getPreviewFrame(pos, w, h)`
     *        能在解码侧直接出目标尺寸，省掉一次全尺寸帧的分配与 Kotlin 侧重缩放。
     *        实现方若只能给源尺寸，返回源尺寸即可，本函数会补做缩放。
     * @param onProgress 进度回调，便于 UI 显示"正在生成 12/60"
     */
    suspend fun record(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedDurationMs: Int,
        requestedFps: Int,
        startPositionMs: Long,
        limits: GifCapturePolicy.Limits = GifCapturePolicy.Limits(),
        paletteColors: Int = 256,
        onProgress: ProgressListener = ProgressListener { _, _ -> },
        captureFrameAt: suspend (positionMs: Long, targetWidth: Int, targetHeight: Int) -> IntArray?,
    ): Outcome {
        val planned = GifCapturePolicy.plan(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            requestedDurationMs = requestedDurationMs,
            requestedFps = requestedFps,
            limits = limits,
        )
        val plan = when (planned) {
            is GifCapturePolicy.Result.Rejected -> return Outcome.Rejected(planned.reason)
            is GifCapturePolicy.Result.Ok -> planned.plan
        }

        // 帧间隔用计划里已经定型（且夹过下限）的 delayCentis 反推，
        // 保证"抓帧的时间点"与"GIF 里播放的时间点"严格一致
        val stepMs = plan.delayCentis * 10L

        // 先按源尺寸抓，再统一缩放到计划尺寸 ——
        // 让平台侧抓帧实现不必关心输出尺寸（少一处三端分歧点）
        val rawCapture = captureFrameAt(
            startPositionMs,
            plan.outputWidth,
            plan.outputHeight,
        ) ?: return Outcome.CaptureFailed(capturedFrames = 0, expectedFrames = plan.frameCount)

        val frames = ArrayList<IntArray>(plan.frameCount)
        onProgress.onProgress(0, plan.frameCount)

        var current = rawCapture
        for (i in 0 until plan.frameCount) {
            if (i > 0) {
                val grabbed = captureFrameAt(
                    startPositionMs + i * stepMs,
                    plan.outputWidth,
                    plan.outputHeight,
                ) ?: return Outcome.CaptureFailed(capturedFrames = i, expectedFrames = plan.frameCount)
                current = grabbed
            }
            frames.add(scaleToPlan(current, sourceWidth, sourceHeight, plan))
            onProgress.onProgress(i + 1, plan.frameCount)
        }

        // 调色板：中位切分失败（例如 OOM）不应让整次录制失败，退化成固定 3:3:2
        val medianCut = runCatching {
            MedianCutPalette.build(frames, maxColors = paletteColors)
        }.getOrNull()
        val palette: GifPalette = medianCut ?: Fixed332Palette
        val usedMedianCut = medianCut != null

        val encoded = runCatching {
            Gif89aEncoder.encode(
                width = plan.outputWidth,
                height = plan.outputHeight,
                frames = frames,
                delayCentis = plan.delayCentis,
                loop = 0,
                palette = palette,
            )
        }.getOrElse { t ->
            return Outcome.EncodeFailed(t.message ?: t::class.simpleName ?: "unknown")
        }

        return Outcome.Success(
            bytes = encoded,
            plan = plan,
            paletteSize = palette.size,
            usedMedianCut = usedMedianCut,
        )
    }

    private fun scaleToPlan(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        plan: GifCapturePolicy.Plan,
    ): IntArray {
        // 抓帧实现已经按输出尺寸给（或源尺寸不明）时直接采用，避免无谓的一次拷贝
        if (pixels.size == plan.outputWidth * plan.outputHeight) return pixels
        if (pixels.size != sourceWidth * sourceHeight) {
            // 尺寸对不上就当它已经是目标尺寸，交给编码器的 require 报错，
            // 但这里先兜住，避免帧数对了尺寸错了导致越界
            return pixels
        }
        return FrameScaler.scale(
            source = pixels,
            srcW = sourceWidth,
            srcH = sourceHeight,
            dstW = plan.outputWidth,
            dstH = plan.outputHeight,
        )
    }
}
