package lovehan1me.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.feature.player.PlaybackController
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackUiState
import lovehan1me.feature.player.PlatformVideoSurface
import lovehan1me.ui.adaptive.rememberRelatedPaneWidth
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.transition.sharedCoverElement
import lovehan1me.video.ui.LocalPlayerHaptic
import org.openani.mediamp.MediampPlayer

/**
 * 播放页的版式：播放器占哪块、右栏占哪块、PiP/全屏时收掉什么。
 *
 * 播放器**内部**的一切（控件、手势、进度、倍速、画面比例、弹幕叠层）都交给
 * [VideoPlayerShell]，本文件只决定给它什么样的框。
 */
@Composable
fun VideoShellContent(
    /** 双栏（内容 + 侧栏）是否启用，由调用方按**内容区可用宽度 ≥ 840dp** 计算。 */
    isDualPane: Boolean,
    isInPipMode: Boolean,
    isFullscreen: Boolean,
    /** 窄屏播放器的框高（null = 由剩余空间决定）。宽屏左列与 PiP 传 null。 */
    playerHeightDp: Dp?,
    controller: PlaybackController,
    playbackState: PlaybackUiState,
    posterUrl: String?,
    // 与列表页卡片封面配对的共享元素 key（null = 不做过渡）
    sharedElementKey: String? = null,
    title: String,
    showLoading: Boolean,
    showResumeButton: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onReplay: () -> Unit,
    onRetry: () -> Unit,
    onResumeClick: () -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    hasNextEpisode: Boolean,
    onClickNextEpisode: () -> Unit,
    onQualitySelected: (Int) -> Unit,
    enhancementLabel: String,
    enhancementOptions: List<String>,
    selectedEnhancementIndex: Int,
    onEnhancementSelected: (Int) -> Unit,
    frameCaptureEnabled: Boolean,
    onOpenGifCapture: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    /** 平台是否支持全屏（iOS 未实现 → false 时隐藏入口）。 */
    fullscreenEnabled: Boolean,
    /** 左半屏竖滑调亮度是否真的生效（桌面无亮度 API → false，UI 不接管该手势）。 */
    brightnessGestureEnabled: Boolean,
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    /** 长按快进倍速。 */
    fastForwardSpeed: Float,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    /**
     * 弹幕绘制层插槽（null = 不画）。
     *
     * PiP 在这里统一折算成 null，而不是让每个调用方记得判断：画中画只有几百分宽，
     * 弹幕上去就是一糊，而且 PiP 期间没有帧循环驱动时钟，弹幕会定在原地。
     */
    danmakuLayer: (@Composable BoxScope.() -> Unit)? = null,
    /** 底栏中间位的弹幕控件（开关 + 设置）。PiP 下同样收掉。 */
    danmakuEditor: (@Composable RowScope.() -> Unit)? = null,
    /**
     * 弹幕设置弹窗宿主（null = 不挂）。
     *
     * 弹窗必须挂在播放器的**最上层槽位**而不是底栏里：底栏随控件自动隐藏被销毁，
     * 弹窗跟着一起没。PiP 下也收掉 —— PiP 没有打开它的入口。
     */
    dialogHost: (@Composable () -> Unit)? = null,
    tabsContent: @Composable () -> Unit,
    /**
     * 宽屏右栏 Tab（详情｜评论），null = 回退到 [tabsContent]。
     * 窄屏/经典双栏传 null。弹幕相关控件在播放器底栏（见 [danmakuEditor]），不挂这里。
     */
    railTabsContent: (@Composable () -> Unit)? = null,
    sidebarVisible: Boolean = true,
    onToggleSidebar: (Boolean) -> Unit = {},
    isFavVideo: Boolean = false,
    onToggleFavoriteVideo: () -> Unit = {},
    onPlayerBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 双栏与否由宽度决定（横屏手机的宽度天然超过阈值，方向语义已被宽度蕴含）。
    // 对齐 animeko EpisodeVideo：`isFullscreen || !sidebarVisible` 时右栏不占位。
    val showSideRelated = isDualPane && sidebarVisible && !isInPipMode && !isFullscreen
    // 顶栏折叠按钮只在「右栏存在过」时出现。
    val showSidebarToggle = isDualPane && !isInPipMode && !isFullscreen
    // animeko 的 expanded 形态（底栏进度条独占一行、控件全展开）：宽屏双栏或全屏。
    val expanded = (isDualPane || isFullscreen) && !isInPipMode
    // PiP 不给弹幕层（见参数文档）；三条布局路径共用同一组插槽。
    val resolvedDanmakuLayer = if (isInPipMode) null else danmakuLayer
    val resolvedDanmakuEditor = if (isInPipMode) null else danmakuEditor
    // 非全屏时页面自己给播放器加了 statusBarsPadding，这里再要一遍就是双份状态栏高度。
    val contentWindowInsets = if (isFullscreen) WindowInsets.safeContent else WindowInsets(0.dp)

    // 播放器内容：单一组合路径，**刻意不再使用 movableContentOf**。
    // 本组合函数持有 isPlaying 等逐帧变化的参数，会持续高频重组；而这里的 movableContentOf
    // 每次重组都被重新创建（未 remember），身份不稳定，Compose 会把它当作「新内容」→
    // 播放器子树连同 Skia 渲染面被反复销毁重建：表现为持续闪烁，并最终把 Skia GPU 资源缓存
    // 搞崩（崩溃栈稳定停在 SkSurface_Ganesh::~SkSurface_Ganesh）。
    @Composable
    fun PlayerBox(playerModifier: Modifier) {
        // 渲染面的身份归本层（它才持有引擎）：key 只认引擎实例，画面比例变化不该
        // 把 Surface 连根重建（会黑一帧）。控件层只拿到"往这个矩形里画视频"的插槽。
        val engine = controller.playbackEngine
        val player = rememberMediampPlayer(engine)
        val videoSurface: @Composable BoxScope.() -> Unit = {
            key(engine) {
                PlatformVideoSurface(
                    engine = engine,
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceAvailable = { engine.attachSurface(it) },
                    onSurfaceDestroyed = { engine.detachSurface(it) },
                )
            }
        }
        // 封面槽位：怎么加载、要不要配列表页卡片的形变过渡（共享元素）都是本层的知识，
        // 控件层只负责"往这个矩形里画封面"。
        val cover: (@Composable BoxScope.() -> Unit)? = posterUrl?.let { url ->
            {
                HanimeAsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedCoverElement(sharedElementKey),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        val haptic = rememberHapticFeedback()
        val hapticSlot: () -> Unit = remember(haptic) { { haptic() } }
        CompositionLocalProvider(LocalPlayerHaptic provides hapticSlot) {
            if (player == null) {
                // 后端还没就绪（桌面 mpv 在 Default 线程惰性初始化）。先画黑盒而不是把
                // 控件挂上去：渲染面与控件层都要等同一个实例，提前挂上去只会建了又拆。
                Box(playerModifier.background(Color.Black))
            } else {
                VideoPlayerShell(
                    player = player,
                    controller = controller,
                    playbackState = playbackState,
                    videoSurface = videoSurface,
                    modifier = playerModifier,
                    expanded = expanded,
                    isFullscreen = isFullscreen,
                    showControls = !isInPipMode,
                    gesturesEnabled = !isInPipMode,
                    fullscreenEnabled = fullscreenEnabled,
                    contentWindowInsets = contentWindowInsets,
                    title = title,
                    cover = cover,
                    showLoading = showLoading,
                    showResumeButton = showResumeButton,
                    onFullscreenChange = onFullscreenChange,
                    onReplay = onReplay,
                    onRetry = onRetry,
                    onResumeClick = onResumeClick,
                    onBackClick = onBackClick,
                    onHomeClick = onHomeClick,
                    isFavVideo = isFavVideo,
                    onToggleFavoriteVideo = onToggleFavoriteVideo,
                    showSidebarToggle = showSidebarToggle,
                    sidebarVisible = sidebarVisible,
                    onToggleSidebar = onToggleSidebar,
                    hasNextEpisode = hasNextEpisode,
                    onClickNextEpisode = onClickNextEpisode,
                    onQualitySelected = onQualitySelected,
                    enhancementLabel = enhancementLabel,
                    enhancementOptions = enhancementOptions,
                    selectedEnhancementIndex = selectedEnhancementIndex,
                    onEnhancementSelected = onEnhancementSelected,
                    frameCaptureEnabled = frameCaptureEnabled,
                    onOpenGifCapture = onOpenGifCapture,
                    onCaptureScreenshot = onCaptureScreenshot,
                    danmakuEnabled = danmakuEnabled,
                    onToggleDanmaku = onToggleDanmaku,
                    danmakuLayer = resolvedDanmakuLayer,
                    danmakuEditor = resolvedDanmakuEditor ?: {},
                    dialogHost = if (isInPipMode) null else dialogHost,
                    currentVolume = currentVolume,
                    onVolumeChange = onVolumeChange,
                    brightnessGestureEnabled = brightnessGestureEnabled,
                    currentBrightness = currentBrightness,
                    onBrightnessChange = onBrightnessChange,
                    fastForwardSpeed = fastForwardSpeed,
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
        // 宽屏：左列纯播放器（expanded，撑满整列高度），右栏 = 详情｜评论 Tab。
        Row(
            modifier = modifier
                .fillMaxSize()
                // 本页是 edge-to-edge，状态栏区域统一由 pageSurface 填充；左列播放器
                // 不再顶到状态栏之下。否则浅色模式下系统栏图标是深色，压在纯黑画面上
                // 会看不见。
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
            // 右栏**跟随设置的主题**（本仓不复刻 animeko 的右栏），固定宽度而不是按比例：
            // `fillMaxWidth(0.38f)` 在 2560dp 窗口下会变成 973dp 的巨型侧栏。
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
                (railTabsContent ?: tabsContent)()
            }
        }
    } else if (isDualPane && !sidebarVisible && !isInPipMode && !isFullscreen) {
        // 折叠态：右栏隐藏，播放器独占整宽整高（不回退到「下方挂简介」的窄屏结构）。
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

/**
 * 取引擎背后的 mediamp 实例。
 *
 * 必须挂起取：桌面端 mpv 惰性初始化（解压 + dlopen + mpv_create）在组合线程直读会冻住 UI。
 * 换引擎时重置为 null，由 [LaunchedEffect] 再取一次。
 */
@Composable
private fun rememberMediampPlayer(engine: PlaybackEngine): MediampPlayer? {
    var player by remember(engine) { mutableStateOf<MediampPlayer?>(null) }
    LaunchedEffect(engine) {
        player = engine.acquireMediampPlayer()
    }
    return player
}
