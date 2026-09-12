package lovehan1me.feature.video

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import lovehan1me.ui.adaptive.rememberRelatedPaneWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
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
import lovehan1me.data.database.entity.HKeyframeEntity
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.ui.component.rememberHapticFeedback
import kotlin.math.roundToInt
import lovehan1me.ui.theme.HanimeDefaults

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
    hKeyframeLabel: String,
    isHKeyframesEnabled: Boolean,
    hKeyframeOptions: List<String>,
    hKeyframes: List<HKeyframeEntity.Keyframe>,
    isHKeyframeLocal: Boolean,
    onHKeyframeSelected: (Int) -> Unit,
    onHKeyframeUpdated: (HKeyframeEntity.Keyframe, HKeyframeEntity.Keyframe) -> Unit,
    onHKeyframeDeleted: (HKeyframeEntity.Keyframe) -> Unit,
    onHKeyframeLongPress: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onProgressGesture: (Float) -> Unit,
    progressGestureSensitivity: Float,
    countdownLabel: String?,
    videoAspectRatio: Float,
    onPlayerBoundsChanged: (Rect) -> Unit,
    tabsContent: @Composable () -> Unit,
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

    @Composable
    fun PlayerContent(modifier: Modifier) {
        Box(modifier = modifier) {
            if (!isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(Color.Black)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isFullscreen) Modifier.statusBarsPadding() else Modifier)
            ) {
                VideoPlayerUi(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            onPlayerBoundsChanged(coordinates.boundsInWindow())
                        },
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
                    hKeyframeLabel = hKeyframeLabel,
                    isHKeyframesEnabled = isHKeyframesEnabled,
                    hKeyframeOptions = hKeyframeOptions,
                    hKeyframes = hKeyframes,
                    isHKeyframeLocal = isHKeyframeLocal,
                    onHKeyframeSelected = onHKeyframeSelected,
                    onHKeyframeUpdated = onHKeyframeUpdated,
                    onHKeyframeDeleted = onHKeyframeDeleted,
                    onHKeyframeLongPress = onHKeyframeLongPress,
                    onLongPressStart = onLongPressStart,
                    onLongPressEnd = onLongPressEnd,
                    onVolumeChange = onVolumeChange,
                    onBrightnessChange = onBrightnessChange,
                    onProgressGesture = onProgressGesture,
                    progressGestureSensitivity = progressGestureSensitivity,
                    countdownLabel = countdownLabel,
                    videoAspectRatio = videoAspectRatio,
                )
            }
        }
    }

    @Composable
    fun MainContent(contentModifier: Modifier) {
        Box(modifier = contentModifier) {
            if (!isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(Color.Black)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isFullscreen) Modifier.statusBarsPadding() else Modifier)
            ) {
                VideoPlayerUi(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!isFullscreen && playerHeightDp != null) {
                                Modifier.height(playerHeightDp)
                            } else {
                                Modifier.weight(1f)
                            }
                        )
                        .onGloballyPositioned { coordinates ->
                            onPlayerBoundsChanged(coordinates.boundsInWindow())
                        },
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
                    hKeyframeLabel = hKeyframeLabel,
                    isHKeyframesEnabled = isHKeyframesEnabled,
                    hKeyframeOptions = hKeyframeOptions,
                    hKeyframes = hKeyframes,
                    isHKeyframeLocal = isHKeyframeLocal,
                    onHKeyframeSelected = onHKeyframeSelected,
                    onHKeyframeUpdated = onHKeyframeUpdated,
                    onHKeyframeDeleted = onHKeyframeDeleted,
                    onHKeyframeLongPress = onHKeyframeLongPress,
                    onLongPressStart = onLongPressStart,
                    onLongPressEnd = onLongPressEnd,
                    onVolumeChange = onVolumeChange,
                    onBrightnessChange = onBrightnessChange,
                    onProgressGesture = onProgressGesture,
                    progressGestureSensitivity = progressGestureSensitivity,
                    countdownLabel = countdownLabel,
                    videoAspectRatio = videoAspectRatio,
                )
                if (!isInPipMode && !isFullscreen) {
                    Box(modifier = Modifier.weight(1f)) {
                        tabsContent()
                    }
                }
            }
        }
    }

    if (showClassicSideRelated) {
        val indicatorWidth = 28.dp
        // P5：固定像素宽度，不再按 `maxWidth * 0.38f` 随窗口比例放大。
        val relatedPaneWidth = rememberRelatedPaneWidth()
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val sideWidth by animateDpAsState(
                targetValue = if (isSideRelatedCollapsed) indicatorWidth else relatedPaneWidth,
                // P6：宽度是空间属性，走 spatial 档而非硬编码 tween(300)。
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "sideRelatedWidth",
            )
            Row(modifier = Modifier.fillMaxSize()) {
                MainContent(
                    contentModifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Row(
                    modifier = Modifier
                        .width(sideWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
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
            }
        }
    } else if (showSideRelated) {
        Row(modifier = modifier.fillMaxSize()) {
            PlayerContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            // P5：固定 360dp（内容宽 < 1000dp 时 320dp）。原 `fillMaxWidth(0.38f)`
            // 在 2560dp 窗口下会变成 973dp 的巨型侧栏，注意力被完全拉走。
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .consumeWindowInsets(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                    )
                    .background(MaterialTheme.colorScheme.background)
                    .width(rememberRelatedPaneWidth()),
            ) {
                tabsContent()
            }
        }
    } else {
        MainContent(contentModifier = modifier.fillMaxSize())
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
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
