package lovehan1me.app.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import lovehan1me.Res
import lovehan1me.ui.viewmodel.sharedViewModel
import lovehan1me.copy_to_clipboard
import lovehan1me.getHanimeSearchShareText
import lovehan1me.getHanimeShareText
import lovehan1me.feature.home.SubscriptionScreen
import lovehan1me.ui.viewmodel.MySubscriptionsViewModel
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.core.util.SonnerToast
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
