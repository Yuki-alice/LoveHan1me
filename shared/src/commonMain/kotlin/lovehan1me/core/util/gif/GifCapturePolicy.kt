package lovehan1me.core.util.gif

/**
 * GIF 录制上限与计划计算（M3-b）。
 *
 * 路线图明确要求「**时长/帧率/分辨率三档封顶，超了直接拒绝并提示（防 OOM/卡死）**」。
 * 这里把"策略"做成**纯函数 + 纯数据**，于是 OOM 这条最容易出人命的路径
 * 可以被单测完整覆盖，而不必真机上反复试。
 *
 * ## 两道闸的分工
 * 1. **夹紧（clamp）**：时长、帧率、分辨率这三档是"用户请求"，
 *    超出上限就夹到上限 —— 因为 UI 本来就只提供合规档位，
 *    走到这里的越界属于程序调用，夹紧比抛异常更稳。
 * 2. **拒绝（reject）**：夹紧之后仍要按**内存预算**复核一次。
 *    这一闸是真正的 OOM 兜底：只要预算不够，无论参数多"合规"都直接拒绝，
 *    把"能不能录"与"录出来多大"解耦，避免以后改上限时算漏。
 *
 * 调用方拿到 [GifCapturePlan] 后应立刻把 [GifCapturePlan.estimatedFrameBytes]
 * 与预算比对结果用于 UI 提示（例如"该片段约需 18 MB 内存"）。
 */
object GifCapturePolicy {

    /** 单帧像素占用的字节数：IntArray 里每个像素一个 Int（ARGB）。 */
    const val BYTES_PER_PIXEL = 4

    /**
     * 三档上限 + 内存硬闸。
     *
     * 默认值选取理由：
     * - `maxDurationMs = 5000`：GIF 体积随帧数线性增长，5s 是"够表达一个片段"与"分享得动"的平衡点；
     * - `maxFps = 12`：GIF 逐帧全量写入，12fps 观感已足够顺，再高只增体积；
     * - `maxLongEdgePx = 360`：分享场景的实际展示尺寸，360p 长边在手机/聊天窗口里不糊；
     * - `maxFrameBufferBytes = 48 MiB`：360 长边 × 16:9 ≈ 360×202，单帧 291 KB，
     *   60 帧（5s@12fps）≈ 17 MB —— 留出约 3 倍余量给不同宽高比的片段与 JVM 堆开销。
     */
    data class Limits(
        val maxDurationMs: Int = 5_000,
        val maxFps: Int = 12,
        val maxLongEdgePx: Int = 360,
        val maxFrameBufferBytes: Long = 48L * 1024 * 1024,
    )

    /**
     * 一次录制的执行计划。尺寸已按上限缩放、帧数已按上限夹紧，
     * 调用方只需照着抓帧即可。
     */
    data class Plan(
        val outputWidth: Int,
        val outputHeight: Int,
        val frameCount: Int,
        /** 每帧间隔，单位 10ms（GIF 规范单位）。 */
        val delayCentis: Int,
        /** 全部帧位图的估算内存占用（字节）。 */
        val estimatedFrameBytes: Long,
    ) {
        /** 这段 GIF 的实际时长（毫秒），= 帧数 × 每帧间隔。 */
        val effectiveDurationMs: Long get() = frameCount.toLong() * delayCentis * 10L
    }

    /** 计划失败的原因，供 UI 直接映射成文案。 */
    enum class RejectReason {
        /** 源尺寸非法（<=0），通常是播放器还没拿到视频尺寸。 */
        InvalidSourceSize,

        /** 请求的时长或帧率非正。 */
        InvalidRequest,

        /** 夹紧后仍超出内存预算 —— 真正的 OOM 闸。 */
        FrameBufferTooLarge,
    }

    sealed interface Result {
        data class Ok(val plan: Plan) : Result
        data class Rejected(val reason: RejectReason) : Result
    }

    /**
     * 计算录制计划。
     *
     * @param sourceWidth 视频源宽
     * @param sourceHeight 视频源高
     * @param requestedDurationMs 想要的时长（会被 [Limits.maxDurationMs] 夹紧）
     * @param requestedFps 想要的帧率（会被 [Limits.maxFps] 夹紧）
     */
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedDurationMs: Int,
        requestedFps: Int,
        limits: Limits = Limits(),
    ): Result {
        if (sourceWidth <= 0 || sourceHeight <= 0) return Result.Rejected(RejectReason.InvalidSourceSize)
        if (requestedDurationMs <= 0 || requestedFps <= 0) return Result.Rejected(RejectReason.InvalidRequest)

        val durationMs = requestedDurationMs.coerceAtMost(limits.maxDurationMs)
        val fps = requestedFps.coerceAtMost(limits.maxFps)

        val (outW, outH) = scaleToLongEdge(sourceWidth, sourceHeight, limits.maxLongEdgePx)

        val frameCount = ((durationMs.toLong() * fps) / 1000L).toInt().coerceAtLeast(1)
        val delayCentis = delayCentisFor(fps)

        val estimated = frameCount.toLong() * outW.toLong() * outH.toLong() * BYTES_PER_PIXEL
        if (estimated > limits.maxFrameBufferBytes) {
            return Result.Rejected(RejectReason.FrameBufferTooLarge)
        }

        return Result.Ok(
            Plan(
                outputWidth = outW,
                outputHeight = outH,
                frameCount = frameCount,
                delayCentis = delayCentis,
                estimatedFrameBytes = estimated,
            )
        )
    }

    /**
     * 帧率 → GIF 的每帧延时间隔（单位 10ms）。
     *
     * ⚠️ **必须夹到下限 2**：GIF 规范里 0/1 厘秒的延时会被绝大多数浏览器
     * 当作"未指定"而按 **10 厘秒（100ms）** 渲染 —— 也就是本该 50fps 的片段
     * 会变成 10fps 的幻灯片。这是 GIF 最经典的坑之一，宁可比请求的慢一点。
     */
    fun delayCentisFor(fps: Int): Int {
        if (fps <= 0) return 100
        val raw = (100.0 / fps).toInt()
        return raw.coerceAtLeast(2)
    }

    /**
     * 等比缩放到长边不超过 [maxLongEdge]，短边按比例取整且至少为 1。
     * 源本来就小于上限时**不放大**（放大只会变大不变清晰）。
     */
    fun scaleToLongEdge(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
        val longEdge = maxOf(width, height)
        if (maxLongEdge <= 0 || longEdge <= maxLongEdge) return width to height
        val ratio = maxLongEdge.toDouble() / longEdge.toDouble()
        val w = (width * ratio).toInt().coerceAtLeast(1)
        val h = (height * ratio).toInt().coerceAtLeast(1)
        return w to h
    }
}
