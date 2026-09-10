package lovehan1me.feature.home.preview.getchupreview

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
import lovehan1me.Res
import lovehan1me.getchu_preview_detail
import lovehan1me.core.domain.state.PageState
import lovehan1me.core.domain.state.dataOrNull
import lovehan1me.data.pienization
import lovehan1me.ui.component.PageContent
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.component.isFirstPageEmpty
import lovehan1me.ui.component.isFirstPageError
import lovehan1me.ui.component.isFirstPageLoading
import lovehan1me.feature.home.preview.PreviewImageViewerDialog
import lovehan1me.feature.home.preview.PreviewImageViewerState
import lovehan1me.ui.component.rememberRandomLoadingHint

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
