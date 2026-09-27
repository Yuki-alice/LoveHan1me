/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import lovehan1me.video.player.ui.gesture.GestureIndicatorState.State.BRIGHTNESS
import lovehan1me.video.player.ui.gesture.GestureIndicatorState.State.FAST_BACKWARD
import lovehan1me.video.player.ui.gesture.GestureIndicatorState.State.FAST_FORWARD
import lovehan1me.video.player.ui.gesture.GestureIndicatorState.State.PAUSED_ONCE
import lovehan1me.video.player.ui.gesture.GestureIndicatorState.State.PLAYBACK_SPEED
import lovehan1me.video.player.ui.gesture.GestureIndicatorState.State.RESUMED_ONCE
import lovehan1me.video.player.ui.gesture.GestureIndicatorState.State.SEEKING
import lovehan1me.video.player.ui.gesture.GestureIndicatorState.State.VOLUME

@Composable
fun rememberGestureIndicatorState(): GestureIndicatorState = remember { GestureIndicatorState() }

/**
 * 画面中央那枚瞬时指示器（暂停/恢复、音量、亮度、seek、倍速、快进）的状态。
 *
 * 每次展示发一张 [counter] 票，收起时只在票号仍是最新的那张时才真收起：
 * 否则"长按快进结束"会把随后"音量拖动"的指示器一起关掉。
 */
@Stable
class GestureIndicatorState {
    internal enum class State {
        PAUSED_ONCE,
        RESUMED_ONCE,
        VOLUME,
        BRIGHTNESS,
        SEEKING,
        FAST_FORWARD,
        FAST_BACKWARD,
        PLAYBACK_SPEED,
    }

    internal var visible: Boolean by mutableStateOf(false)
    internal var state: State? by mutableStateOf(null)
    internal var progressValue: Float by mutableFloatStateOf(0f)
    internal var deltaSeconds: Int by mutableIntStateOf(0)
    internal var seekCancelled: Boolean by mutableStateOf(false)
    internal var playbackSpeed: Float by mutableFloatStateOf(1f)
    private var counter: Int = 0

    private inline fun startShow(
        state: State,
        setup: () -> Unit = {},
    ): Int {
        val ticket = ++counter
        setup()
        this.state = state
        visible = true
        return ticket
    }

    private inline fun show(
        state: State,
        setup: () -> Unit = {},
        action: () -> Unit,
    ) {
        val ticket = ++counter
        try {
            setup()
            this.state = state
            visible = true
            action()
        } finally {
            if (
                this.counter == ticket && // 我们之后没人改过状态
                this.state == state
            ) {
                visible = false
            }
        }
    }

    private companion object {
        private const val LONG: Long = 700
        private const val SHORT: Long = 500
    }

    suspend fun showPausedLong() {
        show(PAUSED_ONCE) {
            delay(LONG)
        }
    }

    suspend fun showResumedLong() {
        show(RESUMED_ONCE) {
            delay(LONG)
        }
    }

    suspend fun showVolumeRange(currentRatio: Float) {
        show(VOLUME, setup = { progressValue = currentRatio }) {
            delay(SHORT)
        }
    }

    suspend fun showBrightnessRange(currentRatio: Float) {
        show(BRIGHTNESS, setup = { progressValue = currentRatio }) {
            delay(SHORT)
        }
    }

    suspend fun showPlaybackSpeed(speed: Float) {
        show(PLAYBACK_SPEED, setup = { playbackSpeed = speed }) {
            delay(SHORT)
        }
    }

    suspend fun showSeeking(
        deltaSeconds: Int,
    ) {
        show(
            SEEKING,
            setup = {
                this.deltaSeconds = deltaSeconds
                seekCancelled = false
            },
        ) {
            delay(SHORT)
        }
    }

    fun startSeekCancellation(): Int {
        return startShow(SEEKING) {
            seekCancelled = true
        }
    }

    fun stopSeekCancellation(ticket: Int) {
        stopShow(ticket)
    }

    /**
     * @param speed 长按期间使用的播放速度，会显示在指示器上。
     */
    fun startFastForward(speed: Float): Int {
        startShow(FAST_FORWARD, setup = { playbackSpeed = speed })
        return counter
    }

    fun stopFastForward(ticket: Int) {
        stopShow(ticket)
    }

    fun startFastBackward(): Int {
        startShow(FAST_BACKWARD, setup = {})
        return counter
    }

    fun stopFastBackward(ticket: Int) {
        stopShow(ticket)
    }

    private fun stopShow(ticket: Int) {
        if (ticket == this.counter) {
            visible = false
        }
    }
}
