package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.sort_most_likes
import lovehan1me.sort_most_dislikes
import lovehan1me.sort_comment
import lovehan1me.sort_by_replies
import lovehan1me.sort_by_oldest
import lovehan1me.sort_by_newest
import lovehan1me.login_first
import lovehan1me.load_failed_retry
import lovehan1me.comment_too_short
import lovehan1me.comment_not_found
import lovehan1me.comment
import lovehan1me.ic_reply
import lovehan1me.core.domain.model.ReportReason
import lovehan1me.core.domain.model.VideoComments
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.ui.component.CommentReplyBar
import lovehan1me.ui.component.CommentReportDialog
import lovehan1me.ui.component.PageContent
import lovehan1me.ui.component.VideoCommentCard
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.core.util.parseTimeStrToMinutes
import lovehan1me.core.util.safeSortedBy
import lovehan1me.ui.component.rememberHapticFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentScreen(
    modifier: Modifier = Modifier,
    commentsFlow: StateFlow<List<VideoComments.VideoComment>>,
    commentStateFlow: StateFlow<WebsiteState<VideoComments>>,
    reportMessageFlow: Flow<CommentMessage>,
    currentSortType: StateFlow<CommentSortType>,
    reportReasons: List<ReportReason>,
    isPreviewCommentPrefetched: Boolean,
    isAlreadyLogin: Boolean,
    onRefresh: () -> Unit,
    onReply: (VideoComments.VideoComment, String) -> Unit,
    onReport: (VideoComments.VideoComment, ReportReason) -> Unit,
    onThumbUp: (VideoComments.VideoComment) -> Unit,
    onThumbDown: (VideoComments.VideoComment) -> Unit,
    onViewMoreReplies: (VideoComments.VideoComment) -> Unit,
    onSortChange: (CommentSortType) -> Unit,
    onComposeComment: (String) -> Unit,
    listContentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    onCommentScrollChange: (Int, Int) -> Unit = { _, _ -> },
) {
    val comments by commentsFlow.collectAsStateWithLifecycle()
    val state by commentStateFlow.collectAsStateWithLifecycle()
    val sortType by currentSortType.collectAsStateWithLifecycle()
    val haptic = rememberHapticFeedback()
    val containerSize = LocalWindowInfo.current.containerSize
    val maxScreenWidth = containerSize.width.dp

    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var replyingComment by remember { mutableStateOf<VideoComments.VideoComment?>(null) }
    var reportComment by remember { mutableStateOf<VideoComments.VideoComment?>(null) }
    var showCommentBar by rememberSaveable { mutableStateOf(false) }
    var replyText by remember { mutableStateOf(TextFieldValue("")) }
    var composeText by remember { mutableStateOf(TextFieldValue("")) }
    var selectedReasonIndex by remember { mutableIntStateOf(-1) }
    var latestReportMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshingState = rememberPullToRefreshState()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset,
    )
    val scope = rememberCoroutineScope()
    val loginFirstText = stringResource(Res.string.login_first)
    val commentTooShortText = stringResource(Res.string.comment_too_short)
    LaunchedEffect(reportMessageFlow) {
        reportMessageFlow.collect {
            latestReportMessage = it.text
            if (it.text.isNotBlank()) {
                snackbarHostState.showSnackbar(it.text)
            }
        }
    }

    val sortedComments = remember(comments, sortType) {
        sortComments(comments, sortType)
    }
    val showCommentFab by rememberCommentFabVisibility(listState)

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            onCommentScrollChange(index, offset)
        }
    }

    // M2：BackHandler 是 Android-only；桌面无系统返回，回复框有关闭按钮。
    // （原：返回键优先收起回复/评论框。）

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.sort_comment),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                CommentSortType.entries.forEach { type ->
                    FilledTonalButton(
                        onClick = {
                            onSortChange(type)
                            showSortSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(sortText(type))
                    }
                }
            }
        }
    }

    if (reportComment != null) {
        CommentReportDialog(
            reportReasons = reportReasons,
            selectedReasonIndex = selectedReasonIndex,
            onSelectReason = { selectedReasonIndex = it },
            onConfirm = {
                val reason = reportReasons.getOrNull(selectedReasonIndex)
                val target = reportComment
                if (reason != null && target != null) {
                    onReport(target, reason)
                }
                reportComment = null
                selectedReasonIndex = -1
            },
            onDismiss = {
                reportComment = null
                selectedReasonIndex = -1
            },
        )
    }

    Scaffold(
        modifier = modifier.widthIn(max = maxScreenWidth),
        containerColor = HanimeDefaults.Colors.pageSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isAlreadyLogin) {
                AnimatedVisibility(
                    visible = showCommentFab && replyingComment == null && !showCommentBar,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(Res.string.comment)) },
                            icon = {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_reply),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                haptic()
                                showCommentBar = true
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection),
                    )
            ) {
                PullToRefreshBox(
                    isRefreshing = state is WebsiteState.Loading && !isPreviewCommentPrefetched,
                    onRefresh = onRefresh,
                    state = refreshingState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = refreshingState,
                            isRefreshing = state is WebsiteState.Loading && !isPreviewCommentPrefetched,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                ) {
                    val loadError = state is WebsiteState.Error && sortedComments.isEmpty()
                    PageContent(
                        isLoading = false,
                        isError = loadError,
                        isEmpty = sortedComments.isEmpty(),
                        errorMessage = (state as? WebsiteState.Error)?.throwable?.message ?: "",
                        onRetry = onRefresh,
                        error = {
                            ErrorContent(
                                title = stringResource(Res.string.load_failed_retry),
                                message = (state as WebsiteState.Error).throwable.message,
                                onRetry = onRefresh,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp),
                            )
                        },
                        empty = {
                            EmptyContent(
                                hint = stringResource(Res.string.comment_not_found),
                                subHint = latestReportMessage ?: ""
                            )
                        },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = listContentPadding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (sortedComments.size >= 3) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        FilledTonalButton(onClick = { showSortSheet = true }) {
                                            Text(sortText(sortType))
                                        }
                                    }
                                }
                            }

                            items(sortedComments, key = { it.stableKey }) { comment ->
                                VideoCommentCard(
                                    comment = comment,
                                    onReply = {
                                        if (!isAlreadyLogin) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    loginFirstText
                                                )
                                            }
                                        } else {
                                            replyingComment = comment
                                        }
                                    },
                                    onThumbUp = {
                                        if (!isAlreadyLogin) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    loginFirstText
                                                )
                                            }
                                        } else {
                                            onThumbUp(comment)
                                        }
                                    },
                                    onThumbDown = {
                                        if (!isAlreadyLogin) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    loginFirstText
                                                )
                                            }
                                        } else {
                                            onThumbDown(comment)
                                        }
                                    },
                                    onReport = {
                                        if (!isAlreadyLogin) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    loginFirstText
                                                )
                                            }
                                        } else {
                                            reportComment = comment
                                        }
                                    },
                                    onViewMoreReplies = if (comment.hasMoreReplies) {
                                        { onViewMoreReplies(comment) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = replyingComment != null || showCommentBar,
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                if (replyingComment != null) {
                    CommentReplyBar(
                        text = replyText,
                        onTextChange = { replyText = it },
                        onSend = {
                            val text = replyText.text.trim()
                            if (text.length < 5) {
                                scope.launch { snackbarHostState.showSnackbar(commentTooShortText) }
                            } else {
                                replyingComment?.let { onReply(it, replyText.text) }
                                replyingComment = null
                                replyText = TextFieldValue("")
                            }
                        },
                        placeholder = stringResource(Res.string.comment),
                    )
                } else {
                    CommentReplyBar(
                        text = composeText,
                        onTextChange = { composeText = it },
                        onSend = {
                            val text = composeText.text.trim()
                            if (text.length < 5) {
                                scope.launch { snackbarHostState.showSnackbar(commentTooShortText) }
                            } else {
                                onComposeComment(text)
                                showCommentBar = false
                                composeText = TextFieldValue("")
                            }
                        },
                        placeholder = stringResource(Res.string.comment),
                    )
                }
            }
        }
    }
}

private fun sortComments(
    list: List<VideoComments.VideoComment>,
    type: CommentSortType,
): List<VideoComments.VideoComment> = when (type) {
    CommentSortType.LATEST -> list.safeSortedBy(
        { parseTimeStrToMinutes(it.date) },
        descending = false
    )

    CommentSortType.EARLIEST -> list.safeSortedBy(
        { parseTimeStrToMinutes(it.date) },
        descending = true
    )

    CommentSortType.MOST_REPLY -> list.safeSortedBy({ it.replyCount ?: 0 }, descending = true)
    CommentSortType.MOST_LIKES -> list.safeSortedBy({ it.realLikesCount ?: 0 }, descending = true)
    CommentSortType.MOST_DISLIKES -> list.safeSortedBy(
        { it.realLikesCount ?: 0 },
        descending = false
    )
}

@Composable
private fun sortText(type: CommentSortType): String = when (type) {
    CommentSortType.LATEST -> stringResource(Res.string.sort_by_newest)
    CommentSortType.EARLIEST -> stringResource(Res.string.sort_by_oldest)
    CommentSortType.MOST_REPLY -> stringResource(Res.string.sort_by_replies)
    CommentSortType.MOST_LIKES -> stringResource(Res.string.sort_most_likes)
    CommentSortType.MOST_DISLIKES -> stringResource(Res.string.sort_most_dislikes)
}

data class CommentMessage(val text: String)

@Composable
private fun rememberCommentFabVisibility(listState: LazyListState): androidx.compose.runtime.State<Boolean> {
    return remember(listState) {
        derivedStateOf {
            val scrollOffset = listState.firstVisibleItemScrollOffset
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val lastScrolledBackward = listState.lastScrolledBackward
            val lastScrolledForward = listState.lastScrolledForward

            when {
                firstVisibleItemIndex == 0 && scrollOffset == 0 -> true
                lastScrolledBackward -> true
                lastScrolledForward -> false
                else -> true
            }
        }
    }
}
