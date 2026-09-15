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
 * 播放器底部控制栏（进度条 / 播放 / 时间 / 倍速与清晰度入口 / 全屏）。
 *
 * 从 `VideoPlayerUi` 主函数提取。底栏自己**不持有状态**：
 * 进度条的乐观值与 seek 节流留在调用方（VideoPlayerUi 主函数），
 * 这里只收 [sliderValue] 与两个滑动回调；开侧栏通过 onOpen*Panel 回调上抛。
 */
@Composable
internal fun BoxScope.PlayerBottomBar(
    visible: Boolean,
    sliderValue: Float,
    bufferedProgress: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    currentTime: String,
    totalTime: String,
    playbackSpeed: Float,
    resolvedQualityLabel: String,
    fullscreenEnabled: Boolean,
    onFullscreenClick: () -> Unit,
    onOpenSpeedPanel: () -> Unit,
    onOpenQualityPanel: () -> Unit,
) {
/**
 * 底部控制栏
 */
AnimatedVisibility(
    visible = visible,
    modifier = Modifier.align(Alignment.BottomCenter),
    enter = fadeIn(),
    exit = fadeOut(),
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = 18.dp,
                vertical = HanimeDefaults.Spacing.small,
            )
    ) {

        /**
         * Background
         */
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.largeIncreased)
        ) {

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .posterBlur()
                    .background(
                        HanimeDefaults.Overlay.barSurface
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        HanimeDefaults.Overlay.border,
                        MaterialTheme.shapes.largeIncreased
                    )
            )
        }

        /**
         * Content
         */
        Column(
            modifier = Modifier.padding(
                horizontal = HanimeDefaults.Spacing.large,
                vertical = 6.dp
            )
        ) {

            /**
             * Ultra Thin Slider
             */
            PlayerSlider(
                value = sliderValue,
                buffered = bufferedProgress,
                onValueChange = onSliderValueChange,
                onValueChangeFinished = onSliderValueChangeFinished,
                // 命中区 48dp（M3 硬指标）/ 视觉 12dp：向上仍报告 12dp，底栏总高不变
                modifier = Modifier
                    .playerHitTarget(visual = HanimeDefaults.Spacing.large)
                    .height(PLAYER_MIN_TOUCH_TARGET)
            )

            /**
             * Bottom Controls
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HanimeDefaults.PlayerSizes.bottomRow),
                verticalAlignment = Alignment.CenterVertically
            ) {

                /**
                 * Play
                 */
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        // 命中区 48dp / 视觉 26dp：向上仍报告 26dp，底栏排布零变化
                        .playerHitTarget(visual = HanimeDefaults.PlayerSizes.bottomBarButton)
                        .size(PLAYER_MIN_TOUCH_TARGET)
                ) {
                    Icon(
                        painter = if (isPlaying)
                            painterResource(Res.drawable.ic_pause)
                        else
                            painterResource(Res.drawable.ic_play_arrow),
                        contentDescription = null,
                        tint = HanimeDefaults.Overlay.onScrim,
                        modifier = Modifier.size(HanimeDefaults.PlayerSizes.iconSmall)
                    )
                }

                Spacer(modifier = Modifier.width(HanimeDefaults.Spacing.small))

                /**
                 * Time
                 */
                Text(
                    text = stringResource(Res.string.player_time_format,
                        currentTime,
                        totalTime
                    ),
                    color = HanimeDefaults.Overlay.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.weight(1f))

                PlayerMenuChip(
                    label = stringResource(Res.string.player_speed_format, playbackSpeed),
                    onClick = onOpenSpeedPanel,
                )

                Spacer(modifier = Modifier.width(6.dp))

                PlayerMenuChip(
                    label = resolvedQualityLabel,
                    onClick = onOpenQualityPanel,
                )

                Spacer(modifier = Modifier.width(HanimeDefaults.Spacing.extraSmall))

                /**
                 * Fullscreen —— 平台没实现全屏时不显示入口（iOS 目前未实现），
                 * 免得按钮按下去什么都不发生（"状态撒谎"）。
                 */
                if (fullscreenEnabled) {
                    IconButton(
                        onClick = onFullscreenClick,
                        modifier = Modifier
                            .playerHitTarget(visual = HanimeDefaults.PlayerSizes.bottomBarButton)
                            .size(PLAYER_MIN_TOUCH_TARGET)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_fullscreen),
                            contentDescription = null,
                            tint = HanimeDefaults.Overlay.onScrim,
                            modifier = Modifier.size(HanimeDefaults.PlayerSizes.iconSmall)
                        )
                    }
                }
            }
        }
    }
}
}
