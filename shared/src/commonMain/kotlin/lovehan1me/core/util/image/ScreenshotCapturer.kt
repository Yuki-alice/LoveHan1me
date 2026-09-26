package lovehan1me.core.util.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lovehan1me.core.platform.encodePngArgb
import lovehan1me.core.util.gif.FrameScaler

/**
 * 单帧截图管线（M3-c）。
 *
 * ## 与 M3-b 的关系
 * **取帧那一层完全共用**（三端 `PlaybackEngine.grabFrameArgb`），差别只在后半段：
 * GIF 要按时间轴连抓 N 帧、建调色板、编 GIF89a；截图只要**一帧**、且用无损的 PNG。
 * 所以这里就是"取一帧 → 定尺寸 → 编码"，没有调色板、没有时长策略，也不需要对话框 ——
 * 一次手势直接落到 `exportMediaAndShare`（保存 + 系统分享）。
 *
 * ## 尺寸：为什么必须"反推"而不是"直接用请求值"
 * 三端取帧实现收到一对 `targetWidth/targetHeight`，但它们的真实语义是**"长边"**
 * （`MpvPlaybackEngine` 传给 mpv 的就是 `maxOf(w, h)`；`pixelCopyArgb` 与
 * `toArgbPixels` 收的也是 `targetLongEdge`；mediamp 的 `getPreviewFrame` 同理）——
 * 引擎按长边等比缩放，**短边由各自的取整方式决定，可能与请求值差 1~2 像素**。
 * 而编码器必须拿到**真实**宽高才能正确解释那个一维数组，否则整张图会错位。
 * 故以"返回的像素总数"为准反推真实尺寸（[resolveActualSize]）；
 * 反推不出来就如实报错，**绝不猜一个尺寸硬编**。
 *
 * ## 失败路径
 * 与 [lovehan1me.core.util.gif.GifRecorder] 同一约定：**全部收敛成结果类型、不抛异常**，
 * 由 UI 逐条映射成提示（M3-c 验收要求"失败有提示无崩溃"）。
 */
object ScreenshotCapturer {

    /**
     * 截图长边上限。
     *
     * 取 1920 而不是源尺寸：4K 源一帧的 `IntArray` 是 33 MB，PNG 也常有十几 MB，
     * 而截图的用途是分享/存图 —— 1920 长边在任何屏幕上都不糊。
     * ⚠️ **不要与 GIF 那一档（360）合并**：那是"聊天窗口里的小动图"，
     * 与本档是两个独立的产品决策（见 `GifCapturePolicy.Limits.maxLongEdgePx`）。
     */
    const val DEFAULT_MAX_LONG_EDGE_PX = 1920

    /**
     * 短边反推允许的误差（像素）。
     *
     * 引擎自己缩过之后，短边与请求值的差只可能来自取整方式（`toInt()` 截断 vs 四舍五入），
     * 即 ±1；留到 ±4 是为了容忍不同平台缩放库的舍入差异，同时**远小于**
     * "错误命中"的门槛（见 [resolveActualSize] 里那条把"引擎给了源尺寸"挡掉的用例）。
     */
    private const val SHORT_EDGE_TOLERANCE_PX = 4

    /** 不透明 alpha（`0xAARRGGBB` 的高 8 位）。 */
    private const val OPAQUE_ALPHA = 0xFF000000.toInt()

    /** 截图结果。UI 应当对每个分支给出提示，不允许静默失败。 */
    sealed interface Outcome {
        /** 成功，[bytes] 是 PNG 字节流。 */
        data class Success(
            val bytes: ByteArray,
            val width: Int,
            val height: Int,
        ) : Outcome {
            // ByteArray 是引用比较，data class 需手写 equals/hashCode 才符合直觉
            override fun equals(other: Any?): Boolean =
                this === other || (other is Success &&
                    width == other.width &&
                    height == other.height &&
                    bytes.contentEquals(other.bytes))

            override fun hashCode(): Int = (bytes.contentHashCode() * 31 + width) * 31 + height
        }

        /** 没拿到可用画面：尺寸未上报 / 抓帧返回 null / 返回的像素数与尺寸对不上。 */
        data class NoFrame(val detail: String) : Outcome

        /** 拿到画面但 PNG 编码失败（异常已捕获，不会崩）。 */
        data class EncodeFailed(val message: String) : Outcome
    }

    /**
     * 抓 [positionMs] 处的一帧并编码成 PNG。
     *
     * @param sourceWidth / [sourceHeight] 视频源尺寸（来自播放器 state）：
     *        既用于算目标尺寸，也是"引擎只给源尺寸"时唯一的还原依据。
     * @param captureFrameArgb 抓帧回调，由
     *        [lovehan1me.feature.player.PlaybackController.grabFrameArgb] 提供
     * @param maxLongEdgePx 长边上限，见 [DEFAULT_MAX_LONG_EDGE_PX]
     */
    suspend fun capture(
        sourceWidth: Int,
        sourceHeight: Int,
        positionMs: Long,
        maxLongEdgePx: Int = DEFAULT_MAX_LONG_EDGE_PX,
        captureFrameArgb: suspend (positionMs: Long, targetWidth: Int, targetHeight: Int) -> IntArray?,
    ): Outcome {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return Outcome.NoFrame("source ${sourceWidth}x$sourceHeight")
        }
        if (positionMs < 0L) return Outcome.NoFrame("position $positionMs")

        val (targetWidth, targetHeight) = targetSize(sourceWidth, sourceHeight, maxLongEdgePx)
        val pixels = captureFrameArgb(positionMs, targetWidth, targetHeight)
            ?: return Outcome.NoFrame("capture returned null")

        val frame = normalize(pixels, sourceWidth, sourceHeight, targetWidth, targetHeight)
            ?: return Outcome.NoFrame("pixels=${pixels.size} target=${targetWidth}x$targetHeight")

        // 编码放 Default：1080p 的 PNG 压缩有几十~几百毫秒，不该压在 UI 线程上。
        // ⚠️ 抓帧**不跟着挪**——mediamp/mpv 要求播放器操作发在主线程（见各引擎的 KDoc），
        // 所以只有编码这一段切换调度器。
        val encoded = runCatching {
            withContext(Dispatchers.Default) {
                encodePngArgb(frame.pixels, frame.width, frame.height)
            }
        }
        val bytes = encoded.getOrNull() ?: return Outcome.EncodeFailed(
            encoded.exceptionOrNull()?.message
                ?: "PNG 编码返回 null（${frame.width}x${frame.height}）",
        )

        return Outcome.Success(bytes = bytes, width = frame.width, height = frame.height)
    }

    /** 一帧的像素与其**真实**宽高。 */
    private class Frame(val pixels: IntArray, val width: Int, val height: Int)

    /**
     * 把抓回来的像素对齐到一组**真实**宽高。
     *
     * 三条路径（按可能性排序）：
     * 1. 尺寸正好等于请求值 —— iOS 与桌面 mediamp 通常如此；
     * 2. 等于**源尺寸** —— 引擎契约允许"只能给源尺寸"（见 `PlaybackEngine.grabFrameArgb`），
     *    此时按源尺寸用 [FrameScaler] 补一次缩放（与 `GifRecorder.scaleToPlan` 同一处理）；
     * 3. 引擎自己缩过了、但短边取整与请求值不同 → [resolveActualSize] 反推。
     *
     * 三条都对不上就返回 null：**宁可不给图，也不要拿错的宽高编出一张错位的图**。
     */
    private fun normalize(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Frame? {
        val opaque = ensureOpaque(pixels)

        if (opaque.size == targetWidth * targetHeight) {
            return Frame(opaque, targetWidth, targetHeight)
        }

        if (opaque.size == sourceWidth * sourceHeight) {
            return runCatching {
                Frame(
                    pixels = FrameScaler.scale(
                        source = opaque,
                        srcW = sourceWidth,
                        srcH = sourceHeight,
                        dstW = targetWidth,
                        dstH = targetHeight,
                    ),
                    width = targetWidth,
                    height = targetHeight,
                )
            }.getOrNull()
        }

        val (width, height) = resolveActualSize(opaque.size, targetWidth, targetHeight) ?: return null
        return Frame(opaque, width, height)
    }

    /**
     * 视频帧恒为不透明，这里统一把 alpha 置 FF。
     *
     * 为什么需要：部分取帧路径的 alpha 位可能是 0（未初始化的位图缓冲 / 像素缓冲的
     * alpha 通道），原样编码会得到一张**全透明**的 PNG —— 在多数查看器里显示为黑块或白块，
     * 而且"图能打开、尺寸也对"，属于最难定位的那类问题。
     * 在公共层统一处理，三端行为一致且可单测。
     *
     * 已经全不透明时**原样返回**，省掉一次 8 MB 级拷贝（取帧实现通常已经给了 FF）。
     */
    private fun ensureOpaque(pixels: IntArray): IntArray {
        if (pixels.all { (it ushr 24) == 0xFF }) return pixels
        return IntArray(pixels.size) { pixels[it] or OPAQUE_ALPHA }
    }

    /**
     * 由"像素总数"反推引擎实际产出的宽高。
     *
     * 依据是三端取帧共享的一条约定：**缩放只按长边做，且长边就等于请求的长边**
     * （见类 KDoc）。于是短边 = `像素数 / 长边`，只需在
     * [SHORT_EDGE_TOLERANCE_PX] 的窗口内试出乘积恰好等于像素数的那个值。
     *
     * ⚠️ 那条容差检查是**必需**的，不是保险丝：若引擎完全忽略目标尺寸、直接返回源尺寸
     * （1080p 源 → 2073600 像素，请求 360 长边），`2073600 / 360 = 5760` 也是整数，
     * 少了容差检查就会把 1080p 的图当成 `360x5760` 去编码。
     *
     * @return 反推失败返回 null（调用方据此报"抓帧尺寸不符"）
     */
    internal fun resolveActualSize(
        pixelCount: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Pair<Int, Int>? {
        if (pixelCount <= 0 || targetWidth <= 0 || targetHeight <= 0) return null
        if (pixelCount == targetWidth * targetHeight) return targetWidth to targetHeight

        val landscape = targetWidth >= targetHeight
        val longEdge = if (landscape) targetWidth else targetHeight
        val targetShort = if (landscape) targetHeight else targetWidth

        for (short in (targetShort - SHORT_EDGE_TOLERANCE_PX)..(targetShort + SHORT_EDGE_TOLERANCE_PX)) {
            if (short <= 0) continue
            if (longEdge.toLong() * short.toLong() != pixelCount.toLong()) continue
            return if (landscape) longEdge to short else short to longEdge
        }
        return null
    }

    /**
     * 目标尺寸：长边封顶、等比、**不放大**。
     *
     * 与 `GifCapturePolicy.scaleToLongEdge` 是同一套取整约定（短边都按 `toInt()` 截断），
     * 刻意**不共用**：两处上限是两个独立的产品决策（360 分享 vs 1920 高清），
     * 而且截图这边的真实尺寸最终由像素数反推、并不依赖本函数的取整结果 ——
     * 共用只会把两件无关的事绑在一起。
     */
    internal fun targetSize(sourceWidth: Int, sourceHeight: Int, maxLongEdgePx: Int): Pair<Int, Int> {
        val longEdge = maxOf(sourceWidth, sourceHeight)
        if (maxLongEdgePx <= 0 || longEdge <= maxLongEdgePx) return sourceWidth to sourceHeight
        val ratio = maxLongEdgePx.toDouble() / longEdge.toDouble()
        return (sourceWidth * ratio).toInt().coerceAtLeast(1) to
            (sourceHeight * ratio).toInt().coerceAtLeast(1)
    }
}
