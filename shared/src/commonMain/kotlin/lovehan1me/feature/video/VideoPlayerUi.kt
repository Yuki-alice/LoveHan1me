package lovehan1me.feature.video

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.video_loading_failed
import lovehan1me.gif_capture
import lovehan1me.screenshot
import lovehan1me.sure_to_delete
import lovehan1me.super_resolution_quality
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_off
import lovehan1me.retry
import lovehan1me.replay
import lovehan1me.player_time_format
import lovehan1me.player_speed_format
import lovehan1me.player_progress_percent
import lovehan1me.player_play_from_beginning
import lovehan1me.player_gesture_volume
import lovehan1me.player_gesture_progress
import lovehan1me.player_gesture_brightness
import lovehan1me.player_auto_quality
import lovehan1me.player_anime4k_label
import lovehan1me.playback_finished
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
import lovehan1me.ic_arrow_back_ios
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.FilledTonalButton
import lovehan1me.ui.component.FilledTonalIconButton
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.IconButton
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlatformVideoSurface
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.feature.player.posterBlur
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.core.util.SonnerToast
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.core.platform.currentEpochMillis
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
    isLocked: Boolean = false,
    showPoster: Boolean = false,
    showResumeButton: Boolean = false,
    showLoading: Boolean = false,
    showRetry: Boolean = false,
    /** 播放失败的真实原因（引擎/网络给的消息），显示在重试卡上；空则不显示。 */
    errorMessage: String? = null,
    onPlayClick: () -> Unit = {},
    onReplay: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onFullscreenClick: () -> Unit = {},
    onLockClick: () -> Unit = {},
    onProgressChange: (Float) -> Unit = {},
    /** M5-3：相对跳转（双击左右快退/快进）。传毫秒增量。 */
    onSeekBy: (Long) -> Unit = {},
    /** 视频总时长（毫秒）。双击跳转的 HUD 要用它把 ±秒 换算成百分比。 */
    durationMs: Long = 0L,
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
    /**
     * M3-b/M3-c：是否显示「截图 / 录 GIF」入口。由调用方传 `controller.supportsFrameCapture`
     * —— 用**能力**判断而不是内核名（与超分同理）。
     *
     * 两个入口共用这一个开关，因为它本来就是**同一个能力**（能否抓到渲染帧）；
     * 名字由 `gifCaptureEnabled` 改为 `frameCaptureEnabled` 正是为了不撒谎。
     */
    frameCaptureEnabled: Boolean = false,
    /** M3-b：点「录 GIF」的回调。默认 null → 不显示入口（未接线时行为零变化）。 */
    onOpenGifCapture: (() -> Unit)? = null,
    /** M3-c：点「截图」的回调。默认 null → 不显示入口。 */
    onCaptureScreenshot: (() -> Unit)? = null,
    onLongPressStart: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    /**
     * 左半屏竖滑是否真的能调亮度。桌面/iOS 的平台宿主没有实现亮度 API，
     * 此前手势照样弹 HUD 但什么都不发生 —— 那是"假动作"。为 false 时直接不接管该手势。
     */
    brightnessGestureEnabled: Boolean = true,
    onBrightnessChange: (Float) -> Unit = {},
    onProgressGesture: (Float) -> Unit = onProgressChange,
    progressGestureSensitivity: Float = PlayerDefaults.DEFAULT_PROGRESS_SLIDE_SENSITIVITY.toFloat(),
    videoAspectRatio: Float = 16f / 9f,
) {
    var showControlsState by remember { mutableStateOf(true) }

    // M5-3：拖动进度条期间的**本地乐观值**。拖动中显示手指位置，而不是引擎回写的旧位置 ——
    // 引擎 seek 后立刻 publishState，而它读到的仍是旧位置，直接显示会"拖了又弹回去"。
    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
    var lastSliderSeekAtMs by remember { mutableLongStateOf(0L) }
    // M5-3：双击左右快进/快退的瞬时反馈（方向 + 目标百分比），复用同一个手势浮层。
    var doubleTapSeekFeedback by remember {
        mutableStateOf<Pair<ProgressGestureDirection, Float>?>(null)
    }
    LaunchedEffect(doubleTapSeekFeedback) {
        if (doubleTapSeekFeedback != null) {
            delay(700)
            doubleTapSeekFeedback = null
        }
    }
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
    val latestBrightnessGestureEnabled by rememberUpdatedState(brightnessGestureEnabled)
    val latestOnSeekBy by rememberUpdatedState(onSeekBy)
    val latestDurationMs by rememberUpdatedState(durationMs)
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
                        // M5-3：双击三分区 —— 左 1/3 快退、右 1/3 快进、中间播放/暂停。
                        // 两家成熟播放器都是这个语义（本项目此前双击只能切播放/暂停，
                        // 而快进快退是视频播放器最常用的手势）。
                        onDoubleTap = { offset ->
                            val stepMs = DOUBLE_TAP_SEEK_STEP_MS
                            val third = size.width / 3f
                            val direction = when {
                                offset.x < third -> ProgressGestureDirection.Backward
                                offset.x > third * 2f -> ProgressGestureDirection.Forward
                                else -> null
                            }
                            if (direction == null) {
                                onPlayClick()
                            } else {
                                val forward = direction == ProgressGestureDirection.Forward
                                latestOnSeekBy(if (forward) stepMs else -stepMs)
                                val duration = latestDurationMs
                                if (duration > 0L) {
                                    val stepPercent = stepMs.toFloat() / duration
                                    val target = if (forward) {
                                        (progress + stepPercent).coerceIn(0f, 1f)
                                    } else {
                                        (progress - stepPercent).coerceIn(0f, 1f)
                                    }
                                    doubleTapSeekFeedback = direction to target
                                }
                            }
                        },
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

            if (playbackEngine != null) {
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
                                // 平台没有亮度能力时**不接管**左半屏竖滑：
                                // 否则 HUD 会照样弹出百分比而实际什么都不发生（假动作）。
                                if (type == GestureIndicatorType.Brightness && !latestBrightnessGestureEnabled) {
                                    return@detectDragGestures
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

        // 双击快进/快退有独立的瞬时反馈（700ms 后自动消失，见上面 LaunchedEffect）；
        // 它与拖动手势复用同一个浮层组件，只是数据来源不同。
        val seekFeedback = doubleTapSeekFeedback
        if (seekFeedback != null) {
            GestureIndicatorOverlay(
                visible = true,
                type = GestureIndicatorType.Progress,
                percent = seekFeedback.second,
                progressDirection = seekFeedback.first,
                modifier = Modifier.fillMaxSize(),
            )
        } else gestureType?.let { type ->
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
                            .clip(MaterialTheme.shapes.largeIncreased)
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
                                    MaterialTheme.shapes.largeIncreased
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

                    if (isFullscreen) {
                        if (superResolutionOptions.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))

                            PlayerMenuChip(
                                label = superResolutionLabel,
                                onClick = { activeSidePanel = PlayerSidePanel.SuperResolution },
                            )


                        }

                        if (frameCaptureEnabled && onCaptureScreenshot != null) {
                            Spacer(modifier = Modifier.width(6.dp))

                            PlayerMenuChip(
                                label = stringResource(Res.string.screenshot),
                                onClick = onCaptureScreenshot,
                            )
                        }

                        if (frameCaptureEnabled && onOpenGifCapture != null) {
                            Spacer(modifier = Modifier.width(6.dp))

                            PlayerMenuChip(
                                label = stringResource(Res.string.gif_capture),
                                onClick = onOpenGifCapture,
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
                shape = MaterialTheme.shapes.medium,
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

        /**
         * 中间播放/暂停按钮
         */
        AnimatedVisibility(
            visible =
                !isLocked &&
                        activeSidePanel == null &&
                        gestureType == null &&
                        !isPlaybackEnded &&
                        !showLoading &&
                        (!isPlaying || effectiveShowControls),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!isPlaying) {
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
                        .clip(MaterialTheme.shapes.largeIncreased)
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
                                MaterialTheme.shapes.largeIncreased
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
                        // 拖动中显示手指位置（乐观值），松手回到引擎位置
                        value = sliderDragValue ?: progress,
                        buffered = bufferedProgress,
                        onValueChange = { value ->
                            sliderDragValue = value
                            // 节流：拖动时**每一帧**都 seek 会让引擎反复重定位
                            // （Exo 每次 seek 都要重新缓冲、mpv 每次 exact seek 都 flush），
                            // 既卡又容易与引擎回写的旧位置打架。
                            val now = nowMs()
                            if (now - lastSliderSeekAtMs >= SLIDER_SEEK_THROTTLE_MS) {
                                lastSliderSeekAtMs = now
                                onProgressChange(value)
                            }
                        },
                        onValueChangeFinished = {
                            // 松手补一次最终位置：节流可能吞掉最后一次回调
                            sliderDragValue?.let(onProgressChange)
                            sliderDragValue = null
                        },
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
            visible = showResumeButton && activeSidePanel == null && !isLocked,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ElevatedButton(
                onClick = onResumeClick,
                shape = HanimeDefaults.Corners.pill,
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
                shape = MaterialTheme.shapes.extraLarge,
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
            visible = showRetry && activeSidePanel == null && !isLocked,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = HanimeDefaults.Colors.pageSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {

                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = stringResource(Res.string.video_loading_failed),
                        style = MaterialTheme.typography.titleMedium
                    )

                    // M5-3：把引擎/网络给的真实原因显示出来。
                    // 此前 errorMessage 只写不读，用户永远只有"加载影片失败"一句，
                    // 分不清是网络、403 还是解码器问题（也拿不到可反馈的信息）。
                    errorMessage?.takeIf { it.isNotBlank() }?.let { reason ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

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
    val shape = MaterialTheme.shapes.medium
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

/** 双击左右跳转的步长（毫秒）。10 秒是 YouTube / 哔哩哔哩的通行值。 */
private const val DOUBLE_TAP_SEEK_STEP_MS = 10_000L

/** 拖动进度条时的 seek 节流间隔（毫秒，M5-3）。 */
private const val SLIDER_SEEK_THROTTLE_MS = 120L

private enum class PlayerSidePanel {
    Speed,
    SuperResolution,
    Quality,
}

@Composable
private fun BoxScope.PlayerSidePanelSheet(
    options: List<String>,
    selectedIndex: Int?,
    onSelected: (Int) -> Unit,
    panelWidth: Dp = 156.dp,
) {
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


@Composable
fun PlayerSlider(
    value: Float,
    buffered: Float,
    onValueChange: (Float) -> Unit,
    /** 松手回调：调用方据此补一次最终 seek 并清掉本地乐观值。 */
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
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
                        .clip(HanimeDefaults.Corners.pill)
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
                        .clip(HanimeDefaults.Corners.pill)
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
                        .clip(HanimeDefaults.Corners.pill)
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
                    .clip(MaterialTheme.shapes.extraLargeIncreased)
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
                            MaterialTheme.shapes.extraLargeIncreased
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
                            .clip(HanimeDefaults.Corners.pill),
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
