package me.lovehan1me.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import me.lovehan1me.Res
import me.lovehan1me.ui.viewmodel.sharedViewModel
import me.lovehan1me.copy_to_clipboard
import me.lovehan1me.getHanimeSearchShareText
import me.lovehan1me.getHanimeShareText
import me.lovehan1me.ui.screen.home.SubscriptionScreen
import me.lovehan1me.ui.viewmodel.MySubscriptionsViewModel
import me.lovehan1me.utils.rememberCopyTextToClipboard
import me.lovehan1me.utils.SonnerToast
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun SubscriptionRouteScreen(
    onBack: () -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val viewModel: MySubscriptionsViewModel = sharedViewModel(::MySubscriptionsViewModel)
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
