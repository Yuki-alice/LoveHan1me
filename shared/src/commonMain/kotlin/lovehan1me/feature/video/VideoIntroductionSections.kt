package lovehan1me.feature.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import lovehan1me.ui.component.HapticButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.Res
import lovehan1me.subscribed
import lovehan1me.subscribe
import lovehan1me.s_view_times
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.ic_thumb_up_alt
import lovehan1me.ic_thumb_down_off_alt
import lovehan1me.ic_thumb_down_alt
import lovehan1me.ic_play_arrow
import lovehan1me.ic_access_time
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.DisplayTextLocalizer
import lovehan1me.ui.component.rememberHapticFeedback
import kotlinx.datetime.format
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.text.style.TextAlign
import lovehan1me.sure_to_download
import lovehan1me.sure_to_redownload
import lovehan1me.watch_later
import lovehan1me.video_might_not_exist
import lovehan1me.sure
import lovehan1me.share
import lovehan1me.series_video
import lovehan1me.related_video
import lovehan1me.quick_checkin
import lovehan1me.quality_with_colon
import lovehan1me.original_comic
import lovehan1me.now_playing
import lovehan1me.no_custom_playlist_hint
import lovehan1me.no
import lovehan1me.name_with_colon
import lovehan1me.more
import lovehan1me.load_failed_retry
import lovehan1me.liked
import lovehan1me.jump_to_webpage
import lovehan1me.go_to_official
import lovehan1me.download_video_detail_below
import lovehan1me.download
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
import lovehan1me.site.hanime1.ResolutionLinkMap
import lovehan1me.data.database.entity.CheckInRecordEntity
import lovehan1me.data.LocalListRepository
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.ui.component.ExpandableRichText
import lovehan1me.ui.component.TagChipGroup
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.component.content.LoadingContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.component.lazy.LazyRow
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberCardResponsiveWidth
import lovehan1me.ui.component.rememberRandomLoadingHint
import lovehan1me.ui.adaptive.PageMetrics
import lovehan1me.ui.adaptive.rememberVideoCardMinWidth
import lovehan1me.core.util.AppToast
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.feature.home.dailycheckin.formatHm
import lovehan1me.feature.home.dailycheckin.today
import kotlin.time.Clock


@Composable
internal fun ArtistSection(
    artist: HanimeVideo.Artist,
    onOpenArtist: () -> Unit,
    onToggleSubscribe: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cardShape = shapeByInteraction(
        shapes = HanimeDefaults.cardShapes(),
        pressed = pressed,
        animationSpec = HanimeDefaults.shapesDefaultAnimationSpec,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        haptic()
                        onOpenArtist()
                    },
                    onLongClick = null,
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HanimeAsyncImage(
                model = artist.avatarUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(52.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist.genre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            artist.post?.let {
                if (artist.isSubscribed) {
                    OutlinedButton(
                        onClick = {
                            haptic()
                            onToggleSubscribe()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(text = stringResource(Res.string.subscribed))
                    }
                } else {
                    Button(
                        onClick = onToggleSubscribe,
                    ) {
                        Text(text = stringResource(Res.string.subscribe))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TitleSection(video: HanimeVideo) {
    val primaryTitle = video.chineseTitle?.takeIf { it.isNotBlank() } ?: video.title
    val secondaryTitle = video.title.takeIf { it != primaryTitle }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionContainer {
            Text(
                text = primaryTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { },
                )
            )
        }

        secondaryTitle?.let {
            SelectionContainer {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { },
                    )
                )
            }
        }
    }
}

@Composable
internal fun MetaSection(
    video: HanimeVideo,
    fromDownload: Boolean,
    onRateVideo: (Boolean) -> Unit,
) {
    val viewsText = if (fromDownload) {
        stringResource(Res.string.s_view_times, "0721")
    } else {
        DisplayTextLocalizer.localizeViews(video.views.toString())
    }
    val uploadTime = video.uploadTime?.format(previewSafeDateFormat).orEmpty()

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!fromDownload && video.ratingCount != null) {
            VideoRatingButtons(
                video = video,
                onRateVideo = onRateVideo,
            )
        }
        MetaInfoItem(
            icon = {
                Icon(
                    painter = painterResource(Res.drawable.ic_play_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            },
            text = viewsText,
        )

        MetaInfoItem(
            icon = {
                Icon(
                    painter = painterResource(Res.drawable.ic_access_time),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            },
            text = uploadTime,
        )
    }
}

@Composable
internal fun MetaInfoItem(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        modifier = Modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun VideoRatingButtons(
    video: HanimeVideo,
    onRateVideo: (Boolean) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val likeContentColor by animateColorAsState(
        targetValue = if (video.isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "LikeColor"
    )
    val likeContainerColor by animateColorAsState(
        targetValue = if (video.isFav) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        label = "LikeContainer"
    )

    val dislikeContentColor by animateColorAsState(
        targetValue = if (video.isUnlike) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "DislikeColor"
    )
    val dislikeContainerColor by animateColorAsState(
        targetValue = if (video.isUnlike) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        label = "DislikeContainer"
    )

    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(MaterialTheme.shapes.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(MaterialTheme.shapes.large.copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)))
                .background(likeContainerColor)
                .combinedClickable(
                    onClick = {
                        haptic()
                        onRateVideo(true)
                    },
                )
                .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter =
                    if (video.isFav) painterResource(Res.drawable.ic_thumb_up_alt)
                    else painterResource(Res.drawable.ic_thumb_up_off_alt),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = likeContentColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${video.likeRatio ?: 0}% (${video.ratingCount ?: 0})",
                style = MaterialTheme.typography.labelMedium,
                color = likeContentColor,
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(32.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )

        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 32.dp)
                .clip(MaterialTheme.shapes.large.copy(topStart = CornerSize(0.dp), bottomStart = CornerSize(0.dp)))
                .background(dislikeContainerColor)
                .combinedClickable(
                    onClick = {
                        haptic()
                        onRateVideo(false)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter =
                    if (video.isUnlike) painterResource(Res.drawable.ic_thumb_down_alt)
                    else painterResource(Res.drawable.ic_thumb_down_off_alt),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = dislikeContentColor
            )
        }
    }
}
