package lovehan1me.ui.screen.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import lovehan1me.ui.component.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.delete_fav
import lovehan1me.fav_video
import lovehan1me.long_press_to_cancel_fav
import lovehan1me.video_count
import org.jetbrains.compose.resources.StringResource
import lovehan1me.sure_to_delete_s
import lovehan1me.ok
import lovehan1me.load_failed_retry
import lovehan1me.help
import lovehan1me.empty_content
import lovehan1me.delete_success
import lovehan1me.delete_failed
import lovehan1me.delete
import lovehan1me.close
import lovehan1me.cancel
import lovehan1me.ic_help
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.PageContent
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.preview.fakeHomePageVideos
import lovehan1me.ui.screen.home.videogrid.VideoGridContent
import lovehan1me.ui.screen.home.videogrid.VideoGridUiState
import lovehan1me.ui.screen.home.videogrid.canLoadMore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 通用视频网格页面 Screen 层。
 *
 * 为"稍后观看"、"收藏视频"等列表页面提供统一的 Scaffold + 下拉刷新 + 删除确认逻辑，
 * 渲染委托给 [VideoGridContent]。
 *
 * @param items 视频列表
 * @param state 加载状态
 * @param deleteStateFlow 删除操作结果流
 * @param loadedPageCount 已加载页数
 * @param isLoadingMore 是否正在加载更多
 * @param titleRes 标题资源 ID
 * @param helpMessageRes 帮助信息资源 ID
 * @param deleteTitleRes 删除确认标题资源 ID
 * @param onBack 返回回调
 * @param onOpenVideo 打开视频详情回调
 * @param onDeleteItem 删除视频回调
 * @param onRefresh 下拉刷新回调
 * @param onLoadMore 加载更多回调
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoGridScreen(
    items: List<HanimeInfo>,
    state: PageLoadingState<*>,
    deleteStateFlow: Flow<WebsiteState<Boolean>>,
    loadedPageCount: Int,
    isLoadingMore: Boolean,
    titleRes: StringResource,
    helpMessageRes: StringResource,
    deleteTitleRes: StringResource,
    onBack: () -> Unit,
    onOpenVideo: (HanimeInfo) -> Unit,
    onDeleteItem: (HanimeInfo) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<HanimeInfo?>(null) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRefresh by rememberSaveable { mutableStateOf(false) }
    val deleteFailedText = stringResource(Res.string.delete_failed)
    val deleteSuccessText = stringResource(Res.string.delete_success)

    val refreshing = state is PageLoadingState.Loading && pendingRefresh
    val refreshingState = rememberPullToRefreshState()
    val isError = state is PageLoadingState.Error && items.isEmpty()
    val isEmpty = state is PageLoadingState.NoMoreData && items.isEmpty()
    val shouldBootstrap = items.isEmpty() && state is PageLoadingState.Loading && loadedPageCount == 0

    LaunchedEffect(shouldBootstrap) {
        if (shouldBootstrap) {
            pendingRefresh = true
            onRefresh()
        }
    }

    LaunchedEffect(state) {
        if (state !is PageLoadingState.Loading) {
            pendingRefresh = false
        }
    }

    LaunchedEffect(gridState.canLoadMore(items, state), pendingRefresh, isLoadingMore) {
        if (gridState.canLoadMore(items, state) && !pendingRefresh && !isLoadingMore) {
            onLoadMore()
        }
    }

    LaunchedEffect(deleteStateFlow, deleteFailedText, deleteSuccessText) {
        deleteStateFlow.collect { deleteState ->
            when (deleteState) {
                is WebsiteState.Error -> snackbarHostState.showSnackbar(message = deleteFailedText)
                is WebsiteState.Success -> snackbarHostState.showSnackbar(message = deleteSuccessText)
                WebsiteState.Loading -> Unit
            }
        }
    }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = stringResource(deleteTitleRes),
        message = stringResource(Res.string.sure_to_delete_s, pendingDelete?.title.orEmpty()),
        confirmText = stringResource(Res.string.delete),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            pendingDelete?.let(onDeleteItem)
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )

    ConfirmDialog(
        visible = showHelpDialog,
        title = stringResource(Res.string.help),
        message = stringResource(helpMessageRes),
        confirmText = stringResource(Res.string.ok),
        dismissText = stringResource(Res.string.close),
        onConfirm = { showHelpDialog = false },
        onDismiss = { showHelpDialog = false },
    )

    val uiState = VideoGridUiState(
        items = items,
        state = state,
        loadedPageCount = loadedPageCount,
        isLoadingMore = isLoadingMore,
        isRefreshing = refreshing,
        isError = isError,
        isEmpty = isEmpty,
    )

    HanimeScaffold(
        title = stringResource(titleRes),
        subtitle = {
            Text(
                text = stringResource(Res.string.video_count, items.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onBack = onBack,
        actions = {
            IconButton(onClick = { showHelpDialog = true }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_help),
                    contentDescription = stringResource(Res.string.help),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            state = refreshingState,
            onRefresh = {
                pendingRefresh = true
                onRefresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshingState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        ) {
            PageContent(
                isLoading = false,
                isError = isError,
                isEmpty = isEmpty,
                onRetry = {
                    pendingRefresh = true
                    onRefresh()
                },
                error = {
                    ErrorContent(
                        title = stringResource(Res.string.load_failed_retry),
                        onRetry = {
                            pendingRefresh = true
                            onRefresh()
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                },
                empty = {
                    EmptyContent(hint = stringResource(Res.string.empty_content))
                },
            ) {
                VideoGridContent(
                    uiState = uiState,
                    gridState = gridState,
                    onOpenVideo = onOpenVideo,
                    onDeleteItem = { pendingDelete = it },
                )
            }
        }
    }
}
