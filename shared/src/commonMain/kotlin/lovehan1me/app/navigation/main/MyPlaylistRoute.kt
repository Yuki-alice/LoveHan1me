package lovehan1me.app.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.app.sharedViewModel
import lovehan1me.data.SettingsRepository
import lovehan1me.feature.home.myplaylist.PlaylistScreen
import lovehan1me.feature.library.LocalPlayListViewModel
import lovehan1me.feature.library.MyPlayListViewModel

@Composable
fun MyPlaylistRouteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val isLoggedIn by SettingsRepository.loginStateFlow.collectAsStateWithLifecycle()
    if (isLoggedIn) {
        val viewModel: MyPlayListViewModel = sharedViewModel(::MyPlayListViewModel, key = "online_playlist")
        PlaylistScreen(
            viewModel = viewModel,
            navigateBack = onBack,
            onClickItem = onNavigateToVideo,
        )
    } else {
        val viewModel: LocalPlayListViewModel = sharedViewModel(::LocalPlayListViewModel, key = "local_playlist")
        PlaylistScreen(
            viewModel = viewModel,
            navigateBack = onBack,
            onClickItem = onNavigateToVideo,
        )
    }
}
