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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
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
import androidx.compose.runtime.rememberCoroutineScope
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
    /** 平台是否支持全屏（iOS 尚未实现 → false 时不显示入口，避免"按了没反应"）。 */
    fullscreenEnabled: Boolean = true,
    onLockClick: () -> Unit = {},
    onProgressChange: (Float) -> Unit = {},
    /** M5-3：相对跳转（双击左右快退/快进）。传毫秒增量。 */
    onSeekBy: (Long) -> Unit = {},
    /**
     * 画面缩放倍率（1f = 原始尺寸）。双指缩放 / 桌面 Ctrl+滚轮 调，双击归零。
     * 状态由屏幕边界持有，这里只拿值与回调。
     */
    scale: Float = 1f,
    onScaleChange: (Float) -> Unit = {},
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
    /** 双指缩放进行中：此时冻结亮度/音量/进度三种手势，避免"一根手指拖动 + 缩放"抢事件。 */
    var isScaleGestureActive by remember { mutableStateOf(false) }
    var isLongPressSpeedActive by remember { mutableStateOf(false) }
    var suppressTapUntilMs by remember { mutableLongStateOf(0L) }
    var activeSidePanel by remember { mutableStateOf<PlayerSidePanel?>(null) }
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

    // 鼠标/触控笔悬停：**只有指针设备才会产生悬停交互**，
    // Android / iOS 的触摸输入不会触发 Enter/Exit，因此这里不需要按平台分支 ——
    // 触摸平台上它恒为 false，"不加 hover 逻辑"是自然结果而不是要特判。
    val hoverInteractionSource = remember { MutableInteractionSource() }
    val isHovered by hoverInteractionSource.collectIsHoveredAsState()

    // 控件自动隐藏：**5 秒**倒计时（3s 太急，音量/亮度条还没看清就被收走）。
    // 三种"先别收"的情形：
    //   ① 手势进行中（含横向拖动 seek）—— 手势结束后**重新计时**，而不是立刻消失；
    //   ② 指针悬停在播放器上 —— 用户显然还在操作；
    //   ③ 侧栏面板展开中 —— 面板要用控件。
    // 键里带上这些状态：它们一变，倒计时就重启，天然做到"手势结束不自动消失"。
    LaunchedEffect(
        showControlsState,
        isPlaying,
        activeSidePanel,
        gestureType,
        isProgressGestureActive,
        isHovered,
    ) {
        if (
            showControlsState &&
            isPlaying &&
            activeSidePanel == null &&
            gestureType == null &&
            !isProgressGestureActive &&
            !isHovered
        ) {
            delay(CONTROLS_AUTO_HIDE_MS.milliseconds)
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
    val latestScale by rememberUpdatedState(scale)
    val latestOnScaleChange by rememberUpdatedState(onScaleChange)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestProgressSensitivity by rememberUpdatedState(progressGestureSensitivity)
    val latestOnProgressGesture by rememberUpdatedState(onProgressGesture)
    val latestOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val latestOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val latestOnLongPressEnd by rememberUpdatedState(onLongPressEnd)

    // 静音前的音量（M 键来回切时用它恢复，避免"取消静音直接拉满"）
    var volumeBeforeMute by remember { mutableFloatStateOf(1f) }
    // 键盘快捷键：动作集用「最新值」语义组装（修饰符只装一次，回调会变）
    val keyActions = rememberPlayerKeyActions(
        onTogglePlay = onPlayClick,
        onSeekBy = onSeekBy,
        onVolumeUp = { fine ->
            onVolumeChange((latestVolume + if (fine) 0.01f else 0.05f).coerceIn(0f, 1f))
        },
        onVolumeDown = { fine ->
            onVolumeChange((latestVolume - if (fine) 0.01f else 0.05f).coerceIn(0f, 1f))
        },
        onToggleMute = {
            if (latestVolume > 0f) {
                volumeBeforeMute = latestVolume
                onVolumeChange(0f)
            } else {
                onVolumeChange(volumeBeforeMute)
            }
        },
        onToggleFullscreen = onFullscreenClick,
        onSpeedSelected = onPlaybackSpeedSelected,
    )


    // ── M5 埋点：手势 / 侧栏面板 ──────────────────────────────────
    // 这两个都是本 composable 的**局部 UI 状态**（不需要上提到 ViewModel），
    // 按"谁拥有状态谁负责副作用"，埋点就放在这里，而不是在屏幕边界镜像一份状态。
    LaunchedEffect(gestureType) {
        gestureType?.let { PlayerTrace.event("gesture", it.name) }
    }
    LaunchedEffect(activeSidePanel) {
        activeSidePanel?.let { PlayerTrace.event("panel", it.name) }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) isLongPressSpeedActive = false
    }

    LaunchedEffect(activeSidePanel) {
        if (activeSidePanel != null) {
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
            .background(HanimeDefaults.Overlay.backdrop)
            // 悬停即"用户在场"：配合上面的自动隐藏倒计时使用
            .hoverable(hoverInteractionSource)
            // 键盘快捷键（桌面端；触摸端 actual 为恒等，见 PlayerKeyboardShortcuts）
            .playerKeyboardShortcuts(keyActions)
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
                            // 缩放非 1 时，双击 = 归零（与快进/快退互斥：两者抢同一个手势，
                            // 归零优先 —— 画面看不清时，用户第一反应就是双击复位）
                            if (latestScale != 1f) {
                                latestOnScaleChange(1f)
                            } else {
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
                            .background(HanimeDefaults.Overlay.backdrop)
                            // 画面缩放（双指 / Ctrl+滚轮）；控件层是兄弟节点，不跟着放大
                            .scale(scale)
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
                    modifier = videoModifier.background(HanimeDefaults.Overlay.backdrop)
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
                                // 双指缩放期间：亮度/音量/进度**全部冻结**（缩放优先，避免抢事件）
                                if (isScaleGestureActive) {
                                    gestureType = null
                                    isProgressGestureActive = false
                                    return@detectDragGestures
                                }
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
                                if (isScaleGestureActive) return@detectDragGestures
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
                }
                // ── 双指缩放：**仅缩放**，不做平移 ──────────────────────────
                // 与亮度/音量/进度同一套"跟随手指"语义：只按两指间距的**比值**增量缩放，
                // 不记起点、不吸附 —— 跟到哪算到哪。缩放期间三种拖动手势被冻结（见
                // onDragStart/onDrag 的 isScaleGestureActive 短路），长按倍速期间也不启动。
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var previousSpan = 0f
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2 && !isLongPressSpeedActive) {
                                val dx = pressed[0].position.x - pressed[1].position.x
                                val dy = pressed[0].position.y - pressed[1].position.y
                                val span = kotlin.math.sqrt(dx * dx + dy * dy)
                                if (previousSpan > 0f && span > 0f) {
                                    isScaleGestureActive = true
                                    latestOnScaleChange(
                                        (latestScale * (span / previousSpan))
                                            .coerceIn(VIDEO_SCALE_MIN, VIDEO_SCALE_MAX)
                                    )
                                }
                                previousSpan = span
                            } else {
                                previousSpan = 0f
                            }
                        } while (event.changes.any { it.pressed })
                        previousSpan = 0f
                        if (isScaleGestureActive) {
                            isScaleGestureActive = false
                            // 双指抬手不能被单击当成"显示/隐藏控件"
                            suppressTapUntilMs = nowMs() + 500L
                        }
                    }
                }
                // 桌面"Ctrl+滚轮"模拟双指缩放（触摸端 actual 为恒等，见 PlayerWheelZoom）
                .playerWheelZoom(latestScale) { next ->
                    latestOnScaleChange(next.coerceIn(VIDEO_SCALE_MIN, VIDEO_SCALE_MAX))
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
                        horizontal = HanimeDefaults.Spacing.extraLarge,
                        vertical = HanimeDefaults.Spacing.small,
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
                }

                /**
                 * Content
                 */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HanimeDefaults.PlayerSizes.topBarMinHeight)
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
                        modifier = Modifier
                            // 命中区 48dp / 视觉 32dp：向上仍报告 32dp，排布零变化
                            .playerHitTarget(visual = HanimeDefaults.Sizes.controlXS)
                            .size(PLAYER_MIN_TOUCH_TARGET)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back_ios),
                            contentDescription = null,
                            tint = HanimeDefaults.Overlay.onScrim,
                            modifier = Modifier.size(HanimeDefaults.PlayerSizes.iconLarge)
                        )
                    }

                    Spacer(modifier = Modifier.width(HanimeDefaults.Spacing.extraSmall))

                    /**
                     * Home
                     */
                    IconButton(
                        onClick = onHomeClick,
                        modifier = Modifier
                            .playerHitTarget(visual = HanimeDefaults.Sizes.controlXS)
                            .size(PLAYER_MIN_TOUCH_TARGET)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_home),
                            contentDescription = null,
                            tint = HanimeDefaults.Overlay.onScrim,
                            modifier = Modifier.size(HanimeDefaults.PlayerSizes.iconLarge)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    /**
                     * Title
                     */
                    Text(
                        text = title,
                        color = HanimeDefaults.Overlay.textPrimary,
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
                                color = HanimeDefaults.Overlay.textTertiary,
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
                .padding(start = HanimeDefaults.Spacing.extraExtraLarge),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = HanimeDefaults.Overlay.videoDim,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fast_forward),
                    contentDescription = null,
                    tint = HanimeDefaults.Overlay.onScrim,
                    modifier = Modifier
                        .padding(HanimeDefaults.Spacing.medium)
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
                        modifier = Modifier.size(HanimeDefaults.PlayerSizes.centerButton)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_play_arrow),
                            contentDescription = null,
                            modifier = Modifier.size(HanimeDefaults.PlayerSizes.centerIcon)
                        )
                    }
                } else {
                    // Playing: small pause button when controls are visible
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(HanimeDefaults.PlayerSizes.centerButton)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_pause),
                            contentDescription = null,
                            tint = HanimeDefaults.Overlay.onScrim,
                            modifier = Modifier.size(HanimeDefaults.PlayerSizes.centerIcon)
                        )
                    }
                }
            }
        }

        /**
         * 最小控件（Minimal controls）：**播放中且控件已自动隐藏**时，中央给一枚小号暂停键，
         * 点它**直接暂停**，而不是把整排控件叫回来（Media3 minimal controls 的行为）。
         *
         * 与上面的大键互斥：大键要求 `!isPlaying || effectiveShowControls`，
         * 本键要求 `isPlaying && !effectiveShowControls` —— 两者不可能同时为真。
         * 锁屏态不出现（PiP 在壳层被折算成 `isLocked = true`，见 VideoShellContent 的两处调用，
         * 所以 PiP 也一并排除）。
         *
         * 只有 `onPlayClick` 一个动作、点击后只切换播放状态：事务脚本只有一步，
         * 因此**绝不可能**出现"点了没反应"（PiP 态同理：不显示就不会被误点）。
         */
        AnimatedVisibility(
            visible =
                isPlaying &&
                        !effectiveShowControls &&
                        !isLocked &&
                        activeSidePanel == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        // 透明无底：命中区 48dp（M3 硬指标）/ 视觉 XS(32dp)，向上仍报告 XS
                        .playerHitTarget(visual = HanimeDefaults.Sizes.controlXS)
                        .size(PLAYER_MIN_TOUCH_TARGET)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_pause),
                        contentDescription = null,
                        tint = HanimeDefaults.Overlay.onScrim,
                        modifier = Modifier.size(HanimeDefaults.Sizes.controlXS)
                    )
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
                    .padding(end = HanimeDefaults.Spacing.extraLarge)
                    .size(HanimeDefaults.PlayerSizes.lockButton),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = HanimeDefaults.Overlay.lockButton
                )
            ) {
                Icon(
                    painter = if (isLocked)
                        painterResource(Res.drawable.ic_lock)
                    else
                        painterResource(Res.drawable.ic_unlock),
                    contentDescription = null,
                    tint = HanimeDefaults.Overlay.onScrim
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
                            onClick = { activeSidePanel = PlayerSidePanel.Speed },
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        PlayerMenuChip(
                            label = resolvedQualityLabel,
                            onClick = { activeSidePanel = PlayerSidePanel.Quality },
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

        /**
         * Resume 按钮
         */
        AnimatedVisibility(
            visible = showResumeButton && activeSidePanel == null && !isLocked,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = HanimeDefaults.PlayerSizes.centerButton),
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

                    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.large))

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

                    // M5-3：把引擎/网络给的真实原因显示出来 —— 按 Media3 官方错误视图规格：
                    //   高 32dp / 底边距 64dp / padding 12&4dp / 文本 14sp（= M3 bodyMedium）。
                    // "文本可选"：没有原因文本 → **整个错误视图不渲染**，只留重试卡。
                    errorMessage?.takeIf { it.isNotBlank() }?.let { reason ->
                        Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.medium))
                        Box(
                            modifier = Modifier
                                .padding(bottom = 64.dp)
                                .height(HanimeDefaults.Spacing.huge)
                                .padding(horizontal = HanimeDefaults.Spacing.large, vertical = HanimeDefaults.Spacing.small),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.large))

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
         * 侧栏面板 → **M3 官方形态**（ModalBottomSheet）。
         *
         * 之前是自绘右侧滑入面板 + 手搓遮罩，缺三样东西：
         *   ① 返回键关闭（含预测性返回）② 官方 scrim ③ 无障碍语义 —— ModalBottomSheet 三样都自带。
         * M3 没有官方的 side sheet 组件（那只是规范，无实现），所以"官方形态"落在 bottom sheet。
         * 保留：面板内容（倍速/清晰度/超分）、单选高亮、选项来源与回调、选完即关。
         */
        when (activeSidePanel) {
            PlayerSidePanel.Speed -> PlayerSidePanelBottomSheet(
                options = PlayerDefaults.speeds.map {
                    stringResource(Res.string.player_speed_format, it)
                },
                selectedIndex = speedSelectedIndex,
                onDismiss = { activeSidePanel = null },
                onSelected = { index ->
                    activeSidePanel = null
                    onPlaybackSpeedSelected(PlayerDefaults.speeds[index])
                },
            )

            PlayerSidePanel.SuperResolution -> PlayerSidePanelBottomSheet(
                options = superResolutionOptions,
                selectedIndex = selectedSuperResolutionIndex,
                onDismiss = { activeSidePanel = null },
                onSelected = { index ->
                    activeSidePanel = null
                    onSuperResolutionSelected(index)
                },
            )

            PlayerSidePanel.Quality -> PlayerSidePanelBottomSheet(
                options = qualities.map(PlaybackQuality::label),
                selectedIndex = qualitySelectedIndex.takeIf { it >= 0 },
                onDismiss = { activeSidePanel = null },
                onSelected = { index ->
                    activeSidePanel = null
                    onQualitySelected(index)
                },
            )

            null -> Unit
        }
    }
}

// M3：createGoogleCastIndicator / whiteDrawable 已随 Cast 按钮搬
// androidMain（ui.player.VideoPlatform.android.kt）。

/**
 * M3 官方硬指标：图标按钮的**触控目标**不得小于 48dp。
 *
 * 注意区分两件事：
 * - **视觉尺寸**：用户看到的图形（图标 18/20dp、锁钮圆底 42dp、chip 药丸高度）—— 本轮一律不动；
 * - **命中区**：真正吃掉点击/触摸的区域 —— 本轮统一补到 [PLAYER_MIN_TOUCH_TARGET]。
 */
private val PLAYER_MIN_TOUCH_TARGET = HanimeDefaults.PlayerSizes.minTouchTarget

/**
 * 把可点控件的**命中区**撑到 [hit]（默认 M3 的 48dp），但**向上仍报告视觉尺寸**，
 * 因此父级排布（间距、容器高度、相邻控件位置）与改动前逐像素一致。
 *
 * 实现要点：
 * 1. 量子节点时把约束放宽到 ≥[hit]，这样被包裹的 clickable 自身边界就是 48dp ——
 *    M3 的 `minimumInteractiveComponentSize()` 只把**上报尺寸**撑到 48，
 *    被 `Modifier.size()` 夹住时 clickable 的边界并不会变大（M3 1.5 `MinimumInteractiveModifierNode`
 *    是用父级约束量子节点、再 `max(placeable, 48dp)` 上报），所以必须放行约束。
 * 2. 量完以后**上报**视觉尺寸（[visual]，或 [visual] 为空时取子节点在原始约束下的固有尺寸），
 *    并把 48dp 的命中盒居中放好 —— 于是：“盒子”变大了，“占位”没变。
 * 3. 超出上报尺寸的那部分命中区依然能收到指针事件：`NodeCoordinator.hitTest` 在
 *    `isPointerInBounds` 为假时会走 `hitNear`（触摸的最小命中区外扩）/ `speculativeHit`
 *    （“本节点没命中，但子节点可能命中”），最终 `hitTestChild` 仍会访问到那个 48dp 的 clickable。
 *    前提是祖先链上没有 `clip` / 裁剪型 graphicsLayer —— 播放器的顶栏、底栏、chip 均无。
 *
 * 已知取舍：底栏是 6+12(进度条)+30(按钮行)+6 = 54dp，装不下两个 48dp，所以底栏按钮的命中盒
 *   会向上压进进度条下沿 9dp（只在按钮的水平范围内），该范围内按钮优先。
 *   彻底消除需要把底栏抬到 ≥60dp（Media3 官方底栏就是 60dp）—— 那是视觉变更，本轮不做。
 *
 * @param visual 向上报告的视觉尺寸；为 null 时取子节点固有尺寸（供尺寸由文字决定的 chip 使用）。
 */
private fun Modifier.playerHitTarget(
    hit: Dp = PLAYER_MIN_TOUCH_TARGET,
    visual: Dp? = null,
): Modifier = layout { measurable, constraints ->
    val hitPx = hit.roundToPx()
    // 1) 放宽约束：让内部 clickable 真的按 48dp 铺开
    val relaxed = constraints.copy(
        minWidth = maxOf(constraints.minWidth, hitPx),
        minHeight = maxOf(constraints.minHeight, hitPx),
        maxWidth = maxOf(constraints.maxWidth, hitPx),
        maxHeight = maxOf(constraints.maxHeight, hitPx),
    )
    // 2) 视觉尺寸：显式给定，或回落到子节点在原始约束下的固有尺寸
    val visualPx = visual?.roundToPx()
    val intrinsicWidth = measurable.minIntrinsicWidth(relaxed.maxHeight)
    val intrinsicHeight = measurable.minIntrinsicHeight(relaxed.maxWidth)
    val placeable = measurable.measure(relaxed)

    fun reported(measured: Int, intrinsic: Int, min: Int, max: Int): Int {
        val target = minOf(measured, visualPx ?: intrinsic)
        return maxOf(target, min).coerceAtMost(max)
    }
    val width = reported(placeable.width, measurable.minIntrinsicWidth(relaxed.maxHeight), constraints.minWidth, constraints.maxWidth)
    val height = reported(placeable.height, measurable.minIntrinsicHeight(relaxed.maxWidth), constraints.minHeight, constraints.maxHeight)

    // 3) 命中盒居中压在视觉尺寸上：视觉位置不变，多出来的部分向四周外扩
    layout(width, height) {
        placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
    }
}

@Composable
private fun PlayerMenuChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    // 命中层：M3 要求可点控件 ≥48dp。药丸外壳（视觉）保持原尺寸，多出来的命中区向外扩。
    Box(
        modifier = Modifier
            .playerHitTarget()
            .clip(shape)
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 视觉外壳：与改动前逐像素一致（clip / 底色 / 描边 / 内边距）
        Box(
            modifier = Modifier
                .clip(shape)
                .background(HanimeDefaults.Overlay.glass)
                .border(
                    1.dp,
                    HanimeDefaults.Overlay.border,
                    shape,
                )
                .padding(
                    horizontal = HanimeDefaults.Spacing.medium,
                    vertical = HanimeDefaults.Spacing.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = HanimeDefaults.Overlay.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 双击左右跳转的步长（毫秒）。10 秒是 YouTube / 哔哩哔哩的通行值。 */
private const val DOUBLE_TAP_SEEK_STEP_MS = 10_000L

/** 拖动进度条时的 seek 节流间隔（毫秒，M5-3）。 */
private const val SLIDER_SEEK_THROTTLE_MS = 120L

/** 控件自动隐藏倒计时（毫秒，M5 体验打磨）：3s → 5s。 */
private const val CONTROLS_AUTO_HIDE_MS = 5_000L

/** 画面缩放下限/上限（双指缩放 / Ctrl+滚轮）。 */
private const val VIDEO_SCALE_MIN = 0.5f
private const val VIDEO_SCALE_MAX = 4f

private enum class PlayerSidePanel {
    Speed,
    SuperResolution,
    Quality,
}

/**
 * 面板选项列表：原自绘侧栏的**内容**原样搬进 M3 ModalBottomSheet。
 *
 * 单选高亮、选项来源与回调不变；行高补到 M3 的 48dp 触控目标；
 * 关闭统一「先播收起动画，动画结束后再摘状态」，避免面板瞬移消失。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSidePanelBottomSheet(
    options: List<String>,
    selectedIndex: Int?,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        // 返回键（含预测性返回）/ 点 scrim / 下滑手势都走这里 —— M3 自带 BackHandler
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HanimeDefaults.Spacing.small),
        ) {
            itemsIndexed(options) { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HanimeDefaults.PlayerSizes.minTouchTarget)
                        .clickable {
                            // 选中即关：先播收起动画，动画结束再回调
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onSelected(index) }
                        }
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
                            HanimeDefaults.Overlay.onScrim
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
                    .size(HanimeDefaults.PlayerSizes.thumbBox),
                contentAlignment = Alignment.Center
            ) {

                /**
                 * Glow
                 */
                Box(
                    modifier = Modifier
                        .size(HanimeDefaults.PlayerSizes.thumbGlow)
                        .background(
                            HanimeDefaults.Overlay.thumbGlow,
                            CircleShape
                        )
                )

                /**
                 * Real Thumb
                 */
                Box(
                    modifier = Modifier
                        .size(HanimeDefaults.PlayerSizes.thumb)
                        .background(
                            HanimeDefaults.Overlay.onScrim,
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
                    .height(HanimeDefaults.PlayerSizes.trackBox),
                contentAlignment = Alignment.CenterStart
            ) {

                /**
                 * Background Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HanimeDefaults.PlayerSizes.track)
                        .clip(HanimeDefaults.Corners.pill)
                        .background(
                            HanimeDefaults.Overlay.track
                        )
                )

                /**
                 * Buffered Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth(buffered.coerceIn(0f, 1f))
                        .height(HanimeDefaults.PlayerSizes.track)
                        .clip(HanimeDefaults.Corners.pill)
                        .background(
                            HanimeDefaults.Overlay.trackBuffered
                        )
                )

                /**
                 * Active Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth(value.coerceIn(0f, 1f))
                        .height(HanimeDefaults.PlayerSizes.track)
                        .clip(HanimeDefaults.Corners.pill)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = HanimeDefaults.Alpha.secondary)
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
                            HanimeDefaults.Overlay.blurDim
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
                                    HanimeDefaults.Overlay.divider,
                                    HanimeDefaults.Overlay.textFaint
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
                            HanimeDefaults.Overlay.divider,
                            MaterialTheme.shapes.extraLargeIncreased
                        )
                )

                /**
                 * Content
                 */
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(HanimeDefaults.Spacing.extraLarge),
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
                        tint = HanimeDefaults.Overlay.onScrim,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.medium))

                    Text(
                        text = when (type) {
                            GestureIndicatorType.Brightness -> stringResource(Res.string.player_gesture_brightness)
                            GestureIndicatorType.Volume -> stringResource(Res.string.player_gesture_volume)
                            GestureIndicatorType.Progress -> stringResource(Res.string.player_gesture_progress)
                        },
                        color = HanimeDefaults.Overlay.textStrong,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.large))

                    LinearProgressIndicator(
                        progress = { percent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HanimeDefaults.Spacing.medium)
                            .clip(HanimeDefaults.Corners.pill),
                        trackColor = HanimeDefaults.Overlay.divider,
                    )

                    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.large))

                    Text(
                        text = displayText,
                        color = HanimeDefaults.Overlay.onScrim,
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
