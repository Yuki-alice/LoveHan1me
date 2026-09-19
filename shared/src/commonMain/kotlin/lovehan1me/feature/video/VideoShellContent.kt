package lovehan1me.feature.video

import androidx.compose.foundation.background
import lovehan1me.ui.adaptive.rememberRelatedPaneWidth
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.ui.theme.HanimeDefaults

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
    /** 下一集（系列视频才有，null = 不显示）。只在 B 站风底栏使用。 */
    onNextClick: (() -> Unit)? = null,
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
    /**
     * 弹幕绘制层插槽（null = 不画）。
     *
     * PiP 在这里统一折算成 null，而不是让每个调用方记得判断：画中画只有几百分宽，
     * 弹幕上去就是一糊，而且 PiP 期间没有帧循环驱动时钟，弹幕会定在原地。
     */
    danmakuLayer: (@Composable () -> Unit)? = null,
    /**
     * 弹幕状态条插槽（底栏中间位）。null = 该位置留空。
     *
     * 与 [danmakuLayer] 一起在 PiP 下被收掉：PiP 的底栏不是这套控件。
     */
    danmakuControls: (@Composable () -> Unit)? = null,
    tabsContent: @Composable () -> Unit,
    /**
     * 宽屏右栏 Tab（详情｜评论），null = 回退到 [tabsContent]。
     * 窄屏/经典双栏传 null。弹幕相关控件在播放器底栏（见 [danmakuControls]），不挂这里。
     */
    railTabsContent: (@Composable () -> Unit)? = null,
    /**
     * animeko 右栏折叠（EpisodeVideo `sidebarVisible`）：宽屏下顶栏的折叠按钮切换它。
     * false → 右栏隐藏、左列播放器占满整宽（全屏/PiP 语义不变）。
     * 默认 true，保持老行为零变化。
     */
    sidebarVisible: Boolean = true,
    onToggleSidebar: (Boolean) -> Unit = {},
    /** 顶栏收藏心（对齐 Kazumi 顶栏 collect 键）。 */
    isFavVideo: Boolean = false,
    onToggleFavoriteVideo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // P0：不再要求「横屏」。原 `isTabletMode && isLandscapeOrientation()` 有两个问题：
    //   1) iOS 侧 `isLandscapeOrientation()` 恒为 false → iPad 永远拿不到双栏；
    //   2) 双栏与否本应由宽度决定（横屏手机的宽度天然超过阈值，方向语义已被宽度蕴含）。
    // 对齐 animeko `EpisodeScreenTabletVeryWide`：`isFullscreen || !sidebarVisible` 时
    // 右栏不占位（`return@Row`），视频独占整行。
    val showSideRelated = isDualPane && sidebarVisible && !isInPipMode && !isFullscreen
    // 顶栏折叠按钮只在「右栏存在过」时出现：宽屏非全屏非 PiP（与 animeko
    // `expanded && isDesktop` 的桌面限定不同 —— 我们三端都给，触摸端同样需要收起右栏看片）。
    val showSidebarToggle = isDualPane && !isInPipMode && !isFullscreen
    // Kazumi B 站风总开关：宽屏双栏或全屏（非 PiP）才开，窄屏竖屏恒 false。
    val bilibiliStyle = (isDualPane || isFullscreen) && !isInPipMode
    // animeko 的「expanded」形态（底栏进度条独占一行）：宽屏双栏或全屏。
    // 与 bilibiliStyle **当前同源但语义不同**——那是皮肤，这是行结构，
    // 所以各自派生一次，将来要拆开时只改这一行。
    val expandedBottomBar = (isDualPane || isFullscreen) && !isInPipMode
    // PiP 不给弹幕层（见参数文档）；其余三条布局路径共用同一个插槽。
    val resolvedDanmakuLayer: (@Composable () -> Unit)? =
        if (isInPipMode) null else danmakuLayer
    val resolvedDanmakuControls: (@Composable () -> Unit)? =
        if (isInPipMode) null else danmakuControls

    // 播放器内容：单一组合路径，**刻意不再使用 movableContentOf**。
    // 本组合函数持有 progress / currentTime / isPlaying 等逐帧变化的参数，会持续高频重组；
    // 而这里的 movableContentOf 每次重组都被重新创建（未 remember），身份不稳定，
    // Compose 会把它当作「新内容」→ 播放器子树连同 Skia 渲染面被反复销毁重建：
    // 表现为持续闪烁，并最终把 Skia GPU 资源缓存搞崩
    // （崩溃栈稳定停在 SkSurface_Ganesh::~SkSurface_Ganesh）。
    // 全项目只有 MainContent 一处调用点，movable 的跨分支搬运能力本就用不上，故回归直接调用。
    @Composable
    fun PlayerBox(playerModifier: Modifier) {
        VideoPlayerUi(
            modifier = playerModifier,
            danmakuLayer = resolvedDanmakuLayer,
            danmakuControls = resolvedDanmakuControls,
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
            onNextClick = onNextClick,
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
            bilibiliStyle = bilibiliStyle,
            expanded = expandedBottomBar,
            showSidebarToggle = showSidebarToggle,
            sidebarVisible = sidebarVisible,
            onToggleSidebar = onToggleSidebar,
            isFavVideo = isFavVideo,
            onToggleFavoriteVideo = onToggleFavoriteVideo,
        )
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
                PlayerBox(
                    Modifier
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
                )
                if (!isInPipMode && !isFullscreen) {
                    Box(modifier = Modifier.weight(1f)) {
                        tabsContent()
                    }
                }
            }
        }
    }

    if (showSideRelated) {
        // Animeko 宽屏（EpisodeScreenTabletVeryWide）：左列纯播放器（expanded video，
        // 撑满整列高度，不再定 16:9 高），右栏 = 详情｜评论 Tab
        // （弹幕占位条 + 标题收藏钮，见 VideoRouteContent wideRail）。
        Row(
            modifier = modifier
                .fillMaxSize()
                // 本页是 edge-to-edge，状态栏区域统一由 pageSurface 填充；左列播放器
                // 不再顶到状态栏之下。否则浅色模式下系统栏图标是深色，压在纯黑画面上
                // 会看不见 —— 原先这点是被右栏那层 `HanimeTheme(darkTheme = true)`
                // 意外「兜住」的（它顺带把系统栏图标强制成浅色），右栏改为跟随主题后
                // 必须在这里显式处理。
                .background(HanimeDefaults.Colors.pageSurface),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding(),
            ) {
                PlayerBox(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            onPlayerBoundsChanged(coordinates.boundsInWindow())
                        },
                )
            }
            // P5：固定 360dp（内容宽 < 1000dp 时 320dp）。原 `fillMaxWidth(0.38f)`
            // 在 2560dp 窗口下会变成 973dp 的巨型侧栏，注意力被完全拉走。
            // 右栏配色**跟随设置的主题**。原先这里按 `bilibiliStyle` 硬编码
            // `Color(0xFF111111)`（Kazumi 沉浸黑详情栏），但该分支在宽屏时恒为 true
            // —— 因为能进这个 Row 的条件本身就是 `isDualPane && !isInPipMode`，
            // 于是浅色模式下右栏也被迫变黑。`pageSurface` 那条 else 实际是死代码。
            // 现统一走 `pageSurface`：浅色近白 / 深色近黑，与 App 其余页面同一色板。
            // 注意：播放器顶栏/底栏的 B 站风皮肤仍由 `bilibiliStyle` 控制，未受影响
            // （那是叠加在视频画面上的控件，本就该是深色场景）。
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .consumeWindowInsets(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                    )
                    .background(HanimeDefaults.Colors.pageSurface)
                    .width(rememberRelatedPaneWidth()),
            ) {
                // 右栏 Tab（详情｜评论）；null 回退旧行为（简介/评论 Tab）。
                (railTabsContent ?: tabsContent)()
            }
        }
    } else if (isDualPane && !sidebarVisible && !isInPipMode && !isFullscreen) {
        // animeko 折叠态：右栏隐藏，播放器独占整宽整高（不再回退到「下方挂简介」的窄屏结构）。
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(HanimeDefaults.Colors.pageSurface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                PlayerBox(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            onPlayerBoundsChanged(coordinates.boundsInWindow())
                        },
                )
            }
        }
    } else {
        MainContent(contentModifier = modifier.fillMaxSize())
    }
}
