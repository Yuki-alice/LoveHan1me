package lovehan1me.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.core.constant.LOCAL_DATE_TIME_FORMAT
import lovehan1me.Res
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.watch_history_watched_at
import lovehan1me.watch_history_total_count
import lovehan1me.watch_history_resume_watch
import lovehan1me.watch_history_released_at
import lovehan1me.watch_history_minutes_short
import lovehan1me.watch_history_empty_title
import lovehan1me.watch_history_empty_description
import lovehan1me.watch_history_delete_all_title
import lovehan1me.watch_history_clear_all
import lovehan1me.watch_history
import lovehan1me.sure_to_delete_s
import lovehan1me.sure_to_delete_all_histories
import lovehan1me.sort_by_oldest
import lovehan1me.sort_by_newest
import lovehan1me.popular
import lovehan1me.online
import lovehan1me.local
import lovehan1me.load_failed_retry
import lovehan1me.delete_success
import lovehan1me.delete_history
import lovehan1me.delete_failed
import lovehan1me.delete
import lovehan1me.cancel
import lovehan1me.ic_play_circle
import lovehan1me.ic_history
import lovehan1me.ic_delete
import lovehan1me.ic_access_time
import lovehan1me.data.database.entity.WatchHistoryEntity
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.OnlineWatchHistorySort
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.ui.component.CardContainerSurface
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.LoadMoreFooter
import lovehan1me.ui.component.PageContent
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.feature.preview.fakeHomePageVideos
import lovehan1me.ui.component.rememberVideoGridColumns
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.SpacingNormal
import lovehan1me.ui.theme.shapeByInteraction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

@Composable
fun WatchHistoryTabScreen(
    localHistoriesFlow: Flow<List<WatchHistoryEntity>>,
    onlineItems: StateFlow<List<HanimeInfo>>,
    onlineState: StateFlow<PageLoadingState<*>>,
    onlineSort: StateFlow<OnlineWatchHistorySort>,
    onlineLoadedPageCount: StateFlow<Int>,
    onlineIsLoadingMore: StateFlow<Boolean>,
    onlineRefreshing: () -> Boolean,
    onlineDeleteStateFlow: SharedFlow<WebsiteState<Boolean>>,
    onBack: () -> Unit,
    onOpenLocalVideo: (WatchHistoryEntity) -> Unit,
    onDeleteLocalHistory: (WatchHistoryEntity) -> Unit,
    onDeleteAllLocalHistories: () -> Unit,
    onOpenOnlineVideo: (HanimeInfo) -> Unit,
    onDeleteOnlineVideo: (HanimeInfo) -> Unit,
    onRefreshOnline: (OnlineWatchHistorySort) -> Unit,
    onLoadMoreOnline: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val localListState = rememberLazyListState()
    val showClearFab by rememberWatchHistoryFabVisibility(localListState)
    val localHistories by localHistoriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentOnlineItems by onlineItems.collectAsState()
    val currentOnlineState by onlineState.collectAsState()
    val currentOnlineSort by onlineSort.collectAsState()
    val currentOnlineLoadedPageCount by onlineLoadedPageCount.collectAsState()
    val currentOnlineIsLoadingMore by onlineIsLoadingMore.collectAsState()
    var showDeleteAllLocalDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1 && currentOnlineItems.isEmpty() && currentOnlineLoadedPageCount == 0 && currentOnlineState is PageLoadingState.Loading) {
            onRefreshOnline(currentOnlineSort)
        }
    }

    ConfirmDialog(
        visible = showDeleteAllLocalDialog,
        title = stringResource(Res.string.watch_history_delete_all_title),
        message = stringResource(Res.string.sure_to_delete_all_histories),
        confirmText = stringResource(Res.string.watch_history_clear_all),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            onDeleteAllLocalHistories()
            showDeleteAllLocalDialog = false
        },
        onDismiss = { showDeleteAllLocalDialog = false },
    )

    HanimeScaffold(
        title = stringResource(Res.string.watch_history),
        onBack = onBack,
        contentHorizontalPadding = 0.dp,
        floatingActionButton = {
            WatchHistoryClearFab(
                visible = pagerState.currentPage == 0 &&
                        localHistories.isNotEmpty() &&
                        showClearFab,
                onClick = { showDeleteAllLocalDialog = true },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(Res.string.local)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(Res.string.online)) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> WatchHistoryListContent(
                        histories = localHistories,
                        onOpenVideo = onOpenLocalVideo,
                        onDeleteHistory = onDeleteLocalHistory,
                        listState = localListState,
                    )

                    else -> OnlineWatchHistoryScreen(
                        items = currentOnlineItems,
                        state = currentOnlineState,
                        sort = currentOnlineSort,
                        loadedPageCount = currentOnlineLoadedPageCount,
                        isLoadingMore = currentOnlineIsLoadingMore,
                        refreshing = onlineRefreshing(),
                        deleteStateFlow = onlineDeleteStateFlow,
                        onOpenVideo = onOpenOnlineVideo,
                        onDeleteVideo = onDeleteOnlineVideo,
                        onRefresh = onRefreshOnline,
                        onLoadMore = onLoadMoreOnline,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchHistoryListContent(
    histories: List<WatchHistoryEntity>,
    onOpenVideo: (WatchHistoryEntity) -> Unit,
    onDeleteHistory: (WatchHistoryEntity) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    var pendingDelete by remember { mutableStateOf<WatchHistoryEntity?>(null) }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = stringResource(Res.string.delete_history),
        message = stringResource(Res.string.sure_to_delete_s, pendingDelete?.title.orEmpty()),
        confirmText = stringResource(Res.string.delete),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            pendingDelete?.let(onDeleteHistory)
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )

    if (histories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyContent(
                hint = stringResource(Res.string.watch_history_empty_title),
                subHint = stringResource(Res.string.watch_history_empty_description),
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(histories, key = { it.id }) { history ->
                WatchHistoryCard(
                    history = history,
                    onClick = { onOpenVideo(history) },
                    onDeleteClick = { pendingDelete = history },
                )
            }
        }
    }
}

@Composable
private fun WatchHistoryClearFab(
    visible: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Box(
            modifier = Modifier.padding(8.dp)
        ) {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(Res.string.watch_history_clear_all)) },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete),
                        contentDescription = null,
                    )
                },
                onClick = {
                    haptic()
                    onClick()
                },
            )
        }
    }
}

@Composable
private fun rememberWatchHistoryFabVisibility(
    listState: LazyListState,
): androidx.compose.runtime.State<Boolean> = remember(listState) {
    derivedStateOf {
        when {
            !listState.canScrollBackward -> true
            listState.lastScrolledBackward -> true
            listState.lastScrolledForward -> false
            else -> true
        }
    }
}

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun OnlineWatchHistoryScreen(
    items: List<HanimeInfo>,
    state: PageLoadingState<*>,
    sort: OnlineWatchHistorySort,
    loadedPageCount: Int,
    isLoadingMore: Boolean,
    refreshing: Boolean,
    deleteStateFlow: SharedFlow<WebsiteState<Boolean>>,
    onOpenVideo: (HanimeInfo) -> Unit,
    onDeleteVideo: (HanimeInfo) -> Unit,
    onRefresh: (OnlineWatchHistorySort) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val refreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<HanimeInfo?>(null) }
    var sortBarVisible by rememberSaveable { mutableStateOf(true) }
    val deleteFailedText = stringResource(Res.string.delete_failed)
    val deleteSuccessText = stringResource(Res.string.delete_success)

    LaunchedEffect(deleteStateFlow, deleteFailedText, deleteSuccessText) {
        deleteStateFlow.collect { deleteState ->
            when (deleteState) {
                is WebsiteState.Error -> snackbarHostState.showSnackbar(message = deleteFailedText)
                is WebsiteState.Success -> snackbarHostState.showSnackbar(message = deleteSuccessText)
                WebsiteState.Loading -> Unit
            }
        }
    }

    LaunchedEffect(gridState, items.size, state, isLoadingMore) {
        if (
            items.isEmpty() ||
            isLoadingMore ||
            state is PageLoadingState.Loading ||
            state is PageLoadingState.NoMoreData ||
            state is PageLoadingState.Error
        ) {
            return@LaunchedEffect
        }

        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layoutInfo.totalItemsCount - 4
        }
            .distinctUntilChanged()
            .first { it }

        onLoadMore()
    }

    LaunchedEffect(gridState) {
        var previousIndex = 0
        var previousOffset = 0
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (currentIndex, currentOffset) ->
                sortBarVisible = when {
                    !gridState.canScrollBackward -> true
                    currentIndex < previousIndex -> true
                    currentIndex > previousIndex -> false
                    currentOffset < previousOffset -> true
                    currentOffset > previousOffset -> false
                    else -> sortBarVisible
                }
                previousIndex = currentIndex
                previousOffset = currentOffset
            }
    }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = stringResource(Res.string.delete_history),
        message = stringResource(Res.string.sure_to_delete_s, pendingDelete?.title.orEmpty()),
        confirmText = stringResource(Res.string.delete),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            pendingDelete?.let(onDeleteVideo)
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            state = refreshState,
            onRefresh = { onRefresh(sort) },
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = refreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp),
                )
            }
        ) {
            PageContent(
                isLoading = state is PageLoadingState.Loading && items.isEmpty(),
                isError = state is PageLoadingState.Error,
                isEmpty = state is PageLoadingState.NoMoreData && items.isEmpty(),
                onRetry = { onRefresh(sort) },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorContent(
                            title = stringResource(Res.string.load_failed_retry),
                            onRetry = { onRefresh(sort) },
                        )
                    }
                },
                empty = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyContent(
                            hint = stringResource(Res.string.watch_history_empty_title),
                            subHint = stringResource(Res.string.watch_history_empty_description),
                        )
                    }
                },
            ) {
                OnlineWatchHistoryGrid(
                    items = items,
                    gridState = gridState,
                    loadedPageCount = loadedPageCount,
                    state = state,
                    isLoadingMore = isLoadingMore,
                    snackbarHostState = snackbarHostState,
                    onOpenVideo = onOpenVideo,
                    onDeleteVideo = { pendingDelete = it },
                )
            }
        }

        AnimatedVisibility(
            visible = sortBarVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnlineHistorySortChip(
                        text = stringResource(Res.string.sort_by_newest),
                        selected = sort == OnlineWatchHistorySort.Latest,
                        onClick = { onRefresh(OnlineWatchHistorySort.Latest) },
                    )
                    OnlineHistorySortChip(
                        text = stringResource(Res.string.popular),
                        selected = sort == OnlineWatchHistorySort.Popular,
                        onClick = { onRefresh(OnlineWatchHistorySort.Popular) },
                    )
                    OnlineHistorySortChip(
                        text = stringResource(Res.string.sort_by_oldest),
                        selected = sort == OnlineWatchHistorySort.Oldest,
                        onClick = { onRefresh(OnlineWatchHistorySort.Oldest) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineWatchHistoryGrid(
    items: List<HanimeInfo>,
    gridState: LazyGridState,
    loadedPageCount: Int,
    state: PageLoadingState<*>,
    isLoadingMore: Boolean,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onOpenVideo: (HanimeInfo) -> Unit,
    onDeleteVideo: (HanimeInfo) -> Unit,
) {
    val videoColumns = rememberVideoGridColumns()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(videoColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SpacingNormal,
                top = 64.dp,
                end = SpacingNormal,
                bottom = SpacingNormal,
            ),
            horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
            verticalArrangement = Arrangement.spacedBy(SpacingNormal),
            enableItemAnimation = false,
        ) {
            item(
                key = "online_history_count",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                Text(
                    text = stringResource(Res.string.watch_history_total_count, items.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            items(
                items = items,
                key = { it.videoCode },
                contentType = { "video" },
            ) { item ->
                VideoCardItem(
                    videoItem = item,
                    showDeleteAction = true,
                    onClickVideosItem = { onOpenVideo(item) },
                    onLongClickVideosItem = { _, _ -> onDeleteVideo(item) },
                )
            }
            if (items.isNotEmpty()) {
                item(
                    key = "online_history_footer",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "footer",
                ) {
                    LoadMoreFooter(
                        state = state,
                        loadedPage = loadedPageCount,
                        isLoadingMore = isLoadingMore,
                    )
                }
            }
        }
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun OnlineHistorySortChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    AssistChip(
        onClick = {
            haptic()
            onClick()
        },
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            labelColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WatchHistoryCard(
    history: WatchHistoryEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val fixTimestamp = { ts: Long -> if (ts < 9999999999L) ts * 1000 else ts }
    val dateFormatter = LOCAL_DATE_TIME_FORMAT
    val watchDate =
        remember(history.watchDate) { Instant.fromEpochMilliseconds(fixTimestamp(history.watchDate)).toLocalDateTime(TimeZone.currentSystemDefault()).format(dateFormatter) }
    val releaseDate =
        remember(history.releaseDate) { Instant.fromEpochMilliseconds(fixTimestamp(history.releaseDate)).toLocalDateTime(TimeZone.currentSystemDefault()).format(dateFormatter) }
    val progressMinutes = remember(history.progress) { history.progress / 60_000 }
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    val cardShape = shapeByInteraction(
        shapes = HanimeDefaults.cardShapes(),
        pressed = pressed,
        animationSpec = HanimeDefaults.shapesDefaultAnimationSpec,
    )
    CardContainerSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = {
                        haptic()
                        onClick()
                    },
                    onLongClick = {},
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                HanimeAsyncImage(
                    model = history.coverUrl,
                    contentDescription = history.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (progressMinutes > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(topEnd = 4.dp),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = stringResource(Res.string.watch_history_minutes_short,
                                progressMinutes
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                WatchHistoryMeta(
                    iconRes = Res.drawable.ic_access_time,
                    label = stringResource(Res.string.watch_history_watched_at, watchDate),
                )
                WatchHistoryMeta(
                    iconRes = Res.drawable.ic_play_circle,
                    label = stringResource(Res.string.watch_history_released_at, releaseDate),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                ) {
                    AssistChip(
                        onClick = {
                            haptic()
                            onClick()
                        },
                        label = {
                            Text(
                                stringResource(Res.string.watch_history_resume_watch),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_history),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer, // 改用 primary 强化引导
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                    FilledIconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(25.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete),
                            contentDescription = stringResource(Res.string.delete_history),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchHistoryMeta(
    iconRes: DrawableResource,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
