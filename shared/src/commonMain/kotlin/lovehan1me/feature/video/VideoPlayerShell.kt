/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.feature.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import lovehan1me.data.SettingsRepository
import lovehan1me.feature.player.PlaybackController
import lovehan1me.feature.player.PlaybackPhase
import lovehan1me.feature.player.PlaybackUiState
import lovehan1me.ui.theme.HanimeTheme
import lovehan1me.ui.theme.ThemeBoard
import lovehan1me.ui.theme.amoled
import lovehan1me.ui.theme.boardColorScheme
import lovehan1me.video.player.ui.ControllerVisibility
import lovehan1me.video.player.ui.PlaybackSpeedControllerState
import lovehan1me.video.player.ui.PlayerControllerState
import lovehan1me.video.player.ui.PlayerStateCards
import lovehan1me.video.player.ui.PlayerStatsOverlay
import lovehan1me.video.player.ui.VideoAspectRatioControllerState
import lovehan1me.video.player.ui.VideoLoadingIndicator
import lovehan1me.video.player.ui.VideoScaffold
import lovehan1me.video.player.ui.rememberAlwaysOnRequester
import lovehan1me.video.player.ui.rememberPlayerFullscreenState
import lovehan1me.video.player.ui.rememberPlayerStatsState
import lovehan1me.video.player.ui.rememberVideoControllerState
import lovehan1me.video.player.ui.toContractMode
import lovehan1me.video.player.ui.gesture.GestureIndicatorState
import lovehan1me.video.player.ui.gesture.GestureLock
import lovehan1me.video.player.ui.gesture.LevelController
import lovehan1me.video.player.ui.gesture.LockableVideoGestureHost
import lovehan1me.video.player.ui.gesture.MediampAudioLevelController
import lovehan1me.video.player.ui.gesture.NoOpLevelController
import lovehan1me.video.player.ui.gesture.SwipeSeekerConfig
import lovehan1me.video.player.ui.gesture.StateLevelController
import lovehan1me.video.player.ui.gesture.gestureFamilyOf
import lovehan1me.video.player.ui.gesture.hasPointerDevice
import lovehan1me.video.player.ui.gesture.mouseFamily
import lovehan1me.video.player.ui.gesture.rememberGestureIndicatorState
import lovehan1me.video.player.ui.gesture.rememberSwipeSeekerState
import lovehan1me.video.player.ui.progress.MediaProgressIndicatorText
import lovehan1me.video.player.ui.progress.MediaProgressSlider
import lovehan1me.video.player.ui.progress.MediaProgressSliderDefaults
import lovehan1me.video.player.ui.progress.PlayerControllerBar
import lovehan1me.video.player.ui.progress.PlayerControllerDefaults
import lovehan1me.video.player.ui.progress.ProgressSliderCenteredPreviewFrame
import lovehan1me.video.player.ui.progress.TouchSeekState
import lovehan1me.video.player.ui.progress.rememberMediaProgressFramePreviewState
import lovehan1me.video.player.ui.progress.rememberMediaProgressSliderState
import lovehan1me.video.player.ui.support.LocalActiveInputSource
import lovehan1me.video.player.ui.support.LocalPlatform
import lovehan1me.video.player.ui.support.isMobile
import lovehan1me.video.player.ui.top.PlayerTopBar
import lovehan1me.video.player.ui.top.SystemTime
import lovehan1me.video.ui.Res as PlayerRes
import lovehan1me.video.ui.gif_capture
import lovehan1me.video.ui.ic_panel_close
import lovehan1me.video.ui.ic_panel_open
import lovehan1me.video.ui.player_buffering
import lovehan1me.video.ui.player_collapse_sidebar
import lovehan1me.video.ui.player_expand_sidebar
import lovehan1me.video.ui.player_favorite
import lovehan1me.video.ui.player_favorited
import lovehan1me.video.ui.player_home
import lovehan1me.video.ui.player_more_options
import lovehan1me.video.ui.player_stats_title_hide
import lovehan1me.video.ui.player_stats_title_show
import lovehan1me.video.ui.screenshot
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.togglePlayWhenReady

/**
 * 播放器外壳：把 mediamp 的实时状态接到控件层，并决定每一层画什么。
 *
 * 状态走三条路，**不是疏漏**：
 * - 画面内的事实（位置、时长、是否在播、缓冲、生效画面比例）直读 [player]，控件因此与
 *   后端同帧刷新，拖进度条不会有"状态机还没跟上"的滞后；
 * - 用户意图（倍速、画面比例、画质、超分）走 [controller]，因为引擎每次开流都会丢画面类
 *   偏好，只有它记着"用户想要什么"并在 load 之后重下。
 * - 只有"画哪一层"才需要的那几个布尔/相位走 [playbackState]（`PlaybackUiState` 投影）。
 *   它刻意不含位置 —— 位置每个推送周期都变，进了参数就等于让本层跟着重组，而本层
 *   对位置没有任何诉求（上面第一条已把位置交给 [player] 直读）。
 * 于是每个选择器都是两段式：操作期间写 feature 出即时反馈，收尾回调 [controller] 落定。
 */
@Composable
fun VideoPlayerShell(
    player: MediampPlayer,
    controller: PlaybackController,
    playbackState: PlaybackUiState,
    videoSurface: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    isFullscreen: Boolean = false,
    showControls: Boolean = true,
    fullscreenEnabled: Boolean = true,
    gesturesEnabled: Boolean = true,
    contentWindowInsets: WindowInsets = WindowInsets.safeContent,
    title: String = "",
    cover: (@Composable BoxScope.() -> Unit)? = null,
    showLoading: Boolean = false,
    showResumeButton: Boolean = false,
    onFullscreenChange: (Boolean) -> Unit = {},
    onReplay: () -> Unit = {},
    onRetry: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    isFavVideo: Boolean = false,
    onToggleFavoriteVideo: () -> Unit = {},
    showSidebarToggle: Boolean = false,
    sidebarVisible: Boolean = true,
    onToggleSidebar: (Boolean) -> Unit = {},
    hasNextEpisode: Boolean = false,
    onClickNextEpisode: () -> Unit = {},
    onQualitySelected: (Int) -> Unit = {},
    enhancementLabel: String = "",
    enhancementOptions: List<String> = emptyList(),
    selectedEnhancementIndex: Int = 0,
    onEnhancementSelected: (Int) -> Unit = {},
    frameCaptureEnabled: Boolean = false,
    onOpenGifCapture: () -> Unit = {},
    onCaptureScreenshot: () -> Unit = {},
    danmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit = {},
    danmakuLayer: (@Composable BoxScope.() -> Unit)? = null,
    danmakuEditor: @Composable RowScope.() -> Unit = {},
    dialogHost: (@Composable () -> Unit)? = null,
    currentVolume: Float = 1f,
    onVolumeChange: (Float) -> Unit = {},
    brightnessGestureEnabled: Boolean = false,
    currentBrightness: Float = 0.5f,
    onBrightnessChange: (Float) -> Unit = {},
    fastForwardSpeed: Float = 3f,
) {
    val scope = rememberCoroutineScope()
    // animeko 的默认是"先藏起来，等鼠标动一下"。触屏端没有"鼠标动一下"这回事，
    // 进播放页看不到任何控件就是死屏 —— 所以进来先亮一次，之后交给手势层那 3 秒自动收起。
    val controllerState = rememberVideoControllerState(ControllerVisibility.Visible)
    // 刻意不 saveable：每次进播放页都该是解锁状态。
    var isLocked by remember { mutableStateOf(false) }
    var showPlayerStats by remember { mutableStateOf(false) }
    val playerStats by rememberPlayerStatsState(player)

    // PiP 没有帧循环驱动时钟，也没有可点的控件：手势与控件一并锁死
    // （VideoScaffold 的 gestureLocked 遮罩盖住顶栏/底栏/进度条/右侧键位列）。
    val gestureLocked = isLocked || !gesturesEnabled || !showControls
    val progressSliderState = rememberMediaProgressSliderState(
        player = player,
        onPreview = {},
        onPreviewFinished = player::seekTo,
    )
    val framePreview = rememberMediaProgressFramePreviewState(player)
    val sliderColors = MediaProgressSliderDefaults.colors()

    val playbackSpeedControllerState = remember(player, controller) {
        player.features[PlaybackSpeed]?.let { feature ->
            PlaybackSpeedControllerState(
                playbackSpeed = feature,
                onCommitSpeed = controller::setPlaybackSpeed,
                scope = scope,
            )
        }
    }
    val videoAspectRatioControllerState = remember(player, controller) {
        if (controller.supportsVideoAspect) {
            player.features[VideoAspectRatio]?.let { feature ->
                VideoAspectRatioControllerState(
                    videoAspectRatio = feature,
                    scope = scope,
                    onCommitMode = { controller.setVideoAspect(it.toContractMode()) },
                )
            }
        } else {
            null
        }
    }
    val fullscreenState = rememberPlayerFullscreenState(
        isFullscreen = { isFullscreen },
        onRequest = onFullscreenChange,
    )
    val audioController = rememberAudioController(player, currentVolume, onVolumeChange)
    val brightnessController = rememberBrightnessController(
        brightnessGestureEnabled,
        currentBrightness,
        onBrightnessChange,
    )

    val indicatorState = rememberGestureIndicatorState()
    val swipeSeekerConfig = SwipeSeekerConfig.Default
    val touchSeekState = rememberPlayerTouchSeekState(controllerState, indicatorState, swipeSeekerConfig)

    // 控件恒为深色场景：亮色主题下 onBackground 是深色，压在画面上等于隐形。
    // 只覆盖配色，不走 HanimeTheme(darkTheme = true) —— 那个会顺带重设系统栏样式，
    // 而系统栏归页面管（全屏进出由宿主改），播放器无权改它。
    // 只订主题四量（themeConfigFlow 已去重）：改弹幕字号之类无关写操作不再重组播放器。
    val theme by SettingsRepository.themeConfigFlow.collectAsStateWithLifecycle()
    val playerColorScheme = boardColorScheme(
        board = ThemeBoard.fromId(theme.themeId),
        // 深色 + 用户选的板子；预生成表查一次，不现场算调色板。
        isDark = true,
        contrastLevel = theme.contrastLevel.spec,
    ).let { if (theme.amoled) it.amoled() else it }

    HanimeTheme(colorScheme = playerColorScheme) {
        VideoScaffold(
            expanded = expanded,
            modifier = modifier,
            contentWindowInsets = contentWindowInsets,
            // 画面框多大归页面定（窄屏的框高会被窗高夹住，套一层 16:9 反而溢出被裁），
            // 这里只铺满拿到的框；渲染面自己按画面比例留黑边。
            maintainAspectRatio = false,
            controllerState = controllerState,
            gestureLocked = gestureLocked,
            video = {
                // 海报垫在渲染面**之下**：首帧没到时它是看得见的那一层，到了就被画面盖住。
                if (!playbackState.hasRenderedFirstFrame) cover?.invoke(this)
                videoSurface()
            },
            danmakuHost = {
                if (danmakuLayer != null) {
                    DanmakuVisibility(visible = danmakuEnabled, content = danmakuLayer)
                }
            },
            topBar = {
                PlayerTopBar(
                    onBackClick = onBackClick,
                    title = if (expanded) {
                        { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        null
                    },
                    actions = {
                        PlayerTopBarActions(
                            controllerState = controllerState,
                            expanded = expanded,
                            isFavVideo = isFavVideo,
                            onToggleFavoriteVideo = onToggleFavoriteVideo,
                            onHomeClick = onHomeClick,
                            showSidebarToggle = showSidebarToggle,
                            sidebarVisible = sidebarVisible,
                            onToggleSidebar = onToggleSidebar,
                            showPlayerStats = showPlayerStats,
                            onTogglePlayerStats = { showPlayerStats = !showPlayerStats },
                            frameCaptureEnabled = frameCaptureEnabled,
                            onCaptureScreenshot = onCaptureScreenshot,
                            onOpenGifCapture = onOpenGifCapture,
                        )
                    },
                    // VideoScaffold 已经在 topBar 外套过 insets，再传一遍就是双份状态栏高度。
                    windowInsets = WindowInsets(0.dp),
                )
            },
            centerOverlay = {
                if (expanded && LocalPlatform.current.isMobile()) {
                    SystemTime()
                }
            },
            gestureHost = {
                val seekerState = rememberSwipeSeekerState(
                    constraints.maxWidth,
                    swipeSeekerConfig,
                ) { player.skip(it * 1000L) }
                val durationMillis by remember(player) {
                    player.mediaProperties.map { it?.durationMillis ?: 0L }
                }.collectAsStateWithLifecycle(0L)
                val onTogglePauseResume by rememberUpdatedState<() -> Unit>(
                    newValue = {
                        // 先翻状态再亮 HUD：showPausedLong 要挂起整个显示时长，
                        // 把它排在 toggle 之前会让点击延迟几百毫秒。
                        val wasPlaying = player.state.value.playWhenReady
                        player.togglePlayWhenReady()
                        scope.launch {
                            if (wasPlaying) {
                                indicatorState.showPausedLong()
                            } else {
                                indicatorState.showResumedLong()
                            }
                        }
                    },
                )
                LockableVideoGestureHost(
                    controllerState = controllerState,
                    seekerState = seekerState,
                    progressSliderState = progressSliderState,
                    playerState = player,
                    locked = gestureLocked,
                    // 时长还没报出来时不许滑 seek：seek 进一个还不知道有多长的流是假动作。
                    enableSwipeToSeek = durationMillis != 0L,
                    audioController = audioController,
                    brightnessController = brightnessController,
                    playbackSpeedControllerState = playbackSpeedControllerState,
                    fullscreenState = fullscreenState,
                    onTogglePauseResume = onTogglePauseResume,
                    onToggleDanmaku = onToggleDanmaku,
                    onTogglePlayerStats = { showPlayerStats = !showPlayerStats },
                    family = gestureFamilyOf(
                        LocalActiveInputSource.current.current,
                        LocalPlatform.current.mouseFamily,
                    ),
                    gestureIndicatorState = indicatorState,
                    fastForwardSpeed = fastForwardSpeed,
                )
            },
            playerStatsOverlay = {
                if (showControls && showPlayerStats) {
                    PlayerStatsOverlay(playerStats)
                }
            },
            floatingMessage = {
                if (showLoading) {
                    VideoLoadingIndicator(
                        showProgress = true,
                        text = { Text(stringResource(PlayerRes.string.player_buffering)) },
                    )
                }
                PlayerStateCards(
                    showResumeButton = showResumeButton,
                    isPlaybackEnded = playbackState.phase == PlaybackPhase.Ended,
                    showRetry = playbackState.phase == PlaybackPhase.Error,
                    isLocked = gestureLocked,
                    errorMessage = playbackState.errorMessage,
                    onResumeClick = onResumeClick,
                    onReplay = onReplay,
                    onRetry = onRetry,
                )
            },
            framePreviewOverlay = {
                if (!expanded) {
                    ProgressSliderCenteredPreviewFrame(
                        frame = framePreview?.frame,
                        borderColor = sliderColors.previewTimeBackgroundColor,
                    )
                }
            },
            // 右侧只留手势锁。截图原本也挂在这里，与顶栏那个是同一个动作、同一个图标，
            // 同屏出现两个一模一样的相机键 —— 现统一由顶栏「更多」菜单出。
            gestureLock = {
                if (showControls && expanded) {
                    GestureLock(isLocked = isLocked, onClick = { isLocked = !isLocked })
                }
            },
            bottomBar = {
                PlayerControllerBar(
                    startActions = {
                        val playWhenReady by remember(player) {
                            player.state.map { it.playWhenReady }
                        }.collectAsStateWithLifecycle(false)
                        PlayerControllerDefaults.PlaybackIcon(
                            isPlaying = { playWhenReady },
                            onClick = player::togglePlayWhenReady,
                        )
                        if (hasNextEpisode && expanded) {
                            PlayerControllerDefaults.NextEpisodeIcon(onClick = onClickNextEpisode)
                        }
                        // 常驻音量键要求"能静音"：Exo / AVKit 没有 AudioLevelController feature，
                        // 与其摆一个点了什么也不会发生的按钮，不如不摆。
                        val mediampAudio = audioController as? MediampAudioLevelController
                        if (
                            expanded && mediampAudio != null &&
                            hasPointerDevice(
                                LocalPlatform.current,
                                LocalActiveInputSource.current.hasSeenMouse,
                            )
                        ) {
                            val level by mediampAudio.levelFlow.collectAsStateWithLifecycle()
                            val isMute by mediampAudio.muteFlow.collectAsStateWithLifecycle()
                            PlayerControllerDefaults.AudioIcon(
                                level,
                                isMute = isMute,
                                maxValue = mediampAudio.range.endInclusive,
                                onClick = mediampAudio::toggleMute,
                                onchange = mediampAudio::setLevel,
                                controllerState = controllerState,
                            )
                        }
                        // 弹幕开关排在音量之后：音量是"有指针设备才摆"的条件位，
                        // 排它前面会让开关的位置随设备类型左右横跳。
                        PlayerControllerDefaults.DanmakuIcon(danmakuEnabled, onClick = onToggleDanmaku)
                    },
                    progressIndicator = {
                        MediaProgressIndicatorText(
                            state = progressSliderState,
                            playbackSpeedState = playbackSpeedControllerState,
                        )
                    },
                    progressSlider = {
                        MediaProgressSlider(
                            state = progressSliderState,
                            colors = sliderColors,
                            showPreviewTimeTextOnThumb = expanded,
                            framePreview = framePreview,
                            showFramePreviewInPopup = expanded,
                            touchSeekState = touchSeekState,
                        )
                    },
                    danmakuEditor = danmakuEditor,
                    endActions = {
                        if (expanded) {
                            val aspectRequester = rememberAlwaysOnRequester(controllerState, "aspectSelector")
                            videoAspectRatioControllerState?.also { state ->
                                PlayerControllerDefaults.VideoAspectRatioSelector(state) {
                                    if (it) aspectRequester.request() else aspectRequester.cancelRequest()
                                }
                            }
                            val speedRequester = rememberAlwaysOnRequester(controllerState, "speedSwitcher")
                            playbackSpeedControllerState?.also { state ->
                                PlayerControllerDefaults.SpeedSwitcher(state) {
                                    if (it) speedRequester.request() else speedRequester.cancelRequest()
                                }
                            }
                            if (enhancementOptions.size > 1) {
                                PlayerControllerDefaults.OptionsSwitcher(
                                    value = selectedEnhancementIndex,
                                    onValueChange = onEnhancementSelected,
                                    optionsProvider = { enhancementOptions.indices.toList() },
                                    renderValue = { Text(enhancementOptions.getOrElse(it) { enhancementLabel }) },
                                )
                            }
                            PlayerControllerDefaults.OptionsSwitcher(
                                value = playbackState.selectedQualityIndex,
                                onValueChange = onQualitySelected,
                                optionsProvider = { playbackState.qualities.indices.toList() },
                                renderValue = { index ->
                                    Text(playbackState.qualities.getOrNull(index)?.label.orEmpty())
                                },
                            )
                        }
                        if (fullscreenEnabled) {
                            PlayerControllerDefaults.FullscreenIcon(fullscreenState)
                        }
                    },
                    expanded = expanded,
                    sliderOnly = controllerState.visibility == ControllerVisibility.InlineSliderOnly,
                )
            },
            detachedProgressSlider = {
                MediaProgressSlider(
                    state = progressSliderState,
                    colors = sliderColors,
                    enabled = false,
                    framePreview = framePreview,
                    showFramePreviewInPopup = expanded,
                )
            },
            floatingBottomEnd = {
                // gestureLocked 的遮罩不盖这一层（它盖的是随控件一起隐藏的顶/底栏），
                // 所以 PiP 与"平台不支持全屏"都要在这里自己收掉。
                if (showControls && fullscreenEnabled) {
                    PlayerControllerDefaults.FullscreenIcon(fullscreenState)
                }
            },
        )

        // 弹窗不许挂在底栏里：底栏随控件自动隐藏被整棵销毁，弹窗会跟着没。
        dialogHost?.invoke()
    }
}

/**
 * 顶栏右侧动作。
 *
 * 倍速 / 画面比例 / 画质 / 超分在 [expanded] 时归底栏（animeko 的位置），窄屏底栏放不下，
 * 所以这几个不进顶栏 —— 窄屏顶栏只留"看这片子"需要的动作。
 */
@Composable
private fun RowScope.PlayerTopBarActions(
    controllerState: PlayerControllerState,
    expanded: Boolean,
    isFavVideo: Boolean,
    onToggleFavoriteVideo: () -> Unit,
    onHomeClick: () -> Unit,
    showSidebarToggle: Boolean,
    sidebarVisible: Boolean,
    onToggleSidebar: (Boolean) -> Unit,
    showPlayerStats: Boolean,
    onTogglePlayerStats: () -> Unit,
    frameCaptureEnabled: Boolean,
    onCaptureScreenshot: () -> Unit,
    onOpenGifCapture: () -> Unit,
) {
    val dropdownRequester = rememberAlwaysOnRequester(controllerState, "topBarActions")
    IconButton(
        onClick = {
            if (isFavVideo) dropdownRequester.cancelRequest()
            onToggleFavoriteVideo()
        },
    ) {
        Icon(
            imageVector = if (isFavVideo) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavVideo) PlayerRes.string.player_favorited else PlayerRes.string.player_favorite,
            ),
        )
    }
    // 低频动作收进「更多」：截图 / 录 GIF / 播放统计 三件事同屏并列，
    // 会把顶栏变成一排同权重图标，读不出主次。收进菜单后顶栏只剩四个：
    // 收藏 · 更多 · 抽屉 · 主页。
    if (frameCaptureEnabled || expanded) {
        var moreOpen by remember { mutableStateOf(false) }
        val moreRequester = rememberAlwaysOnRequester(controllerState, "topBarMore")
        // 菜单开着时不能按 3 秒倒计时收起控件 —— 收了菜单会跟着整棵消失。
        LaunchedEffect(moreOpen) {
            if (moreOpen) moreRequester.request() else moreRequester.cancelRequest()
        }
        Box {
            IconButton(onClick = { moreOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(PlayerRes.string.player_more_options),
                )
            }
            DropdownMenu(
                expanded = moreOpen,
                onDismissRequest = { moreOpen = false },
            ) {
                if (frameCaptureEnabled) {
                    DropdownMenuItem(
                        text = { Text(stringResource(PlayerRes.string.screenshot)) },
                        leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                        onClick = {
                            moreOpen = false
                            onCaptureScreenshot()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(PlayerRes.string.gif_capture)) },
                        leadingIcon = { Icon(Icons.Rounded.Movie, contentDescription = null) },
                        onClick = {
                            moreOpen = false
                            onOpenGifCapture()
                        },
                    )
                }
                if (expanded) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (showPlayerStats) {
                                        PlayerRes.string.player_stats_title_hide
                                    } else {
                                        PlayerRes.string.player_stats_title_show
                                    },
                                ),
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            moreOpen = false
                            onTogglePlayerStats()
                        },
                    )
                }
            }
        }
    }
    if (showSidebarToggle) {
        // 抽屉按钮用"面板 + 箭头"的专用图形，而不是裸 Chevron：裸箭头只说方向，
        // 说不清点它动的是什么；侧栏在画面的右侧，所以开/关各有一张镜像图。
        IconButton(onClick = { onToggleSidebar(!sidebarVisible) }) {
            Icon(
                painter = painterResource(
                    if (sidebarVisible) PlayerRes.drawable.ic_panel_close
                    else PlayerRes.drawable.ic_panel_open,
                ),
                contentDescription = stringResource(
                    if (sidebarVisible) {
                        PlayerRes.string.player_collapse_sidebar
                    } else {
                        PlayerRes.string.player_expand_sidebar
                    },
                ),
            )
        }
    }
    IconButton(onClick = onHomeClick) {
        Icon(Icons.Rounded.Home, contentDescription = stringResource(PlayerRes.string.player_home))
    }
}

/**
 * 音量：优先用后端自己的 feature（有真实音量与静音状态可回报），退化到宿主的一个本地状态。
 *
 * 退化路径必须是 `MutableFloatState` 而不是普通值：控制器身份要稳定（滑动修饰符按它挂载，
 * 换身份会打断正在进行的拖动），而读到的值必须是当下最新。
 */
@Composable
private fun rememberAudioController(
    player: MediampPlayer,
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
): LevelController {
    val onVolume by rememberUpdatedState(onVolumeChange)
    val hostVolume = remember { mutableFloatStateOf(currentVolume) }
    LaunchedEffect(currentVolume) {
        // 宿主改了音量（例如设置页恢复默认）要能被这里读到，否则下次滑动从旧值起算。
        if (hostVolume.floatValue != currentVolume) hostVolume.floatValue = currentVolume
    }
    val fallback = remember(hostVolume, onVolume) {
        StateLevelController(hostVolume) { level ->
            hostVolume.floatValue = level
            onVolume(level)
        }
    }
    return remember(player, fallback, onVolume) {
        player.features[AudioLevelController]
            ?.let { MediampAudioLevelController(it) { level, _ -> onVolume(level) } }
            ?: fallback
    }
}

/** 亮度只有真能改的端才接管手势；给一个假控制器会让 HUD 弹而屏幕不动。 */
@Composable
private fun rememberBrightnessController(
    enabled: Boolean,
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
): LevelController {
    if (!enabled) return NoOpLevelController
    val onChange by rememberUpdatedState(onBrightnessChange)
    val level = remember { mutableFloatStateOf(currentBrightness) }
    LaunchedEffect(currentBrightness) {
        if (level.floatValue != currentBrightness) level.floatValue = currentBrightness
    }
    return remember(level, onChange) {
        StateLevelController(level) { value ->
            level.floatValue = value
            onChange(value)
        }
    }
}

/**
 * 把进度条的触摸状态机接到播放器 UI：拖动期间保留 inline 进度条，
 * 手指上滑过取消阈值时持续显示取消提示。
 *
 * 状态始终存在，是否响应触摸由 [MediaProgressSlider] 按本次指针事件判断 —— 否则切换输入设备后的
 * 第一次拖动会受组合期算出的手势约定影响。
 */
@Composable
private fun rememberPlayerTouchSeekState(
    controllerState: PlayerControllerState,
    indicatorState: GestureIndicatorState,
    swipeSeekerConfig: SwipeSeekerConfig,
): TouchSeekState {
    val density = LocalDensity.current
    return remember(controllerState, indicatorState, swipeSeekerConfig, density) {
        // 同一 TouchSeekState 生命周期内，每次请求都由固定 requester 和固定 indicator 票号撤销。
        val controllerRequester = Any()
        var indicatorTicket: Int? = null
        fun stopCancellationIndicator() {
            indicatorTicket?.let(indicatorState::stopSeekCancellation)
            indicatorTicket = null
        }
        TouchSeekState(
            swipeSeekerConfig = swipeSeekerConfig,
            density = density,
            onStateChanged = { state ->
                when (state) {
                    TouchSeekState.State.Idle -> {
                        controllerState.cancelRequestInlineProgressSlider(controllerRequester)
                        stopCancellationIndicator()
                    }

                    TouchSeekState.State.Seeking -> {
                        controllerState.setRequestInlineProgressSlider(controllerRequester)
                        stopCancellationIndicator()
                    }

                    TouchSeekState.State.Cancelling -> {
                        indicatorTicket = indicatorState.startSeekCancellation()
                    }
                }
            },
        )
    }
}

/**
 * 弹幕层的显隐。铺满播放器区由 `VideoScaffold` 的 danmakuHost 槽负责，这里只管淡入淡出。
 *
 * 用 alpha 而不是 `AnimatedVisibility`：后者的容器是 `wrapContentSize`，里面的
 * `matchParentSize` 拿到的是"内容自己有多大"而不是播放器区，弹幕会钉在左上角。
 */
@Composable
private fun BoxScope.DanmakuVisibility(
    visible: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val danmakuAlpha by animateFloatAsState(if (visible) 1f else 0f, label = "danmakuVisibility")
    if (danmakuAlpha <= 0f) return
    Box(
        modifier = Modifier.matchParentSize().graphicsLayer { alpha = danmakuAlpha },
        content = content,
    )
}
