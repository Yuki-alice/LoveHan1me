package lovehan1me.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import lovehan1me.ui.component.rememberHanimeImageLoader
import lovehan1me.core.constant.PREVIEW_COMMENT_PREFIX
import lovehan1me.core.constant.HANIME_BASE_URL
import lovehan1me.core.domain.model.HanimePreview
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.feature.video.CommentViewModel
import lovehan1me.feature.video.PreviewCommentPrefetcher
import lovehan1me.feature.preview.PreviewViewModel
import lovehan1me.app.navigation.main.shiftMonthCodeForPreview
import lovehan1me.feature.home.preview.PreviewContent
import lovehan1me.feature.home.preview.PreviewEvent
import lovehan1me.feature.home.preview.PreviewImageViewerDialog
import lovehan1me.feature.home.preview.PreviewImageViewerState
import lovehan1me.feature.home.preview.PreviewMonthHeaderState
import lovehan1me.feature.home.preview.PreviewRouteUiState
import lovehan1me.feature.home.preview.PreviewUiState
import lovehan1me.feature.home.preview.shiftMonthCode
import lovehan1me.feature.home.preview.toNormalDateLabel
import kotlinx.coroutines.launch

/**
 * 预览页面 Screen 层。
 *
 * 持有 [PreviewViewModel] 和 [CommentViewModel]，负责状态收集、图片预加载、
 * 评论预取器生命周期、月份翻页编排和图片查看器 UI 管理。
 * 渲染委托给 [lovehan1me.feature.home.preview.PreviewContent]。
 *
 * @param onBack 返回回调
 * @param onNavigateToGetchuPreview 打开 Getchu 新番预告页回调
 * @param onNavigateToPreviewComment 打开预览评论页回调
 * @param onNavigateToVideo 打开视频详情回调
 * @param previewViewModel 预览 ViewModel
 * @param commentViewModel 评论 ViewModel（需 Activity scope）
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    onNavigateToGetchuPreview: () -> Unit,
    onNavigateToPreviewComment: (String, String) -> Unit,
    onNavigateToVideo: (String) -> Unit,
    previewViewModel: PreviewViewModel,
    commentViewModel: CommentViewModel,
) {
    // M2：SingletonImageLoader（需 Activity Context）在桌面/iOS 无意义，
    // 且旁路了 HDns/代理；改共享 rememberHanimeImageLoader。
    val platformContext = LocalPlatformContext.current
    val uriHandler = LocalUriHandler.current
    val imageLoader = rememberHanimeImageLoader()
    val previewState = previewViewModel.previewFlow.collectAsStateWithLifecycle().value
    val commentCount = PreviewCommentPrefetcher.here(commentViewModel)
        .commentFlow
        .collectAsStateWithLifecycle()
        .value
        .size

    fun preloadImages(preview: HanimePreview?) {
        if (preview == null) return
        buildList {
            preview.headerPicUrl?.let(::add)
            addAll(preview.latestHanime.map { it.coverUrl })
            addAll(preview.previewInfo.mapNotNull { it.coverUrl })
        }.distinct().forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(platformContext)
                    .data(url)
                    .crossfade(true)
                    .build()
            )
        }
    }

    LaunchedEffect(previewState) {
        when (previewState) {
            is WebsiteState.Success -> preloadImages(previewState.info)
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        PreviewCommentPrefetcher.here(commentViewModel)
            .tag(PreviewCommentPrefetcher.Scope.PREVIEW_ACTIVITY)
        onDispose {
            PreviewCommentPrefetcher.bye(PreviewCommentPrefetcher.Scope.PREVIEW_ACTIVITY)
        }
    }

    var routeState by rememberSaveable(stateSaver = PreviewRouteUiState.Saver) {
        mutableStateOf(PreviewRouteUiState())
    }
    var imageViewerState by remember { mutableStateOf<PreviewImageViewerState?>(null) }
    var monthAnimationDirection by remember { mutableIntStateOf(1) }
    val currentDateCode = routeState.currentDateCode
    val selectedIndex = routeState.selectedIndex

    val currentDateLabel = remember(currentDateCode) { toNormalDateLabel(currentDateCode) }
    val prevDateCode = remember(currentDateCode) { shiftMonthCode(currentDateCode, -1) }
    val nextDateCode = remember(currentDateCode) { shiftMonthCode(currentDateCode, 1) }
    val prevDateLabel = remember(prevDateCode) { toNormalDateLabel(prevDateCode) }
    val nextDateLabel = remember(nextDateCode) { toNormalDateLabel(nextDateCode) }

    val displayState = remember(currentDateCode, previewState) {
        val cached = previewViewModel.getCachedPreview(currentDateCode)
        if (previewState is WebsiteState.Loading && cached is WebsiteState.Success) {
            cached
        } else {
            previewState
        }
    }

    val success = displayState as? WebsiteState.Success
    val previewInfoList = success?.info?.previewInfo.orEmpty()
    val previewPagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { previewInfoList.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    val canPrev = when (displayState) {
        is WebsiteState.Loading -> false
        is WebsiteState.Success -> displayState.info.hasPrevious
        is WebsiteState.Error -> true
    }
    val canNext = when (displayState) {
        is WebsiteState.Success -> displayState.info.hasNext
        else -> false
    }
    val monthHeaderState = remember(
        currentDateCode,
        success?.info?.headerPicUrl,
        prevDateLabel,
        nextDateLabel,
        canPrev,
        canNext,
    ) {
        PreviewMonthHeaderState(
            dateCode = currentDateCode,
            headerImageUrl = success?.info?.headerPicUrl,
            prevLabel = prevDateLabel,
            nextLabel = nextDateLabel,
            canPrev = canPrev,
            canNext = canNext,
        )
    }

    val uiState = PreviewUiState(
        routeState = routeState,
        currentDateLabel = currentDateLabel,
        prevDateLabel = prevDateLabel,
        nextDateLabel = nextDateLabel,
        monthAnimationDirection = monthAnimationDirection,
        displayState = displayState,
        commentCount = commentCount,
        canPrev = canPrev,
        canNext = canNext,
        monthHeaderState = monthHeaderState,
        imageViewerState = imageViewerState,
    )

    val handleEvent: (PreviewEvent) -> Unit = { event ->
        when (event) {
            PreviewEvent.OnBack -> onBack()
            is PreviewEvent.OnPrevMonth -> {
                monthAnimationDirection = -1
                routeState = routeState.copy(
                    currentDateCode = shiftMonthCode(event.fromDateCode, -1),
                )
            }
            is PreviewEvent.OnNextMonth -> {
                monthAnimationDirection = 1
                routeState = routeState.copy(
                    currentDateCode = shiftMonthCode(event.fromDateCode, 1),
                )
            }
            is PreviewEvent.OnSelectTourItem -> {
                if (event.index != previewPagerState.currentPage) {
                    scope.launch {
                        previewPagerState.animateScrollToPage(event.index)
                    }
                }
            }
            is PreviewEvent.OnOpenImage -> {
                imageViewerState = PreviewImageViewerState(
                    imageUrls = event.imageUrls,
                    initialPage = event.index,
                )
            }
            PreviewEvent.OnDismissImageViewer -> { imageViewerState = null }
            is PreviewEvent.OnOpenVideo -> event.videoCode?.let(onNavigateToVideo)
            PreviewEvent.OnOpenGetchuPreview -> onNavigateToGetchuPreview()
            PreviewEvent.OnOpenWebPreview -> uriHandler.openUri(
                "https://www.getchu.com/"
            )
            is PreviewEvent.OnOpenComment -> onNavigateToPreviewComment(event.label, event.dateCode)
            PreviewEvent.OnRetryLoad -> {
                val code = uiState.routeState.currentDateCode
                previewViewModel.getHanimePreview(code)
                previewViewModel.preloadPreview(shiftMonthCodeForPreview(code, -1))
                previewViewModel.preloadPreview(shiftMonthCodeForPreview(code, 1))
                PreviewCommentPrefetcher.here(commentViewModel).fetch(PREVIEW_COMMENT_PREFIX, code)
            }
        }
    }

    LaunchedEffect(currentDateCode) {
        previewViewModel.getHanimePreview(currentDateCode)
        previewViewModel.preloadPreview(shiftMonthCodeForPreview(currentDateCode, -1))
        previewViewModel.preloadPreview(shiftMonthCodeForPreview(currentDateCode, 1))
        PreviewCommentPrefetcher.here(commentViewModel).fetch(PREVIEW_COMMENT_PREFIX, currentDateCode)
        routeState = routeState.copy(selectedIndex = 0)
    }

    LaunchedEffect(uiState.routeState.selectedIndex, previewInfoList.size) {
        if (previewInfoList.isEmpty()) return@LaunchedEffect
        val targetPage = uiState.routeState.selectedIndex.coerceIn(previewInfoList.indices)
        if (previewPagerState.currentPage != targetPage) {
            if (!previewPagerState.isScrollInProgress) {
                previewPagerState.scrollToPage(targetPage)
            }
        }
    }

    LaunchedEffect(previewPagerState.currentPage, previewInfoList.size) {
        if (previewInfoList.isEmpty()) return@LaunchedEffect
        val pagerPage = previewPagerState.currentPage.coerceIn(previewInfoList.indices)
        if (pagerPage != uiState.routeState.selectedIndex) {
            routeState = routeState.copy(selectedIndex = pagerPage)
        }
    }

    uiState.imageViewerState?.let { viewerState ->
        PreviewImageViewerDialog(
            imageUrls = viewerState.imageUrls,
            initialPage = viewerState.initialPage,
            onDismiss = { handleEvent(PreviewEvent.OnDismissImageViewer) },
        )
    }

    PreviewContent(
        uiState = uiState,
        onEvent = handleEvent,
        previewPagerState = previewPagerState,
        previewInfoList = previewInfoList,
    )
}
