package lovehan1me.feature.danmaku

/**
 * 帧时刻平滑。
 *
 * ## 它解决什么
 * 弹幕位置是「帧时刻 × 速度」的闭式外推，所以 `withFrameNanos` 给的帧时刻怎么跳，
 * 画面上就怎么跳。真机上帧时刻**不是抖动的，是量化的**：vsync 本身严格周期，
 * 出问题的只有掉帧 —— 本该 16.7ms 的一帧变成 33.3ms。这一帧里所有滚动弹幕会
 * 一次性往前窜一整帧的距离，看着就是"顿一下"。
 *
 * 这里把那一跳摊到随后的几帧里：帧间隔做低通，再用相位误差把输出拉回真实时刻。
 * 代价是输出会短暂领先真实时刻，**领先量有硬上界**（见 [maxDeviationNanos]）：
 * 弹幕比视频早一帧以内秀出来是看不出来的，无限跑飞不是。
 *
 * ## 与 [DanmakuPositionTracker] 的分工（两者串联，互不知道对方存在）
 * - 追踪器管**采样 → 帧**：引擎位置稀疏（250ms / 500ms / 事件驱动）且带台阶，靠闭式外推补齐。
 * - 本类管**帧 → 帧**：帧时刻自身带量化跳变，外推再准也会一比一传到屏上。
 *
 * ## 不追大跳
 * 窗口最小化、长卡顿之后帧间隔会突然变成几百毫秒甚至几秒。那是**真的过去了那么久**
 * （视频也在走），必须当帧立刻跟上；滑动收敛会让弹幕整整慢半拍。判据是"超过估计间隔
 * [resyncFactor] 倍"，跟上之后重新用下一帧的间隔当估计 —— 不能拿那个大跳当间隔用，
 * 否则输出会按 500ms 每帧往前跑。
 */
internal class FrameTimeSmoother(
    /** 帧间隔低通增益。越小越平滑，代价是真实帧率变化（60→120Hz）时收敛更慢。 */
    private val intervalGain: Float = DEFAULT_INTERVAL_GAIN,
    /** 相位拉回增益。它决定输出贴回真实时刻的快慢；取小值才能把那一下跳变摊开。 */
    private val phaseGain: Float = DEFAULT_PHASE_GAIN,
    private val resyncFactor: Float = DEFAULT_RESYNC_FACTOR,
    /** 输出与真实帧时刻的最大允许偏差。也是"一跳最多能摊掉多少"的上限。 */
    private val maxDeviationNanos: Long = DEFAULT_MAX_DEVIATION_NANOS,
) {

    private var smoothedNanos = 0L
    private var lastOutputNanos = 0L
    private var lastRawNanos = 0L
    private var intervalNanos = 0f
    private var sawFirst = false
    private var sawSecond = false

    /** 把一帧的原始时刻平滑一次。**必须逐帧调用**（跳帧调用会让间隔估计失真）。 */
    fun smooth(rawNanos: Long): Long {
        if (!sawFirst) {
            // 首帧没有间隔可用：不猜，原样透传（此时引入滞后就是给自己挖坑）
            sawFirst = true
            lastRawNanos = rawNanos
            return commit(rawNanos)
        }

        val rawDelta = rawNanos - lastRawNanos
        lastRawNanos = rawNanos

        if (!sawSecond) {
            // 第二帧才拿到第一个间隔：直接采纳，同样不引入启动期滞后
            sawSecond = true
            intervalNanos = rawDelta.toFloat().coerceAtLeast(0f)
            return commit(rawNanos)
        }

        if (rawDelta <= 0L || rawDelta > intervalNanos * resyncFactor) {
            // 时钟倒流或大跳：当帧跟上，并重新锁相（下一帧的间隔重新当估计）
            sawSecond = false
            intervalNanos = 0f
            return commit(rawNanos)
        }

        intervalNanos += intervalGain * (rawDelta - intervalNanos)
        val advanced = smoothedNanos + intervalNanos.toLong()
        var next = advanced + ((rawNanos - advanced) * phaseGain).toLong()
        if (next < rawNanos - maxDeviationNanos) next = rawNanos - maxDeviationNanos
        if (next > rawNanos + maxDeviationNanos) next = rawNanos + maxDeviationNanos
        // 平滑归平滑，时钟不许倒流：消费者把帧时刻当单调时钟用
        if (next < lastOutputNanos) next = lastOutputNanos
        return commit(next)
    }

    /** 定下这一帧的输出。所有分支都得过这里，单调性与"最后一帧"的记账才只有一个写者。 */
    private fun commit(nanos: Long): Long {
        smoothedNanos = nanos
        lastOutputNanos = nanos
        return nanos
    }

    companion object {
        private const val DEFAULT_INTERVAL_GAIN = 0.05f
        private const val DEFAULT_PHASE_GAIN = 0.05f

        /** 超过估计间隔这么多倍算大跳（真掉帧/窗口最小化），当帧跟上而不滑动。 */
        private const val DEFAULT_RESYNC_FACTOR = 4f

        /** 一帧（60Hz）。跳变最多摊掉一帧的量，再多就是让弹幕跟视频脱钩了。 */
        private const val DEFAULT_MAX_DEVIATION_NANOS = 16_666_666L
    }
}