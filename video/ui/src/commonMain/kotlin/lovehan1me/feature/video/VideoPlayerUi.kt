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
import lovehan1me.video.ui.Res
import lovehan1me.video.ui.ic_fast_forward
import lovehan1me.video.ui.player_auto_quality
import lovehan1me.video.ui.FilledIconButton
import lovehan1me.video.ui.FilledTonalButton
import lovehan1me.video.ui.FilledTonalIconButton
import lovehan1me.video.ui.IconButton
import lovehan1me.video.ui.LocalPlayerDiagnostics
import lovehan1me.video.ui.LocalPlayerHaptic
import lovehan1me.video.ui.PlayerTokens
import lovehan1me.video.contract.PlaybackQuality
import lovehan1me.video.contract.PlayerDefaults
import lovehan1me.video.contract.VideoAspectMode
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.video.ui.PlayerControllerState
import lovehan1me.video.ui.VideoScaffold
import lovehan1me.video.ui.resolveVideoAspectRatio
import lovehan1me.video.ui.videoFillsWidth

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerUi(
    modifier: Modifier = Modifier,
    /**
     * 画面渲染插槽。null = 只画占位底色（无引擎 / 未起播）。
     *
     * 给插槽而不是引擎实例：本组件不必知道引擎是什么，只要"往这个矩形里画视频"；
     * 引擎身份与 surface 的 attach/detach 归调用方（它才持有引擎）。画面比例、黑边、
     * 缩放这些**布局**规则仍由本组件算，调用方不必复制一份。
     */
    videoSurface: (@Composable (Modifier) -> Unit)? = null,
    /**
     * 封面插槽：起播前的海报。null = 不画。
     *
     * 给插槽而不是 URL：封面怎么加载、要不要配列表页卡片的形变过渡（共享元素）
     * 都是编排层的知识，本组件只负责"往这个矩形里画封面"。
     * 注意 poster 只在起播前显示，起播后共享元素自然消失 —— 这是当前页面结构
     * （详情区顶部就是播放器、没有静态大封面）下的正常表现。
     */
    cover: (@Composable () -> Unit)? = null,
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
     * G2-3b：画面比例可选档位（引擎真实支持的那些）。空 = 不支持 → 底栏不画该菜单。
     */
    videoAspectOptions: List<VideoAspectMode> = emptyList(),
    selectedVideoAspect: VideoAspectMode = VideoAspectMode.Fit,
    onVideoAspectSelected: (VideoAspectMode) -> Unit = {},
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
    progressGestureSensitivity: Float = PlayerDefaults.PROGRESS_SLIDE_SENSITIVITY,
    /**
     * 引擎上报的画面宽高比；**`0f` = 还没上报**，兜底比例由 UI 布局层唯一持有
     * （见 `DEFAULT_VIDEO_ASPECT_RATIO`）。调用方不要自己算默认值 —— 两处各算一次
     * 会让"上报值到达"看起来像换了个视频。
     */
    videoAspectRatio: Float = 0f,
    /**
     * 弹幕绘制层插槽。null = 不画（未配置数据源、PiP、首帧未到都由调用方决定）。
     *
     * 给的是插槽而不是 `DanmakuSession`：绘制层的字号/透明度/区域来自设置，
     * session 的生命周期也归屏幕边界，本组件只知道**画面矩形在哪**。
     */
    danmakuLayer: (@Composable () -> Unit)? = null,
    /**
     * 弹窗插槽：挂在最上层、**不在任何会自动隐藏的槽里**。
     *
     * 弹幕设置这类弹窗必须走这里 —— 组合进底栏的可见性容器，控件一自动隐藏就会
     * 连带把它销毁，表现成"弹窗自己消失了"。状态由调用方持有，本组件只负责摆位。
     */
    dialogHost: (@Composable () -> Unit)? = null,
    /**
     * 弹幕**双钮**插槽：放在底栏中间位（原假输入框的位置）。
     *
     * 与 [danmakuLayer] 分开传，因为可见条件不同：绘制层要等首帧，双钮不该等
     * （开关与设置在海报阶段就该能点）。
     */
    danmakuControls: (@Composable () -> Unit)? = null,
    /**
     * Kazumi 哔哩哔哩风总开关（顶/底 scrim、白字入口、无中央大键、小手势 HUD）。
     * 调用方按 `isDualPane || isFullscreen` 置位：窄屏竖屏恒 false → 零视觉变化。
     */
    bilibiliStyle: Boolean = false,
    /** 下一集（系列视频才有，null = 不显示）。只在 [bilibiliStyle] 底栏使用。 */
    onNextClick: (() -> Unit)? = null,
    /**
     * 系列自动连播开关值 + 变更回调（与 [onNextClick] 同条件透传到底栏，
     * 单片时不展示，见 PlayerBottomBar）。
     */
    autoPlayNext: Boolean = true,
    onAutoPlayNextChange: (Boolean) -> Unit = {},
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
    // 控件可见性由 PlayerControllerState 仲裁：单击/倒计时/手势各自只动自己那份输入，
    // 可见性从它们派生，不再由多处布尔量互相覆盖。
    val controls = remember { PlayerControllerState() }
    val controlsVisible by controls.controlsVisible
    val alwaysOn by controls.alwaysOnActive
    fun requestAlwaysOn(key: Any) = controls.requestAlwaysOn(key)
    fun cancelAlwaysOn(key: Any) = controls.cancelAlwaysOn(key)

    // M5-3：拖动进度条期间的**本地乐观值**。拖动中显示手指位置，而不是引擎回写的旧位置 ——
    // 引擎 seek 后立刻 publishState，而它读到的仍是旧位置，直接显示会"拖了又弹回去"。
    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
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
    // 滑条拖动请求常亮（P3-2 补的漏）：触屏上滑条拖动不经过手势仲裁
    //（gestureType 为 null）也没有悬停，5 秒计时器此前会中途藏控件。
    // sliderDragValue 非空即拖动中（滑条/手势两条路径共用，松手即 null），天然配对。
    LaunchedEffect(sliderDragValue) {
        if (sliderDragValue != null) requestAlwaysOn(SliderDragRequest)
        else cancelAlwaysOn(SliderDragRequest)
    }

    // 功能弹窗 hold（Kazumi `acquirePlayerPanelHold`）：顶栏/底栏菜单各占一个 key
    //（两菜单可同时开，同 key 会早退）。onMenuOpenChange 的 true/false 天然配对
    //（弹窗 dismiss 必调），不存在"结束事件丢失"的泄漏场景；手势类状态不敢转请求方，见上。
    fun setTopMenuHold(open: Boolean) {
        if (open) requestAlwaysOn(TopMenuHoldRequest) else cancelAlwaysOn(TopMenuHoldRequest)
    }

    fun setBottomMenuHold(open: Boolean) {
        if (open) requestAlwaysOn(BottomMenuHoldRequest) else cancelAlwaysOn(BottomMenuHoldRequest)
    }
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
    val haptic = LocalPlayerHaptic.current
    // 出口取一次：它的读是 @Composable 语义，不能在普通 lambda（LaunchedEffect / remember 计算块）里读。
    val diagnostics = LocalPlayerDiagnostics.current
    // M3：原 DateFormat.getTimeFormat(context)（Android-only）；改当前时分，
    // 24 小时制零填充（与系统 12/24 小时制差异可接受）。
    var deviceTime by remember { mutableStateOf(formatDeviceTime(Clock.System.now().toEpochMilliseconds())) }

    LaunchedEffect(Unit) {
        while (true) {
            deviceTime = formatDeviceTime(Clock.System.now().toEpochMilliseconds())
            delay(60_000L.milliseconds)
        }
    }

    // 鼠标/触控笔悬停：**只有指针设备才会产生悬停交互**，
    // Android / iOS 的触摸输入不会触发 Enter/Exit，因此这里不需要按平台分支 ——
    // 触摸平台上它恒为 false，"不加 hover 逻辑"是自然结果而不是要特判。
    //
    // ⚠️ 悬停只挂在**顶栏 / 底栏容器**上，不挂整个播放器 Box。
    // 挂满整屏的后果是"鼠标停在画面正中也会让控件永不隐藏"——
    // 那不是用户意图，也让"自动隐藏"在桌面端形同虚设。
    val topBarHoverSource = remember { MutableInteractionSource() }
    val bottomBarHoverSource = remember { MutableInteractionSource() }
    val isTopBarHovered by topBarHoverSource.collectIsHoveredAsState()
    val isBottomBarHovered by bottomBarHoverSource.collectIsHoveredAsState()
    val isControlsHovered = isTopBarHovered || isBottomBarHovered

    // 控件自动隐藏：**5 秒**倒计时（3s 太急，音量/亮度条还没看清就被收走）。
    // 三种"先别收"的情形：
    //   ① 手势进行中（含横向拖动 seek）—— 手势结束后**重新计时**，而不是立刻消失；
    //   ② 指针悬停在**控件上**（顶栏/底栏）—— 用户显然还在操作；
    //   ③ 常亮令牌非空（功能弹窗/滑条拖动）。
    // 键里带上这些状态：它们一变，倒计时就重启，天然做到"手势结束不自动消失"。
    // 唯一的写入是到点时的 onAutoHideElapsed()；条件不满足时**什么都不写**，
    // 否则会与"到点已隐藏"互相触发成隐藏/重计的循环。
    val transientControlsActive =
        gestureType != null || isProgressGestureActive || isControlsHovered
    LaunchedEffect(
        controlsVisible,
        isPlaying,
        alwaysOn,
        transientControlsActive,
        isLocked,
        showControls,
    ) {
        if (showControls && !isLocked && isPlaying &&
            controls.canStartAutoHideTimer(transientControlsActive)
        ) {
            delay(CONTROLS_AUTO_HIDE_MS.milliseconds)
            controls.onAutoHideElapsed()
        }
    }

    val effectiveShowControls = showControls && controlsVisible && !isLocked
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
        gestureType?.let { diagnostics.event("gesture", it.name) }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) isLongPressSpeedActive = false
    }

    LaunchedEffect(isLocked, unlockButtonTimeoutToken) {
        if (isLocked) {
            controls.hideByUserInteraction()
            showUnlockButton = true
            delay(3000.milliseconds)
            showUnlockButton = false
        } else {
            showUnlockButton = false
            controls.showByUserInteraction()
        }
    }

    // 根修饰符（背景/键盘快捷键/锁定态点击）：手势层与槽位骨架共用同一层交互语义。
    val rootModifier = modifier
            .background(PlayerTokens.Overlay.backdrop)
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
                                        controls.hideByUserInteraction()
                                        suppressTapUntilMs = Long.MAX_VALUE
                                        haptic()
                                        latestOnLongPressStart()
                                    }
                                }
                                val released = tryAwaitRelease()
                                activationJob.cancel()
                                if (activated && released) {
                                    isLongPressSpeedActive = false
                                    controls.hideByUserInteraction()
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
                                controls.toggleByTap()
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
                                    // 目标值取**最新**进度：本手势块按 isLocked 挂载，
                                    // 闭包的 progress 会停在挂载那一刻，双击算出来的
                                    // 目标会一直是"旧位置 ± 一步"。
                                    val target = if (forward) {
                                        (latestProgress + stepPercent).coerceIn(0f, 1f)
                                    } else {
                                        (latestProgress - stepPercent).coerceIn(0f, 1f)
                                    }
                                    doubleTapSeekFeedback = direction to target
                                }
                            }
                            }
                        },
                    )
                }
            }

    /**
     * 视频渲染层
     */
    @Composable
    fun VideoSlot() {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val safeAspectRatio = resolveVideoAspectRatio(videoAspectRatio)
            val containerAspectRatio = if (maxHeight.value > 0f) {
                maxWidth.value / maxHeight.value
            } else {
                safeAspectRatio
            }
            val videoModifier = if (videoFillsWidth(videoAspectRatio, containerAspectRatio)) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(safeAspectRatio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(safeAspectRatio)
            }
            // 诊断：弹幕/画面矩形只取决于这三个数。线上错位先看这条日志，
            // 再决定是容器量错了、引擎报的尺寸错了，还是 surface 在内部又套了黑边。
            // remember 限流：值不变不重打，进度条的重组刷不到这里。
            remember(safeAspectRatio, maxWidth, maxHeight) {
                diagnostics.log(
                    "DanmakuPlacement",
                    "container=${maxWidth.value.toInt()}x${maxHeight.value.toInt()} " +
                        "videoAspect=$safeAspectRatio letterbox=${safeAspectRatio < containerAspectRatio}",
                )
            }

            if (videoSurface != null) {
                Box(
                    modifier = videoModifier
                        .background(PlayerTokens.Overlay.backdrop)
                        // 画面缩放（双指 / Ctrl+滚轮）；控件层是兄弟节点，不跟着放大
                        .scale(scale)
                ) {
                    // 渲染面由调用方给（引擎/mediamp 的 attach/detach 留在它那边），
                    // 本组件只提供矩形。**不要把比例放进 key**：引擎每次上报尺寸都会把
                    // Surface 连根重建（黑一帧）—— surface 的身份归调用方。
                    videoSurface(Modifier.fillMaxSize())
                }
            } else {
                Box(
                    modifier = videoModifier.background(PlayerTokens.Overlay.backdrop)
                )
            }

            // 弹幕层：**与画面同一个矩形**（不是整个播放器 Box），否则黑边上也会有字。
            // 刻意不套 `.scale(scale)`：用户放大画面时弹幕该留在屏幕尺寸上，
            // 跟着放大只会看到字变糊变大。它在控件层之前，也吃不到任何手势
            // （绘制层自身没有 pointerInput）。
            danmakuLayer?.let { layer ->
                Box(modifier = videoModifier) { layer() }
            }
        }
    }

    /**
     * 手势层：铺满播放器，与根修饰符共用同一套锁定态语义。
     */
    @Composable
    fun BoxScope.GestureHost() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // P3-3 手势仲裁结论（已审计，不拆两套表）：触屏/鼠标共用同一套
                // 拖拽/点击/双击/长按语义 —— 三组核对均无须分叉：
                // 悬停天然只在指针设备上产生（触屏恒 false，见上）；键盘快捷键已是
                // desktop-only actual；长按倍速/双击分区在鼠标上同样可用，无禁用理由。
                // 挂载期 key 只有 isLocked（锁定态整层换成"点任意处计解锁"）。
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
                                    controls.hideByUserInteraction()
                                    if (suppressTapUntilMs == Long.MAX_VALUE) {
                                        suppressTapUntilMs = nowMs() + 500L
                                    }
                                    latestOnLongPressEnd()
                                    longPressOwnsDrag = false
                                } else if (gestureType == GestureIndicatorType.Progress) {
                                    // P3-1：横向手势松手才提交一次（拖动期间只写 sliderDragValue 预览）。
                                    // 此前这里每帧调 onProgressGesture → 路由每帧 seekTo，
                                    // 弱机上足以打垮解码器（Exo 每次 seek 重缓冲、mpv 每次 exact 都 flush）。
                                    sliderDragValue?.let { latestOnProgressGesture(it) }
                                }
                                sliderDragValue = null
                                gestureType = null
                                isProgressGestureActive = false
                            },
                            onDragCancel = {
                                if (longPressOwnsDrag) {
                                    isLongPressSpeedActive = false
                                    controls.hideByUserInteraction()
                                    latestOnLongPressEnd()
                                    longPressOwnsDrag = false
                                } else if (gestureType == GestureIndicatorType.Progress) {
                                    // 被打断的手势按当前位置提交（此前是每帧已 seek，
                                    // 提交即最后位置，语义不变，只是少了中间 N 次）。
                                    sliderDragValue?.let { latestOnProgressGesture(it) }
                                }
                                sliderDragValue = null
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
                                    // P3-1：进度手势拖动期间只写本地预览（底栏跟手指走，
                                    // 见 sliderValue = sliderDragValue ?: progress），
                                    // **禁止在这里调 onProgressGesture**：每帧 seek 打垮解码器，
                                    // 提交只在 onDragEnd / onDragCancel 做一次。
                                    GestureIndicatorType.Progress -> sliderDragValue = next
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
    }

    /**
     * 海报与暗化垫底层：压在画面之上、控件之下。
     */
    @Composable
    fun BoxScope.BackdropSlot() {
        PlayerBackdropLayers(
            showPoster = showPoster,
            cover = cover,
            playerUiVisible = playerUiVisible,
        )
    }

    @Composable
    fun BoxScope.TopBarSlot() {
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
            onMenuOpenChange = { setTopMenuHold(it) },
        )
    }

    /** 中央区：长按倍速徽标、缓冲圈、中央键、音量 pill。各件靠 [BoxScope] 的 align 定位。 */
    @Composable
    fun BoxScope.CenterSlot() {
        AnimatedVisibility(
            visible = isLongPressSpeedActive,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = PlayerTokens.Spacing.extraExtraLarge),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = PlayerTokens.Overlay.videoDim,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fast_forward),
                    contentDescription = null,
                    tint = PlayerTokens.Overlay.onScrim,
                    modifier = Modifier
                        .padding(PlayerTokens.Spacing.medium)
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
    }

    @Composable
    fun BoxScope.BottomBarSlot() {
        PlayerBottomBar(
            visible = playerUiVisible,
            // 拖动中显示手指位置（乐观值），松手回到引擎位置
            sliderValue = sliderDragValue ?: progress,
            bufferedProgress = bufferedProgress,
            onSliderValueChange = { value ->
                // P3-1 收尾：滑条拖动纯预览（与横向手势同语义），松手提交一次。
                // 此前 120ms 节流 seek 在弱机上仍卡，且与手势路径不一致；现统一。
                sliderDragValue = value
            },
            onSliderValueChangeFinished = {
                // 松手提交一次最终位置（拖动期间零 seek）。
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
            videoAspectOptions = videoAspectOptions,
            selectedVideoAspect = selectedVideoAspect,
            onVideoAspectSelected = onVideoAspectSelected,
            onNextClick = onNextClick,
            autoPlayNext = autoPlayNext,
            onAutoPlayNextChange = onAutoPlayNextChange,
            isFullscreen = isFullscreen,
            durationMs = durationMs,
            expanded = expanded,
            onMenuOpenChange = { setBottomMenuHold(it) },
            hoverInteractionSource = bottomBarHoverSource,
            danmakuControls = danmakuControls,
        )
    }

    /**
     * 最上层宿主：状态卡与弹窗。**不在任何会自动隐藏的槽里**，
     * 故弹幕设置弹窗不会随底栏自动隐藏被销毁。
     */
    @Composable
    fun BoxScope.OverlaySlot() {
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
        dialogHost?.invoke()
    }

    VideoScaffold(
        modifier = rootModifier,
        video = { VideoSlot() },
        gestureHost = { GestureHost() },
        backdrop = { BackdropSlot() },
        topBar = { TopBarSlot() },
        center = { CenterSlot() },
        bottomBar = { BottomBarSlot() },
        overlayHost = { OverlaySlot() },
    )
}

// P3-2 常亮请求 key：顶栏菜单 / 底栏菜单 / 滑条（手势）拖动各占一个，
// 同 key 重复请求幂等，撤销只删自己的，不会早退别人的 hold。
private object TopMenuHoldRequest
private object BottomMenuHoldRequest
private object SliderDragRequest
