package lovehan1me.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
import lovehan1me.sure_to_download
import lovehan1me.sure_to_redownload
import lovehan1me.watch_later
import lovehan1me.sure
import lovehan1me.series_video
import lovehan1me.quick_checkin
import lovehan1me.quality_with_colon
import lovehan1me.now_playing
import lovehan1me.no_custom_playlist_hint
import lovehan1me.no
import lovehan1me.name_with_colon
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
import lovehan1me.ic_access_time
import lovehan1me.site.hanime1.ResolutionLinkMap
import lovehan1me.data.database.entity.CheckInRecordEntity
import lovehan1me.data.LocalListRepository
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.component.rememberHapticFeedback
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.feature.home.dailycheckin.formatHm
import lovehan1me.feature.home.dailycheckin.today
import kotlin.time.Clock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import lovehan1me.ui.component.HapticButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.text.style.TextAlign
import lovehan1me.video_might_not_exist
import lovehan1me.subscribed
import lovehan1me.subscribe
import lovehan1me.share
import lovehan1me.s_view_times
import lovehan1me.related_video
import lovehan1me.original_comic
import lovehan1me.more
import lovehan1me.load_failed_retry
import lovehan1me.liked
import lovehan1me.jump_to_webpage
import lovehan1me.add_to_fav
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.ic_thumb_up_alt
import lovehan1me.ic_thumb_down_off_alt
import lovehan1me.ic_thumb_down_alt
import lovehan1me.ic_share
import lovehan1me.ic_play_arrow
import lovehan1me.ic_language
import lovehan1me.ic_format_list_bulleted_add
import lovehan1me.ic_favorite_border
import lovehan1me.ic_favorite
import lovehan1me.ic_download
import lovehan1me.ic_check_circle
import lovehan1me.ic_book
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.ui.component.ExpandableRichText
import lovehan1me.ui.component.TagChipGroup
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.component.content.LoadingContent
import lovehan1me.ui.component.lazy.LazyRow
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberCardResponsiveWidth
import lovehan1me.ui.component.rememberRandomLoadingHint
import lovehan1me.ui.adaptive.PageMetrics
import lovehan1me.ui.adaptive.rememberVideoCardMinWidth
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.DisplayTextLocalizer
import lovehan1me.core.util.AppToast
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format


@Composable
internal fun DownloadQualityDialog(
    videoUrls: ResolutionLinkMap,
    onDismiss: () -> Unit,
    onOpenOfficial: () -> Unit,
    onSelectQuality: (String) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val qualities = videoUrls.keys.toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.download)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = false,
                                onClick = {
                                    haptic()
                                    if (quality == lovehan1me.site.hanime1.HanimeResolution.RES_UNKNOWN) {
                                        onOpenOfficial()
                                    } else {
                                        onSelectQuality(quality)
                                    }
                                },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(quality)
                        if (quality == lovehan1me.site.hanime1.HanimeResolution.RES_UNKNOWN) {
                            Text(
                                text = stringResource(Res.string.go_to_official),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
internal fun DownloadConfirmDialog(
    video: HanimeVideo,
    prompt: DownloadPromptState,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    onOpenOfficial: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    var autoCreateGroup by remember(prompt, video.title) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (prompt.oldQuality != null) Res.string.sure_to_redownload else Res.string.sure_to_download
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.download_video_detail_below))
                prompt.oldQuality?.let {
                    Text(stringResource(Res.string.check_video_exists_in_download, it))
                }
                Text(stringResource(Res.string.name_with_colon) + video.title)
                Text(
                    stringResource(Res.string.quality_with_colon) + if (
                        prompt.oldQuality != null && prompt.oldQuality != prompt.newQuality
                    ) {
                        "${prompt.oldQuality} → ${prompt.newQuality}"
                    } else {
                        prompt.newQuality
                    }
                )
                Text(
                    text = stringResource(Res.string.after_download_tips),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = autoCreateGroup,
                            onValueChange = { checked ->
                                haptic()
                                autoCreateGroup = checked
                            },
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = autoCreateGroup,
                        onCheckedChange = null,
                    )
                    Text(stringResource(Res.string.auto_create_same_name_download_group))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(autoCreateGroup) }) {
                Text(stringResource(Res.string.sure))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenOfficial) {
                    Text(stringResource(Res.string.go_to_official))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.no))
                }
            }
        },
    )
}

@Composable
internal fun QuickCheckInDialog(
    video: HanimeVideo,
    onDismiss: () -> Unit,
    onConfirm: (CheckInRecordEntity) -> Unit,
) {
    var feeling by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.quick_checkin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = feeling,
                    onValueChange = { if (it.length <= 200) feeling = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.dialog_feeling_hint)) },
                    minLines = 3,
                    supportingText = { Text("${feeling.length}/200") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // M2：java.time 在 commonMain 不可用；改共享 today()/formatHm()
                    //（HomeRouteScreen 打卡同款写法），输出同为 ISO 日期 + "HH:mm"。
                    onConfirm(
                        CheckInRecordEntity(
                            date = today().toString(),
                            time = Clock.System.now()
                                .toLocalDateTime(TimeZone.currentSystemDefault()).time.formatHm(),
                            type = lovehan1me.data.database.entity.CheckInType.MASTURBATION.storeName,
                            feeling = feeling,
                        )
                    )
                },
            ) {
                Text(stringResource(Res.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        },
    )
}

@Composable
internal fun MyListDialog(
    myList: HanimeVideo.MyList,
    onDismiss: () -> Unit,
    onConfirm: (List<Boolean>) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    var selectedStates by remember(myList.myListInfo) {
        mutableStateOf(myList.myListInfo.map { it.isSelected })
    }
    val hasCustomPlaylist = myList.myListInfo.any { it.code != "save" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_to_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasCustomPlaylist) {
                    Text(
                        text = stringResource(Res.string.no_custom_playlist_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(myList.myListInfo.indices.toList()) { index ->
                        val info = myList.myListInfo[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = selectedStates[index],
                                    onValueChange = { checked ->
                                        haptic()
                                        selectedStates =
                                            selectedStates.toMutableList()
                                                .also { it[index] = checked }
                                    },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(
                                checked = selectedStates[index],
                                onCheckedChange = null,
                            )
                            Text(
                                // P6c：VM 下沉后 watch-later 条目 title 为占位（commonMain 无同步资源读）
                                text = if (info.code == LocalListRepository.WATCH_LATER_CODE) {
                                    stringResource(Res.string.watch_later)
                                } else {
                                    info.title
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedStates) },
            ) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.back))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistBottomSheet(
    playlist: HanimeVideo.Playlist,
    onDismiss: () -> Unit,
    onOpenVideo: (HanimeInfo) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val playingIndex = remember(playlist) {
        playlist.video.indexOfFirst { it.isPlaying }.coerceAtLeast(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HanimeDefaults.Colors.pageSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.series_video),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    playlist.playlistName?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.blank_brackets, playlist.video.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val listState = remember(playlist, playingIndex) {
                LazyListState(firstVisibleItemIndex = playingIndex)
            }
            LaunchedEffect(playingIndex) {
                listState.scrollToItem(playingIndex)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(playlist.video, key = { it.videoCode }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (item.isPlaying) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                            .combinedClickable(
                                enabled = !item.isPlaying,
                                onClick = {
                                    haptic()
                                    onOpenVideo(item)
                                },
                                onLongClick = null,
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HanimeAsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.title,
                            modifier = Modifier
                                .width(100.dp)
                                .height(66.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            val metaColor = if (item.isPlaying) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (item.isPlaying) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.currentArtist?.takeIf { it.isNotBlank() }?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = metaColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            item.duration?.takeIf { it.isNotBlank() }?.let { duration ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_access_time),
                                        contentDescription = null,
                                        tint = metaColor,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = duration,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = metaColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (item.isPlaying) {
                            Text(
                                text = stringResource(Res.string.now_playing),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
