package lovehan1me.feature.danmaku

import kotlin.math.abs

/**
 * 稀疏位置采样 → 连续播放时刻。
 *
 * ## 为什么需要它
 * 播放位置由引擎按 **Android 250ms / iOS 500ms / 桌面事件驱动** 推过来。拿这个值直接
 * 驱动动画会看到明显的台阶（250ms 一跳，且推送本身有抖动）；而弹幕每一帧都要有位置。
 * 本类把两件事彻底拆开：
 *  - 采样只负责**重锚定**（记下位置 + 当时那一帧的时间）；
 *  - 帧回调负责**闭式外推** `anchor + elapsed × speed`。
 * 外推是闭式解而非数值积分，所以没有累积误差；采样抖动最多让"重锚定"晚半拍，
 * 永远渗不到屏幕上。
 *
 * ## 时钟域（别照抄别人的做法）
 * [positionAt] 的纳秒由**调用方**给出 —— 渲染循环里就是 `withFrameNanos` 的 frameTimeNanos。
 * 锚定同样推迟到"第一次看到这条采样的那一帧"，于是锚点与查询恒在同一时钟域：
 * 既不必为各端 `nanoTime` 的基准差异写 expect/actual，也不会在没有帧循环时
 * 用一个来自协程线程的时间戳污染锚点。
 *
 * ## seek 与回绕
 * 250ms 的采样太粗，靠"delta 方向"判断 seek 会漏（回退一步正好落在两个采样之间）。
 * 这里用**外推与实际到达位置的偏差**判定：偏差超过 [seekThresholdMs]（且超过
 * 3 倍采样间隔）即视为 seek —— 涵盖手动拖动、循环播放回绕、切画质后的位置重置。
 * 判定结果只通过 [seekGeneration] 单调递增暴露出去，消费者发现它变了就重建可见弹幕集。
 *
 * ## 冻结
 * 暂停 / 卡顿 / 切画质时 [setFrozen]，期间时钟**滚动停摆**（不推进也不清空弹幕）：
 * 每帧把锚点挪到当前时刻，于是解冻后从原处继续，不会一次性跳出冻结的时长。
 * 桌面端暂停时引擎**根本不再推位置**，所以这一路不是可选项。
 *
 * 单线程使用（帧循环与流采集都在主线程），故不加锁。
 */
class DanmakuPositionTracker(
    private val seekThresholdMs: Long = SEEK_THRESHOLD_MS,
    /** 距上次锚点最远外推多久。超过就 hold —— 防止"没帧循环/没采样"时跑飞。 */
    private val maxLookaheadMs: Long = MAX_LOOKAHEAD_MS,
) {

    private var anchored = false
    private var anchorPositionMs = 0L
    private var anchorNanos = 0L
    private var anchorSpeed = 1f
    private var durationMs = 0L

    private var hasPending = false
    private var pendingPositionMs = 0L
    private var pendingSpeed = 1f
    private var pendingDurationMs = 0L

    private var frozen = false

    /** 每次 seek / 回绕 / [reset] 递增。消费者据此决定要不要重建可见集。 */
    var seekGeneration = 0L
        private set

    /**
     * 喂入一次引擎位置采样。来自任意协程，**不碰时钟**。
     *
     * [playbackSpeed] 取引擎回显值，不要本地猜（Android 长按变速时回显有延迟）。
     */
    fun onSample(positionMs: Long, playbackSpeed: Float, durationMs: Long = 0L) {
        pendingPositionMs = positionMs
        // 0 速与 NaN 都会被乘进外推式，宁可退回常速
        pendingSpeed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) playbackSpeed else 1f
        pendingDurationMs = durationMs
        hasPending = true
    }

    /** true = 暂停/卡顿/切画质，时钟停摆但不清空。 */
    fun setFrozen(frozen: Boolean) {
        this.frozen = frozen
    }

    /** 换片子 / 播放 phase 离开 Ready：位置彻底失去参考意义。 */
    fun reset() {
        anchored = false
        hasPending = false
        frozen = false
        durationMs = 0L
        seekGeneration++
    }

    /**
     * 帧时刻的播放位置。[nowNanos] 必须与上一次调用同源于一个单调时钟。
     *
     * @return null 表示还没有任何可用锚点（尚未收到采样），调用方应当什么都不画。
     */
    fun positionAt(nowNanos: Long): Long? {
        if (!anchored) {
            if (!hasPending) return null
            anchorToPending(nowNanos)
            return clamp(anchorPositionMs)
        }

        if (hasPending) {
            val intervalMs = ((nowNanos - anchorNanos).coerceAtLeast(0L) / NANOS_PER_MILLI).coerceAtMost(maxLookaheadMs)
            val extrapolated = advanceTo(nowNanos)
            // 抖动阈值随采样间隔放宽（桌面事件驱动可能几十秒才来一条），但不放宽到能盖住真 seek
            if (abs(extrapolated - pendingPositionMs) > maxOf(seekThresholdMs, 3L * intervalMs)) {
                seekGeneration++
            }
            anchorToPending(nowNanos)
        }

        if (frozen) {
            // 滚动锚点：冻结期间时间流逝不计入，解冻后才继续走
            anchorNanos = nowNanos
            return clamp(anchorPositionMs)
        }
        return clamp(advanceTo(nowNanos))
    }

    private fun anchorToPending(nowNanos: Long) {
        anchorPositionMs = pendingPositionMs
        anchorSpeed = pendingSpeed
        durationMs = pendingDurationMs
        anchorNanos = nowNanos
        anchored = true
        hasPending = false
    }

    private fun advanceTo(nowNanos: Long): Long {
        val elapsedMs = ((nowNanos - anchorNanos).coerceAtLeast(0L) / NANOS_PER_MILLI)
            .coerceAtMost(maxLookaheadMs)
        return anchorPositionMs + (elapsedMs * anchorSpeed).toLong()
    }

    private fun clamp(positionMs: Long): Long {
        val end = durationMs
        return if (end > 0L) positionMs.coerceIn(0L, end) else positionMs.coerceAtLeast(0L)
    }

    companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /**
         * 超过这个偏差算 seek。
         *
         * 1.5 秒 = "正常播放下最粗的采样间隔(500ms) × 3"，再小就会被网络抖动误判，
         * 再大则用户拖一下进度条要等好几秒弹幕才对得上。
         */
        const val SEEK_THRESHOLD_MS = 1_500L

        /** 外推最远看多久：两个最粗采样间隔，再多说明上游已经不出位置了。 */
        const val MAX_LOOKAHEAD_MS = 1_000L
    }
}
