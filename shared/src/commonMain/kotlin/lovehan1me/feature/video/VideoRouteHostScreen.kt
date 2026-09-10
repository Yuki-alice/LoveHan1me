package lovehan1me.feature.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import lovehan1me.ui.adaptive.WindowWidthSizeClass
import lovehan1me.ui.adaptive.rememberContentWidthSizeClass
import lovehan1me.Res
import lovehan1me.add_failed
import lovehan1me.add_success
import lovehan1me.copy_to_clipboard
import lovehan1me.fail_to_get_video_link
import lovehan1me.large_screen_tablet_mode_hint
import lovehan1me.local_favorite_cancelled
import lovehan1me.modify_failed
import lovehan1me.modify_success
import lovehan1me.pause_then_long_press
import lovehan1me.video_might_not_exist
import lovehan1me.add_to_h_keyframe
import lovehan1me.app.navigation.main.cancel
import lovehan1me.confirm
import lovehan1me.current_position_d_ms
import lovehan1me.long_press_share_to_copy
import lovehan1me.mobile_data_playback_warning
import lovehan1me.no
import lovehan1me.play_pause
import lovehan1me.player_untitled_video
import lovehan1me.sure
import lovehan1me.sure_to_add_to_h_keyframe
import lovehan1me.sure_to_unsubscribe
import lovehan1me.unsubscribe_artist
import lovehan1me.core.util.warning
import lovehan1me.player_keyframe_option
import lovehan1me.player_h_keyframe
import lovehan1me.super_resolution_off
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_quality
import lovehan1me.player_anime4k_label
import lovehan1me.local_list_notice_title
import lovehan1me.local_list_notice_message
import lovehan1me.do_not_show_again
import lovehan1me.continues
import lovehan1me.data.getHanimeVideoLink
import lovehan1me.data.DatabaseRepo
import lovehan1me.data.database.entity.HKeyframeEntity
import lovehan1me.data.database.entity.WatchHistoryEntity
import lovehan1me.core.domain.exception.ParseException
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.core.domain.model.VideoLandscapeLayoutStyle
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.app.bridge.NoopVideoPageHost
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.app.navigation.main.SearchRoute
import lovehan1me.app.navigation.main.VideoRoute
import lovehan1me.feature.player.BindOrientationAutoFullscreen
import lovehan1me.feature.player.ComposePlaybackController
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackPhase
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlayerKernel
import lovehan1me.feature.player.createPlaybackEngine
import lovehan1me.feature.player.isActiveNetworkMetered
import lovehan1me.feature.video.CommentViewModel
import lovehan1me.feature.video.VideoViewModel
import lovehan1me.app.sharedViewModel
import lovehan1me.core.util.decodeComposeAsset
import lovehan1me.core.util.SonnerToast
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.core.util.rememberShareText
import kotlinx.coroutines.launch
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
    onEnqueueDownload: (EnqueueDownloadRequest) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val copyTextToClipboard = rememberCopyTextToClipboard()
    val shareText = rememberShareText()
    val viewModel: VideoViewModel = sharedViewModel(::VideoViewModel)
    val commentViewModel: CommentViewModel = sharedViewModel(::CommentViewModel)
    val kernel = remember { PlayerKernel.fromPreference(SettingsRepository.switchPlayerKernel) }
    val playbackEngine: PlaybackEngine = remember(route.videoCode, route.localUri, kernel) {
        createPlaybackEngine(
            kernel = kernel,
            allowCast = SettingsRepository.enableGoogleCast &&
                    route.localUri == null && route.videoCode != "-1",
        )
    }
    val playbackController = remember(playbackEngine) { ComposePlaybackController(playbackEngine) }
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
    val appSettings by SettingsRepository.settings.collectAsStateWithLifecycle()
    // 大屏判定：改用统一断点 + **内容区可用宽度**。
    // 演进链：原 LocalConfiguration.smallestScreenWidthDp（Android 设备物理属性，桌面无此语义）
    // → 窗口 dp 宽 → 内容区宽。常驻抽屉会占掉约 360dp，用整窗宽会在 1000dp 窗口
    // （内容区仅约 640dp）误判为大屏。
    val isLargeScreenDevice =
        rememberContentWidthSizeClass() >= WindowWidthSizeClass.Medium
    val hostUiState by viewModel.videoHostUiStateFlow.collectAsStateWithLifecycle()
    val videoState by viewModel.hanimeVideoStateFlow.collectAsStateWithLifecycle()
    val video = viewModel.hanimeVideoFlow.collectAsStateWithLifecycle().value
    val relatedItems = video?.relatedHanimes.orEmpty()

    LaunchedEffect(playbackController) {
        playbackController.setPlaybackSpeed(SettingsRepository.playerSpeed)
    }
    LaunchedEffect(isLargeScreenDevice) {
        val currentSettings = SettingsRepository.current
        if (
            isLargeScreenDevice &&
            !currentSettings.tabletMode &&
            !currentSettings.largeScreenTabletModeHintShown
        ) {
            SettingsRepository.update {
                it.copy(largeScreenTabletModeHintShown = true)
            }
            SonnerToast.info(getString(Res.string.large_screen_tablet_mode_hint))
        }
    }
    val stringLongPressShare = stringResource(Res.string.long_press_share_to_copy)
    val pipPlayPauseText = stringResource(Res.string.play_pause)
    val untitledVideoText = stringResource(Res.string.player_untitled_video)
    val genres = remember(SettingsRepository.baseUrl) {
        decodeComposeAsset<List<SearchOption>>(
            if (SettingsRepository.baseUrl == lovehan1me.core.constant.HanimeConstants.HANIME_URL[3]) {
                "files/search_options/genre_av.json"
            } else {
                "files/search_options/genre.json"
            }
        ).orEmpty()
    }

    var checkedQuality by remember(
        route.videoCode,
        route.localUri
    ) { mutableStateOf<String?>(null) }
    var pendingDownloadPrompt by remember(route.videoCode, route.localUri) {
        mutableStateOf<DownloadPromptState?>(null)
    }
    var videoTitle by remember(route.videoCode, route.localUri) { mutableStateOf("") }
    var isSideRelatedCollapsed by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isPlayerLocked by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(1f) }
    var brightness by remember { mutableStateOf(platformHost.currentBrightness()) }
    var previousScreenBrightness by remember { mutableStateOf<Float?>(null) }
    var speedBeforeLongPress by remember { mutableStateOf<Float?>(null) }
    var showResumeButton by remember { mutableStateOf(false) }
    var pendingPlayback by remember { mutableStateOf<PendingPlayback?>(null) }
    var mobilePlaybackConfirmed by remember(route.videoCode, route.localUri) {
        mutableStateOf(false)
    }
    var showAddHKeyframeDialog by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var hKeyframes by remember { mutableStateOf<HKeyframeEntity?>(null) }
    var superResolutionIndex by remember { mutableStateOf(0) }
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
        val engineState = playbackController.state.value.engine
        platformHost.applyFullscreen(
            true,
            forceLandscape = forceLandscape ||
                    !(engineState.videoWidth > 0 && engineState.videoHeight > engineState.videoWidth),
        )
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
                return !state.isCasting &&
                        state.phase == PlaybackPhase.Ready &&
                        (state.isPlaying || state.positionMs > 0L)
            }

            override fun enterPipMode() {
                val state = playbackController.state.value.engine
                platformHost.enterPipMode(state.isPlaying, pipPlayPauseText)
            }

            override fun onPipModeChanged(isInPip: Boolean) {
                viewModel.setPipMode(isInPip)
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
        onDispose {
            onRegisterPageHost?.invoke(null)
            platformHost.onHostStopped()
            playbackController.release()
            if (isFullscreen) {
                platformHost.applyFullscreen(false)
            }
        }
    }

    BindOrientationAutoFullscreen(
        enabled = !appSettings.tabletMode,
        onLandscape = { enterFullscreen(forceLandscape = true) },
        onPortrait = { exitFullscreen() },
    )

    DisposableEffect(lifecycleOwner, playbackController, route.videoCode, appSettings.tabletMode) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (route.videoCode != "-1") {
                        val progress = playbackController.state.value.engine.positionMs
                        scope.launch {
                            DatabaseRepo.WatchHistory.updateProgress(
                                route.videoCode,
                                progress
                            )
                        }
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    if (!hostUiState.isInPipMode &&
                        !playbackController.state.value.engine.isCasting
                    ) {
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

    LaunchedEffect(route.videoCode, route.localUri) {
        checkedQuality = null
        pendingDownloadPrompt = null
        videoTitle = ""
        viewModel.videoCode = route.videoCode
        viewModel.fromDownload = route.videoCode == "-1" || route.localUri != null
        viewModel.getHanimeVideo(route.videoCode, route.localUri)
    }

    LaunchedEffect(route.videoCode, route.localUri, playbackController, viewModel.fromDownload) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            viewModel.hanimeVideoStateFlow.collect { state ->
                when (state) {
                    is VideoLoadingState.Error -> {
                        state.throwable.message?.let(SonnerToast::error)
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
                            SonnerToast.error(getString(Res.string.fail_to_get_video_link))
                            uriHandler.openUri(getHanimeVideoLink(route.videoCode))
                        } else {
                            val history = DatabaseRepo.WatchHistory.findBy(route.videoCode)
                            showResumeButton = SettingsRepository.allowResumePlayback &&
                                    (history?.progress ?: 0L) > 5_000L
                            val request = PendingPlayback(
                                title = info.title,
                                qualities = qualities,
                                preferredQuality = SettingsRepository.videoQuality,
                                artworkUri = info.coverUrl,
                                startPositionMs = history?.progress ?: 0L,
                            )
                            if (!viewModel.fromDownload &&
                                !SettingsRepository.disableMobileDataWarning &&
                                !mobilePlaybackConfirmed &&
                                isActiveNetworkMetered()
                            ) {
                                pendingPlayback = request
                            } else {
                                playbackController.load(
                                    title = request.title,
                                    qualities = request.qualities,
                                    preferredQuality = request.preferredQuality,
                                    artworkUri = request.artworkUri,
                                    startPositionMs = request.startPositionMs,
                                    playWhenReady = true,
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

                    is VideoLoadingState.NoContent -> SonnerToast.error(getString(Res.string.video_might_not_exist))
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

    LaunchedEffect(route.videoCode) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            viewModel.observeKeyframe(route.videoCode).collect {
                hKeyframes = it
                viewModel.hKeyframes = it
            }
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            launch {
                viewModel.localFavoriteActionFlow.collect { state ->
                    when (state) {
                        is WebsiteState.Error -> SonnerToast.error(getString(Res.string.add_failed))
                        is WebsiteState.Success -> SonnerToast.success(
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
                        is WebsiteState.Error -> SonnerToast.error(getString(Res.string.modify_failed))
                        is WebsiteState.Success -> SonnerToast.success(getString(Res.string.modify_success))
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

    LaunchedEffect(showResumeButton) {
        if (showResumeButton) {
            kotlinx.coroutines.delay(5_000L.milliseconds)
            showResumeButton = false
        }
    }

    val countdownLabel = remember(playbackState.engine.positionMs, hKeyframes, isFullscreen) {
        if (!isFullscreen || !SettingsRepository.hKeyframesEnable) {
            null
        } else {
            hKeyframes?.keyframes.orEmpty().mapIndexedNotNull { index, keyframe ->
                val remaining = keyframe.position - playbackState.engine.positionMs
                if (remaining in 0L until SettingsRepository.whenCountdownRemind) {
                    val time = if (remaining >= 1000L) {
                        ((remaining / 1000L) + 1L).toString()
                    } else {
                        // M3：原 "%.1f".format（JVM-only）；整数拼一位小数。
                        val tenths = remaining / 100L
                        "${tenths / 10L}.${tenths % 10L}"
                    }
                    if (SettingsRepository.showCommentWhenCountdown && !keyframe.prompt.isNullOrBlank()) {
                        "#${index + 1} ${keyframe.prompt}\n$time"
                    } else {
                        time
                    }
                } else {
                    null
                }
            }.firstOrNull()
        }
    }

    val resolvedPlayerHeightDp = when {
        hostUiState.isInPipMode -> null
        appSettings.tabletMode -> if (isSideRelatedCollapsed) 500.dp else 400.dp
        else -> 250.dp
    }

    LaunchedEffect(resolvedPlayerHeightDp, hostUiState.playerHeightDp) {
        if (hostUiState.playerHeightDp != resolvedPlayerHeightDp) {
            viewModel.setPlayerHeightDp(resolvedPlayerHeightDp)
        }
    }

    VideoShellContent(
        isTabletMode = appSettings.tabletMode,
        isInPipMode = hostUiState.isInPipMode,
        isFullscreen = isFullscreen,
        playerHeightDp = resolvedPlayerHeightDp,
        playbackEngine = playbackEngine,
        posterUrl = video?.coverUrl,
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
        showCastButton = playbackState.engine.isCastSupported,
        isCasting = playbackState.engine.isCasting,
        castDeviceName = playbackState.engine.castDeviceName,
        isLocked = isPlayerLocked,
        showPoster = !playbackState.engine.hasRenderedFirstFrame,
        showLoading =
            videoState is VideoLoadingState.Loading ||
                    playbackState.engine.phase == PlaybackPhase.Preparing,
        showRetry = playbackState.engine.phase == PlaybackPhase.Error,
        showResumeButton = showResumeButton,
        onPlayClick = playbackController::togglePlayPause,
        onReplay = playbackController::replay,
        onBackClick = onBack,
        onHomeClick = onNavigateHome,
        onFullscreenClick = {
            if (isFullscreen) exitFullscreen() else enterFullscreen()
        },
        onLockClick = { isPlayerLocked = !isPlayerLocked },
        onProgressChange = { value ->
            val duration = playbackState.engine.durationMs
            if (duration > 0L) playbackController.seekTo((duration * value).toLong())
        },
        onRetry = {
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
        superResolutionOptions = if (kernel == PlayerKernel.MpvPlayer && !playbackState.engine.isCasting) {
            listOf(
                stringResource(Res.string.super_resolution_off),
                stringResource(Res.string.super_resolution_performance),
                stringResource(Res.string.super_resolution_quality),
            )
        } else {
            emptyList()
        },
        selectedSuperResolutionIndex = superResolutionIndex,
        onSuperResolutionSelected = { index ->
            superResolutionIndex = index
            playbackEngine.setSuperResolution(index)
        },
        hKeyframeLabel = stringResource(Res.string.player_h_keyframe),
        isHKeyframesEnabled = SettingsRepository.hKeyframesEnable,
        hKeyframeOptions = hKeyframes?.keyframes.orEmpty().mapIndexed { index, keyframe ->
            stringResource(Res.string.player_keyframe_option,
                index + 1,
                formatPlaybackTime(keyframe.position),
            )
        },
        hKeyframes = hKeyframes?.keyframes.orEmpty(),
        isHKeyframeLocal = hKeyframes?.author == null,
        onHKeyframeSelected = { index ->
            hKeyframes?.keyframes?.getOrNull(index)?.position?.let(playbackController::seekTo)
        },
        onHKeyframeUpdated = { oldKeyframe, newKeyframe ->
            viewModel.modifyHKeyframe(route.videoCode, oldKeyframe, newKeyframe)
        },
        onHKeyframeDeleted = { keyframe ->
            viewModel.removeHKeyframe(route.videoCode, keyframe)
        },
        onHKeyframeLongPress = {
            if (playbackState.engine.isPlaying) {
                scope.launch { SonnerToast.info(getString(Res.string.pause_then_long_press)) }
            } else {
                showAddHKeyframeDialog = playbackState.engine.positionMs to videoTitle.ifBlank {
                    untitledVideoText
                }
            }
        },
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
        progressGestureSensitivity = realProgressSensitivity(SettingsRepository.slideSensitivity),
        countdownLabel = countdownLabel,
        videoAspectRatio = if (
            playbackState.engine.videoWidth > 0 &&
            playbackState.engine.videoHeight > 0
        ) {
            playbackState.engine.videoWidth.toFloat() / playbackState.engine.videoHeight.toFloat()
        } else {
            16f / 9f
        },
        onPlayerBoundsChanged = { platformHost.setPipSourceRect(it) },
        tabsContent = {
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
                onOpenArtist = actions::openArtistSearch,
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
                    scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
                },
                onIntroductionLinkClick = actions::openIntroductionLink,
                stringLongPressShare = stringLongPressShare,
                pageHost = pageHost,
            )
        },
        classicTabletLayout = if (
            appSettings.tabletMode &&
            appSettings.videoLandscapeLayoutStyle == VideoLandscapeLayoutStyle.Classic
        ) {
            ClassicTabletLayoutConfig(
                relatedItems = relatedItems,
                onHideRelatedInIntroChange = { viewModel.hideRelatedInIntro = it },
                onSideRelatedCollapsedChange = { isSideRelatedCollapsed = it },
                onOpenVideo = { item -> onNavigateToVideo(item.videoCode) },
            )
        } else {
            null
        },
        modifier = Modifier.fillMaxSize(),
    )

    showAddHKeyframeDialog?.let { (currentPosition, title) ->
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.add_to_h_keyframe),
            message = buildString {
                appendLine(stringResource(Res.string.sure_to_add_to_h_keyframe))
                append(stringResource(Res.string.current_position_d_ms, currentPosition))
            },
            confirmText = stringResource(Res.string.confirm),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                viewModel.appendHKeyframe(
                    route.videoCode,
                    title,
                    HKeyframeEntity.Keyframe(position = currentPosition, prompt = null),
                )
                showAddHKeyframeDialog = null
            },
            onDismiss = { showAddHKeyframeDialog = null },
        )
    }

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
                    playWhenReady = true,
                )
            }
        },
        onDismiss = { pendingPlayback = null },
    )
}

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

private fun realProgressSensitivity(value: Int): Float {
    val clampedValue = value.coerceIn(1, 7)
    return 4f - (clampedValue - 1) * (3.5f / 6f)
}
