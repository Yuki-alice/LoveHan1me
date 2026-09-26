package lovehan1me.feature.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.defaultPlayerMpvOptionsProvider
import lovehan1me.data.network.defaultPlayerNetworkConfig
import lovehan1me.site.SiteIdentity
import lovehan1me.ui.adaptive.WindowHeightBreakpoints
import lovehan1me.ui.adaptive.WindowWidthBreakpoints
import lovehan1me.ui.adaptive.rememberContentWidthDp
import lovehan1me.ui.adaptive.rememberWindowHeightDp
import lovehan1me.Res
import lovehan1me.add_failed
import lovehan1me.add_success
import lovehan1me.copy_to_clipboard
import lovehan1me.fail_to_get_video_link
import lovehan1me.local_favorite_cancelled
import lovehan1me.modify_failed
import lovehan1me.modify_success
import lovehan1me.video_might_not_exist
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.long_press_share_to_copy
import lovehan1me.mobile_data_playback_warning
import lovehan1me.no
import lovehan1me.play_pause
import lovehan1me.screenshot_failed
import lovehan1me.screenshot_saved
import lovehan1me.sure
import lovehan1me.sure_to_unsubscribe
import lovehan1me.unsubscribe_artist
import lovehan1me.warning
import lovehan1me.super_resolution_off
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_quality
import lovehan1me.player_anime4k_label
import lovehan1me.local_list_notice_title
import lovehan1me.local_list_notice_message
import lovehan1me.do_not_show_again
import lovehan1me.continues
import lovehan1me.data.getHanimeVideoLink
import lovehan1me.core.constant.VIDEO_COMMENT_PREFIX
import lovehan1me.data.DatabaseRepo
import lovehan1me.data.database.entity.WatchHistoryEntity
import lovehan1me.core.domain.exception.ParseException
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.core.platform.MediaExportOutcome
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.platform.exportMediaAndShare
import lovehan1me.app.bridge.NoopVideoPageHost
import lovehan1me.app.bridge.PipModeReporter
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.app.navigation.main.SearchRoute
import lovehan1me.app.navigation.main.ArtistRoute
import lovehan1me.app.navigation.main.SitePlaylistRoute
import lovehan1me.app.navigation.main.VideoRoute
import lovehan1me.feature.player.BindOrientationAutoFullscreen
import lovehan1me.feature.player.PlaybackController
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackPhase
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.feature.player.PlayerKernel
import lovehan1me.feature.player.createPlaybackEngine
import lovehan1me.feature.player.isActiveNetworkMetered
import lovehan1me.feature.player.shouldAutoPlayNext
import lovehan1me.video.contract.VideoEnhancementLevels
import lovehan1me.feature.danmaku.DanmakuLayer
import lovehan1me.feature.danmaku.DanmakuControls
import lovehan1me.feature.danmaku.DanmakuSettingsDialog
import lovehan1me.feature.danmaku.rememberDanmakuRenderOptions
import lovehan1me.feature.danmaku.rememberDanmakuSession
import lovehan1me.feature.video.CommentViewModel
import lovehan1me.feature.video.VideoViewModel
import lovehan1me.app.sharedViewModel
import lovehan1me.core.util.decodeComposeAsset
import lovehan1me.core.util.image.ScreenshotCapturer
import lovehan1me.core.util.AppToast
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.core.util.rememberShareText
import lovehan1me.ui.transition.coverSharedElementKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * M3：自 `:app` 下沉（视频页宿主：引擎装配 + 播放编排 + 全屏/PiP/亮度/对话框）。
 *
 * 与 `:app` 版的差异：
 * - `activity: MainActivity` 改注入：[platformHost]（窗口操作：PiP/常亮/全屏/亮度/
 *   系统栏/host 注册，见 [VideoPageHost]）+ [onRegisterPageHost]（Android 侧把内部
 *   pageHost 注册给 MainActivity 的 PiP 广播）+ [onBack]/[onNavigateHome]/
 *   [onNavigateToVideo]/[onOpenSearchRoute]/[onEnqueueDownload] 回调；
 * - 引擎改共享 [createPlaybackEngine]（三端 expect；原 `PlaybackEngineFactory.create`
 *   留 `:app` 给旧播放链以外的调用方——经查仅本文件使用，见下条）；
 * - 返回键回调/通知权限申请（ActivityResult）删除：桌面无系统返回，下载通知权限
 *   在下载实际入队时由平台层处理；
 * - 陀螺仪自动横竖屏改 [BindOrientationAutoFullscreen]（Android 生效）；
 * - 移动数据判定改 [isActiveNetworkMetered] expect；
 * - 镜像站 genre 词典改共享 `decodeComposeAsset("files/...")`；
 * - 时间格式化 `"%.1f"/"%02d"`（JVM-only）改纯 Kotlin 实现；
 * - 反篡改块（`svc()`/`getString()` external + Base64Dialog + `isX86_64Device`）
 *   删除：其 JNI 实现仅存在于 `:app`，桌面/iOS 无对应能力。
 */
@OptIn(ExperimentalTime::class)
@Composable
fun VideoRouteHostScreen(
    route: VideoRoute,
    platformHost: VideoPageHost = NoopVideoPageHost,
    onRegisterPageHost: ((VideoPageHost?) -> Unit)? = null,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenSearchRoute: (SearchRoute) -> Unit,
    onOpenArtistRoute: (ArtistRoute) -> Unit,
    onOpenSitePlaylist: (SitePlaylistRoute) -> Unit,
    onEnqueueDownload: (EnqueueDownloadRequest) -> Unit,
    /** 弹幕「去设置」：跳到播放器设置页（弹幕分组在那里）。 */
    onOpenDanmakuSettings: () -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val copyTextToClipboard = rememberCopyTextToClipboard()
    val shareText = rememberShareText()
    val viewModel: VideoViewModel = sharedViewModel(::VideoViewModel)
    val commentViewModel: CommentViewModel = sharedViewModel(::CommentViewModel)
    // A-1：会话起点 = 进详情页。用 remember 在组合阶段就 begin，确保后续的
    // 引擎同步构造与异步 fetch 的 marks 都落在同一会话内（此前 begin 在
    // LaunchedEffect，发生在组合之后，组合内 mark 全被 clear 丢弃）。
    val sessionKey = remember(route.videoCode, route.localUri) { "video:" + route.videoCode }
    remember(sessionKey) { PlayerTrace.begin(sessionKey); sessionKey }
    // A-1b：进页到 fetch-start 之间 13s 空白的切分——组合完成与 effect 调度两段
    androidx.compose.runtime.SideEffect { PlayerTrace.mark("compose-done") }
    val kernel = remember { PlayerKernel.fromPreference(SettingsRepository.switchPlayerKernel) }
    val playbackEngine: PlaybackEngine = remember(route.videoCode, route.localUri, kernel) {
        PlayerTrace.mark("engine-create-start")
        createPlaybackEngine(
            kernel = kernel,
            network = defaultPlayerNetworkConfig(),
            mpvOptions = defaultPlayerMpvOptionsProvider,
        ).also {
            PlayerTrace.mark("engine-create-end")
        }
    }
    val playbackController = remember(playbackEngine) { PlaybackController(playbackEngine) }
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
    // 单栏 / 双栏判定：对齐 animeko `EpisodePage.showExpandedUI`
    // （`showExpandedUI = (w >= 840) || (w >= 600 && h < 480)`）。
    // 宽度用内容区可用宽度（常驻抽屉占宽已扣，见 ProvideContentWidth），
    // 高度用整窗高 —— 第二项覆盖"横屏手机"（宽够高不够），此前这档被误判成窄屏。
    val contentWidth = rememberContentWidthDp()
    val windowHeight = rememberWindowHeightDp()
    val isDualPane = contentWidth >= WindowWidthBreakpoints.ExpandedDp ||
        (contentWidth >= WindowWidthBreakpoints.MediumDp && windowHeight < WindowHeightBreakpoints.CompactDp)
    val hostUiState by viewModel.videoHostUiStateFlow.collectAsStateWithLifecycle()
    val videoState by viewModel.hanimeVideoStateFlow.collectAsStateWithLifecycle()
    val video = viewModel.hanimeVideoFlow.collectAsStateWithLifecycle().value

    LaunchedEffect(playbackController) {
        playbackController.setPlaybackSpeed(SettingsRepository.playerSpeed)
    }
    val stringLongPressShare = stringResource(Res.string.long_press_share_to_copy)
    val pipPlayPauseText = stringResource(Res.string.play_pause)
    // 词典 JSON 移出组合：磁盘 IO 不许卡首帧，进场后异步装，到了重组 actions 即可。
    // key 用 domainName 而非 baseUrl：开了自定义镜像时 baseUrl 会变成镜像地址，
    // 用它作 key 会让切镜像也触发一次无谓的词典重载、且判定本身不可靠（见 SiteIdentity）。
    var genres by remember(SettingsRepository.domainName) { mutableStateOf(emptyList<SearchOption>()) }
    LaunchedEffect(SettingsRepository.domainName) {
        // Native 侧没有 Dispatchers.IO（JVM-only）：词典 JSON 解码放 Default，一样不在主线程。
        genres = withContext(Dispatchers.Default) {
            decodeComposeAsset<List<SearchOption>>(
                if (SiteIdentity.isAvSite) {
                    "files/search_options/genre_av.json"
                } else {
                    "files/search_options/genre.json"
                }
            ).orEmpty()
        }
    }

    var checkedQuality by remember(
        route.videoCode,
        route.localUri
    ) { mutableStateOf<String?>(null) }
    var pendingDownloadPrompt by remember(route.videoCode, route.localUri) {
        mutableStateOf<DownloadPromptState?>(null)
    }
    var videoTitle by remember(route.videoCode, route.localUri) { mutableStateOf("") }
    var isFullscreen by remember { mutableStateOf(false) }
    var isPlayerLocked by remember { mutableStateOf(false) }
    // animeko 右栏折叠（EpisodeViewModel.sidebarVisible）：宽屏下顶栏按钮切换，
    // 换片不重置（与选集/tab 状态同级，会话级 UI 状态）。
    var sidebarVisible by remember { mutableStateOf(true) }
    var volume by remember { mutableStateOf(1f) }
    var brightness by remember { mutableStateOf(platformHost.currentBrightness()) }
    var previousScreenBrightness by remember { mutableStateOf<Float?>(null) }
    var speedBeforeLongPress by remember { mutableStateOf<Float?>(null) }
    var showResumeButton by remember { mutableStateOf(false) }
    /** 本次实际用于起播的位置（用于"上次看到结尾则从头"的判定）。 */
    var startPositionUsed by remember(route.videoCode) { mutableStateOf(0L) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var mobilePlaybackConfirmed by remember(route.videoCode, route.localUri) {
        mutableStateOf(false)
    }
    // 超分档位。存的是**档位值**（0/1/2）而不是菜单下标：引擎可选档位可能是子集，
    // 用下标会把"档位"和"位置"混成一件事（下标在渲染前才换算）。
    var superResolutionIndex by remember { mutableStateOf(VideoEnhancementLevels.OFF) }
    // 重进播放页要把落盘档位重新下发一次：`remember` 的本地态活不过页面销毁，存档才活得下去。
    // 引擎降级/分辨率门控时返回的是**生效值**，直接拿它刷新显示，不显示"选了却没生效"的档。
    LaunchedEffect(playbackController) {
        playbackController.setEnhancementLevel(SettingsRepository.superResolutionLevel)
            ?.let { superResolutionIndex = it }
    }
    // G2-3b：画面比例。初值取"引擎真实生效值"，而不是直接读设置 ——
    // 设置里可能存着 Stretch，但当前引擎是 Exo（无此档），引擎已经降级成 Fit 了；
    // 拿引擎状态当唯一真相，菜单就不会显示一个"选中了却没生效"的档。
    val aspectOptions = playbackController.supportedAspectModes
    val currentVideoAspect = playbackState.engine.videoAspect
    // 超分可选档位由引擎声明（空 = 本端不支持 → 底栏整块不画）。
    val enhancementLevels = playbackController.enhancement?.levels.orEmpty()
    LaunchedEffect(playbackController, aspectOptions) {
        // 首次进入时把用户存的偏好下发（引擎不支持的档会被降级并回报）。
        playbackController.setVideoAspect(SettingsRepository.videoAspect)
        playbackController.setPictureAdjust(SettingsRepository.pictureAdjust)
    }
    // M3-b：录 GIF 对话框的开关。入口是否显示由 controller.supportsFrameCapture 决定
    var showGifCapture by remember { mutableStateOf(false) }
    // M3-c：截图没有对话框（一次手势走完），只用这个标志防连点重入
    var screenshotInFlight by remember { mutableStateOf(false) }

    /**
     * M3-c：抓当前帧 → PNG → 保存并唤起系统分享。
     *
     * 三个刻意的选择：
     * 1. **现场读 `playbackController.state.value`**，而不是用组合期捕获的 `playbackState`：
     *    后者是"上一次重组那一刻"的快照，而播放位置每几百毫秒就变一次 ——
     *    点截图却截到半秒前的画面，用户只会觉得"截偏了"。
     * 2. **原本在播就接着播**：抓帧实现会 pause 播放器（mpv 要先 pause 才取到稳定帧，
     *    Exo 与 iOS 同理），截图不该顺手把播放停掉。
     * 3. **失败全部走 AppToast**：没有对话框可以承载错误文案，
     *    而 `ScreenshotCapturer` 已把失败收敛成结果类型（含"抓不到帧"与"编码失败"的区分）。
     */
    fun captureScreenshot() {
        if (screenshotInFlight) return
        screenshotInFlight = true

        val engine = playbackController.state.value.engine
        val resumeAfterCapture = engine.isPlaying
        scope.launch {
            try {
                val outcome = ScreenshotCapturer.capture(
                    sourceWidth = engine.videoWidth,
                    sourceHeight = engine.videoHeight,
                    positionMs = engine.positionMs,
                    captureFrameArgb = { positionMs, width, height ->
                        playbackController.grabFrameArgb(positionMs, width, height)
                    },
                )
                when (outcome) {
                    is ScreenshotCapturer.Outcome.Success -> when (
                        val export = exportMediaAndShare(
                            bytes = outcome.bytes,
                            fileName = "LoveHan1me_${currentEpochMillis()}.png",
                            mimeType = PNG_MIME,
                        )
                    ) {
                        // Shared 与 SavedOnly 对用户都是"存好了"，区别只在有没有弹分享面板
                        is MediaExportOutcome.Shared -> AppToast.success(
                            getString(Res.string.screenshot_saved, export.location),
                        )

                        is MediaExportOutcome.SavedOnly -> AppToast.success(
                            getString(Res.string.screenshot_saved, export.location),
                        )

                        is MediaExportOutcome.Failed -> AppToast.error(
                            getString(Res.string.screenshot_failed, export.message),
                        )
                    }

                    is ScreenshotCapturer.Outcome.NoFrame -> AppToast.error(
                        getString(Res.string.screenshot_failed, outcome.detail),
                    )

                    is ScreenshotCapturer.Outcome.EncodeFailed -> AppToast.error(
                        getString(Res.string.screenshot_failed, outcome.message),
                    )
                }
            } finally {
                screenshotInFlight = false
                if (resumeAfterCapture) playbackController.play()
            }
        }
    }
    var pendingUnsubscribeArtist by remember { mutableStateOf<HanimeVideo.Artist?>(null) }
    var pendingLocalListAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val actions = remember(scope, viewModel, genres) {
        VideoRouteActions(
            scope = scope,
            viewModel = viewModel,
            genres = genres,
            onPendingDownloadPromptChange = { pendingDownloadPrompt = it },
            getCheckedQuality = { checkedQuality },
            setCheckedQuality = { checkedQuality = it },
            onOpenUri = uriHandler::openUri,
            onCopyText = copyTextToClipboard,
            onOpenSearchRoute = onOpenSearchRoute,
            onOpenArtistRoute = onOpenArtistRoute,
            onRequestUnsubscribe = { pendingUnsubscribeArtist = it },
            onRequestNotificationPermission = { platformHost.requestNotificationPermission() },
            onRequestLocalListAction = { action ->
                if (SettingsRepository.localListNoticeDismissed) {
                    action()
                } else {
                    pendingLocalListAction = action
                }
            },
            onEnqueueDownload = onEnqueueDownload,
        )
    }

    fun exitFullscreen() {
        if (!isFullscreen) return
        isFullscreen = false
        platformHost.applyFullscreen(false)
        previousScreenBrightness?.let { platformHost.applyBrightness(it) }
        previousScreenBrightness = null
        brightness = platformHost.currentBrightness()
    }

    fun enterFullscreen(forceLandscape: Boolean = false) {
        isFullscreen = true
        // ⚠️ 尺寸还没上报时**不要**据此判定竖屏：原式 `!(w>0 && h>w)` 在 `w==0` 时恒为 true，
        // 于是竖屏视频首次进全屏会被强行转横屏，而且事后不会纠正（不会再进一次全屏）。
        // 现在先按横屏进入（绝大多数视频是横屏），尺寸到达后由下面的 LaunchedEffect 纠正。
        platformHost.applyFullscreen(true, forceLandscape = forceLandscape)
    }

    fun updatePipAction() {
        if (!hostUiState.isInPipMode) return
        platformHost.refreshPipAction(
            playbackController.state.value.engine.isPlaying,
            pipPlayPauseText,
        )
    }

    val pageHost = remember(platformHost, playbackController, viewModel, pipPlayPauseText) {
        object : VideoPageHost {
            override fun showCommentBadge(count: Int) = viewModel.setCommentBadgeCount(count)

            override fun shouldEnterPip(): Boolean {
                val state = playbackController.state.value.engine
                return state.phase == PlaybackPhase.Ready &&
                        (state.isPlaying || state.positionMs > 0L)
            }

            override fun enterPipMode() {
                val state = playbackController.state.value.engine
                platformHost.enterPipMode(state.isPlaying, pipPlayPauseText)
            }

            override fun onPipModeChanged(isInPip: Boolean) {
                viewModel.setPipMode(isInPip)
                // 进 PiP 就退出全屏：两者互斥（此前不同步 —— 从全屏进 PiP 会让
                // isFullscreen 假真，退出 PiP 后系统栏已恢复、顶部却被当成全屏处理）。
                if (isInPip) exitFullscreen()
                updatePipAction()
            }

            override fun togglePlayPause() {
                playbackController.togglePlayPause()
                updatePipAction()
            }
        }
    }

    DisposableEffect(platformHost, playbackController, pageHost) {
        platformHost.onHostStarted()
        onRegisterPageHost?.invoke(pageHost)
        // 阶段一⑨：iOS 无 Activity，PiP 状态经 PipModeReporter 直连共享 pageHost
        //（Android 走 MainActivity 广播，不受影响；桌面/旧实现直接不是 Reporter）。
        val pipReporter = platformHost as? PipModeReporter
        pipReporter?.pipModeListener = pageHost::onPipModeChanged
        onDispose {
            pipReporter?.pipModeListener = null
            onRegisterPageHost?.invoke(null)
            platformHost.onHostStopped()
            playbackController.release()
            if (isFullscreen) {
                platformHost.applyFullscreen(false)
            }
        }
    }

    // 全屏期间尺寸上报后按**实际**比例再应用一次方向：竖屏视频会被正确留在竖屏，
    // 横屏视频保持横屏（幂等，不会来回抖）。
    LaunchedEffect(
        isFullscreen,
        playbackController,
        playbackState.engine.videoWidth,
        playbackState.engine.videoHeight,
    ) {
        if (!isFullscreen) return@LaunchedEffect
        val width = playbackController.state.value.engine.videoWidth
        val height = playbackController.state.value.engine.videoHeight
        if (width > 0 && height > 0) {
            platformHost.applyFullscreen(true, forceLandscape = height <= width)
        }
    }

    BindOrientationAutoFullscreen(
        enabled = !isDualPane,
        onLandscape = { enterFullscreen(forceLandscape = true) },
        onPortrait = { exitFullscreen() },
    )

    /**
     * 落一次观看进度（M5-3）。
     *
     * 原本只有生命周期 ON_PAUSE 一个时机，而"应用内返回上一页 / 关窗 / 相关影片跳转"
     * **不会**触发它 —— 那些路径的进度直接丢。现在多个时机共用本函数：
     * ON_PAUSE（退后台）、播放中每 15s 心跳、以及播完后写回（见各自的调用点）。
     *
     * 取值一律用 controller 的**实时**状态，不用组合期快照（快照最多滞后一次重组）。
     */
    fun persistPlaybackProgress(override: Long? = null) {
        if (route.videoCode == "-1") return
        val position = override ?: playbackController.state.value.engine.positionMs
        if (override == null && position <= 0L) return
        scope.launch { DatabaseRepo.WatchHistory.updateProgress(route.videoCode, position) }
    }

    // M5-3：播放中每 15 秒落一次进度。原实现只在 ON_PAUSE 写，进程被杀或崩溃就全丢；
    // 15s 粒度把最坏损失压到 15 秒，而写入只是本地 Room 的单行 UPDATE，代价可忽略。
    LaunchedEffect(route.videoCode) {
        while (true) {
            delay(15_000)
            if (playbackController.state.value.engine.isPlaying) persistPlaybackProgress()
        }
    }

    DisposableEffect(lifecycleOwner, playbackController, route.videoCode, isDualPane) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> persistPlaybackProgress()

                Lifecycle.Event.ON_STOP -> {
                    if (!hostUiState.isInPipMode) {
                        playbackController.pause()
                        exitFullscreen()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    // M5-3：上次是"看到结尾"的话，这次从头开始（成熟播放器同规则）。
    // 判据是"本次起播位置已接近总时长"，避免用户一点进来就撞上结束卡；
    // 时长要等引擎报出来，故放在 LaunchedEffect 里随 durationMs 变化触发。
    LaunchedEffect(route.videoCode, playbackState.engine.durationMs) {
        val duration = playbackState.engine.durationMs
        if (startPositionUsed > 0L && duration > 0L &&
            startPositionUsed >= duration - NEAR_END_TOLERANCE_MS
        ) {
            playbackController.seekTo(0L)
            startPositionUsed = 0L
        }
    }

    // fetch 触发改同步：此前用 LaunchedEffect，经实测 effect 调度在进页后被
    // 主线程其它工作堵住 9s 才起跑（compose-done +35ms → effect-fetch-start +9312ms）。
    // 改用 remember 在组合阶段同步发起，绕过调度空转；重复进入同视频由
    // introRestored 守卫，已有内存缓存则秒画后再刷新。
    remember(route.videoCode, route.localUri) {
        PlayerTrace.mark("effect-fetch-start")
        checkedQuality = null
        pendingDownloadPrompt = null
        videoTitle = ""
        viewModel.videoCode = route.videoCode
        viewModel.fromDownload = route.videoCode == "-1" || route.localUri != null
        if (route.localUri == null && route.videoCode != "-1") {
            viewModel.restoreFromCacheIfExists(route.videoCode)
            viewModel.clearVideoIntroRestoredFlag(route.videoCode)
        }
        viewModel.getHanimeVideo(route.videoCode, route.localUri)
        route.videoCode
    }

    LaunchedEffect(route.videoCode, route.localUri, playbackController, viewModel.fromDownload) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            viewModel.hanimeVideoStateFlow.collect { state ->
                when (state) {
                    is VideoLoadingState.Error -> {
                        state.throwable.message?.let(AppToast::error)
                        if (state.throwable is ParseException) {
                            uriHandler.openUri(getHanimeVideoLink(route.videoCode))
                        }
                    }

                    is VideoLoadingState.Loading -> Unit

                    is VideoLoadingState.Success -> {
                        val info = state.info
                        videoTitle = info.title
                        val qualities = info.videoUrls.map { (label, link) ->
                            PlaybackQuality(
                                label = label,
                                uri = link.link,
                                mimeType = link.subtype?.let { "video/$it" },
                            )
                        }
                        if (qualities.isEmpty()) {
                            AppToast.error(getString(Res.string.fail_to_get_video_link))
                            uriHandler.openUri(getHanimeVideoLink(route.videoCode))
                        } else {
                            val history = DatabaseRepo.WatchHistory.findBy(route.videoCode)
                            showResumeButton = SettingsRepository.allowResumePlayback &&
                                    (history?.progress ?: 0L) > 5_000L
                            // ⚠️ 这个开关必须真的拦起播位置：此前它只决定"从头播放"按钮显不显示，
                            // 关掉之后照样从上次位置起播 —— 设置语义与实际行为不符。
                            val resumePosition = if (SettingsRepository.allowResumePlayback) {
                                history?.progress ?: 0L
                            } else {
                                0L
                            }
                            startPositionUsed = resumePosition
                            val request = PendingPlayback(
                                title = info.title,
                                qualities = qualities,
                                preferredQuality = SettingsRepository.videoQuality,
                                artworkUri = info.coverUrl,
                                startPositionMs = resumePosition,
                            )
                            if (!viewModel.fromDownload &&
                                !SettingsRepository.disableMobileDataWarning &&
                                !mobilePlaybackConfirmed &&
                                isActiveNetworkMetered()
                            ) {
                                pendingPlayback = request
                            } else {
                                // A-1：直链到手 → 引擎 load，load→首帧的间隔从这里起算。
                                PlayerTrace.mark("load-called")
                                // 播放器侧缓冲已在 LaunchedEffect(showLoading) 记 span，
                                // 这里额外记一个进入 preparing 时刻，方便算 load→首帧
                                PlayerTrace.event("player-phase", playbackState.engine.phase.name)
                                playbackController.load(
                                    title = request.title,
                                    qualities = request.qualities,
                                    preferredQuality = request.preferredQuality,
                                    artworkUri = request.artworkUri,
                                    startPositionMs = request.startPositionMs,
                                    // M5-3：进入详情页是否自动播放由设置决定（默认**关**）。
                    // 关了之后：媒体仍然加载并渲染首帧，但停在暂停态 —— 用户看到
                    // "封面/首帧 + 中央播放键"，由自己决定何时开播（移动网络下尤其重要）。
                    playWhenReady = SettingsRepository.autoPlayOnEnter,
                                )
                            }
                        }
                        if (!viewModel.fromDownload) {
                            viewModel.insertWatchHistoryWithCover(
                                WatchHistoryEntity(
                                    info.coverUrl,
                                    info.title,
                                    info.uploadTimeMillis,
                                    kotlin.time.Clock.System.now().toEpochMilliseconds(),
                                    route.videoCode,
                                )
                            )
                        }
                    }

                    is VideoLoadingState.NoContent -> AppToast.error(getString(Res.string.video_might_not_exist))
                }
            }
        }
    }

    LaunchedEffect(viewModel, route.videoCode) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            viewModel.loadDownloadedFlow.collect { entity ->
                val newQuality = checkedQuality ?: return@collect
                pendingDownloadPrompt = DownloadPromptState(
                    newQuality = newQuality,
                    oldQuality = entity?.quality,
                    oldGroupId = entity?.groupId,
                )
            }
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            launch {
                viewModel.localFavoriteActionFlow.collect { state ->
                    when (state) {
                        is WebsiteState.Error -> AppToast.error(getString(Res.string.add_failed))
                        is WebsiteState.Success -> AppToast.success(
                            getString(
                                if (state.info) Res.string.add_success
                                else Res.string.local_favorite_cancelled
                            )
                        )
                        WebsiteState.Loading -> Unit
                    }
                }
            }
            launch {
                viewModel.localMyListActionFlow.collect { state ->
                    when (state) {
                        is WebsiteState.Error -> AppToast.error(getString(Res.string.modify_failed))
                        is WebsiteState.Success -> AppToast.success(getString(Res.string.modify_success))
                        WebsiteState.Loading -> Unit
                    }
                }
            }
        }
    }

    LaunchedEffect(playbackState.engine.isPlaying) {
        viewModel.setScrollDisabled(playbackState.engine.isPlaying)
        updatePipAction()
    }

    // 画面缩放（双指 / 桌面 Ctrl+滚轮）：状态归屏幕边界持有，UI 只拿值与回调
    var videoScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(showResumeButton) {
        if (showResumeButton) {
            kotlinx.coroutines.delay(5_000L.milliseconds)
            showResumeButton = false
        }
    }

    // Animeko 宽屏左列是纯播放器（撑满高度），不再定 16:9 高；窄屏保持比例式高度。
    // 原固定 250dp（单栏）/ 400dp（双栏）在 1920dp 桌面窗口下播放器只占 13% 宽度；
    // 窄屏取「内容宽 × 9/16」与「窗高 × 0.55」的较小值、兜底 240dp。
    val resolvedPlayerHeightDp = when {
        hostUiState.isInPipMode || isDualPane -> null
        else -> maxOf(
            240.dp,
            minOf(contentWidth * 9f / 16f, windowHeight * 0.55f),
        )
    }

    LaunchedEffect(resolvedPlayerHeightDp, hostUiState.playerHeightDp) {
        if (hostUiState.playerHeightDp != resolvedPlayerHeightDp) {
            viewModel.setPlayerHeightDp(resolvedPlayerHeightDp)
        }
    }

    // ── M5 埋点：一次播放会话（换片才重置，同片重组不重置）──────────────
    // A-1：begin 已前移到进页（videoCode），这里只记"简介到货"（原来 begin 的位置）。
    LaunchedEffect(videoTitle) { PlayerTrace.mark("video-info-ready") }
    DisposableEffect(videoTitle) { onDispose { PlayerTrace.summary() } }

    // 首帧：量"起播 → 出画"，三端可比
    val hasRenderedFirstFrame = playbackState.engine.hasRenderedFirstFrame
    LaunchedEffect(hasRenderedFirstFrame) {
        if (hasRenderedFirstFrame) PlayerTrace.mark("first-frame")
    }

    // 缓冲起止：提前成 val —— 下面 showLoading 直接复用，避免两处表达式漂移
    val showLoading =
        (videoState is VideoLoadingState.Loading ||
                playbackState.engine.phase == PlaybackPhase.Preparing ||
                // 卡顿看门狗（M5-3）：位置停滞时也转圈 —— 引擎侧的 isBuffering
                // 三端语义不一致，只有 Exo 是真信号，mpv/iOS 中途卡住根本不置位。
                playbackState.isStalled) &&
                // 切画质期间不显示全屏转圈：画面保留上一帧才像"无缝换档"，
                // 转圈+海报反而是"重新打开了一遍"的观感。
                !playbackState.isSwitchingQuality
    LaunchedEffect(showLoading) {
        if (showLoading) PlayerTrace.spanStart("buffering") else PlayerTrace.spanEnd("buffering")
    }

    // 错误：引擎/网络给的真实原因（与重试卡上屏的同源）
    val engineError = playbackState.engine.errorMessage
    LaunchedEffect(engineError) {
        if (!engineError.isNullOrBlank()) PlayerTrace.event("error", engineError)
    }

    // B 站风底栏「下一集」：系列视频当前播放项的后一项；
    // 没有系列/已是最后一集则为 null，底栏不显示入口（窄屏不受影响）。
    val nextPlaylistItem = remember(video) {
        val playlist = video?.playlist?.video.orEmpty()
        val playingIndex = playlist.indexOfFirst { it.isPlaying }
        if (playingIndex >= 0) playlist.getOrNull(playingIndex + 1) else null
    }

    // 系列自动连播：播完（Ended 跃迁）+ 开关开 + 有下一集 → 走与手动「下一集」
    // 同一条导航。新页面 phase 从头开始，不会连环触发；单片/尾集/开关关闭
    // 时 shouldAutoPlayNext 为 false，原地停在结束态（行为与之前一致）。
    val autoPlayNext = SettingsRepository.settings.collectAsStateWithLifecycle().value.autoPlayNext
    LaunchedEffect(playbackState.engine.phase, autoPlayNext, nextPlaylistItem) {
        if (shouldAutoPlayNext(
                playbackState.engine.phase,
                autoPlayNext,
                nextPlaylistItem != null,
            )
        ) {
            nextPlaylistItem?.let { onNavigateToVideo(it.videoCode) }
        }
    }

    // 简介/评论 Tab 只有一处装配：窄屏播放器下方（wideRail=false）与
    // Animeko 宽屏右栏（wideRail=true：弹幕占位条 + 标题收藏钮）共用，改只改这里。
    @Composable
    fun HostVideoTabs(wideRail: Boolean) {
        VideoRouteContent(
            videoCode = route.videoCode,
            videoState = videoState,
            videoViewModel = viewModel,
            commentViewModel = commentViewModel,
            fromDownload = viewModel.fromDownload,
            pendingDownloadPrompt = pendingDownloadPrompt,
            onPendingDownloadPromptChange = { pendingDownloadPrompt = it },
            onRetry = { viewModel.getHanimeVideo(route.videoCode, route.localUri) },
            onOpenVideo = { item -> onNavigateToVideo(item.videoCode) },
            onOpenArtist = actions::openArtist,
            onOpenSitePlaylist = onOpenSitePlaylist,
            onNavigateToSearch = actions::openTagSearch,
            onToggleSubscribe = actions::toggleArtistSubscription,
            onToggleFavorite = actions::toggleFavorite,
            onRequestManageMyList = { action ->
                if (SettingsRepository.isAlreadyLogin ||
                    SettingsRepository.localListNoticeDismissed
                ) {
                    action()
                } else {
                    pendingLocalListAction = action
                }
            },
            onRateVideo = actions::rateVideo,
            onManageMyList = actions::updateMyListSelection,
            onQuickCheckIn = actions::quickCheckIn,
            onPrepareDownload = { quality, item ->
                checkedQuality = quality
                item?.let(actions::startDownloadFlow)
            },
            onConfirmDownloadPrompt = { item, autoCreateGroup ->
                item?.let {
                    actions.confirmPendingDownload(
                        it,
                        pendingDownloadPrompt,
                        autoCreateGroup,
                    )
                }
            },
            onRequestOpenOfficialDownloadPage = actions::openOfficialDownloadPage,
            onOpenWebPage = actions::openVideoWebPage,
            onOpenOriginalComic = actions::openOriginalComic,
            onOpenShare = shareText,
            onCopyText = {
                copyTextToClipboard(it)
                scope.launch { AppToast.success(getString(Res.string.copy_to_clipboard)) }
            },
            onIntroductionLinkClick = actions::openIntroductionLink,
            stringLongPressShare = stringLongPressShare,
            pageHost = pageHost,
            wideRail = wideRail,
        )
    }

    // ── 弹幕（评论主源 + 弹弹增强层）─────────────────────────────────
    // 关联表的键：本地播放 videoCode 恒 "-1"，直接拿它当键会让**所有**本地文件
    // 共用同一条关联（换一部片子还留着上一部的弹幕源）。
    val danmakuVideoCode = route.videoCode.takeIf { it != "-1" }
        ?: route.localUri?.let { "local:$it" }
    // 评论流复用评论区的同一份 StateFlow：不为弹幕单开请求。
    // 评论 Tab 打开时也会调 ensureComments（命中 Success 直接返回），两边共享一次加载。
    val danmakuComments by commentViewModel.videoCommentFlow.collectAsStateWithLifecycle()
    danmakuVideoCode?.let { code ->
        LaunchedEffect(code) {
            if (SettingsRepository.current.danmakuCommentEnabled) {
                commentViewModel.ensureComments(VIDEO_COMMENT_PREFIX, code)
            }
        }
    }
    val danmakuSession = danmakuVideoCode?.let { code ->
        rememberDanmakuSession(
            videoCode = code,
            title = videoTitle,
            playbackState = playbackState,
            comments = danmakuComments,
        )
    }
    // 弹幕设置弹窗的开关由**页面层**持有：弹窗挂在播放器最上层槽位，
    // 不随底栏自动隐藏被销毁（底栏里挂弹窗，点开后一松手弹窗就跟着没）。
    // 换关联键（换片/换文件）时关掉，避免弹窗里留着上一部片的会话。
    var showDanmakuSettingsDialog by remember(danmakuVideoCode) { mutableStateOf(false) }

    VideoShellContent(
        isDualPane = isDualPane,
        isInPipMode = hostUiState.isInPipMode,
        isFullscreen = isFullscreen,
        sidebarVisible = sidebarVisible,
        onToggleSidebar = { sidebarVisible = it },
        isFavVideo = video?.isFav == true,
        onToggleFavoriteVideo = {
            video?.let(actions::toggleFavorite)
        },
        playerHeightDp = resolvedPlayerHeightDp,
        playbackEngine = playbackEngine,
        posterUrl = video?.coverUrl,
        // 与首页/搜索页的卡片封面配对（同 videoCode）
        sharedElementKey = route.videoCode.takeIf { it != "-1" }?.let(::coverSharedElementKey),
        title = videoTitle,
        currentTime = formatPlaybackTime(playbackState.engine.positionMs),
        totalTime = formatPlaybackTime(playbackState.engine.durationMs),
        progress = playbackProgress(
            playbackState.engine.positionMs,
            playbackState.engine.durationMs
        ),
        bufferedProgress = playbackProgress(
            playbackState.engine.bufferedPositionMs,
            playbackState.engine.durationMs,
        ),
        currentVolume = volume,
        currentBrightness = brightness,
        isPlaying = playbackState.engine.isPlaying,
        isPlaybackEnded = playbackState.engine.phase == PlaybackPhase.Ended,
        isLocked = isPlayerLocked,
        showPoster = !playbackState.engine.hasRenderedFirstFrame,
        showLoading = showLoading,
        showRetry = playbackState.engine.phase == PlaybackPhase.Error,
        errorMessage = playbackState.engine.errorMessage,
        brightnessGestureEnabled = platformHost.supportsBrightness(),
        onSeekBy = { deltaMs ->
            playbackController.seekBy(deltaMs)
            PlayerTrace.event("seek-by", "delta=${deltaMs}ms")
        },
        scale = videoScale,
        onScaleChange = { videoScale = it },
        durationMs = playbackState.engine.durationMs,
        fullscreenEnabled = platformHost.supportsFullscreen(),
        showResumeButton = showResumeButton,
        onPlayClick = {
            playbackController.togglePlayPause()
            // isPlaying 是点击**前**的值，所以这里记的是"点了要变成的状态"
            PlayerTrace.event("play-click", if (playbackState.engine.isPlaying) "暂停" else "播放")
        },
        onReplay = playbackController::replay,
        onBackClick = onBack,
        onHomeClick = onNavigateHome,
        onFullscreenClick = {
            if (isFullscreen) exitFullscreen() else enterFullscreen()
        },
        onLockClick = { isPlayerLocked = !isPlayerLocked },
        onProgressChange = { value ->
            val duration = playbackState.engine.durationMs
            if (duration > 0L) {
                playbackController.seekTo((duration * value).toLong())
                // 埋点：P3-1 后此处只在松手/手势结束时调一次（拖动期间零 seek），不会刷屏
                PlayerTrace.event("seek", "-> ${(value * 100).toInt()}%")
            }
        },
        onRetry = {
            PlayerTrace.event("retry")
            video?.let { info ->
                val qualities =
                    info.videoUrls.map { (label, link) ->
                        PlaybackQuality(label, link.link, mimeType = link.subtype?.let { "video/$it" })
                    }
                playbackController.load(
                    title = info.title,
                    qualities = qualities,
                    preferredQuality = SettingsRepository.videoQuality,
                    artworkUri = info.coverUrl,
                    // M5-3：从**失败发生的位置**重试。此前不传 startPositionMs（默认 0），
                    // 于是一次播放错误就把用户送回片头，"重试"名不副实。
                    startPositionMs = playbackController.state.value.engine.positionMs,
                )
            }
        },
        onResumeClick = {
            playbackController.seekTo(0L)
            showResumeButton = false
        },
        qualities = playbackState.qualities,
        selectedQuality = playbackState.qualities
            .getOrNull(playbackState.selectedQualityIndex)
            ?.label,
        onQualitySelected = playbackController::selectQuality,
        playbackSpeed = playbackState.engine.playbackSpeed,
        onPlaybackSpeedSelected = playbackController::setPlaybackSpeed,
        superResolutionLabel = stringResource(Res.string.player_anime4k_label),
        // 文案按**档位值**取，不按序号 —— 引擎哪天只给子集（比如只有 OFF/PERFORMANCE）也不会错位。
        superResolutionOptions = enhancementLevels.map { level ->
            stringResource(
                when (level) {
                    VideoEnhancementLevels.PERFORMANCE -> Res.string.super_resolution_performance
                    VideoEnhancementLevels.QUALITY -> Res.string.super_resolution_quality
                    else -> Res.string.super_resolution_off
                }
            )
        },
        selectedSuperResolutionIndex = enhancementLevels.indexOf(superResolutionIndex)
            .coerceAtLeast(0),
        onSuperResolutionSelected = { index ->
            val level = enhancementLevels.getOrNull(index) ?: VideoEnhancementLevels.OFF
            // 落盘存**请求值**（用户意图；换了引擎选择还在），显示取引擎回报的生效值。
            scope.launch {
                playbackController.setEnhancementLevel(level)?.let { superResolutionIndex = it }
                SettingsRepository.setSuperResolutionLevel(level)
            }
        },
        // G2-3b：画面比例 —— 引擎真实支持的档 + 引擎真实生效的值。
        // 选完即存偏好：下次起播由上面的 LaunchedEffect 下发。
        videoAspectOptions = aspectOptions,
        selectedVideoAspect = currentVideoAspect,
        onVideoAspectSelected = { mode ->
            playbackController.setVideoAspect(mode)
            scope.launch { SettingsRepository.setVideoAspect(mode) }
        },
        // M3-b/M3-c：能力判断（不用内核名）—— 引擎不支持抓帧时两个入口都自动不显示
        frameCaptureEnabled = playbackController.supportsFrameCapture,
        onOpenGifCapture = { showGifCapture = true },
        onCaptureScreenshot = ::captureScreenshot,
        onLongPressStart = {
            if (playbackState.engine.isPlaying) {
                val currentSpeed = playbackState.engine.playbackSpeed
                speedBeforeLongPress = currentSpeed
                playbackController.setPlaybackSpeed(
                    (currentSpeed * SettingsRepository.longPressSpeedTime).coerceAtMost(5f)
                )
            }
        },
        onLongPressEnd = {
            speedBeforeLongPress?.let(playbackController::setPlaybackSpeed)
            speedBeforeLongPress = null
        },
        onVolumeChange = { value ->
            volume = value
            playbackController.setVolume(value)
        },
        onBrightnessChange = { value ->
            brightness = value
            if (previousScreenBrightness == null) {
                previousScreenBrightness = platformHost.currentBrightness()
            }
            platformHost.applyBrightness(value.coerceIn(0.01f, 1f))
        },
        onProgressGesture = { value ->
            val duration = playbackState.engine.durationMs
            if (duration > 0L) playbackController.seekTo((duration * value).toLong())
        },
        progressGestureSensitivity = PlayerDefaults.PROGRESS_SLIDE_SENSITIVITY,
        // 引擎还没报尺寸时传 0（"未上报"），由播放器的 resolveVideoAspectRatio 兜底。
        // 这里不写死 16:9：兜底比例是**一个**所有者，两处各写一份迟早对不上。
        videoAspectRatio = if (
            playbackState.engine.videoWidth > 0 &&
            playbackState.engine.videoHeight > 0
        ) {
            playbackState.engine.videoWidth.toFloat() / playbackState.engine.videoHeight.toFloat()
        } else {
            0f
        },
        // 首帧未到不画：弹幕飘在海报上是穿帮。PiP 由 VideoShellContent 统一收掉。
        danmakuLayer = danmakuSession?.takeIf {
            playbackState.engine.hasRenderedFirstFrame
        }?.let { session ->
            @Composable {
                // 在 lambda **内**取：设置变了只重组弹幕这一槽，不动整个播放器组合
                val danmakuOptions = rememberDanmakuRenderOptions()
                DanmakuLayer(
                    driver = session,
                    modifier = Modifier.fillMaxSize(),
                    options = danmakuOptions,
                )
            }
        },
        // 双钮不等首帧：开关与设置在海报阶段就该能点（关联动作不依赖画面）。
        // 连关联键都没有时不摆——点了也没东西可关联，那才是假动作。
        danmakuControls = danmakuVideoCode?.let {
            @Composable {
                DanmakuControls(
                    session = danmakuSession,
                    // 有关联会话时开播放器内的设置弹窗；会话为 null（功能休眠）时
                    // 去设置页开——弹窗里的"状况/外观"对 null 会话无从下手。
                    onOpenSettings = {
                        if (danmakuSession != null) {
                            showDanmakuSettingsDialog = true
                        } else {
                            onOpenDanmakuSettings()
                        }
                    },
                )
            }
        },
        // 弹窗宿主：挂在播放器最上层槽位（见 VideoShellContent 的 dialogHost 文档）。
        dialogHost = danmakuSession?.let { session ->
            @Composable {
                if (showDanmakuSettingsDialog) {
                    DanmakuSettingsDialog(
                        session = session,
                        onOpenSettings = onOpenDanmakuSettings,
                        onDismiss = { showDanmakuSettingsDialog = false },
                    )
                }
            }
        },
        onPlayerBoundsChanged = { platformHost.setPipSourceRect(it) },
        onNextClick = nextPlaylistItem?.let { item ->
            { onNavigateToVideo(item.videoCode) }
        },
        autoPlayNext = autoPlayNext,
        onAutoPlayNextChange = { scope.launch { SettingsRepository.setAutoPlayNext(it) } },
        tabsContent = {
            HostVideoTabs(wideRail = false)
        },
        // Animeko 宽屏右栏：详情｜评论（弹幕占位条 + 标题收藏钮），左列纯播放器。
        // 非双栏传 null，Shell 回退窄屏行为。
        railTabsContent = if (isDualPane) {
            { HostVideoTabs(wideRail = true) }
        } else {
            null
        },
        modifier = Modifier.fillMaxSize(),
    )

    pendingUnsubscribeArtist?.let { artist ->
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.unsubscribe_artist),
            message = stringResource(Res.string.sure_to_unsubscribe),
            confirmText = stringResource(Res.string.sure),
            dismissText = stringResource(Res.string.no),
            onConfirm = {
                actions.confirmUnsubscribe(artist)
                pendingUnsubscribeArtist = null
            },
            onDismiss = { pendingUnsubscribeArtist = null },
        )
    }

    // M3-b：录 GIF。抓帧回调直连 controller（同包，无需 import）
    if (showGifCapture) {
        GifCaptureDialog(
            sourceWidth = playbackState.engine.videoWidth,
            sourceHeight = playbackState.engine.videoHeight,
            startPositionMs = playbackState.engine.positionMs,
            onDismiss = { showGifCapture = false },
            captureFrameAt = { positionMs, width, height ->
                playbackController.grabFrameArgb(positionMs, width, height)
            },
            // 平台「保存 / 分享」留在本层：同一套 exportMediaAndShare 也服务上面的截图路径
            exportGif = { bytes, fileName ->
                when (val export = exportMediaAndShare(bytes, fileName, "image/gif")) {
                    is MediaExportOutcome.Shared -> GifExportOutcome.Saved(export.location)
                    is MediaExportOutcome.SavedOnly -> GifExportOutcome.Saved(export.location)
                    is MediaExportOutcome.Failed -> GifExportOutcome.Failed(export.message)
                }
            },
        )
    }

    ConfirmDialog(
        visible = pendingLocalListAction != null,
        title = stringResource(Res.string.local_list_notice_title),
        message = stringResource(Res.string.local_list_notice_message),
        confirmText = stringResource(Res.string.continues),
        dismissText = stringResource(Res.string.do_not_show_again),
        onConfirm = {
            val action = pendingLocalListAction
            pendingLocalListAction = null
            action?.invoke()
        },
        onDismiss = { pendingLocalListAction = null },
        onDismissButtonClick = {
            val action = pendingLocalListAction
            pendingLocalListAction = null
            scope.launch {
                SettingsRepository.dismissLocalListNotice()
                action?.invoke()
            }
        },
    )

    ConfirmDialog(
        visible = pendingPlayback != null,
        title = stringResource(Res.string.warning),
        message = stringResource(Res.string.mobile_data_playback_warning),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            val request = pendingPlayback
            pendingPlayback = null
            mobilePlaybackConfirmed = true
            request?.let {
                playbackController.load(
                    title = it.title,
                    qualities = it.qualities,
                    preferredQuality = it.preferredQuality,
                    artworkUri = it.artworkUri,
                    startPositionMs = it.startPositionMs,
                    // M5-3：进入详情页是否自动播放由设置决定（默认**关**）。
                    // 关了之后：媒体仍然加载并渲染首帧，但停在暂停态 —— 用户看到
                    // "封面/首帧 + 中央播放键"，由自己决定何时开播（移动网络下尤其重要）。
                    playWhenReady = SettingsRepository.autoPlayOnEnter,
                )
            }
        },
        onDismiss = { pendingPlayback = null },
    )
}

/** 截图的 MIME：`MediaExport` 按它决定落点（非 GIF 一律进 screenshots 目录）。 */
private const val PNG_MIME = "image/png"

/** "上次看到结尾"的容差：起播位置距总时长不足这么久，就认为上次已经看完（M5-3）。 */
private const val NEAR_END_TOLERANCE_MS = 10_000L

private fun playbackProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    // M3：原 "%d:%02d" String.format（JVM-only）；padStart 等价实现。
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

private data class PendingPlayback(
    val title: String,
    val qualities: List<PlaybackQuality>,
    val preferredQuality: String?,
    val artworkUri: String?,
    val startPositionMs: Long,
)
