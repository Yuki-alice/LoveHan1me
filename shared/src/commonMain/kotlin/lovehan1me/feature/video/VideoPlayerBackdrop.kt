package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.SonnerToast
import lovehan1me.delete
import lovehan1me.edit
import lovehan1me.feature.player.PlatformVideoSurface
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.feature.player.posterBlur
import lovehan1me.gif_capture
import lovehan1me.here_is_empty
import lovehan1me.ic_arrow_back_ios
import lovehan1me.ic_fast_forward
import lovehan1me.ic_fast_rewind
import lovehan1me.ic_fullscreen
import lovehan1me.ic_home
import lovehan1me.ic_light_mode
import lovehan1me.ic_lock
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.ic_refresh
import lovehan1me.ic_unlock
import lovehan1me.ic_volume_up
import lovehan1me.playback_finished
import lovehan1me.player_anime4k_label
import lovehan1me.player_auto_quality
import lovehan1me.player_gesture_brightness
import lovehan1me.player_gesture_progress
import lovehan1me.player_gesture_volume
import lovehan1me.player_play_from_beginning
import lovehan1me.player_progress_percent
import lovehan1me.player_speed_format
import lovehan1me.player_time_format
import lovehan1me.replay
import lovehan1me.retry
import lovehan1me.screenshot
import lovehan1me.super_resolution_off
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_quality
import lovehan1me.sure_to_delete
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.FilledTonalButton
import lovehan1me.ui.component.FilledTonalIconButton
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.transition.sharedCoverElement
import lovehan1me.video_loading_failed
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器的"背景层"：封面 poster、顶部/底部渐变 scrim、缓冲指示。
 *
 * 从 `VideoPlayerUi` 主函数提取；两块都只读主函数状态（零改写），
 * 底部渐变的 `align(BottomCenter)` 需要 BoxScope 接收者。
 */

/** 封面（含共享元素过渡）+ 控件显隐联动的上下渐变。 */
@Composable
internal fun BoxScope.PlayerBackdropLayers(
    showPoster: Boolean,
    posterUrl: String?,
    sharedElementKey: String?,
    playerUiVisible: Boolean,
) {
/**
 * 封面
 */
if (showPoster && posterUrl != null) {
    HanimeAsyncImage(
        model = posterUrl,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .sharedCoverElement(sharedElementKey),
        contentScale = ContentScale.Crop,
    )
}

/**
 * 顶部渐变
 */
AnimatedVisibility(
    visible = playerUiVisible,
    enter = fadeIn(),
    exit = fadeOut(),
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HanimeDefaults.PlayerSizes.scrimTop)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        HanimeDefaults.Overlay.scrimTopEnd,
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * 底部渐变
 */
AnimatedVisibility(
    visible = playerUiVisible,
    modifier = Modifier.align(Alignment.BottomCenter),
    enter = fadeIn(),
    exit = fadeOut(),
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HanimeDefaults.PlayerSizes.scrimBottom)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        HanimeDefaults.Overlay.scrimBottomEnd
                    )
                )
            )
    )
}
}

/** 缓冲/卡顿指示（独立于控件显隐，见块内注释）。 */
@Composable
internal fun BoxScope.PlayerBufferingOverlay(
    isLocked: Boolean,
    activeSidePanel: PlayerSidePanel?,
    gestureType: GestureIndicatorType?,
    isPlaybackEnded: Boolean,
    showLoading: Boolean,
    isProgressGestureActive: Boolean,
) {
/**
 * 缓冲/卡顿指示（**独立于控件显隐**）
 *
 * 此前它与下面的播放按钮共用一个 AnimatedVisibility，条件里有
 * `(!isPlaying || effectiveShowControls)` —— 于是"播放中控件自动隐藏"时
 * 转圈也被一起藏掉，卡顿表现为"画面定住但界面看着一切正常"。
 * 缓冲反馈不该受控件显隐影响，故拆成独立浮层。
 */
AnimatedVisibility(
    visible =
        !isLocked &&
                activeSidePanel == null &&
                gestureType == null &&
                !isPlaybackEnded &&
                showLoading &&
                !isProgressGestureActive,
    enter = fadeIn(),
    exit = fadeOut(),
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator()
    }
}
}
