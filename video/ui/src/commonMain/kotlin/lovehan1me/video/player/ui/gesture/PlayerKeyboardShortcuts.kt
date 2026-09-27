/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.gesture

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import lovehan1me.video.player.ui.nextPlaybackSpeed
import lovehan1me.video.player.ui.support.ComposeKey
import lovehan1me.video.player.ui.support.onKey

private val PLAYBACK_SPEED_SHORTCUTS = listOf(
    ComposeKey.One to 1f,
    ComposeKey.NumPad1 to 1f,
    ComposeKey.Two to 2f,
    ComposeKey.NumPad2 to 2f,
    ComposeKey.Three to 3f,
    ComposeKey.NumPad3 to 3f,
)

/**
 * 在同一个焦点目标上安装播放器的键盘命令。
 *
 * 焦点策略由调用方持有：只有被修饰的节点真正持有焦点时命令才生效，因此弹幕输入框或别的
 * 控件可以临时接管键盘而不触发播放命令。
 */
internal fun Modifier.playerKeyboardShortcuts(
    seekerState: SwipeSeekerState,
    fastSkipState: FastSkipState?,
    currentPlaybackSpeed: Float?,
    playbackSpeedRange: ClosedFloatingPointRange<Float>,
    onPlaybackSpeedChanged: (Float) -> Unit,
    volumeEnabled: Boolean,
    onVolumeUp: (fineAdjustment: Boolean) -> Unit,
    onVolumeDown: (fineAdjustment: Boolean) -> Unit,
    onTogglePauseResume: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onTogglePlayerStats: () -> Unit,
): Modifier {
    var result = keyboardSeekAndFastForward(
        onSeekBackward = { seekerState.onSeek(-5) },
        onSeekForward = { seekerState.onSeek(5) },
        fastSkipState = fastSkipState,
    )
    if (volumeEnabled) {
        result = result.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) return@onKeyEvent false
            when (event.key) {
                ComposeKey.DirectionUp -> {
                    onVolumeUp(event.isShiftPressed)
                    true
                }

                ComposeKey.DirectionDown -> {
                    onVolumeDown(event.isShiftPressed)
                    true
                }

                else -> false
            }
        }
    }
    result = result
        .onKey(ComposeKey.Spacebar, onTogglePauseResume)
        .onKey(ComposeKey.F, onToggleFullscreen)
    if (currentPlaybackSpeed != null) {
        result = result
            .onKey(ComposeKey.A) {
                onPlaybackSpeedChanged(nextPlaybackSpeed(currentPlaybackSpeed, playbackSpeedRange, -1))
            }
            .onKey(ComposeKey.D) {
                onPlaybackSpeedChanged(nextPlaybackSpeed(currentPlaybackSpeed, playbackSpeedRange, 1))
            }
            .onKey(ComposeKey.S) {
                onPlaybackSpeedChanged(1f.coerceIn(playbackSpeedRange))
            }
        for ((key, speed) in PLAYBACK_SPEED_SHORTCUTS) {
            result = result.onKey(key) {
                onPlaybackSpeedChanged(speed.coerceIn(playbackSpeedRange))
            }
        }
    }
    return result
        .onKey(ComposeKey.B, onToggleDanmaku)
        .onKey(ComposeKey.I, onTogglePlayerStats)
        // 同一个节点带着 combinedClickable，聚焦时它会把 Enter 当成点击。
        // Enter 不是播放器快捷键，所以吞掉它；DPad 中心键留给 clickable，
        // 让遥控器/方向键确认仍然表现得像点一下。
        .onPreviewKeyEvent { event ->
            event.key == ComposeKey.Enter || event.key == ComposeKey.NumPadEnter
        }
}
