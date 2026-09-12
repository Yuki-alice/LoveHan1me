@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package lovehan1me.feature.home.preview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.Res
import lovehan1me.visit_web_version
import lovehan1me.view_getchu_preview
import lovehan1me.preview_month_not_updated
import lovehan1me.preview_discontinued_notice
import lovehan1me.new_anime_trailers
import lovehan1me.latest_hanime_list_monthly
import lovehan1me.hanime_list
import lovehan1me.empty_content
import lovehan1me.comment
import lovehan1me.ic_chevron_left
import lovehan1me.ic_chevron_right
import lovehan1me.ic_comment
import lovehan1me.core.domain.exception.HanimeNotFoundException
import lovehan1me.core.domain.model.HanimePreview
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.data.pienization
import lovehan1me.ui.component.CardContainerSurface
import lovehan1me.ui.component.FilledTonalButton
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.appbar.HanimePageSurface
import lovehan1me.ui.component.appbar.HanimeTopAppBar
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.component.content.LoadingContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.component.rememberRandomLoadingHint
import lovehan1me.ui.component.HapticButton as Button
import lovehan1me.ui.component.HapticTextButton as TextButton

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreviewContent(
    uiState: PreviewUiState,
    onEvent: (PreviewEvent) -> Unit,
    previewPagerState: PagerState,
    previewInfoList: List<HanimePreview.PreviewInfo>,
    modifier: Modifier = Modifier,
) {
    val loadingHint = rememberRandomLoadingHint()
    // P6 动效统一：位移走 spatial 档、淡入淡出走 effects 档，取代原先手调的
    // 320/260/220/170/420/190 与 delayMillis。
    // ⚠️ transitionSpec 的 lambda **不是** @Composable 上下文，而 motionScheme 是
    // @Composable —— 四个 spec 必须在外面先取好，且要显式类型参数：
    // slide 动画的是 Int 偏移，fade 动画的是 Float 透明度，两者不通用。
    val titleEnterSlideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val titleEnterFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val titleExitSlideSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val titleExitFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val headerEnterSlideSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
    val headerEnterFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val headerExitSlideSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val headerExitFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    HanimePageSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            HanimeTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = uiState.currentDateLabel,
                        transitionSpec = {
                            val forward = uiState.monthAnimationDirection >= 0
                            (slideInVertically(
                                animationSpec = titleEnterSlideSpec,
                                initialOffsetY = { height -> if (forward) height / 2 else -height / 2 }
                            ) + fadeIn(
                                animationSpec = titleEnterFadeSpec
                            )) togetherWith
                                    (slideOutVertically(
                                        animationSpec = titleExitSlideSpec,
                                        targetOffsetY = { height -> if (forward) -height / 2 else height / 2 }
                                    ) + fadeOut(
                                        animationSpec = titleExitFadeSpec
                                    ))
                        },
                        label = "preview_month_title",
                    ) { animatedDateLabel ->
                        Text(stringResource(Res.string.latest_hanime_list_monthly, animatedDateLabel))
                    }
                },
                onBack = { onEvent(PreviewEvent.OnBack) },
                actions = {
                    IconButton(onClick = {
                        onEvent(
                            PreviewEvent.OnOpenComment(
                                uiState.currentDateLabel,
                                uiState.routeState.currentDateCode
                            )
                        )
                    }) {
                        BadgedBox(
                            badge = {
                                if (uiState.commentCount > 0) {
                                    Badge(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 20.dp)
                                    ) {
                                        Text(
                                            text = if (uiState.commentCount > 999) "999+" else uiState.commentCount.toString(),
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_comment),
                                contentDescription = stringResource(Res.string.comment),
                            )
                        }
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    PreviewSourceNoticeCard(
                        onOpenWeb = { onEvent(PreviewEvent.OnOpenWebPreview) },
                        onOpenGetchu = { onEvent(PreviewEvent.OnOpenGetchuPreview) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }

                item {
                    AnimatedContent(
                        targetState = uiState.monthHeaderState,
                        contentKey = { it.dateCode },
                        transitionSpec = {
                            val forward = uiState.monthAnimationDirection >= 0
                            (slideInHorizontally(
                                animationSpec = headerEnterSlideSpec,
                                initialOffsetX = { width -> if (forward) width else -width }
                            ) + fadeIn(
                                animationSpec = headerEnterFadeSpec
                            )) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = headerExitSlideSpec,
                                        targetOffsetX = { width -> if (forward) -width else width }
                                    ) + fadeOut(
                                        animationSpec = headerExitFadeSpec
                                    ))
                        },
                        label = "preview_month_header",
                    ) { animatedHeaderState ->
                        PreviewHeaderSection(
                            headerImageUrl = animatedHeaderState.headerImageUrl,
                            prevLabel = animatedHeaderState.prevLabel,
                            nextLabel = animatedHeaderState.nextLabel,
                            canPrev = animatedHeaderState.canPrev,
                            canNext = animatedHeaderState.canNext,
                            onPrev = { onEvent(PreviewEvent.OnPrevMonth(animatedHeaderState.dateCode)) },
                            onNext = { onEvent(PreviewEvent.OnNextMonth(animatedHeaderState.dateCode)) },
                        )
                    }
                }

                // P6d-2：displayState 来自 shared 模块，跨模块 public 属性不可智能转换，先取局部量
                when (val displayState = uiState.displayState) {
                    is WebsiteState.Loading -> item {
                        LoadingContent(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            message = loadingHint
                        )
                    }

                    is WebsiteState.Error -> item {
                        val isPreviewEmpty =
                            displayState.throwable is HanimeNotFoundException
                        ErrorContent(
                            title = stringResource(Res.string.hanime_list),
                            message = if (isPreviewEmpty) {
                                stringResource(Res.string.preview_month_not_updated)
                            } else {
                                displayState.throwable.pienization.toString()
                            },
                            onRetry = if (isPreviewEmpty) null else {
                                { onEvent(PreviewEvent.OnRetryLoad) }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    is WebsiteState.Success -> {
                        item {
                            PreviewTourRow(
                                latestHanime = displayState.info.latestHanime,
                                selectedIndex = uiState.routeState.selectedIndex,
                                onSelect = { onEvent(PreviewEvent.OnSelectTourItem(it)) },
                            )
                        }

                        item {
                            if (previewInfoList.isEmpty()) {
                                EmptyContent(
                                    hint = stringResource(Res.string.empty_content),
                                    subHint = stringResource(Res.string.new_anime_trailers)
                                )
                            } else {
                                HorizontalPager(
                                    state = previewPagerState,
                                    beyondViewportPageCount = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 620.dp)
                                        .animateContentSize(),
                                    verticalAlignment = Alignment.Top,
                                ) { page ->
                                    PreviewInfoCard(
                                        previewInfo = previewInfoList[page],
                                        onOpenVideo = { code ->
                                            onEvent(PreviewEvent.OnOpenVideo(code))
                                        },
                                        onOpenImage = { index, imageUrls ->
                                            onEvent(PreviewEvent.OnOpenImage(index, imageUrls))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSourceNoticeCard(
    onOpenWeb: () -> Unit,
    onOpenGetchu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardContainerSurface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.preview_discontinued_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenWeb) {
                    Text(stringResource(Res.string.visit_web_version))
                }
                Button(onClick = onOpenGetchu) {
                    Text(stringResource(Res.string.view_getchu_preview))
                }
            }
        }
    }
}

@Composable
private fun PreviewHeaderSection(
    headerImageUrl: String?,
    prevLabel: String,
    nextLabel: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        HanimeAsyncImage(
            model = headerImageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onPrev,
                enabled = canPrev,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painterResource(Res.drawable.ic_chevron_left),
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(prevLabel)
            }

            FilledTonalButton(
                onClick = onNext,
                enabled = canNext,
                modifier = Modifier.weight(1f)
            ) {
                Text(nextLabel)
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null
                )
            }
        }
    }
}
