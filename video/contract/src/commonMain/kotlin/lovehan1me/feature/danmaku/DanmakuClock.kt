package lovehan1me.feature.danmaku

/**
 * 弹幕时钟：帧时刻减去"暂停/卡顿累计掉的时长"，得到弹幕自己经历过多久的毫秒数。
 *
 * 弹幕的位置是 [DanmakuSlot.placedAtMs] 到"此刻"的闭式函数，而"此刻"必须与
 * 播放位置解耦：跟着视频时间走的话，倍速会让字飞得成倍快，而且播放位置由 250ms
 * 一跳的采样外推而来，那个修正台阶会被速度放大成整屏抖动。
 *
 * ## 只吃帧时刻，不吃回调密度
 * [nowMs] 是 `(frameNanos, frozen)` 的纯函数：没有"上次值 + delta"的累加，
 * 所以帧循环掉帧、空屏退到 20Hz 轮询、首次调用距离程序启动多久，都不影响读数。
 * 冻结时长同样按"进入冻结的那一帧"与"离开冻结的那一帧"记账，两次之间调用多少次都一样。
 *
 * ## 冻结
 * 暂停 / 缓冲 / 卡顿 / 切画质期间时钟停摆，弹幕原地悬住；解冻从原处继续，
 * 不会一次性跳出冻结的时长。
 *
 * 单线程使用（帧循环与快照都在主线程），故不加锁。
 */
class DanmakuClock {

    private var frozenNanos = 0L
    private var frozenStartNanos = 0L
    private var recordingFrozen = false

    /**
     * @param frameNanos 帧时刻（绘制层给的是平滑后的 `withFrameNanos` 值），
     *   必须每帧同源于一个单调时钟
     * @param frozen true = 此刻播放没在推进
     */
    fun nowMs(frameNanos: Long, frozen: Boolean): Long {
        if (frozen) {
            if (!recordingFrozen) {
                recordingFrozen = true
                frozenStartNanos = frameNanos
            }
            return (frozenStartNanos - frozenNanos) / NANOS_PER_MILLI
        }
        if (recordingFrozen) {
            recordingFrozen = false
            frozenNanos += frameNanos - frozenStartNanos
        }
        return (frameNanos - frozenNanos) / NANOS_PER_MILLI
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
