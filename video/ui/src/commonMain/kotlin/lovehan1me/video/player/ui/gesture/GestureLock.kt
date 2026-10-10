/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.gesture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import lovehan1me.video.player.ui.ControllerVisibility
import lovehan1me.video.player.ui.PlaybackSpeedControllerState
import lovehan1me.video.player.ui.PlayerControllerState
import lovehan1me.video.player.ui.PlayerFullscreenState
import lovehan1me.video.player.ui.progress.PlayerProgressSliderState
import lovehan1me.video.player.ui.support.LocalActiveInputSource
import lovehan1me.video.player.ui.support.LocalPlatform
import lovehan1me.video.player.ui.support.slightlyWeaken
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.time.Duration.Companion.seconds

const val TAG_GESTURE_LOCK = "GestureLock"

@Composable
fun GestureLock(
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.testTag(TAG_GESTURE_LOCK),
        // 16dp = M3 large。
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.background.copy(0.05f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.slightlyWeaken()),
    ) {
        IconButton(onClick) {
            val color = if (isLocked) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White
            }
            CompositionLocalProvider(LocalContentColor provides color) {
                if (isLocked) {
                    Icon(Icons.Outlined.Lock, contentDescription = "UnLock screen")
                } else {
                    Icon(Icons.Outlined.LockOpen, contentDescription = "Lock screen")
                }
            }
        }
    }
}

/**
 * Handles click events and auto-hide controller.
 *
 * @see LockableVideoGestureHost
 */
@Composable
fun LockedScreenGestureHost(
    controllerVisibility: () -> ControllerVisibility,
    setFullVisible: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = { setFullVisible(true) },
            ).fillMaxSize(),
    )

    if (controllerVisibility() == ControllerVisibility.Visible) {
        LaunchedEffect(true) {
            delay(2.seconds)
            setFullVisible(false)
        }
    }
    return
}

@Composable
fun LockableVideoGestureHost(
    controllerState: PlayerControllerState,
    seekerState: SwipeSeekerState,
    progressSliderState: PlayerProgressSliderState,
    playerState: MediampPlayer,
    locked: Boolean,
    enableSwipeToSeek: Boolean,
    audioController: LevelController,
    brightnessController: LevelController,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    fullscreenState: PlayerFullscreenState,
    modifier: Modifier = Modifier,
    onTogglePauseResume: () -> Unit = {},
    onToggleDanmaku: () -> Unit = {},
    onTogglePlayerStats: () -> Unit = {},
    family: GestureFamily = gestureFamilyOf(
        LocalActiveInputSource.current.current,
        LocalPlatform.current.mouseFamily,
    ),
    gestureIndicatorState: GestureIndicatorState = rememberGestureIndicatorState(),
    fastForwardSpeed: Float = 3f,
    fastSkipState: FastSkipState? = playerState.features[PlaybackSpeed]?.let {
        rememberPlayerFastSkipState(
            playerState = it,
            gestureIndicatorState,
            fastForwardSpeed = fastForwardSpeed,
        )
    },
) {
    if (locked) {
        LockedScreenGestureHost(
            { controllerState.visibility },
            controllerState.setFullVisible,
            modifier.testTag("LockedScreenGestureHost"),
        )
    } else {
        PlayerGestureHost(
            controllerState,
            seekerState,
            progressSliderState,
            gestureIndicatorState,
            fastSkipState,
            playerState,
            enableSwipeToSeek,
            audioController,
            brightnessController,
            playbackSpeedControllerState,
            fullscreenState,
            modifier,
            onTogglePauseResume = onTogglePauseResume,
            onToggleDanmaku = onToggleDanmaku,
            onTogglePlayerStats = onTogglePlayerStats,
            family = family,
        )
    }
}
