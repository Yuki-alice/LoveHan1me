package lovehan1me.feature.video

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.data.SettingsRepository
import lovehan1me.data.SettingsRepository.isAlreadyLogin
import lovehan1me.Res
import lovehan1me.comment
import lovehan1me.related_video
import lovehan1me.there_is_a_small_issue
import lovehan1me.core.constant.VIDEO_COMMENT_PREFIX
import lovehan1me.data.getHanimeShareText
import lovehan1me.data.database.entity.CheckInRecordEntity
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.HanimeTheme
import lovehan1me.feature.video.CommentViewModel
import lovehan1me.feature.video.VideoViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun RenderVideoIntroductionContent(
    videoCode: String,
    viewModel: VideoViewModel,
    pendingDownloadPrompt: DownloadPromptState?,
    onPendingDownloadPromptChange: (DownloadPromptState?) -> Unit,
    onOpenVideo: (HanimeInfo) -> Unit,
    onOpenArtist: (HanimeVideo.Artist) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onToggleSubscribe: (HanimeVideo.Artist) -> Unit,
    onToggleFavorite: (HanimeVideo) -> Unit,
    onRequestManageMyList: (() -> Unit) -> Unit,
    onRateVideo: (HanimeVideo, Boolean) -> Unit,
    onManageMyList: (HanimeVideo.MyList?, List<Boolean>) -> Unit,
    onQuickCheckIn: (CheckInRecordEntity) -> Unit,
    onPrepareDownload: (String, HanimeVideo?) -> Unit,
    onConfirmDownloadPrompt: (HanimeVideo?, Boolean) -> Unit,
    onRequestOpenOfficialDownloadPage: () -> Unit,
    onOpenWebPage: () -> Unit,
    onOpenOriginalComic: (String) -> Unit,
    onOpenShare: (String, String) -> Unit,
    onCopyText: (String) -> Unit,
    onIntroductionLinkClick: (String) -> Unit,
    stringLongPressShare: String,
) {
    val videoState = viewModel.hanimeVideoStateFlow.collectAsStateWithLifecycle().value
    val video = viewModel.displayVideoFlow.collectAsStateWithLifecycle().value
    val checkInEnabled by SettingsRepository.checkInEnabledFlow.collectAsStateWithLifecycle()
    val videoShareText = video?.title?.let { title ->
        getHanimeShareText(title, videoCode)
    }.orEmpty()
    val introScrollState = viewModel.getIntroScrollState(videoCode)

    HanimeTheme {
        VideoIntroductionScreen(
            video = video,
            state = videoState,
            fromDownload = viewModel.fromDownload,
            hideRelatedInIntro = viewModel.hideRelatedInIntro,
            playlistInitialIndex = viewModel.getPlaylistFirstVisibleIndex(videoCode),
            introFirstVisibleItemIndex = introScrollState.firstVisibleItemIndex,
            introFirstVisibleItemScrollOffset = introScrollState.firstVisibleItemScrollOffset,
            downloadPrompt = pendingDownloadPrompt,
            onRetry = { viewModel.getHanimeVideo(videoCode) },
            onOpenVideo = onOpenVideo,
            onOpenArtist = onOpenArtist,
            onNavigateToSearch = { tag ->
                onNavigateToSearch(viewModel.resolveTagSearchKey(tag))
            },
            onToggleSubscribe = onToggleSubscribe,
            onToggleFavorite = { video?.let(onToggleFavorite) },
            onRequestManageMyList = onRequestManageMyList,
            onRateVideo = { isPositive ->
                video?.let { onRateVideo(it, isPositive) }
            },
            onManageMyList = { _, selectedStates ->
                onManageMyList(video?.myList, selectedStates)
            },
            checkInEnabled = checkInEnabled,
            onQuickCheckIn = onQuickCheckIn,
            onPrepareDownload = { quality ->
                onPrepareDownload(quality, video)
            },
            onDismissDownloadPrompt = {
                onPendingDownloadPromptChange(null)
            },
            onConfirmDownloadPrompt = { autoCreateGroup ->
                onConfirmDownloadPrompt(video, autoCreateGroup)
            },
            onRequestOpenOfficialDownloadPage = onRequestOpenOfficialDownloadPage,
            onShare = {
                onOpenShare(videoShareText, stringLongPressShare)
            },
            onCopyShareText = {
                if (videoShareText.isNotBlank()) {
                    onCopyText(videoShareText)
                }
            },
            onOpenWebPage = onOpenWebPage,
            onOpenOriginalComic = video?.originalComic
                ?.takeIf { it.isNotBlank() }
                ?.let { comicLink -> { onOpenOriginalComic(comicLink) } },
            onShowAllPlaylist = if (!viewModel.fromDownload && video?.playlist != null) {
                {}
            } else {
                null
            },
            onPlaylistScrollChange = { index ->
                viewModel.setPlaylistFirstVisibleIndex(videoCode, index)
            },
            onIntroductionScrollChange = { index, offset ->
                viewModel.setIntroScrollState(videoCode, index, offset)
            },
            onIntroductionLinkClick = onIntroductionLinkClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderVideoCommentContent(
    videoCode: String,
    viewModel: CommentViewModel,
    reportMessages: MutableSharedFlow<CommentMessage>,
    getMessageText: (CommentViewModel.Message) -> String,
    pageHost: VideoPageHost? = null,
) {
    val commentUiState = remember(videoCode) {
        viewModel.getCommentUiState(videoCode)
    }
    var childCommentId by remember { mutableStateOf(commentUiState.childCommentId) }
    val childSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.PartiallyExpanded,
            SheetValue.Expanded,
        ),
    )
    val scope = rememberCoroutineScope()

    HanimeTheme {
        LaunchedEffect(videoCode) {
            viewModel.code = videoCode
            viewModel.getComment(VIDEO_COMMENT_PREFIX, videoCode)
        }

        LaunchedEffect(Unit) {
            viewModel.videoCommentStateFlow.collect { state ->
                if (state is WebsiteState.Success) {
                    viewModel.currentUserId = state.info.currentUserId
                    pageHost?.showCommentBadge(state.info.videoComment.size)
                }
            }
        }

        childCommentId?.let { currentCommentId ->
            ModalBottomSheet(
                onDismissRequest = {
                    childCommentId = null
                    viewModel.setChildCommentId(videoCode, null)
                    viewModel.clearVideoReplyList()
                },
                sheetState = childSheetState,
                containerColor = HanimeDefaults.Colors.pageSurface,
            ) {
                LaunchedEffect(currentCommentId) {
                    viewModel.getCommentReply(currentCommentId)
                }
                val childReportFlow = remember(viewModel.reportMessage) {
                    viewModel.reportMessage.map { message ->
                        // P6c：VM Message 已携带文本
                        CommentMessage(message.text)
                    }
                }
                ChildCommentScreen(
                    commentsFlow = viewModel.videoReplyFlow,
                    commentStateFlow = viewModel.videoReplyStateFlow,
                    reportMessageFlow = childReportFlow,
                    postReplyStateFlow = viewModel.postReplyFlow,
                    commentLikeStateFlow = viewModel.commentLikeFlow,
                    reportReasons = viewModel.reportReason,
                    isAlreadyLogin = isAlreadyLogin,
                    onRefresh = { viewModel.getCommentReply(currentCommentId) },
                    onReply = { _, text ->
                        viewModel.postReply(currentCommentId, text)
                    },
                    onReport = { comment, reason ->
                        viewModel.reportComment(
                            reason.reasonKey ?: reason.value,
                            viewModel.currentUserId,
                            "${SettingsRepository.baseUrl}watch?v=${videoCode}",
                            comment.reportableType,
                            comment.reportableId,
                        )
                    },
                    onThumbUp = { comment ->
                        viewModel.likeChildComment(
                            true,
                            0,
                            comment,
                            likeCommentStatus = comment.post.likeCommentStatus,
                        )
                    },
                    onThumbDown = { comment ->
                        viewModel.likeChildComment(
                            false,
                            0,
                            comment,
                            unlikeCommentStatus = comment.post.unlikeCommentStatus,
                        )
                    },
                    onCommentLikeSuccess = viewModel::handleCommentLike,
                    onReplyStateChange = { isReplying ->
                        if (isReplying) {
                            scope.launch { childSheetState.expand() }
                        }
                    },
                )
            }
        }

        val sharedReportFlow = remember(reportMessages) { reportMessages.asSharedFlow() }
        CommentScreen(
            commentsFlow = viewModel.videoCommentFlow,
            commentStateFlow = viewModel.videoCommentStateFlow,
            reportMessageFlow = sharedReportFlow,
            currentSortType = viewModel.currentSortType,
            reportReasons = viewModel.reportReason,
            isPreviewCommentPrefetched = false,
            isAlreadyLogin = isAlreadyLogin,
            onRefresh = { viewModel.getComment(VIDEO_COMMENT_PREFIX, videoCode) },
            onReply = { comment, text ->
                if (!isAlreadyLogin) return@CommentScreen
                val replyTargetId = comment.replyTargetIdOrNull
                if (replyTargetId == null) {
                    scope.launch {
                        reportMessages.emit(CommentMessage(getMessageText(CommentViewModel.Message(getString(Res.string.there_is_a_small_issue)))))
                    }
                    return@CommentScreen
                }
                viewModel.postReply(replyTargetId, text)
            },
            onReport = { comment, reason ->
                viewModel.reportComment(
                    reason.reasonKey ?: reason.value,
                    viewModel.currentUserId,
                    "${SettingsRepository.baseUrl}watch?v=${videoCode}",
                    comment.reportableType,
                    comment.reportableId,
                )
            },
            onThumbUp = { comment ->
                if (!isAlreadyLogin) return@CommentScreen
                if (comment.isChildComment) {
                    viewModel.likeChildComment(
                        true,
                        0,
                        comment,
                        likeCommentStatus = comment.post.likeCommentStatus,
                    )
                } else {
                    viewModel.likeComment(
                        true,
                        0,
                        comment,
                        likeCommentStatus = comment.post.likeCommentStatus,
                    )
                }
            },
            onThumbDown = { comment ->
                if (!isAlreadyLogin) return@CommentScreen
                if (comment.isChildComment) {
                    viewModel.likeChildComment(
                        false,
                        0,
                        comment,
                        unlikeCommentStatus = comment.post.unlikeCommentStatus,
                    )
                } else {
                    viewModel.likeComment(
                        false,
                        0,
                        comment,
                        unlikeCommentStatus = comment.post.unlikeCommentStatus,
                    )
                }
            },
            onViewMoreReplies = { comment ->
                val replyTargetId = comment.replyTargetIdOrNull
                if (replyTargetId == null) {
                    scope.launch {
                        reportMessages.emit(CommentMessage(getMessageText(CommentViewModel.Message(getString(Res.string.there_is_a_small_issue)))))
                    }
                    return@CommentScreen
                }
                childCommentId = replyTargetId
                viewModel.setChildCommentId(videoCode, replyTargetId)
            },
            onSortChange = { viewModel.setSortType(it) },
            onComposeComment = {
                viewModel.currentUserId?.let { id ->
                    viewModel.postComment(id, videoCode, VIDEO_COMMENT_PREFIX, it)
                } ?: scope.launch {
                    reportMessages.emit(CommentMessage(getMessageText(CommentViewModel.Message(getString(Res.string.there_is_a_small_issue)))))
                }
            },
            initialFirstVisibleItemIndex = commentUiState.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = commentUiState.firstVisibleItemScrollOffset,
            onCommentScrollChange = { index, offset ->
                viewModel.setCommentScrollState(videoCode, index, offset)
            },
        )
    }
}

/**
 * 宽屏右栏 Tab：**相关推荐｜评论**（设计稿 §宽屏右栏）。
 *
 * 默认相关推荐，切到评论后列表独立滚动、播放器继续播（边看边刷）。
 * 评论页直接复用 [RenderVideoCommentContent]（弹窗看二级回复、滚动记忆全在里面）；
 * 相关页复用简介底部的 [RelatedVideosSection]（同卡片同网格）。
 * 窄屏/经典双栏不用它，走简介/评论双 Tab。
 */
@Composable
fun VideoRailTabsContent(
    videoCode: String,
    relatedItems: List<HanimeInfo>,
    onOpenVideo: (HanimeInfo) -> Unit,
    commentViewModel: CommentViewModel,
    pageHost: VideoPageHost,
    commentsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tabs = remember(commentsEnabled) {
        buildList {
            add(VideoTabItem(Res.string.related_video))
            if (commentsEnabled) add(VideoTabItem(Res.string.comment))
        }
    }
    var selectedTabIndex by rememberSaveable(videoCode) { mutableIntStateOf(0) }
    // 评论被关掉时只剩相关一页，选中态 clip 回来（读时裁，不写 state，避免组合内写入）。
    val safeTabIndex = selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    VideoTabsContent(
        tabs = tabs,
        selectedTabIndex = safeTabIndex,
        onSelectedTabChange = { selectedTabIndex = it },
        modifier = modifier.fillMaxSize(),
    ) { page ->
        if (page == 0) {
            RelatedVideosSection(
                videos = relatedItems,
                onOpenVideo = onOpenVideo,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            RenderVideoCommentContent(
                videoCode = videoCode,
                viewModel = commentViewModel,
                reportMessages = remember { kotlinx.coroutines.flow.MutableSharedFlow() },
                getMessageText = { message ->
                    // 与 VideoRouteContent 评论页同语义：VM Message 已携带文本。
                    message.text
                },
                pageHost = pageHost,
            )
        }
    }
}
