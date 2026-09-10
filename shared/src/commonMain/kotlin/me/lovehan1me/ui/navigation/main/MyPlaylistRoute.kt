package me.lovehan1me.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.lovehan1me.Res
import me.lovehan1me.ui.viewmodel.sharedViewModel
import me.lovehan1me.copy_to_clipboard
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.getHanimeShareText
import me.lovehan1me.ui.screen.home.myplaylist.PlaylistScreen
import me.lovehan1me.ui.viewmodel.LocalPlayListViewModel
import me.lovehan1me.ui.viewmodel.MyPlayListViewModel
import me.lovehan1me.utils.rememberCopyTextToClipboard
import me.lovehan1me.utils.SonnerToast
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun MyPlaylistRouteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val isLoggedIn by SettingsRepository.loginStateFlow.collectAsStateWithLifecycle()
    val copyTextToClipboard = rememberCopyTextToClipboard()
    // P6d-3-C3：回调内 toast 转 suspend getString，经 scope 桥接
    val scope = rememberCoroutineScope()
    if (isLoggedIn) {
        val viewModel: MyPlayListViewModel = sharedViewModel(::MyPlayListViewModel, key = "online_playlist")
        PlaylistScreen(
            viewModel = viewModel,
            navigateBack = onBack,
            onClickItem = onNavigateToVideo,
            onLongClickItem = { videoCode, title ->
                copyTextToClipboard(getHanimeShareText(title, videoCode))
                scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
            },
        )
    } else {
        val viewModel: LocalPlayListViewModel = sharedViewModel(::LocalPlayListViewModel, key = "local_playlist")
        PlaylistScreen(
            viewModel = viewModel,
            navigateBack = onBack,
            onClickItem = onNavigateToVideo,
            onLongClickItem = { videoCode, title ->
                copyTextToClipboard(getHanimeShareText(title, videoCode))
                scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
            },
        )
    }
}
