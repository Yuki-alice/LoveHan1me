package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.copy_to_clipboard
import io.github.daisukikaffuchino.han1meviewer.getHanimeSearchShareText
import io.github.daisukikaffuchino.han1meviewer.getHanimeShareText
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.SubscriptionScreen
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.MySubscriptionsViewModel
import io.github.daisukikaffuchino.utils.rememberCopyTextToClipboard
import io.github.daisukikaffuchino.utils.SonnerToast
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun SubscriptionRouteScreen(
    onBack: () -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val viewModel: MySubscriptionsViewModel = viewModel()
    val copyTextToClipboard = rememberCopyTextToClipboard()
    // P6d-3-C3：回调内 toast 转 suspend getString，经 scope 桥接
    val scope = rememberCoroutineScope()
    SubscriptionScreen(
        navigateBack = onBack,
        viewModel = viewModel,
        onClickArtist = { onNavigateToSearch(it) },
        onLongClickArtist = { artistName ->
            copyTextToClipboard(getHanimeSearchShareText(artistName))
            scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
        },
        onClickVideosItem = onNavigateToVideo,
        onLongClickVideosItem = { videoCode, title ->
            copyTextToClipboard(getHanimeShareText(title, videoCode))
            scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
        },
    )
}
