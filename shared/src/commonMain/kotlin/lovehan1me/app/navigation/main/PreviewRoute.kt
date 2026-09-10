package lovehan1me.app.navigation.main

import androidx.compose.runtime.Composable
import lovehan1me.feature.home.PreviewScreen
import lovehan1me.ui.viewmodel.CommentViewModel
import lovehan1me.ui.viewmodel.PreviewViewModel
import lovehan1me.ui.viewmodel.sharedViewModel

/**
 * M2：自 `:app` 下沉（去 `MainActivity` 依赖）。
 * 原 `viewModel(viewModelStoreOwner = activity)` 与默认 owner 同义
 *（Android 上默认即 Activity），改统一 sharedViewModel。
 */
@Composable
fun PreviewRouteScreen(
    onBack: () -> Unit,
    onNavigateToGetchuPreview: () -> Unit,
    onNavigateToPreviewComment: (String, String) -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val previewViewModel: PreviewViewModel = sharedViewModel(::PreviewViewModel)
    val commentViewModel: CommentViewModel = sharedViewModel(::CommentViewModel)

    PreviewScreen(
        onBack = onBack,
        onNavigateToGetchuPreview = onNavigateToGetchuPreview,
        onNavigateToPreviewComment = onNavigateToPreviewComment,
        onNavigateToVideo = onNavigateToVideo,
        previewViewModel = previewViewModel,
        commentViewModel = commentViewModel,
    )
}
