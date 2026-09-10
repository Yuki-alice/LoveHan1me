package lovehan1me.ui.screen.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.h_keyframes_not_enabled
import lovehan1me.enable_google_cast
import lovehan1me.video_loading_failed
import lovehan1me.sure_to_delete
import lovehan1me.super_resolution_quality
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_off
import lovehan1me.retry
import lovehan1me.replay
import lovehan1me.prompt
import lovehan1me.position_ms
import lovehan1me.player_time_format
import lovehan1me.player_speed_format
import lovehan1me.player_progress_percent
import lovehan1me.player_play_from_beginning
import lovehan1me.player_keyframe_index
import lovehan1me.player_h_keyframe
import lovehan1me.player_gesture_volume
import lovehan1me.player_gesture_progress
import lovehan1me.player_gesture_brightness
import lovehan1me.player_casting_to
import lovehan1me.player_cast_device
import lovehan1me.player_auto_quality
import lovehan1me.player_anime4k_label
import lovehan1me.playback_finished
import lovehan1me.modify_h_keyframe
import lovehan1me.long_press_to_add_h_keyframe
import lovehan1me.here_is_empty
import lovehan1me.edit
import lovehan1me.delete
import lovehan1me.confirm
import lovehan1me.cancel
import lovehan1me.ic_volume_up
import lovehan1me.ic_unlock
import lovehan1me.ic_refresh
import lovehan1me.ic_play_arrow
import lovehan1me.ic_pause
import lovehan1me.ic_lock
import lovehan1me.ic_light_mode
import lovehan1me.ic_home
import lovehan1me.ic_fullscreen
import lovehan1me.ic_fast_rewind
import lovehan1me.ic_fast_forward
import lovehan1me.ic_edit
import lovehan1me.ic_delete
import lovehan1me.ic_arrow_back_ios
import lovehan1me.logic.entity.HKeyframeEntity
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.FilledTonalButton
import lovehan1me.ui.component.FilledTonalIconButton
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.player.CastRouteButton
import lovehan1me.ui.player.PlaybackEngine
import lovehan1me.ui.player.PlaybackQuality
import lovehan1me.ui.player.PlatformVideoSurface
import lovehan1me.ui.player.PlayerDefaults
import lovehan1me.ui.player.posterBlur
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.utils.SonnerToast
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.logic.currentEpochMillis
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerUi(
    modifier: Modifier = Modifier,
    playbackEngine: PlaybackEngine? = null,
    posterUrl: String? = null,
    title: String,
    currentTime: String,
    totalTime: String,
    progress: Float,
    bufferedProgress: Float,
    currentVolume: Float,
    currentBrightness: Float,
    showControls: Boolean = true,
    isFullscreen: Boolean = false,
    isPlaying: Boolean = false,
    isPlaybackEnded: Boolean = false,
    showCastButton: Boolean = false,
    isCasting: Boolean = false,
    castDeviceName: String? = null,
    isLocked: Boolean = false,
    showPoster: Boolean = false,
    showResumeButton: Boolean = false,
    showLoading: Boolean = false,
    showRetry: Boolean = false,
    onPlayClick: () -> Unit = {},
    onReplay: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onFullscreenClick: () -> Unit = {},
    onLockClick: () -> Unit = {},
    onProgressChange: (Float) -> Unit = {},
    onResumeClick: () -> Unit = onPlayClick,
    onRetry: () -> Unit = {},
    qualities: List<PlaybackQuality> = emptyList(),
    selectedQuality: String? = null,
    onQualitySelected: (Int) -> Unit = {},
    playbackSpeed: Float = PlayerDefaults.DEFAULT_SPEED,
    onPlaybackSpeedSelected: (Float) -> Unit = {},
    superResolutionLabel: String,
    superResolutionOptions: List<String> = emptyList(),
    selectedSuperResolutionIndex: Int = 0,
    onSuperResolutionSelected: (Int) -> Unit = {},
    hKeyframeLabel: String,
    isHKeyframesEnabled: Boolean = true,
    hKeyframeOptions: List<String> = emptyList(),
    hKeyframes: List<HKeyframeEntity.Keyframe> = emptyList(),
    isHKeyframeLocal: Boolean = false,
    onHKeyframeSelected: (Int) -> Unit = {},
    onHKeyframeUpdated: (HKeyframeEntity.Keyframe, HKeyframeEntity.Keyframe) -> Unit = { _, _ -> },
    onHKeyframeDeleted: (HKeyframeEntity.Keyframe) -> Unit = {},
    onHKeyframeLongPress: () -> Unit = {},
    onLongPressStart: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onBrightnessChange: (Float) -> Unit = {},
    onProgressGesture: (Float) -> Unit = onProgressChange,
    progressGestureSensitivity: Float = PlayerDefaults.DEFAULT_PROGRESS_SLIDE_SENSITIVITY.toFloat(),
    countdownLabel: String? = null,
    videoAspectRatio: Float = 16f / 9f,
) {
    var showControlsState by remember { mutableStateOf(true) }
    var gestureType by remember { mutableStateOf<GestureIndicatorType?>(null) }
    var gesturePercent by remember { mutableFloatStateOf(0.5f) }
    var dragStartedOnLeft by remember { mutableStateOf(true) }
    var progressDirection by remember { mutableStateOf<ProgressGestureDirection?>(null) }
    var isProgressGestureActive by remember { mutableStateOf(false) }
    var isLongPressSpeedActive by remember { mutableStateOf(false) }
    var suppressTapUntilMs by remember { mutableLongStateOf(0L) }
    var activeSidePanel by remember { mutableStateOf<PlayerSidePanel?>(null) }
    var displayedSidePanel by remember { mutableStateOf<PlayerSidePanel?>(null) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var unlockButtonTimeoutToken by remember { mutableIntStateOf(0) }
    val haptic = rememberHapticFeedback()
    // P6d-3-C2：AndroidView factory 非 @Composable 上下文，描述串外提
    val castButtonDesc = stringResource(Res.string.enable_google_cast)
    // P6d-3-C3：回调内固定串预解析
    val hKeyframesDisabledText = stringResource(Res.string.h_keyframes_not_enabled)
    // M3：原 DateFormat.getTimeFormat(context)（Android-only）；改当前时分，
    // 24 小时制零填充（与系统 12/24 小时制差异可接受）。
    var deviceTime by remember { mutableStateOf(formatDeviceTime(currentEpochMillis())) }

    LaunchedEffect(Unit) {
        while (true) {
            deviceTime = formatDeviceTime(currentEpochMillis())
            delay(60_000L.milliseconds)
        }
    }

    LaunchedEffect(showControlsState, isPlaying, activeSidePanel) {
        if (showControlsState && isPlaying && activeSidePanel == null) {
            delay(3000.milliseconds)
            showControlsState = false
        }
    }

    val effectiveShowControls = showControls && showControlsState && !isLocked
    val playerUiVisible = effectiveShowControls && activeSidePanel == null
    val speedSelectedIndex = PlayerDefaults.speeds.indexOfFirst { it == playbackSpeed }
        .takeIf { it >= 0 }
        ?: PlayerDefaults.speeds.indexOfFirst { it == PlayerDefaults.DEFAULT_SPEED }
    val resolvedQualityLabel =
        selectedQuality ?: qualities.lastOrNull()?.label
        ?: stringResource(Res.string.player_auto_quality)
    val qualitySelectedIndex = qualities.indexOfFirst { it.label == resolvedQualityLabel }
    val latestProgress by rememberUpdatedState(progress)
    val latestVolume by rememberUpdatedState(currentVolume)
    val latestBrightness by rememberUpdatedState(currentBrightness)
    val latestProgressSensitivity by rememberUpdatedState(progressGestureSensitivity)
    val latestOnProgressGesture by rememberUpdatedState(onProgressGesture)
    val latestOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val latestOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val latestOnLongPressEnd by rememberUpdatedState(onLongPressEnd)

    LaunchedEffect(isPlaying) {
        if (!isPlaying) isLongPressSpeedActive = false
    }

    LaunchedEffect(activeSidePanel) {
        if (activeSidePanel != null) {
            displayedSidePanel = activeSidePanel
            showControlsState = true
        }
    }

    LaunchedEffect(effectiveShowControls, isLocked) {
        if (!effectiveShowControls || isLocked) {
            activeSidePanel = null
        }
    }

    LaunchedEffect(isLocked, unlockButtonTimeoutToken) {
        if (isLocked) {
            showControlsState = false
            showUnlockButton = true
            delay(3000.milliseconds)
            showUnlockButton = false
        } else {
            showUnlockButton = false
            showControlsState = true
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (isLocked) {
                    detectTapGestures(onTap = { unlockButtonTimeoutToken++ })
                } else {
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    detectTapGestures(
                        onPress = {
                            coroutineScope {
                                var activated = false
                                val activationJob = launch {
                                    delay(longPressTimeout.milliseconds)
                                    if (latestIsPlaying) {
                                        activated = true
                                        isLongPressSpeedActive = true
                                        showControlsState = false
                                        suppressTapUntilMs = Long.MAX_VALUE
                                        haptic()
                                        latestOnLongPressStart()
                                    }
                                }
                                val released = tryAwaitRelease()
                                activationJob.cancel()
                                if (activated && released) {
                                    isLongPressSpeedActive = false
                                    showControlsState = false
                                    if (suppressTapUntilMs == Long.MAX_VALUE) {
                                        suppressTapUntilMs = nowMs() + 500L
                                    }
                                    latestOnLongPressEnd()
                                }
                            }
                        },
                        onTap = {
                            if (nowMs() <= suppressTapUntilMs) {
                                suppressTapUntilMs = 0L
                            } else {
                                showControlsState = !showControlsState
                            }
                        },
                        onDoubleTap = { onPlayClick() },
                    )
                }
            }
    ) {

        /**
         * 视频渲染层
         */
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val safeAspectRatio = if (videoAspectRatio > 0f) videoAspectRatio else 16f / 9f
            val containerAspectRatio = if (maxHeight.value > 0f) {
                maxWidth.value / maxHeight.value
            } else {
                safeAspectRatio
            }
            val videoModifier = if (safeAspectRatio >= containerAspectRatio) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(safeAspectRatio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(safeAspectRatio)
            }

            if (playbackEngine != null && !isCasting) {
                key(playbackEngine, safeAspectRatio) {
                    Box(
                        modifier = videoModifier
                            .background(Color.Black)
                    ) {
                        // P5-1：Surface 渲染走 shared 插槽（androidMain 内为原 SurfaceView 代码，
                        // 含 attach/detach 回调 + Mpv updateSurfaceSize；其余 1900 行零改动）
                        PlatformVideoSurface(
                            engine = playbackEngine,
                            modifier = Modifier.fillMaxSize(),
                            onSurfaceAvailable = { playbackEngine.attachSurface(it) },
                            onSurfaceDestroyed = { playbackEngine.detachSurface(it) },
                        )
                    }
                }
            } else {
                Box(
                    modifier = videoModifier.background(Color.Black)
                )
            }

            if (isCasting) {
                Surface(
                    modifier = Modifier.offset(y = (-48).dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                ) {
                    Text(
                        text = stringResource(Res.string.player_casting_to,
                            castDeviceName ?: stringResource(Res.string.player_cast_device),
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        var gestureStartProgress = 0f
                        var gestureStartVolume = 0f
                        var gestureStartBrightness = 0f
                        var longPressOwnsDrag = false
                        detectDragGestures(
                            onDragStart = { offset ->
                                longPressOwnsDrag = isLongPressSpeedActive
                                if (longPressOwnsDrag) {
                                    gestureType = null
                                    progressDirection = null
                                    isProgressGestureActive = false
                                    return@detectDragGestures
                                }
                                dragStartedOnLeft = offset.x < size.width / 2f
                                gestureType = null
                                progressDirection = null
                                isProgressGestureActive = false
                                gestureStartProgress = latestProgress
                                gestureStartVolume = latestVolume
                                gestureStartBrightness = latestBrightness
                            },
                            onDragEnd = {
                                if (longPressOwnsDrag) {
                                    isLongPressSpeedActive = false
                                    showControlsState = false
                                    if (suppressTapUntilMs == Long.MAX_VALUE) {
                                        suppressTapUntilMs = nowMs() + 500L
                                    }
                                    latestOnLongPressEnd()
                                    longPressOwnsDrag = false
                                }
                                gestureType = null
                                isProgressGestureActive = false
                            },
                            onDragCancel = {
                                if (longPressOwnsDrag) {
                                    isLongPressSpeedActive = false
                                    showControlsState = false
                                    latestOnLongPressEnd()
                                    longPressOwnsDrag = false
                                }
                                gestureType = null
                                isProgressGestureActive = false
                            },
                            onDrag = { _, dragAmount ->
                                if (longPressOwnsDrag || isLongPressSpeedActive) {
                                    return@detectDragGestures
                                }
                                val type =
                                    gestureType ?: if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                        GestureIndicatorType.Progress
                                    } else if (dragStartedOnLeft) {
                                        GestureIndicatorType.Brightness
                                    } else {
                                        GestureIndicatorType.Volume
                                    }
                                if (gestureType == null) {
                                    gesturePercent = when (type) {
                                        GestureIndicatorType.Progress -> gestureStartProgress
                                        GestureIndicatorType.Brightness -> gestureStartBrightness
                                        GestureIndicatorType.Volume -> gestureStartVolume
                                    }
                                    isProgressGestureActive = type == GestureIndicatorType.Progress
                                }
                                gestureType = type
                                val next = when (type) {
                                    GestureIndicatorType.Progress -> {
                                        progressDirection = if (dragAmount.x < 0f) {
                                            ProgressGestureDirection.Backward
                                        } else {
                                            ProgressGestureDirection.Forward
                                        }
                                        (gesturePercent + dragAmount.x /
                                                (size.width * latestProgressSensitivity.coerceAtLeast(
                                                    1f
                                                )))
                                            .coerceIn(0f, 1f)
                                    }

                                    else ->
                                        (gesturePercent - dragAmount.y /
                                                (size.height * 0.8f).coerceAtLeast(1f)).coerceIn(
                                            0f,
                                            1f
                                        )
                                }
                                gesturePercent = next
                                when (type) {
                                    GestureIndicatorType.Brightness -> latestOnBrightnessChange(next)
                                    GestureIndicatorType.Volume -> latestOnVolumeChange(next)
                                    GestureIndicatorType.Progress -> latestOnProgressGesture(next)
                                }
                            },
                        )
                    }
                },
        )

        gestureType?.let { type ->
            GestureIndicatorOverlay(
                visible = true,
                type = type,
                percent = gesturePercent,
                progressDirection = progressDirection,
                modifier = Modifier.fillMaxSize(),
            )
        }

        /**
         * 封面
         */
        if (showPoster && posterUrl != null) {
            HanimeAsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
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
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
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
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            )
        }

        /**
         * 顶部控制栏
         */
        AnimatedVisibility(
            visible = playerUiVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(
                        horizontal = 16.dp,
                        vertical = 4.dp,
                    )
            ) {

                /**
                 * Background
                 */
                if (isFullscreen) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(20.dp))
                    ) {

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                // M3：Android S+ RenderEffect 模糊改跨平台 posterBlur()，
                                // 其他平台恒等（见 ui.player.VideoPlatform）。
                                .posterBlur()
                                .background(
                                    Color.Black.copy(alpha = 0.18f)
                                )
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.06f),
                                    RoundedCornerShape(20.dp)
                                )
                        )
                    }
                }

                /**
                 * Content
                 */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    /**
                     * Back
                     */
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back_ios),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    /**
                     * Home
                     */
                    IconButton(
                        onClick = onHomeClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_home),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    /**
                     * Title
                     */
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    if (showCastButton) {
                        // M3：MediaRouteButton + Cast SDK 仅 Android（见 ui.player.VideoPlatform）。
                        CastRouteButton(contentDescription = castButtonDesc)
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (isFullscreen) {
                        PlayerMenuChip(
                            label = hKeyframeLabel,
                            onClick = {
                                if (isHKeyframesEnabled) {
                                    activeSidePanel = PlayerSidePanel.HKeyframe
                                } else {
                                    SonnerToast.info(hKeyframesDisabledText)
                                }
                            },
                            onLongClick = {
                                if (isHKeyframesEnabled) {
                                    onHKeyframeLongPress()
                                } else {
                                    SonnerToast.info(hKeyframesDisabledText)
                                }
                            },
                        )

                        if (superResolutionOptions.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))

                            PlayerMenuChip(
                                label = superResolutionLabel,
                                onClick = { activeSidePanel = PlayerSidePanel.SuperResolution },
                            )


                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = deviceTime,
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            PlayerBatteryIndicator()
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isLongPressSpeedActive,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.46f),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fast_forward),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                )
            }
        }

        /**
         * 中间播放按钮
         */
        AnimatedVisibility(
            visible =
                !isLocked &&
                        activeSidePanel == null &&
                        gestureType == null &&
                        !isPlaybackEnded &&
                        (!isPlaying || effectiveShowControls),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (showLoading && !isProgressGestureActive) {
                    ContainedLoadingIndicator()
                } else if (!isPlaying) {
                    FilledTonalIconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_play_arrow),
                            contentDescription = null,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                } else {
                    // Playing: small pause button when controls are visible
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_pause),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }
        }

        /**
         * 锁定按钮
         */
        AnimatedVisibility(
            visible = if (isLocked) showUnlockButton else playerUiVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            FilledIconButton(
                onClick = onLockClick,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(42.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f)
                )
            ) {
                Icon(
                    painter = if (isLocked)
                        painterResource(Res.drawable.ic_lock)
                    else
                        painterResource(Res.drawable.ic_unlock),
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        /**
         * 底部控制栏
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
                    .navigationBarsPadding()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 4.dp,
                    )
            ) {

                /**
                 * Background
                 */
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(18.dp))
                ) {

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .posterBlur()
                            .background(
                                Color.Black.copy(alpha = 0.18f)
                            )
                    )

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(18.dp)
                            )
                    )
                }

                /**
                 * Content
                 */
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 6.dp
                    )
                ) {

                    /**
                     * Ultra Thin Slider
                     */
                    PlayerSlider(
                        value = progress,
                        buffered = bufferedProgress,
                        onValueChange = onProgressChange,
                        modifier = Modifier.height(12.dp)
                    )

                    /**
                     * Bottom Controls
                     */
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        /**
                         * Play
                         */
                        IconButton(
                            onClick = onPlayClick,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                painter = if (isPlaying)
                                    painterResource(Res.drawable.ic_pause)
                                else
                                    painterResource(Res.drawable.ic_play_arrow),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        /**
                         * Time
                         */
                        Text(
                            text = stringResource(Res.string.player_time_format,
                                currentTime,
                                totalTime
                            ),
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelSmall
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        PlayerMenuChip(
                            label = stringResource(Res.string.player_speed_format, playbackSpeed),
                            onClick = { activeSidePanel = PlayerSidePanel.Speed },
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        PlayerMenuChip(
                            label = resolvedQualityLabel,
                            onClick = { activeSidePanel = PlayerSidePanel.Quality },
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        /**
                         * Fullscreen
                         */
                        IconButton(
                            onClick = onFullscreenClick,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fullscreen),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        /**
         * Resume 按钮
         */
        AnimatedVisibility(
            visible = showResumeButton && activeSidePanel == null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ElevatedButton(
                onClick = onResumeClick,
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(Res.string.player_play_from_beginning))
            }
        }

        AnimatedVisibility(
            visible = isPlaybackEnded && activeSidePanel == null && !isLocked,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = HanimeDefaults.Colors.pageSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.playback_finished),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(onClick = onReplay) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_refresh),
                            contentDescription = null,
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(stringResource(Res.string.replay))
                    }
                }
            }
        }

        /**
         * Retry
         */
        AnimatedVisibility(
            visible = showRetry && activeSidePanel == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = HanimeDefaults.Colors.pageSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(28.dp)
            ) {

                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = stringResource(Res.string.video_loading_failed),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = onRetry
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_refresh),
                            contentDescription = null
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(stringResource(Res.string.retry))
                    }
                }
            }
        }

        /**
         * Timer
         */
        AnimatedVisibility(
            visible = countdownLabel != null && activeSidePanel == null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    top = 90.dp,
                    start = 12.dp
                ),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)
            ) {
                Text(
                    text = countdownLabel.orEmpty(),
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                    color = Color.White,
                    fontSize = 22.sp
                )
            }
        }

        AnimatedVisibility(
            visible = activeSidePanel != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            activeSidePanel = null
                        }
                )

            }
        }

        AnimatedVisibility(
            visible = activeSidePanel != null,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (displayedSidePanel) {
                    PlayerSidePanel.HKeyframe -> {
                        PlayerSidePanelSheet(
                            options = hKeyframeOptions,
                            selectedIndex = null,
                            panelWidth = 216.dp,
                            hKeyframes = hKeyframes,
                            isHKeyframeLocal = isHKeyframeLocal,
                            emptyText = stringResource(Res.string.here_is_empty) + "\n" +
                                    stringResource(Res.string.long_press_to_add_h_keyframe),
                            onSelected = { index ->
                                activeSidePanel = null
                                onHKeyframeSelected(index)
                            },
                            onHKeyframeUpdated = onHKeyframeUpdated,
                            onHKeyframeDeleted = onHKeyframeDeleted,
                        )
                    }

                    PlayerSidePanel.Speed -> {
                        PlayerSidePanelSheet(
                            options = PlayerDefaults.speeds.map {
                                stringResource(Res.string.player_speed_format, it)
                            },
                            selectedIndex = speedSelectedIndex,
                            panelWidth = 156.dp,
                            onSelected = { index ->
                                activeSidePanel = null
                                onPlaybackSpeedSelected(PlayerDefaults.speeds[index])
                            },
                        )
                    }

                    PlayerSidePanel.SuperResolution -> {
                        PlayerSidePanelSheet(
                            options = superResolutionOptions,
                            selectedIndex = selectedSuperResolutionIndex,
                            panelWidth = 156.dp,
                            onSelected = { index ->
                                activeSidePanel = null
                                onSuperResolutionSelected(index)
                            },
                        )
                    }

                    PlayerSidePanel.Quality -> {
                        PlayerSidePanelSheet(
                            options = qualities.map(PlaybackQuality::label),
                            selectedIndex = qualitySelectedIndex.takeIf { it >= 0 },
                            panelWidth = 156.dp,
                            onSelected = { index ->
                                activeSidePanel = null
                                onQualitySelected(index)
                            },
                        )
                    }

                    null -> Unit
                }
            }
        }
    }
}

// M3：createGoogleCastIndicator / whiteDrawable 已随 Cast 按钮搬
// androidMain（ui.player.VideoPlatform.android.kt）。

@Composable
private fun PlayerMenuChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.06f),
                shape,
            )
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private enum class PlayerSidePanel {
    HKeyframe,
    Speed,
    SuperResolution,
    Quality,
}

@Composable
private fun BoxScope.PlayerSidePanelSheet(
    options: List<String>,
    selectedIndex: Int?,
    onSelected: (Int) -> Unit,
    emptyText: String? = null,
    panelWidth: Dp = 156.dp,
    hKeyframes: List<HKeyframeEntity.Keyframe> = emptyList(),
    isHKeyframeLocal: Boolean = false,
    onHKeyframeUpdated: (HKeyframeEntity.Keyframe, HKeyframeEntity.Keyframe) -> Unit = { _, _ -> },
    onHKeyframeDeleted: (HKeyframeEntity.Keyframe) -> Unit = {},
) {
    val isHKeyframePanel = hKeyframes.isNotEmpty() || isHKeyframeLocal || emptyText != null
    var editingKeyframe by remember { mutableStateOf<HKeyframeEntity.Keyframe?>(null) }
    var deletingKeyframe by remember { mutableStateOf<HKeyframeEntity.Keyframe?>(null) }
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(panelWidth)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                // M3：见 posterBlur()（Android S+ RenderEffect，其他平台恒等）。
                .posterBlur()
                .background(Color.Black.copy(alpha = 0.72f))
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            if (isHKeyframePanel && hKeyframes.isNotEmpty()) {
                itemsIndexed(hKeyframes) { index, keyframe ->
                    val label = options.getOrNull(index).orEmpty()
                    val separatorIndex = label.indexOf(' ')
                    val marker = if (separatorIndex >= 0) {
                        label.substring(0, separatorIndex)
                    } else {
                        stringResource(Res.string.player_keyframe_index, index + 1)
                    }
                    val time =
                        if (separatorIndex >= 0) label.substring(separatorIndex + 1) else label
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(index) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = marker,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = time,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (isHKeyframeLocal) {
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { editingKeyframe = keyframe },
                                    modifier = Modifier.size(28.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = Color.White.copy(alpha = 0.76f),
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_edit),
                                        contentDescription = stringResource(Res.string.edit),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { deletingKeyframe = keyframe },
                                    modifier = Modifier.size(28.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = Color.White.copy(alpha = 0.76f),
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_delete),
                                        contentDescription = stringResource(Res.string.delete),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        keyframe.prompt?.takeIf(String::isNotBlank)?.let { prompt ->
                            Text(
                                text = prompt,
                                color = Color.White.copy(alpha = 0.68f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else if (options.isEmpty()) {
                item {
                    Text(
                        text = emptyText.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 24.dp),
                    )
                }
            } else {
                itemsIndexed(options) { index, option ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(index) }
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .padding(vertical = 11.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                Color.White
                            },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    editingKeyframe?.let { keyframe ->
        var positionText by remember(keyframe) { mutableStateOf(keyframe.position.toString()) }
        var promptText by remember(keyframe) { mutableStateOf(keyframe.prompt.orEmpty()) }
        var isPositionError by remember(keyframe) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingKeyframe = null },
            title = { Text(stringResource(Res.string.modify_h_keyframe)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = positionText,
                        onValueChange = {
                            positionText = it
                            isPositionError = false
                        },
                        label = { Text(stringResource(Res.string.position_ms)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isPositionError,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text(stringResource(Res.string.prompt)) },
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val position = positionText.toLongOrNull()
                        if (position == null || position < 0L) {
                            isPositionError = true
                        } else {
                            onHKeyframeUpdated(
                                keyframe,
                                keyframe.copy(
                                    position = position,
                                    prompt = promptText.ifBlank { null },
                                ),
                            )
                            editingKeyframe = null
                        }
                    }
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingKeyframe = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    deletingKeyframe?.let { keyframe ->
        AlertDialog(
            onDismissRequest = { deletingKeyframe = null },
            title = { Text(stringResource(Res.string.sure_to_delete)) },
            text = { Text(keyframe.position.toString()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onHKeyframeDeleted(keyframe)
                        deletingKeyframe = null
                    }
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingKeyframe = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
fun PlayerSlider(
    value: Float,
    buffered: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,

        /**
         * Thumb
         */
        thumb = {
            Box(
                modifier = Modifier
                    .size(14.dp),
                contentAlignment = Alignment.Center
            ) {

                /**
                 * Glow
                 */
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .background(
                            Color.White.copy(alpha = 0.22f),
                            CircleShape
                        )
                )

                /**
                 * Real Thumb
                 */
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            Color.White,
                            CircleShape
                        )
                )
            }
        },

        /**
         * Track
         */
        track = {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart
            ) {

                /**
                 * Background Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(100))
                        .background(
                            Color.White.copy(alpha = 0.14f)
                        )
                )

                /**
                 * Buffered Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth(buffered.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(100))
                        .background(
                            Color.White.copy(alpha = 0.32f)
                        )
                )

                /**
                 * Active Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth(value.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(100))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                                )
                            )
                        )
                )
            }
        }
    )
}

enum class GestureIndicatorType {
    Brightness,
    Volume,
    Progress,
}

private enum class ProgressGestureDirection {
    Backward,
    Forward,
}

@Composable
private fun GestureIndicatorOverlay(
    visible: Boolean,
    type: GestureIndicatorType,
    percent: Float,
    modifier: Modifier = Modifier,
    progressDirection: ProgressGestureDirection? = null,
    text: String? = null,
) {
    val displayText = text ?: stringResource(Res.string.player_progress_percent,
        (percent * 100).toInt(),
    )

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            /**
             * Glass Container
             */
            Box(
                modifier = Modifier
                    .size(
                        width = 170.dp,
                        height = 190.dp
                    )
                    .clip(RoundedCornerShape(36.dp))
            ) {

                /**
                 * Blur Layer
                 */
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .posterBlur(55f)
                        .background(
                            Color.Black.copy(alpha = 0.32f)
                        )
                )

                /**
                 * Glass Gradient
                 */
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        )
                )

                /**
                 * Border
                 */
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(36.dp)
                        )
                )

                /**
                 * Content
                 */
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Icon(
                        painter = when (type) {
                            GestureIndicatorType.Brightness -> painterResource(Res.drawable.ic_light_mode)
                            GestureIndicatorType.Volume -> painterResource(Res.drawable.ic_volume_up)
                            GestureIndicatorType.Progress -> when (progressDirection) {
                                ProgressGestureDirection.Backward -> painterResource(Res.drawable.ic_fast_rewind)
                                else -> painterResource(Res.drawable.ic_fast_forward)
                            }
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when (type) {
                            GestureIndicatorType.Brightness -> stringResource(Res.string.player_gesture_brightness)
                            GestureIndicatorType.Volume -> stringResource(Res.string.player_gesture_volume)
                            GestureIndicatorType.Progress -> stringResource(Res.string.player_gesture_progress)
                        },
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { percent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(100)),
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = displayText,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}

// M3：设备时钟（原 DateFormat 系统格式）与单调时钟的跨平台实现。
private fun nowMs(): Long = currentEpochMillis()

private fun formatDeviceTime(epochMillis: Long): String {
    val dateTime = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}
