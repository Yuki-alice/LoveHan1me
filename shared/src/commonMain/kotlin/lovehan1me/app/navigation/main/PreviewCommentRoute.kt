package lovehan1me.app.navigation.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.core.constant.PREVIEW_COMMENT_PREFIX
import lovehan1me.data.SettingsRepository
import lovehan1me.Res
import lovehan1me.there_is_a_small_issue
import lovehan1me.latest_hanime_comment
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.feature.video.CommentMessage
import lovehan1me.feature.video.CommentScreen
import lovehan1me.feature.video.CommentViewModel
import lovehan1me.feature.video.PreviewCommentPrefetcher
import lovehan1me.app.sharedViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun PreviewCommentRouteScreen(
    route: PreviewCommentRoute,
    onBack: () -> Unit,
) {
    val viewModel: CommentViewModel = sharedViewModel(::CommentViewModel)
    val comments = viewModel.videoCommentFlow
    val commentState = viewModel.videoCommentStateFlow
    val commentUiState = remember(route.dateCode) {
        viewModel.getCommentUiState(route.dateCode)
    }
    val scope = rememberCoroutineScope()
    val prefetchedComments = PreviewCommentPrefetcher.here(viewModel)
        .commentFlow
        .collectAsStateWithLifecycle()
        .value
    val hasPrefetchedComments = prefetchedComments.isNotEmpty()
    val reportMessages = remember { kotlinx.coroutines.flow.MutableSharedFlow<CommentMessage>() }

    LaunchedEffect(route.dateCode, hasPrefetchedComments, prefetchedComments) {
        viewModel.code = route.dateCode
        if (hasPrefetchedComments) {
            viewModel.updateComments(route.dateCode, prefetchedComments)
        } else {
            viewModel.getComment(PREVIEW_COMMENT_PREFIX, route.dateCode)
        }
    }

    DisposableEffect(Unit) {
        PreviewCommentPrefetcher.here(viewModel)
            .tag(PreviewCommentPrefetcher.Scope.PREVIEW_COMMENT_ACTIVITY)
        onDispose {
            PreviewCommentPrefetcher.bye(PreviewCommentPrefetcher.Scope.PREVIEW_COMMENT_ACTIVITY)
        }
    }

    LaunchedEffect(Unit) {
        commentState.collect { state ->
            if (state is WebsiteState.Success) {
                viewModel.currentUserId = state.info.currentUserId
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.postCommentFlow.collect { state ->
            if (state is WebsiteState.Success) {
                viewModel.getComment(PREVIEW_COMMENT_PREFIX, route.dateCode)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.postReplyFlow.collect { state ->
            if (state is WebsiteState.Success) {
                viewModel.getComment(PREVIEW_COMMENT_PREFIX, route.dateCode)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.commentLikeFlow.collect { state ->
            if (state is WebsiteState.Success) {
                viewModel.handleCommentLike(state.info)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.reportMessage.collect { msg ->
            // P6c：VM 的 Message 已由 suspend getString 携带文本，不再传 R-int
            reportMessages.emit(CommentMessage(msg.text))
        }
    }

    HanimeScaffold(
        topBarWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        title = stringResource(Res.string.latest_hanime_comment, route.date),
        onBack = onBack,
    ) { paddingValues ->
        CommentScreen(
            commentsFlow = comments,
            commentStateFlow = commentState,
            reportMessageFlow = reportMessages,
            postCommentStateFlow = viewModel.postCommentFlow,
            postReplyStateFlow = viewModel.postReplyFlow,
            replyThreadsFlow = viewModel.replyThreads,
            onLoadReplies = viewModel::loadReplies,
            currentSortType = viewModel.currentSortType,
            reportReasons = viewModel.reportReason,
            isPreviewCommentPrefetched = hasPrefetchedComments,
            isAlreadyLogin = SettingsRepository.isAlreadyLogin,
            onRefresh = { viewModel.getComment(PREVIEW_COMMENT_PREFIX, route.dateCode) },
            onReply = { comment, text ->
                if (!SettingsRepository.isAlreadyLogin) return@CommentScreen
                val replyTargetId = comment.replyTargetIdOrNull
                if (replyTargetId == null) {
                    scope.launch {
                        reportMessages.emit(CommentMessage(getString(Res.string.there_is_a_small_issue)))
                    }
                    return@CommentScreen
                }
                viewModel.postReply(replyTargetId, text)
            },
            onReport = { comment, reason ->
                viewModel.reportComment(
                    reason.reasonKey ?: reason.value,
                    viewModel.currentUserId,
                    "${SettingsRepository.baseUrl}watch?v=${viewModel.code}",
                    comment.reportableType,
                    comment.reportableId,
                )
            },
            onThumbUp = { comment ->
                if (!SettingsRepository.isAlreadyLogin) return@CommentScreen
                if (comment.isChildComment) {
                    viewModel.likeChildComment(
                        true,
                        0,
                        comment,
                        likeCommentStatus = comment.post.likeCommentStatus
                    )
                } else {
                    viewModel.likeComment(
                        true,
                        0,
                        comment,
                        likeCommentStatus = comment.post.likeCommentStatus
                    )
                }
            },
            onThumbDown = { comment ->
                if (!SettingsRepository.isAlreadyLogin) return@CommentScreen
                if (comment.isChildComment) {
                    viewModel.likeChildComment(
                        false,
                        0,
                        comment,
                        unlikeCommentStatus = comment.post.unlikeCommentStatus
                    )
                } else {
                    viewModel.likeComment(
                        false,
                        0,
                        comment,
                        unlikeCommentStatus = comment.post.unlikeCommentStatus
                    )
                }
            },
            onSortChange = viewModel::setSortType,
            onComposeComment = { text ->
                viewModel.currentUserId?.let { id ->
                    viewModel.postComment(id, viewModel.code, PREVIEW_COMMENT_PREFIX, text)
                } ?: scope.launch {
                    reportMessages.emit(CommentMessage(getString(Res.string.there_is_a_small_issue)))
                }
            },
            listContentPadding = PaddingValues(vertical = 8.dp),
            initialFirstVisibleItemIndex = commentUiState.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = commentUiState.firstVisibleItemScrollOffset,
            onCommentScrollChange = { index, offset ->
                viewModel.setCommentScrollState(route.dateCode, index, offset)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}
