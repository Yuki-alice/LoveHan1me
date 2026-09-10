package lovehan1me.ui.screen.home.myplaylist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.Res
import lovehan1me.add_failed
import lovehan1me.add_success
import lovehan1me.my_list
import lovehan1me.load_failed_with_reason
import lovehan1me.create_new_playlist
import lovehan1me.h_chan_sad
import lovehan1me.ic_add
import lovehan1me.logic.state.WebsiteState
import lovehan1me.ui.component.PullRefreshOverlay
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.viewmodel.PlaylistController
import lovehan1me.core.util.SonnerToast

/**
 * 播放列表页面 Screen 层。
 *
 * 持有 [PlaylistController]，管理缓存、下拉刷新、底部弹窗等状态编排。
 * 渲染委托给 [PlaylistContent] 和 [PlaylistBottomSheet]。
 *
 * @param viewModel 播放列表 ViewModel
 * @param navigateBack 返回回调
 * @param onClickItem 点击视频项回调
 * @param onLongClickItem 长按视频项回调
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistController,
    navigateBack: () -> Unit,
    onClickItem: (String) -> Unit,
    onLongClickItem: (String, String) -> Unit,
) {
    val state by viewModel.myPlaylistsFlow.collectAsState()
    val uiState by viewModel.mainUiState.collectAsState()
    val scrollBehavior = pinnedScrollBehavior(rememberTopAppBarState())
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()
    val haptic = rememberHapticFeedback()
    val lifecycleOwner = LocalLifecycleOwner.current
    var temporarilyHideSheetForNavigation by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshCompleted.collect { isRefreshing = false }
    }

    LaunchedEffect(Unit) {
        if (uiState.playlists.isEmpty()) viewModel.loadMyPlayList()
    }

    DisposableEffect(lifecycleOwner, uiState.showSheet) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.showSheet) {
                temporarilyHideSheetForNavigation = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.createPlaylistFlow.collect { result ->
            when (result) {
                is WebsiteState.Error -> SonnerToast.error(getString(Res.string.add_failed))
                is WebsiteState.Loading -> Unit
                is WebsiteState.Success -> {
                    SonnerToast.success(getString(Res.string.add_success))
                    viewModel.loadMyPlayList()
                }
            }
        }
    }

    val handleEvent: (PlaylistEvent) -> Unit = { event ->
        when (event) {
            PlaylistEvent.OnBack -> navigateBack()
            PlaylistEvent.OnRefresh -> {
                isRefreshing = true
                viewModel.loadMyPlayList(forceReload = true)
            }
            PlaylistEvent.OnLoadMore -> viewModel.loadMyPlayList(viewModel.playlistPage + 1)
            is PlaylistEvent.OnPlaylistClick -> {
                viewModel.setShowSheet(true)
                viewModel.setListInfo(event.listCode, event.title)
            }
            PlaylistEvent.OnDismissSheet -> {
                temporarilyHideSheetForNavigation = false
                viewModel.setShowSheet(false)
                viewModel.currentPage = 1
                viewModel.clearCurrentList()
            }
            is PlaylistEvent.OnCreatePlaylist -> viewModel.createPlaylist(event.title, event.desc)
        }
    }

    HanimeScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        title = stringResource(Res.string.my_list),
        onBack = navigateBack,
        scrollBehavior = scrollBehavior,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic()
                    showCreatePlaylistDialog = true
                },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = stringResource(Res.string.create_new_playlist)
                )
            }
        },
    ) { innerPadding ->
        if (showCreatePlaylistDialog) {
            PlaylistEditDialog(
                title = stringResource(Res.string.create_new_playlist),
                onConfirm = { title, description ->
                    handleEvent(PlaylistEvent.OnCreatePlaylist(title, description))
                },
                onDismiss = { showCreatePlaylistDialog = false },
            )
        }

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pullToRefresh(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = { handleEvent(PlaylistEvent.OnRefresh) })
        ) {
            when (state) {
                is WebsiteState.Loading -> {
                    if (uiState.playlists.isEmpty()) {
                        LoadingIndicator(Modifier.align(Alignment.Center))
                    } else {
                        PlaylistContent(uiState = uiState, onEvent = handleEvent, rawState = state)
                    }
                }

                is WebsiteState.Error -> {
                    if (uiState.playlists.isEmpty()) {
                        EmptyContent(
                            hint = stringResource(Res.string.load_failed_with_reason,
                                (state as WebsiteState.Error).throwable.message.orEmpty()
                            ),
                            picRes = Res.drawable.h_chan_sad
                        )
                    } else {
                        PlaylistContent(uiState = uiState, onEvent = handleEvent, rawState = state)
                    }
                }

                is WebsiteState.Success -> {
                    PlaylistContent(uiState = uiState, onEvent = handleEvent, rawState = state)
                }
            }

            PullRefreshOverlay(state = refreshState, isRefreshing = isRefreshing)

            if (uiState.showSheet && !temporarilyHideSheetForNavigation) {
                PlaylistBottomSheet(
                    listCode = uiState.selectedListCode,
                    onDismiss = { handleEvent(PlaylistEvent.OnDismissSheet) },
                    playListTitle = uiState.selectedListTitle,
                    onClickItem = { item ->
                        temporarilyHideSheetForNavigation = true
                        onClickItem(item)
                    },
                    onLongClickItem = onLongClickItem,
                    vm = viewModel,
                )
            }
        }
    }
}
