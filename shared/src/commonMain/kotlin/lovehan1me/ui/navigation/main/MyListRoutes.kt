package lovehan1me.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.data.SettingsRepository
import lovehan1me.ui.viewmodel.sharedViewModel
import lovehan1me.Res
import lovehan1me.delete_fav
import lovehan1me.delete_watch_later
import lovehan1me.fav_video
import lovehan1me.long_press_to_cancel_fav
import lovehan1me.long_press_to_cancel_watch_later
import lovehan1me.watch_later
import lovehan1me.ui.screen.home.VideoGridScreen
import lovehan1me.ui.viewmodel.MyListViewModel
import lovehan1me.ui.viewmodel.mylist.FavVideoListController
import lovehan1me.ui.viewmodel.mylist.WatchLaterListController

@Composable
fun FavVideoRouteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val isLoggedIn by SettingsRepository.loginStateFlow.collectAsStateWithLifecycle()
    val viewModel: MyListViewModel = sharedViewModel(::MyListViewModel)
    val fav: FavVideoListController = if (isLoggedIn) viewModel.fav else viewModel.localFav
    val items = fav.favVideoFlow.collectAsStateWithLifecycle().value
    val state = fav.favVideoStateFlow.collectAsStateWithLifecycle().value
    val loadedPageCount = fav.loadedPageCount.collectAsStateWithLifecycle().value
    val isLoadingMore = fav.isLoadingMore.collectAsStateWithLifecycle().value

    VideoGridScreen(
        items = items,
        state = state,
        deleteStateFlow = fav.deleteMyFavVideoFlow,
        loadedPageCount = loadedPageCount,
        isLoadingMore = isLoadingMore,
        titleRes = Res.string.fav_video,
        helpMessageRes = Res.string.long_press_to_cancel_fav,
        deleteTitleRes = Res.string.delete_fav,
        onBack = onBack,
        onOpenVideo = { onNavigateToVideo(it.videoCode) },
        onDeleteItem = { item ->
            val position = items.indexOfFirst { it.videoCode == item.videoCode }
            if (position >= 0) fav.deleteMyFavVideo(item.videoCode, position)
        },
        onRefresh = {
            fav.favVideoPage = 1
            fav.clearMyListItems()
            fav.getMyFavVideoItems(SettingsRepository.savedUserId, 1)
            fav.favVideoPage = 2
        },
        onLoadMore = {
            val page = fav.favVideoPage
            fav.getMyFavVideoItems(SettingsRepository.savedUserId, page)
            fav.favVideoPage = page + 1
        },
    )
}

@Composable
fun WatchLaterRouteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val isLoggedIn by SettingsRepository.loginStateFlow.collectAsStateWithLifecycle()
    val viewModel: MyListViewModel = sharedViewModel(::MyListViewModel)
    val wl: WatchLaterListController =
        if (isLoggedIn) viewModel.watchLater else viewModel.localWatchLater
    val items = wl.watchLaterFlow.collectAsStateWithLifecycle().value
    val state = wl.watchLaterStateFlow.collectAsStateWithLifecycle().value
    val loadedPageCount = wl.loadedPageCount.collectAsStateWithLifecycle().value
    val isLoadingMore = wl.isLoadingMore.collectAsStateWithLifecycle().value

    VideoGridScreen(
        items = items,
        state = state,
        deleteStateFlow = wl.deleteMyWatchLaterFlow,
        loadedPageCount = loadedPageCount,
        isLoadingMore = isLoadingMore,
        titleRes = Res.string.watch_later,
        helpMessageRes = Res.string.long_press_to_cancel_watch_later,
        deleteTitleRes = Res.string.delete_watch_later,
        onBack = onBack,
        onOpenVideo = { onNavigateToVideo(it.videoCode) },
        onDeleteItem = { item ->
            val position = items.indexOfFirst { it.videoCode == item.videoCode }
            if (position >= 0) wl.deleteMyWatchLater(item.videoCode, position)
        },
        onRefresh = {
            wl.watchLaterPage = 1
            wl.clearMyListItems()
            wl.getMyWatchLaterItems(1)
            wl.watchLaterPage = 2
        },
        onLoadMore = {
            val page = wl.watchLaterPage
            wl.getMyWatchLaterItems(page)
            wl.watchLaterPage = page + 1
        },
    )
}
