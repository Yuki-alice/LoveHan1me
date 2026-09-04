package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.copy_to_clipboard
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.getHanimeShareText
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.myplaylist.PlaylistScreen
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.LocalPlayListViewModel
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.MyPlayListViewModel
import io.github.daisukikaffuchino.utils.rememberCopyTextToClipboard
import io.github.daisukikaffuchino.utils.SonnerToast
import io.github.daisukikaffuchino.utils.toastText
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
        val viewModel: MyPlayListViewModel = viewModel(key = "online_playlist")
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
        val viewModel: LocalPlayListViewModel = viewModel(key = "local_playlist")
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
