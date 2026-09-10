package lovehan1me.ui.navigation.main

import androidx.compose.runtime.Composable
import lovehan1me.ui.screen.home.preview.getchupreview.GetchuPreviewDetailScreen
import lovehan1me.ui.screen.home.preview.getchupreview.GetchuPreviewScreen
import lovehan1me.ui.screen.home.preview.getchupreview.GetchuPreviewViewModel
import lovehan1me.ui.viewmodel.sharedViewModel

// M2：自 `:app` 下沉（仅 viewModel() → sharedViewModel，签名不变）。

@Composable
fun GetchuPreviewRouteScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    val viewModel: GetchuPreviewViewModel = sharedViewModel(::GetchuPreviewViewModel)
    GetchuPreviewScreen(
        onBack = onBack,
        onNavigateToDetail = onNavigateToDetail,
        viewModel = viewModel,
    )
}

@Composable
fun GetchuPreviewDetailRouteScreen(
    route: GetchuPreviewDetailRoute,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToVideoUrl: (String) -> Unit,
) {
    val viewModel: GetchuPreviewViewModel = sharedViewModel(::GetchuPreviewViewModel)
    GetchuPreviewDetailScreen(
        id = route.id,
        onBack = onBack,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToVideoUrl = onNavigateToVideoUrl,
        viewModel = viewModel,
    )
}
