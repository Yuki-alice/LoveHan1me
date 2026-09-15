package lovehan1me.feature.video

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.share
import lovehan1me.series_video
import lovehan1me.related_video
import lovehan1me.quick_checkin
import lovehan1me.original_comic
import lovehan1me.more
import lovehan1me.liked
import lovehan1me.jump_to_webpage
import lovehan1me.download
import lovehan1me.add_to_playlist
import lovehan1me.add_to_fav
import lovehan1me.ic_share
import lovehan1me.ic_language
import lovehan1me.ic_format_list_bulleted_add
import lovehan1me.ic_favorite_border
import lovehan1me.ic_favorite
import lovehan1me.ic_download
import lovehan1me.ic_check_circle
import lovehan1me.ic_book
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.ui.component.ExpandableRichText
import lovehan1me.ui.component.TagChipGroup
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.lazy.LazyRow
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberCardResponsiveWidth
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.adaptive.rememberVideoCardMinWidth
import lovehan1me.ui.component.rememberHapticFeedback
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import lovehan1me.ic_keyboard_arrow_down
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import lovehan1me.ui.component.HapticButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.sure_to_download
import lovehan1me.sure_to_redownload
import lovehan1me.watch_later
import lovehan1me.video_might_not_exist
import lovehan1me.sure
import lovehan1me.subscribed
import lovehan1me.subscribe
import lovehan1me.s_view_times
import lovehan1me.quality_with_colon
import lovehan1me.now_playing
import lovehan1me.no_custom_playlist_hint
import lovehan1me.no
import lovehan1me.name_with_colon
import lovehan1me.load_failed_retry
import lovehan1me.go_to_official
import lovehan1me.download_video_detail_below
import lovehan1me.dialog_feeling_hint
import lovehan1me.dialog_confirm
import lovehan1me.dialog_cancel
import lovehan1me.confirm
import lovehan1me.check_video_exists_in_download
import lovehan1me.cancel
import lovehan1me.blank_brackets
import lovehan1me.back
import lovehan1me.auto_create_same_name_download_group
import lovehan1me.after_download_tips
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.ic_thumb_up_alt
import lovehan1me.ic_thumb_down_off_alt
import lovehan1me.ic_thumb_down_alt
import lovehan1me.ic_play_arrow
import lovehan1me.ic_access_time
import lovehan1me.site.hanime1.ResolutionLinkMap
import lovehan1me.data.database.entity.CheckInRecordEntity
import lovehan1me.data.LocalListRepository
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.component.content.LoadingContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.component.rememberRandomLoadingHint
import lovehan1me.ui.adaptive.PageMetrics
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.DisplayTextLocalizer
import lovehan1me.core.util.SonnerToast
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import lovehan1me.feature.home.dailycheckin.formatHm
import lovehan1me.feature.home.dailycheckin.today
import kotlin.time.Clock


@Composable
internal fun ExpandableIntroductionSection(
    introduction: String,
    onIntroductionLinkClick: (String) -> Unit,
) {
    if (introduction.isBlank()) return
    ExpandableRichText(
        text = introduction,
        onLinkClick = onIntroductionLinkClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActionSection(
    isFav: Boolean,
    hasOriginalComic: Boolean,
    checkInEnabled: Boolean,
    onQuickCheckIn: () -> Unit,
    onOpenOriginalComic: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onManageMyList: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onCopyShareText: () -> Unit,
    onOpenWebPage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (checkInEnabled) {
            VideoActionButton(
                iconRes = Res.drawable.ic_check_circle,
                label = stringResource(Res.string.quick_checkin),
                onClick = onQuickCheckIn,
            )
        }
        if (hasOriginalComic && onOpenOriginalComic != null) {
            VideoActionButton(
                iconRes = Res.drawable.ic_book,
                label = stringResource(Res.string.original_comic),
                onClick = onOpenOriginalComic,
            )
        }
        VideoActionButton(
            iconRes = if (isFav) Res.drawable.ic_favorite else Res.drawable.ic_favorite_border,
            label = if (isFav) stringResource(Res.string.liked) else stringResource(Res.string.add_to_fav),
            onClick = onToggleFavorite,
        )
        VideoActionButton(
            iconRes = Res.drawable.ic_format_list_bulleted_add,
            label = stringResource(Res.string.add_to_playlist),
            onClick = onManageMyList,
        )
        VideoActionButton(
            iconRes = Res.drawable.ic_download,
            label = stringResource(Res.string.download),
            onClick = onDownload,
        )
        VideoActionButton(
            iconRes = Res.drawable.ic_share,
            label = stringResource(Res.string.share),
            onClick = onShare,
            onLongClick = onCopyShareText,
        )
        VideoActionButton(
            iconRes = Res.drawable.ic_language,
            label = stringResource(Res.string.jump_to_webpage),
            onClick = onOpenWebPage,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoActionButton(
    iconRes: DrawableResource,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = rememberHapticFeedback()
    Column(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = {
                    haptic()
                    onClick()
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        haptic()
                        action()
                    }
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun TagsSection(
    tags: List<String>,
    onTagClick: (String) -> Unit,
) {
    TagChipGroup(
        tags = tags,
        collapsible = true,
        collapsedMaxLines = 2,
        modifier = Modifier.fillMaxWidth(),
        onTagClick = onTagClick,
    )
}

@Composable
internal fun PlaylistSection(
    playlist: HanimeVideo.Playlist,
    initialIndex: Int?,
    onOpenVideo: (HanimeInfo) -> Unit,
    onShowAllPlaylist: (() -> Unit)?,
    onPlaylistScrollChange: (Int) -> Unit,
    /**
     * Animeko 剧集列表折叠卡：标题行点击展开/收起（默认收起，箭头 ∨/∧）。
     * 只在宽屏详情列打开；窄屏保持 false → 原平铺行为不变。
     */
    collapsible: Boolean = false,
) {
    val (_, itemsToShow) = rememberCardResponsiveWidth()
    val videos = remember(playlist.video) { playlist.video.distinctBy(HanimeInfo::videoCode) }
    val playingIndex = videos.indexOfFirst { it.isPlaying }
    val visibleItemCount = itemsToShow.toInt().coerceAtLeast(1)
    val centeredInitialIndex = if (playingIndex >= 0) {
        val centerOffset = (itemsToShow / 2f).toInt()
        val maxStartIndex = (videos.size - visibleItemCount).coerceAtLeast(0)
        (playingIndex - centerOffset).coerceIn(0, maxStartIndex)
    } else {
        0
    }
    val resolvedInitialIndex = (initialIndex ?: centeredInitialIndex)
        .coerceIn(0, videos.lastIndex.coerceAtLeast(0))
    val listState = remember(videos, resolvedInitialIndex) {
        LazyListState(firstVisibleItemIndex = resolvedInitialIndex)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect(onPlaylistScrollChange)
    }
    // 折叠卡展开态：collapsible=false 时恒展开（窄屏原样）。
    var expanded by remember(collapsible) { androidx.compose.runtime.mutableStateOf(!collapsible) }
    val haptic = rememberHapticFeedback()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (collapsible) {
            // Animeko 折叠卡头：标题 + 副标题 + 尾部箭头，整行可点。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        haptic()
                        expanded = !expanded
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.series_video),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!playlist.playlistName.isNullOrBlank()) {
                    Text(
                        text = playlist.playlistName.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Icon(
                    painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            rotationZ = if (expanded) 180f else 0f
                        },
                )
            }
        } else {
            SectionHeader(
                title = stringResource(Res.string.series_video),
                subtitle = playlist.playlistName,
                actionText = if (onShowAllPlaylist != null) stringResource(Res.string.more) else null,
                onActionClick = onShowAllPlaylist,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val (cardWidth, _) = rememberCardResponsiveWidth()
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(videos, key = { it.videoCode }) { item ->
                    VideoCardItem(
                        modifier = Modifier.width(cardWidth),
                        videoItem = item,
                        isHorizontalCard = item.itemType == HanimeInfo.NORMAL,
                        isPlaying = item.isPlaying,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClickVideosItem = { onOpenVideo(item) },
                        onLongClickVideosItem = { _, _ -> },
                    )
                }
            }
        }
    }
}

@Composable
internal fun RelatedVideosSection(
    videos: List<HanimeInfo>,
    onOpenVideo: (HanimeInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNormal = videos.firstOrNull()?.itemType == HanimeInfo.NORMAL
    val minCardWidth = rememberVideoCardMinWidth(simplified = !isNormal)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCardWidth),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
        enableItemAnimation = false,
    ) {
        item(
            key = "related_header",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "section_header",
        ) {
            SectionHeader(title = stringResource(Res.string.related_video))
        }
        items(
            items = videos,
            key = { it.videoCode },
            contentType = { "related_video" },
        ) { item ->
            RelatedVideoCard(
                item = item,
                onOpenVideo = onOpenVideo,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun RelatedVideoCard(
    item: HanimeInfo,
    onOpenVideo: (HanimeInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoCardItem(
        modifier = modifier,
        videoItem = item,
        isHorizontalCard = item.itemType == HanimeInfo.NORMAL,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onClickVideosItem = { onOpenVideo(item) },
        onLongClickVideosItem = { _, _ -> },
    )
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!actionText.isNullOrBlank() && onActionClick != null) {
                TextButton(
                    onClick = onActionClick,
                ) {
                    Text(actionText)
                }
            }
        }
        HorizontalDivider()
    }
}
