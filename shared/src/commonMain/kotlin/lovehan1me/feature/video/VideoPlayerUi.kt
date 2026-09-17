package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.ic_fast_forward
import lovehan1me.video_loading_failed
import lovehan1me.gif_capture
import lovehan1me.screenshot
import lovehan1me.retry
import lovehan1me.replay
import lovehan1me.player_time_format
import lovehan1me.player_speed_format
import lovehan1me.player_play_from_beginning
import lovehan1me.player_auto_quality
import lovehan1me.playback_finished
import lovehan1me.cancel
import lovehan1me.ic_unlock
import lovehan1me.ic_refresh
import lovehan1me.ic_play_arrow
import lovehan1me.ic_pause
import lovehan1me.ic_lock
import lovehan1me.ic_home
import lovehan1me.ic_fullscreen
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
import lovehan1me.ui.transition.sharedCoverElement
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.theme.HanimeDefaults
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lovehan1me.core.platform.currentEpochMillis
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import lovehan1me.sure_to_delete
import lovehan1me.super_resolution_quality
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_off
import lovehan1me.player_progress_percent
import lovehan1me.player_gesture_volume
import lovehan1me.player_gesture_progress
import lovehan1me.player_gesture_brightness
import lovehan1me.player_anime4k_label
import lovehan1me.here_is_empty
import lovehan1me.edit
import lovehan1me.delete
import lovehan1me.confirm
import lovehan1me.ic_volume_up
import lovehan1me.ic_light_mode
import lovehan1me.ic_fast_rewind
import lovehan1me.core.util.AppToast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    // 与列表页卡片封面配对：进页面时封面形变飞过来。
    // 注意 poster 只在起播前显示，起播后共享元素自然消失——这是当前页面结构
    // （详情区顶部就是播放器、没有静态大封面）下的正常表现。
    sharedElementKey: String? = null,
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
    /**
     * Kazumi 哔哩哔哩风总开关（顶/底 scrim、白字入口、无中央大键、小手势 HUD）。
     * 调用方按 `isDualPane || isFullscreen` 置位：窄屏竖屏恒 false → 零视觉变化。
     */
    bilibiliStyle: Boolean = false,
    /** 下一集（系列视频才有，null = 不显示）。只在 [bilibiliStyle] 底栏使用。 */
    onNextClick: (() -> Unit)? = null,
    /**
     * animeko 的「expanded」形态（宽屏双栏 / 全屏）：底栏进度条从"内联在图标之间"
     * 切换为"独占一行"。
     *
     * 与 [bilibiliStyle] 是**两件事** —— 那个管皮肤，这个管行结构。当前调用方两者由
     * 同一个条件（`isDualPane || isFullscreen`）派生，但刻意分成两个参数：
     * 将来要拆开时不必再动一遍签名。
     */
    expanded: Boolean = false,
    /**
     * animeko 右栏折叠开关（EpisodeVideoTopBarActions 的 RightPanelClose/Open）。
     * 只在宽屏右栏存在时由壳层置 true；窄屏/全屏/PiP 恒 false → 零视觉变化。
     */
    showSidebarToggle: Boolean = false,
    sidebarVisible: Boolean = true,
    onToggleSidebar: (Boolean) -> Unit = {},
    /** 顶栏收藏心：是否已收藏 + 切换回调（对齐 Kazumi 顶栏 collect 键）。 */
    isFavVideo: Boolean = false,
    onToggleFavoriteVideo: () -> Unit = {},
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
    // 功能弹窗 hold（Kazumi `acquirePlayerPanelHold`）：底栏选单 / 顶栏更多菜单
    // 任一打开，控件自动隐藏就暂停计时。
    var topMenuOpen by remember { mutableStateOf(false) }
    var bottomMenuOpen by remember { mutableStateOf(false) }
    val menuHold = topMenuOpen || bottomMenuOpen
    // Kazumi 音量 pill：调音量时弹出来，1 秒无变化后收起（Kazumi 手势 650ms / 滚轮 2s，取中间）。
    var volumeHudVisible by remember { mutableStateOf(false) }
    var volumeHudTick by remember { mutableIntStateOf(0) }
    fun pokeVolumeHud() {
        volumeHudTick++
        volumeHudVisible = true
    }
    LaunchedEffect(volumeHudTick) {
        if (volumeHudTick > 0) {
            delay(1_000L.milliseconds)
            volumeHudVisible = false
        }
    }
    // 经由本包装器的音量变更都会点亮 pill；手势/键盘/ pill 滑块统一走这里。
    val volumeChangeWithHud: (Float) -> Unit = { value ->
        onVolumeChange(value)
        if (!isLocked) pokeVolumeHud()
    }
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
    //
    // ⚠️ 悬停挂在**顶栏 / 底栏容器**上，不挂整个播放器 Box。
    // animeko 用的是 `Modifier.hoverToRequestAlwaysOn()`（挂在 VideoScaffold 的顶/底栏槽位，
    // 见 `VideoScaffold.kt:189` / `:236`）。挂满整屏的后果是"鼠标停在画面正中也会让控件
    // 永不隐藏"—— 那不是用户意图，也让"自动隐藏"在桌面端形同虚设。
    val topBarHoverSource = remember { MutableInteractionSource() }
    val bottomBarHoverSource = remember { MutableInteractionSource() }
    val isTopBarHovered by topBarHoverSource.collectIsHoveredAsState()
    val isBottomBarHovered by bottomBarHoverSource.collectIsHoveredAsState()
    val isControlsHovered = isTopBarHovered || isBottomBarHovered

    // 控件自动隐藏：**5 秒**倒计时（3s 太急，音量/亮度条还没看清就被收走）。
    // 三种"先别收"的情形：
    //   ① 手势进行中（含横向拖动 seek）—— 手势结束后**重新计时**，而不是立刻消失；
    //   ② 指针悬停在**控件上**（顶栏/底栏）—— 用户显然还在操作；
    //   ③ 功能弹窗打开中（Kazumi 的 panel hold：选单开着时控件不能收）。
    // 键里带上这些状态：它们一变，倒计时就重启，天然做到"手势结束不自动消失"。
    LaunchedEffect(
        showControlsState,
        isPlaying,
        menuHold,
        gestureType,
        isProgressGestureActive,
        isControlsHovered,
    ) {
        if (
            showControlsState &&
            isPlaying &&
            !menuHold &&
            gestureType == null &&
            !isProgressGestureActive &&
            !isControlsHovered
        ) {
            delay(CONTROLS_AUTO_HIDE_MS.milliseconds)
            showControlsState = false
        }
    }

    val effectiveShowControls = showControls && showControlsState && !isLocked
    val playerUiVisible = effectiveShowControls
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
    val latestOnVolumeChange by rememberUpdatedState(volumeChangeWithHud)
    val latestOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val latestOnLongPressEnd by rememberUpdatedState(onLongPressEnd)

    // 静音前的音量（M 键来回切时用它恢复，避免"取消静音直接拉满"）
    var volumeBeforeMute by remember { mutableFloatStateOf(1f) }
    // 键盘快捷键：动作集用「最新值」语义组装（修饰符只装一次，回调会变）。
    // 音量三件套走带 pill 的包装（Kazumi 调音量必亮 pill）。
    val keyActions = rememberPlayerKeyActions(
        onTogglePlay = onPlayClick,
        onSeekBy = onSeekBy,
        onVolumeUp = { fine ->
            volumeChangeWithHud((latestVolume + if (fine) 0.01f else 0.05f).coerceIn(0f, 1f))
        },
        onVolumeDown = { fine ->
            volumeChangeWithHud((latestVolume - if (fine) 0.01f else 0.05f).coerceIn(0f, 1f))
        },
        onToggleMute = {
            if (latestVolume > 0f) {
                volumeBeforeMute = latestVolume
                volumeChangeWithHud(0f)
            } else {
                volumeChangeWithHud(volumeBeforeMute)
            }
        },
        onToggleFullscreen = onFullscreenClick,
        onSpeedSelected = onPlaybackSpeedSelected,
    )


    // ── M5 埋点：手势 ────────────────────────────────────────────
    // 本 composable 的**局部 UI 状态**（不需要上提到 ViewModel），
    // 按"谁拥有状态谁负责副作用"，埋点就放在这里，而不是在屏幕边界镜像一份状态。
    LaunchedEffect(gestureType) {
        gestureType?.let { PlayerTrace.event("gesture", it.name) }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) isLongPressSpeedActive = false
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
                bilibiliStyle = bilibiliStyle,
            )
        } else gestureType?.let { type ->
            GestureIndicatorOverlay(
                visible = true,
                type = type,
                percent = gesturePercent,
                progressDirection = progressDirection,
                modifier = Modifier.fillMaxSize(),
                bilibiliStyle = bilibiliStyle,
            )
        }

        PlayerBackdropLayers(
            showPoster = showPoster,
            posterUrl = posterUrl,
            sharedElementKey = sharedElementKey,
            playerUiVisible = playerUiVisible,
        )

        PlayerTopBar(
            visible = playerUiVisible,
            isFullscreen = isFullscreen,
            title = title,
            deviceTime = deviceTime,
            frameCaptureEnabled = frameCaptureEnabled,
            onCaptureScreenshot = onCaptureScreenshot,
            onOpenGifCapture = onOpenGifCapture,
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            isFav = isFavVideo,
            onToggleFavorite = onToggleFavoriteVideo,
            showSidebarToggle = showSidebarToggle,
            sidebarVisible = sidebarVisible,
            onToggleSidebar = onToggleSidebar,
            expanded = expanded,
            onMenuOpenChange = { topMenuOpen = it },
        )

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

        PlayerBufferingOverlay(
            isLocked = isLocked,
            gestureType = gestureType,
            isPlaybackEnded = isPlaybackEnded,
            showLoading = showLoading,
            isProgressGestureActive = isProgressGestureActive,
        )

        PlayerCenterControls(
            isLocked = isLocked,
            gestureType = gestureType,
            isPlaybackEnded = isPlaybackEnded,
            showLoading = showLoading,
            isPlaying = isPlaying,
            effectiveShowControls = effectiveShowControls,
            showUnlockButton = showUnlockButton,
            playerUiVisible = playerUiVisible,
            onPlayClick = onPlayClick,
            onLockClick = onLockClick,
            bilibiliStyle = bilibiliStyle,
            // 截图悬浮钮只在 expanded 下出现（对齐 animeko `expanded && Desktop` 的限定，
            // 能力判定仍走 frameCaptureEnabled，见调用方）。
            showScreenshotButton = frameCaptureEnabled && expanded,
            onScreenshotClick = { onCaptureScreenshot?.invoke() },
        )

        // Kazumi 音量 pill：手势/键盘/pill 滑块调音量时弹顶栏下方，1 秒后收起。
        VolumePill(
            visible = volumeHudVisible && !isLocked,
            volume = currentVolume,
            onVolumeChange = volumeChangeWithHud,
            onToggleMute = {
                if (currentVolume > 0f) {
                    volumeBeforeMute = currentVolume
                    volumeChangeWithHud(0f)
                } else {
                    volumeChangeWithHud(volumeBeforeMute)
                }
            },
        )

        PlayerBottomBar(
            visible = playerUiVisible,
            // 拖动中显示手指位置（乐观值），松手回到引擎位置
            sliderValue = sliderDragValue ?: progress,
            bufferedProgress = bufferedProgress,
            onSliderValueChange = { value ->
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
            onSliderValueChangeFinished = {
                // 松手补一次最终位置：节流可能吞掉最后一次回调
                sliderDragValue?.let(onProgressChange)
                sliderDragValue = null
            },
            isPlaying = isPlaying,
            onPlayClick = onPlayClick,
            currentTime = currentTime,
            totalTime = totalTime,
            playbackSpeed = playbackSpeed,
            resolvedQualityLabel = resolvedQualityLabel,
            qualities = qualities,
            qualitySelectedIndex = qualitySelectedIndex.takeIf { it >= 0 },
            onQualitySelected = onQualitySelected,
            fullscreenEnabled = fullscreenEnabled,
            onFullscreenClick = onFullscreenClick,
            onPlaybackSpeedSelected = onPlaybackSpeedSelected,
            superResolutionOptions = superResolutionOptions,
            selectedSuperResolutionIndex = selectedSuperResolutionIndex,
            onSuperResolutionSelected = onSuperResolutionSelected,
            onNextClick = onNextClick,
            isFullscreen = isFullscreen,
            durationMs = durationMs,
            expanded = expanded,
            onMenuOpenChange = { bottomMenuOpen = it },
            hoverInteractionSource = bottomBarHoverSource,
        )

        PlayerStateCards(
            showResumeButton = showResumeButton,
            isPlaybackEnded = isPlaybackEnded,
            showRetry = showRetry,
            isLocked = isLocked,
            errorMessage = errorMessage,
            onResumeClick = onResumeClick,
            onReplay = onReplay,
            onRetry = onRetry,
        )
    }
}
