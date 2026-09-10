package lovehan1me.ui.navigation.main

import androidx.compose.runtime.Composable
import lovehan1me.ui.screen.home.WatchHistoryTabScreen
import lovehan1me.ui.viewmodel.sharedViewModel
import lovehan1me.ui.screen.home.homepage.HomePageViewModel
import lovehan1me.ui.viewmodel.OnlineWatchHistoryViewModel

@Composable
fun WatchHistoryRouteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val localViewModel: HomePageViewModel = sharedViewModel(::HomePageViewModel)
    val onlineViewModel: OnlineWatchHistoryViewModel = sharedViewModel(::OnlineWatchHistoryViewModel)
    WatchHistoryTabScreen(
        localHistoriesFlow = localViewModel.loadAllWatchHistories(),
        onlineItems = onlineViewModel.items,
        onlineState = onlineViewModel.state,
        onlineSort = onlineViewModel.selectedSort,
        onlineLoadedPageCount = onlineViewModel.loadedPageCount,
        onlineIsLoadingMore = onlineViewModel.isLoadingMore,
        onlineRefreshing = onlineViewModel::isRefreshing,
        onlineDeleteStateFlow = onlineViewModel.deleteFlow,
        onBack = onBack,
        onOpenLocalVideo = { onNavigateToVideo(it.videoCode) },
        onDeleteLocalHistory = localViewModel::deleteWatchHistory,
        onDeleteAllLocalHistories = localViewModel::deleteAllWatchHistories,
        onOpenOnlineVideo = { onNavigateToVideo(it.videoCode) },
        onDeleteOnlineVideo = onlineViewModel::deleteItem,
        onRefreshOnline = onlineViewModel::refresh,
        onLoadMoreOnline = onlineViewModel::loadNextPage,
    )
}
