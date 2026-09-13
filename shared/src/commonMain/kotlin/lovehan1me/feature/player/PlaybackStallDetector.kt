package lovehan1me.feature.player

import lovehan1me.core.platform.currentEpochMillis

/**
 * 播放卡顿看门狗（M5-3「卡顿可见」）。
 *
 * ## 为什么需要它
 * `PlaybackEngineState.isBuffering` 只有**一个布尔**，而三端语义并不一致：
 * - Android-Exo 是真实信号（`isLoading` / `STATE_BUFFERING`）；
 * - Android-mpv 是 `position == 0` 启发式，且它配了 `cache-pause=no`，
 *   中途饿缓存时**不会**置位；
 * - MediaPlayer / iOS 中途 rebuffer 同样不置位（iOS 的 phase 甚至仍是 Ready）。
 *
 * 结果是"画面定住但界面看着一切正常"——这正是最容易被用户当成"播放器卡死"的状态。
 *
 * ## 判据（引擎无关）
 * **播放中位置长时间不前进**即判卡顿：[thresholdMs] 内 `positionMs` 没有变化。
 * 暂停、引擎已明确报缓冲、以及刚 seek 完（位置跳变）都不会误判。
 *
 * 纯计算 + 注入时钟，故可在单测里完整覆盖（见 `PlaybackStallDetectorTest`）。
 */
class PlaybackStallDetector(
    private val thresholdMs: Long = DEFAULT_THRESHOLD_MS,
    private val clockMillis: () -> Long = { currentEpochMillis() },
) {

    private var lastPositionMs = Long.MIN_VALUE
    private var lastAdvancedAtMs = 0L

    /**
     * 喂入一次采样。
     *
     * @return 是否判定为"卡顿"（位置停滞已达 [thresholdMs]）
     */
    fun update(positionMs: Long, isPlaying: Boolean, isBuffering: Boolean): Boolean {
        val now = clockMillis()
        // 没在播、或引擎已明确在缓冲：交由既有的 isBuffering 表达，不叠加"停滞"语义
        if (!isPlaying || isBuffering) {
            lastPositionMs = positionMs
            lastAdvancedAtMs = now
            return false
        }
        if (positionMs != lastPositionMs) {
            // 位置前进（含 seek 跳变）：重建基线
            lastPositionMs = positionMs
            lastAdvancedAtMs = now
            return false
        }
        if (lastAdvancedAtMs == 0L) {
            // 首个采样只建立基线，避免"刚开播就报卡顿"
            lastAdvancedAtMs = now
            return false
        }
        return now - lastAdvancedAtMs >= thresholdMs
    }

    companion object {
        /**
         * 停滞多久算卡顿。
         *
         * 2.5 秒是"正常网络的抖动不会误报、真卡住又能立刻看见"的折中：
         * 位置采样本身在 Android 是 250ms、iOS 500ms，阈值远大于采样间隔。
         */
        const val DEFAULT_THRESHOLD_MS = 2_500L
    }
}
