/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.gesture

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import lovehan1me.video.player.ui.support.ComposeKey
import lovehan1me.video.player.ui.support.MonoTasker
import lovehan1me.video.player.ui.support.onKey
import lovehan1me.video.player.ui.support.rememberUiMonoTasker

@Stable
class KeyboardHorizontalDirectionState(
    val onBackward: () -> Unit,
    val onForward: () -> Unit,
)

fun Modifier.onKeyboardHorizontalDirection(
    state: KeyboardHorizontalDirectionState,
): Modifier = onKeyboardHorizontalDirection(
    onBackward = state.onBackward,
    onForward = state.onForward,
)

fun Modifier.onKeyboardHorizontalDirection(
    onBackward: () -> Unit,
    onForward: () -> Unit,
): Modifier = composed(
    inspectorInfo = {
        name = "keyboardSeek"
    },
) {
    val layoutDirection = LocalLayoutDirection.current
    val backwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionLeft
    } else {
        ComposeKey.DirectionRight
    }
    val forwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionRight
    } else {
        ComposeKey.DirectionLeft
    }

    val onBackwardState by rememberUpdatedState(onBackward)
    val onForwardState by rememberUpdatedState(onForward)
    onKey(backwardKey) {
        onBackwardState()
    }.onKey(forwardKey) {
        onForwardState()
    }
}

/**
 * 左右方向键：左键抬起即回退一档；右键短按前进一档、按住超过 200ms 转为长按快进。
 */
fun Modifier.keyboardSeekAndFastForward(
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    fastSkipState: FastSkipState?,
): Modifier = composed(
    inspectorInfo = {
        name = "keyboardSeekAndFastForward"
    },
) {
    val layoutDirection = LocalLayoutDirection.current
    val backwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionLeft
    } else {
        ComposeKey.DirectionRight
    }
    val forwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionRight
    } else {
        ComposeKey.DirectionLeft
    }

    val onBackwardState by rememberUpdatedState(onSeekBackward)
    val onForwardState by rememberUpdatedState(onSeekForward)

    val tasker: MonoTasker = rememberUiMonoTasker()
    var ticket by remember { mutableStateOf<Int?>(null) }

    onPreviewKeyEvent { event ->
        if (event.key == backwardKey) {
            if (event.type == KeyEventType.KeyDown) {
                return@onPreviewKeyEvent true
            } else if (event.type == KeyEventType.KeyUp) {
                onBackwardState()
                return@onPreviewKeyEvent true
            }
        }

        if (event.key == forwardKey) {
            if (event.type == KeyEventType.KeyDown) {
                if (!tasker.isRunning.value) {
                    tasker.launch {
                        try {
                            delay(200)
                            fastSkipState?.let {
                                ticket = it.startSkipping(SkipDirection.FORWARD)
                            }
                            awaitCancellation()
                        } finally {
                            ticket?.let {
                                fastSkipState?.stopSkipping(it)
                            }
                            ticket = null
                        }
                    }
                }
                return@onPreviewKeyEvent true
            } else if (event.type == KeyEventType.KeyUp) {
                val isSkipping = ticket != null
                tasker.cancel()
                if (!isSkipping) {
                    onForwardState()
                }
                return@onPreviewKeyEvent true
            }
        }
        false
    }
}
