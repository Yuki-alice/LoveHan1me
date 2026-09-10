package me.lovehan1me.ui.screen.home.preview.getchupreview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.lovehan1me.Res
import me.lovehan1me.getchu_preview_detail
import me.lovehan1me.logic.state.PageState
import me.lovehan1me.logic.state.dataOrNull
import me.lovehan1me.pienization
import me.lovehan1me.ui.component.PageContent
import me.lovehan1me.ui.component.appbar.HanimeScaffold
import me.lovehan1me.ui.component.isFirstPageEmpty
import me.lovehan1me.ui.component.isFirstPageError
import me.lovehan1me.ui.component.isFirstPageLoading
import me.lovehan1me.ui.screen.home.preview.PreviewImageViewerDialog
import me.lovehan1me.ui.screen.home.preview.PreviewImageViewerState
import me.lovehan1me.ui.screen.rememberRandomLoadingHint

@Composable
fun GetchuPreviewDetailScreen(
    id: String,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToVideoUrl: (String) -> Unit,
    viewModel: GetchuPreviewViewModel,
) {
    val detailState = remember(id) { viewModel.detailState(id) }
    val state = detailState.collectAsStateWithLifecycle().value
    var imageViewerState by remember { mutableStateOf<PreviewImageViewerState?>(null) }
    val loadingHint = rememberRandomLoadingHint()
    val isInspectionMode = LocalInspectionMode.current
    val imageLoader = rememberGetchuImageLoader()
    LaunchedEffect(id, isInspectionMode) {
        if (!isInspectionMode) viewModel.getDetail(id)
    }

    HanimeScaffold(
            title = stringResource(Res.string.getchu_preview_detail),
            onBack = onBack,
            contentHorizontalPadding = 0.dp,
    ) {
            PageContent(
            isLoading = state.isFirstPageLoading,
            isError = state.isFirstPageError,
            isEmpty = state.isFirstPageEmpty,
            errorMessage = (state as? PageState.Error)?.throwable?.pienization.toString(),
            onRetry = { if (!isInspectionMode) viewModel.getDetail(id) },
            modifier = Modifier.fillMaxSize(),
            loadingMessage = loadingHint
            ) {
                state.dataOrNull?.let { detail ->
                    GetchuPreviewDetailContent(
                        detail = detail,
                        onOpenImage = { index, images ->
                            imageViewerState = PreviewImageViewerState(images, index)
                        },
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToVideoUrl = onNavigateToVideoUrl,
                        imageLoader = imageLoader
                    )
                }
            }
        }

    imageViewerState?.let { viewerState ->
        PreviewImageViewerDialog(
            imageUrls = viewerState.imageUrls,
            initialPage = viewerState.initialPage,
            onDismiss = { imageViewerState = null },
            imageLoader = imageLoader,
        )
    }
}
