/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lovehan1me.video.player.ui.support.asGesturePointerType
import org.openani.mediamp.features.PlaybackSpeed

@Composable
fun rememberPlayerFastSkipState(
    playerState: PlaybackSpeed,
    gestureIndicatorState: GestureIndicatorState,
    fastForwardSpeed: Float = 3f,
): FastSkipState {
    return remember(playerState, fastForwardSpeed) {
        PlayerFastSkipState(playerState, gestureIndicatorState, fastForwardSpeed).fastSkipState
    }
}

class PlayerFastSkipState(
    private val playbackSpeed: PlaybackSpeed,
    private val gestureIndicatorState: GestureIndicatorState,
    private val fastForwardSpeed: Float = 3f,
) {
    private var originalSpeed = 0f
    private var gestureIndicatorTicket = 0
    val fastSkipState: FastSkipState = FastSkipState(
        onStart = { skipDirection ->
            originalSpeed = playbackSpeed.value
            playbackSpeed.set(
                when (skipDirection) {
                    SkipDirection.FORWARD -> fastForwardSpeed
                    SkipDirection.BACKWARD -> error("Backward skipping is not supported")
                },
            )
            gestureIndicatorTicket = gestureIndicatorState.startFastForward(fastForwardSpeed)
        },
        onStop = {
            playbackSpeed.set(originalSpeed)
            gestureIndicatorState.stopFastForward(gestureIndicatorTicket)
        },
    )
}

@Stable
class FastSkipState(
    private val onStart: (skipDirection: SkipDirection) -> Unit,
    private val onStop: () -> Unit,
) {
    private var skippingDirection: SkipDirection? by mutableStateOf(null)
    private var ticket: Int = 0

    fun startSkipping(direction: SkipDirection): Int {
        skippingDirection = direction
        onStart(direction)
        return ++ticket
    }

    fun stopSkipping(ticket: Int) {
        if (ticket == this.ticket) {
            skippingDirection = null
            onStop()
        }
    }
}

enum class SkipDirection {
    FORWARD,
    BACKWARD,
}

/**
 * @param requiredPointerType 不为 null 时只响应同一手势约定的指针；Stylus/Eraser 视为 Touch。
 * 用于让长按快进只属于触摸，不影响鼠标长按。
 */
fun Modifier.longPressFastSkip(
    state: FastSkipState,
    direction: SkipDirection,
    requiredPointerType: PointerType? = null,
): Modifier {
    var ticket = 0
    return detectLongPressGesture(
        onStart = {
            ticket = state.startSkipping(direction)
        },
        onEnd = {
            state.stopSkipping(ticket)
        },
        requiredPointerType = requiredPointerType,
    )
}

fun Modifier.detectLongPressGesture(
    onStart: () -> Unit,
    onEnd: () -> Unit,
    longPressTimeout: Long = 500L,
    requiredPointerType: PointerType? = null,
): Modifier = pointerInput(requiredPointerType) {
    coroutineScope {
        val touchSlop = viewConfiguration.touchSlop
        var isLongPressDetected = false

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // 这里刻意不消费 down 事件
            if (
                requiredPointerType != null &&
                down.type.asGesturePointerType() != requiredPointerType.asGesturePointerType()
            ) {
                return@awaitEachGesture
            }
            val initialPosition = down.position

            // 若用户停在同一位置不动，则起一个任务把长按标记为已检测。
            val longPressJob = launch {
                delay(longPressTimeout)
                onStart()
                isLongPressDetected = true
            }

            var change = awaitPointerEvent()
            while (change.changes.any { it.pressed }) { // 指针仍按住
                val pointer = change.changes[0]
                if (isLongPressDetected) {
                    // 消费所有事件，免得连带触发滑动等其它手势
                    change.changes.forEach { it.consume() }
                }
                if ((pointer.position - initialPosition).getDistance() > touchSlop) {
                    // 用户在滑动。长按已检测之后也可能走到这里。
                    longPressJob.cancel()
                }
                change = awaitPointerEvent()
            }
            // 已经松手
            if (isLongPressDetected) {
                // 消费抬起事件
                change.changes.forEach { it.consume() }
            }

            longPressJob.cancel()
            if (isLongPressDetected) {
                onEnd()
                isLongPressDetected = false
            }
        }
    }
}
