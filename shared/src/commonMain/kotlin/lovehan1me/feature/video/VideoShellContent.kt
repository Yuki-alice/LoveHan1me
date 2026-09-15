package lovehan1me.feature.video

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import lovehan1me.ui.adaptive.rememberRelatedPaneWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.ic_chevron_left
import lovehan1me.ic_chevron_right
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.ui.component.rememberHapticFeedback
import kotlin.math.roundToInt
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.HanimeTheme

data class ClassicTabletLayoutConfig(
    val relatedItems: List<HanimeInfo>,
    val onHideRelatedInIntroChange: (Boolean) -> Unit,
    val onSideRelatedCollapsedChange: (Boolean) -> Unit,
    val onOpenVideo: (HanimeInfo) -> Unit,
)

@Composable
fun VideoShellContent(
    /** 双栏（内容 + 侧栏）是否启用。P0 起由调用方按**内容区可用宽度 ≥ 840dp** 计算。 */
    isDualPane: Boolean,
    isInPipMode: Boolean,
    isFullscreen: Boolean,
    playerHeightDp: Dp?,
    playbackEngine: PlaybackEngine,
    posterUrl: String?,
    // 与列表页卡片封面配对的共享元素 key（null = 不做过渡）
    sharedElementKey: String? = null,
    title: String,
    currentTime: String,
    totalTime: String,
    progress: Float,
    bufferedProgress: Float,
    currentVolume: Float,
    currentBrightness: Float,
    isPlaying: Boolean,
    isPlaybackEnded: Boolean,
    isLocked: Boolean,
    showPoster: Boolean,
    showLoading: Boolean,
    showRetry: Boolean,
    showResumeButton: Boolean,
    onPlayClick: () -> Unit,
    onReplay: () -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onLockClick: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onRetry: () -> Unit,
    onResumeClick: () -> Unit,
    qualities: List<PlaybackQuality>,
    selectedQuality: String?,
    onQualitySelected: (Int) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedSelected: (Float) -> Unit,
    superResolutionLabel: String,
    superResolutionOptions: List<String>,
    selectedSuperResolutionIndex: Int,
    onSuperResolutionSelected: (Int) -> Unit,
    /** M3-b/M3-c：是否显示「截图 / 录 GIF」入口（= controller.supportsFrameCapture）。 */
    frameCaptureEnabled: Boolean,
    /** M3-b：点「录 GIF」的回调。 */
    onOpenGifCapture: () -> Unit,
    /** M3-c：点「截图」的回调。 */
    onCaptureScreenshot: () -> Unit,
    /** 播放失败的真实原因，透传给播放器重试卡。 */
    errorMessage: String?,
    /** 左半屏竖滑调亮度是否真的生效（桌面/iOS 无亮度 API → false，UI 不接管该手势）。 */
    brightnessGestureEnabled: Boolean,
    /** M5-3：双击左右快退/快进的相对跳转。 */
    onSeekBy: (Long) -> Unit,
    /** 画面缩放倍率（1f = 原始尺寸）。 */
    scale: Float = 1f,
    onScaleChange: (Float) -> Unit = {},
    /** M5-3：视频总时长（双击 HUD 换算百分比用）。 */
    durationMs: Long,
    /** 平台是否支持全屏（iOS 未实现 → false 时隐藏入口）。 */
    fullscreenEnabled: Boolean,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onProgressGesture: (Float) -> Unit,
    progressGestureSensitivity: Float,
    videoAspectRatio: Float,
    onPlayerBoundsChanged: (Rect) -> Unit,
    tabsContent: @Composable () -> Unit,
    /**
     * 宽屏右栏 Tab（相关推荐｜评论），null = 回退到 [tabsContent]。
     * 窄屏/经典双栏传 null。
     */
    railTabsContent: (@Composable () -> Unit)? = null,
    classicTabletLayout: ClassicTabletLayoutConfig?,
    modifier: Modifier = Modifier,
) {
    // P0：不再要求「横屏」。原 `isTabletMode && isLandscapeOrientation()` 有两个问题：
    //   1) iOS 侧 `isLandscapeOrientation()` 恒为 false → iPad 永远拿不到双栏；
    //   2) 双栏与否本应由宽度决定（横屏手机的宽度天然超过阈值，方向语义已被宽度蕴含）。
    val showSideRelated = isDualPane && !isInPipMode && !isFullscreen
    val showClassicSideRelated = showSideRelated && classicTabletLayout != null
    var isSideRelatedCollapsed by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(showClassicSideRelated) {
        if (showClassicSideRelated) {
            classicTabletLayout.onHideRelatedInIntroChange.invoke(true)
        }
        onDispose {
            if (showClassicSideRelated) {
                classicTabletLayout.onHideRelatedInIntroChange.invoke(false)
                classicTabletLayout.onSideRelatedCollapsedChange.invoke(false)
            }
        }
    }

    LaunchedEffect(showClassicSideRelated, isSideRelatedCollapsed) {
        if (!showClassicSideRelated) isSideRelatedCollapsed = false
        classicTabletLayout?.onSideRelatedCollapsedChange?.invoke(
            showClassicSideRelated && isSideRelatedCollapsed
        )
    }

    // 播放器实例跨布局分支复用（Compose movableContentOf）：
    // 原先 PlayerContent / MainContent 各写一次 VideoPlayerUi，窗口尺寸变化导致布局分支
    // 切换时会重建播放内核（黑帧 + 进度丢失）；现在同样的组合内容在分支间"移动"。
    // 两处 53 个参数完全一致、只有 modifier 不同，所以 modifier 作为唯一入参。
    @Composable
    fun PlayerBox(playerModifier: Modifier) {
        // R3：播放器强制暗色（学 animeko）：叠层/控件与浅色主题解耦，
        // 省掉浅色下整套叠层 token。
        HanimeTheme(darkTheme = true) {
            VideoPlayerUi(
                modifier = playerModifier,
            playbackEngine = playbackEngine,
            posterUrl = posterUrl,
            title = title,
            currentTime = currentTime,
            totalTime = totalTime,
            progress = progress,
            bufferedProgress = bufferedProgress,
            currentVolume = currentVolume,
            currentBrightness = currentBrightness,
            isFullscreen = isFullscreen,
            isPlaying = isPlaying,
            isPlaybackEnded = isPlaybackEnded,
            isLocked = isLocked || isInPipMode,
            showPoster = showPoster,
            sharedElementKey = sharedElementKey,
            showControls = !isInPipMode,
            showLoading = showLoading,
            showRetry = showRetry,
            showResumeButton = showResumeButton,
            onPlayClick = onPlayClick,
            onReplay = onReplay,
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            onFullscreenClick = onFullscreenClick,
            onLockClick = onLockClick,
            onProgressChange = onProgressChange,
            onRetry = onRetry,
            onResumeClick = onResumeClick,
            qualities = qualities,
            selectedQuality = selectedQuality,
            onQualitySelected = onQualitySelected,
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedSelected = onPlaybackSpeedSelected,
            superResolutionLabel = superResolutionLabel,
            superResolutionOptions = superResolutionOptions,
            selectedSuperResolutionIndex = selectedSuperResolutionIndex,
            onSuperResolutionSelected = onSuperResolutionSelected,
            frameCaptureEnabled = frameCaptureEnabled,
            onOpenGifCapture = onOpenGifCapture,
            onCaptureScreenshot = onCaptureScreenshot,
            errorMessage = errorMessage,
            brightnessGestureEnabled = brightnessGestureEnabled,
            onSeekBy = onSeekBy,
            scale = scale,
            onScaleChange = onScaleChange,
            durationMs = durationMs,
            fullscreenEnabled = fullscreenEnabled,
            onLongPressStart = onLongPressStart,
            onLongPressEnd = onLongPressEnd,
            onVolumeChange = onVolumeChange,
            onBrightnessChange = onBrightnessChange,
            onProgressGesture = onProgressGesture,
            progressGestureSensitivity = progressGestureSensitivity,
            videoAspectRatio = videoAspectRatio,
        )
    }

    // R1 单组合路径：下面三块（播放器 / 下方内容 / 右栏）按模式显隐，
    // VideoPlayerUi 只出现一次——窗口跨断点、进出全屏都不移动、不重建实例。
    // （此前 movableContentOf 跨分支搬运 + 全屏/PiP 换分支，渲染面反复 detach。）
    val fullBleedPlayer = isFullscreen || isInPipMode
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 右栏宽：经典=可折叠动画宽；普通双栏=总宽 25% clamp 340–460（学 animeko）；
        // 其余（单栏/全屏/PiP）无右栏。
        val normalRailWidth = (maxWidth * 0.25f).coerceIn(340.dp, 460.dp)
        val indicatorWidth = 28.dp
        val classicRailWidth by animateDpAsState(
            targetValue = if (isSideRelatedCollapsed) indicatorWidth else rememberRelatedPaneWidth(),
            // P6：宽度是空间属性，走 spatial 档而非硬编码 tween(300)。
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "sideRelatedWidth",
        )
        val railWidth: Dp? = when {
            showClassicSideRelated -> classicRailWidth
            showSideRelated -> normalRailWidth
            else -> null
        }
        val mainWidth = if (railWidth != null) maxWidth - railWidth else maxWidth

        if (!isFullscreen) {
            // 状态栏底衬（黑色）：原 PlayerContent/MainContent 逐分支各画一条，收拢到一处。
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color.Black)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isFullscreen) Modifier.statusBarsPadding() else Modifier)
        ) {
            // 1. 播放器：唯一实例。尺寸按模式给，外框变化不触碰实例。
            PlayerBox(
                Modifier
                    .align(Alignment.TopStart)
                    .then(
                        if (fullBleedPlayer || playerHeightDp == null) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .width(mainWidth)
                                .height(playerHeightDp)
                        }
                    )
                    .onGloballyPositioned { coordinates ->
                        onPlayerBoundsChanged(coordinates.boundsInWindow())
                    },
            )
            // 2. 下方内容：单栏/双栏左列/经典左列的简介 Tab（全屏/PiP 无）。
            if (!fullBleedPlayer && playerHeightDp != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = playerHeightDp)
                        .width(mainWidth)
                        .fillMaxHeight()
                ) {
                    tabsContent()
                }
            }
            // 3. 右栏：经典=可折叠相关推荐；普通双栏=相关/评论 Tab。
            if (railWidth != null) {
                if (showClassicSideRelated) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(railWidth)
                            .fillMaxHeight()
                            .background(HanimeDefaults.Colors.pageSurface)
                    ) {
                        RelatedCollapseIndicator(
                            collapsed = isSideRelatedCollapsed,
                            onClick = { isSideRelatedCollapsed = !isSideRelatedCollapsed },
                            modifier = Modifier
                                .width(indicatorWidth)
                                .fillMaxHeight(),
                        )
                        if (!isSideRelatedCollapsed) {
                            RelatedVideosSection(
                                videos = classicTabletLayout.relatedItems,
                                onOpenVideo = classicTabletLayout.onOpenVideo,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(railWidth)
                            .fillMaxHeight()
                            .consumeWindowInsets(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                            )
                            .background(HanimeDefaults.Colors.pageSurface),
                    ) {
                        // 右栏 Tab（相关推荐｜评论）；null 回退旧行为（简介/评论 Tab）。
                        (railTabsContent ?: tabsContent)()
                    }
                }
            }
}
        }
    }
}

@Composable
private fun RelatedCollapseIndicator(
    collapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()
    Row(
        modifier = modifier
            .clickable {
                haptic()
                onClick()
            }
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = if (collapsed) {
                painterResource(Res.drawable.ic_chevron_left)
            } else {
                painterResource(Res.drawable.ic_chevron_right)
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .clip(HanimeDefaults.Corners.pill)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        )
    }
}
