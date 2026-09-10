package lovehan1me.feature.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import lovehan1me.ui.component.HapticButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.Res
import lovehan1me.sure_to_download
import lovehan1me.sure_to_redownload
import lovehan1me.watch_later
import lovehan1me.video_might_not_exist
import lovehan1me.sure
import lovehan1me.subscribed
import lovehan1me.subscribe
import lovehan1me.share
import lovehan1me.series_video
import lovehan1me.s_view_times
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
import lovehan1me.ic_access_time
import lovehan1me.site.hanime1.ResolutionLinkMap
import lovehan1me.data.database.entity.CheckInRecordEntity
import lovehan1me.data.LocalListRepository
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeVideo
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
import lovehan1me.ui.theme.SpacingNormal
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.VideoNormalCardMinWidth
import lovehan1me.ui.theme.VideoSimplifiedCardMinWidth
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.DisplayTextLocalizer
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.core.util.SonnerToast
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import lovehan1me.feature.home.dailycheckin.formatHm
import lovehan1me.feature.home.dailycheckin.today
import kotlin.time.Clock

private val previewSafeDateFormat = LocalDate.Formats.ISO

data class DownloadPromptState(
    val newQuality: String,
    val oldQuality: String? = null,
    val oldGroupId: Int? = null,
)

@Composable
fun VideoIntroductionScreen(
    video: HanimeVideo?,
    state: VideoLoadingState<HanimeVideo>,
    fromDownload: Boolean,
    hideRelatedInIntro: Boolean,
    playlistInitialIndex: Int?,
    introFirstVisibleItemIndex: Int,
    introFirstVisibleItemScrollOffset: Int,
    downloadPrompt: DownloadPromptState?,
    onRetry: () -> Unit,
    onOpenVideo: (HanimeInfo) -> Unit,
    onOpenArtist: (HanimeVideo.Artist) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onToggleSubscribe: (HanimeVideo.Artist) -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestManageMyList: (() -> Unit) -> Unit,
    onRateVideo: (Boolean) -> Unit,
    onManageMyList: (List<String>, List<Boolean>) -> Unit,
    checkInEnabled: Boolean,
    onQuickCheckIn: (CheckInRecordEntity) -> Unit,
    onPrepareDownload: (String) -> Unit,
    onDismissDownloadPrompt: () -> Unit,
    onConfirmDownloadPrompt: (Boolean) -> Unit,
    onRequestOpenOfficialDownloadPage: () -> Unit,
    onShare: () -> Unit,
    onCopyShareText: () -> Unit,
    onOpenWebPage: () -> Unit,
    onOpenOriginalComic: (() -> Unit)?,
    onShowAllPlaylist: (() -> Unit)?,
    onPlaylistScrollChange: (Int) -> Unit,
    onIntroductionScrollChange: (Int, Int) -> Unit,
    onIntroductionLinkClick: (String) -> Unit,
) {
    val maxScreenWidth = LocalWindowInfo.current.containerSize.width.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = maxScreenWidth)
    ) {
        val currentVideo = video ?: (state as? VideoLoadingState.Success)?.info
        val loadingHint = rememberRandomLoadingHint()
        when {
            currentVideo != null -> VideoIntroductionContent(
                video = currentVideo,
                fromDownload = fromDownload,
                hideRelatedInIntro = hideRelatedInIntro,
                playlistInitialIndex = playlistInitialIndex,
                introFirstVisibleItemIndex = introFirstVisibleItemIndex,
                introFirstVisibleItemScrollOffset = introFirstVisibleItemScrollOffset,
                downloadPrompt = downloadPrompt,
                onOpenVideo = onOpenVideo,
                onOpenArtist = onOpenArtist,
                onNavigateToSearch = onNavigateToSearch,
                onToggleSubscribe = onToggleSubscribe,
                onToggleFavorite = onToggleFavorite,
                onRequestManageMyList = onRequestManageMyList,
                onRateVideo = onRateVideo,
                onManageMyList = onManageMyList,
                checkInEnabled = checkInEnabled,
                onQuickCheckIn = onQuickCheckIn,
                onPrepareDownload = onPrepareDownload,
                onDismissDownloadPrompt = onDismissDownloadPrompt,
                onConfirmDownloadPrompt = onConfirmDownloadPrompt,
                onRequestOpenOfficialDownloadPage = onRequestOpenOfficialDownloadPage,
                onShare = onShare,
                onCopyShareText = onCopyShareText,
                onOpenWebPage = onOpenWebPage,
                onOpenOriginalComic = onOpenOriginalComic,
                onShowAllPlaylist = onShowAllPlaylist,
                onPlaylistScrollChange = onPlaylistScrollChange,
                onIntroductionScrollChange = onIntroductionScrollChange,
                onIntroductionLinkClick = onIntroductionLinkClick,
            )

            state is VideoLoadingState.Error -> ErrorContent(
                title = stringResource(Res.string.load_failed_retry),
                message = state.throwable.message,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )

            state is VideoLoadingState.NoContent -> EmptyContent(
                hint = stringResource(Res.string.video_might_not_exist),
            )

            else -> LoadingContent(
                modifier = Modifier.align(Alignment.Center),
                message = loadingHint,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoIntroductionContent(
    video: HanimeVideo,
    fromDownload: Boolean,
    hideRelatedInIntro: Boolean,
    playlistInitialIndex: Int?,
    introFirstVisibleItemIndex: Int,
    introFirstVisibleItemScrollOffset: Int,
    downloadPrompt: DownloadPromptState?,
    onOpenVideo: (HanimeInfo) -> Unit,
    onOpenArtist: (HanimeVideo.Artist) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onToggleSubscribe: (HanimeVideo.Artist) -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestManageMyList: (() -> Unit) -> Unit,
    onRateVideo: (Boolean) -> Unit,
    onManageMyList: (List<String>, List<Boolean>) -> Unit,
    checkInEnabled: Boolean,
    onQuickCheckIn: (CheckInRecordEntity) -> Unit,
    onPrepareDownload: (String) -> Unit,
    onDismissDownloadPrompt: () -> Unit,
    onConfirmDownloadPrompt: (Boolean) -> Unit,
    onRequestOpenOfficialDownloadPage: () -> Unit,
    onShare: () -> Unit,
    onCopyShareText: () -> Unit,
    onOpenWebPage: () -> Unit,
    onOpenOriginalComic: (() -> Unit)?,
    onShowAllPlaylist: (() -> Unit)?,
    onPlaylistScrollChange: (Int) -> Unit,
    onIntroductionScrollChange: (Int, Int) -> Unit,
    onIntroductionLinkClick: (String) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = introFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = introFirstVisibleItemScrollOffset,
    )
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showQuickCheckInDialog by remember { mutableStateOf(false) }
    var showMyListDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }

    if (showDownloadQualityDialog) {
        DownloadQualityDialog(
            videoUrls = video.videoUrls,
            onDismiss = { showDownloadQualityDialog = false },
            onOpenOfficial = {
                showDownloadQualityDialog = false
                onRequestOpenOfficialDownloadPage()
            },
            onSelectQuality = { quality ->
                showDownloadQualityDialog = false
                onPrepareDownload(quality)
            },
        )
    }

    if (downloadPrompt != null) {
        DownloadConfirmDialog(
            video = video,
            prompt = downloadPrompt,
            onDismiss = onDismissDownloadPrompt,
            onConfirm = onConfirmDownloadPrompt,
            onOpenOfficial = {
                onDismissDownloadPrompt()
                onRequestOpenOfficialDownloadPage()
            },
        )
    }

    val playlistForSheet = video.playlist
    if (showPlaylistSheet && playlistForSheet != null) {
        PlaylistBottomSheet(
            playlist = playlistForSheet,
            onDismiss = { showPlaylistSheet = false },
            onOpenVideo = {
                showPlaylistSheet = false
                onOpenVideo(it)
            },
        )
    }

    if (showQuickCheckInDialog) {
        QuickCheckInDialog(
            video = video,
            onDismiss = { showQuickCheckInDialog = false },
            onConfirm = {
                onQuickCheckIn(it)
                showQuickCheckInDialog = false
            },
        )
    }

    val myListForDialog = video.myList
    if (showMyListDialog && myListForDialog != null) {
        MyListDialog(
            myList = myListForDialog,
            onDismiss = { showMyListDialog = false },
            onConfirm = { selectedStates ->
                onManageMyList(myListForDialog.titleArray.toList(), selectedStates)
                showMyListDialog = false
            },
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (index, offset) ->
            onIntroductionScrollChange(index, offset)
        }
    }

    // M2：AndroidView 互操作连接在 CMP common 不可用；纯 Compose 列表无需它。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val relatedItems = video.relatedHanimes
        val relatedIsNormal = relatedItems.firstOrNull()?.itemType == HanimeInfo.NORMAL
        val relatedMinCardWidth =
            if (relatedIsNormal) VideoNormalCardMinWidth else VideoSimplifiedCardMinWidth
        val relatedAvailableWidth = (maxWidth - 12.dp).coerceAtLeast(0.dp)
        val relatedColumns = maxOf(
            2,
            ((relatedAvailableWidth + SpacingNormal) / (relatedMinCardWidth + SpacingNormal)).toInt(),
        )
        val relatedRows = remember(relatedItems, relatedColumns) {
            relatedItems.chunked(relatedColumns)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 6.dp,
                top = 12.dp,
                end = 6.dp,
                bottom = 24.dp + bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            enableItemAnimation = false,
        ) {
            video.artist?.let { artist ->
                item(key = "artist") {
                    ArtistSection(
                        artist = artist,
                        onOpenArtist = { onOpenArtist(artist) },
                        onToggleSubscribe = { onToggleSubscribe(artist) },
                    )
                }
            }

            item(key = "title") {
                TitleSection(video = video)
            }

            item(key = "meta") {
                MetaSection(
                    video = video,
                    fromDownload = fromDownload,
                    onRateVideo = onRateVideo,
                )
            }

            item(key = "intro") {
                ExpandableIntroductionSection(
                    introduction = video.introduction.orEmpty(),
                    onIntroductionLinkClick = onIntroductionLinkClick,
                )
            }

            if (!fromDownload) {
                item(key = "actions") {
                    ActionSection(
                        isFav = video.isFav,
                        hasOriginalComic = !video.originalComic.isNullOrBlank(),
                        checkInEnabled = checkInEnabled,
                        onQuickCheckIn = { showQuickCheckInDialog = true },
                        onOpenOriginalComic = onOpenOriginalComic,
                        onToggleFavorite = onToggleFavorite,
                        onManageMyList = {
                            onRequestManageMyList { showMyListDialog = true }
                        },
                        onDownload = { showDownloadQualityDialog = true },
                        onShare = onShare,
                        onCopyShareText = onCopyShareText,
                        onOpenWebPage = onOpenWebPage,
                    )
                }
            }

            if (video.tags.isNotEmpty()) {
                item(key = "tags") {
                    TagsSection(
                        tags = video.tags,
                        onTagClick = onNavigateToSearch,
                    )
                }
            }

            val playlistForRow = video.playlist
            if (!fromDownload && playlistForRow != null && playlistForRow.video.isNotEmpty()) {
                item(key = "playlist") {
                    PlaylistSection(
                        playlist = playlistForRow,
                        initialIndex = playlistInitialIndex,
                        onOpenVideo = onOpenVideo,
                        onShowAllPlaylist = if (onShowAllPlaylist != null) {
                            { showPlaylistSheet = true }
                        } else {
                            null
                        },
                        onPlaylistScrollChange = onPlaylistScrollChange,
                    )
                }
            }

            if (!fromDownload && !hideRelatedInIntro && relatedItems.isNotEmpty()) {
                item(key = "related_header", contentType = "section_header") {
                    SectionHeader(title = stringResource(Res.string.related_video))
                }
                itemsIndexed(
                    items = relatedRows,
                    key = { index, row -> "related_row_${index}_${row.first().videoCode}" },
                    contentType = { _, _ -> "related_row" },
                ) { _, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                    ) {
                        row.forEach { item ->
                            RelatedVideoCard(
                                item = item,
                                onOpenVideo = onOpenVideo,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(relatedColumns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadQualityDialog(
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
private fun DownloadConfirmDialog(
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
private fun QuickCheckInDialog(
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
private fun MyListDialog(
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
private fun PlaylistBottomSheet(
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
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
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
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
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

@Composable
private fun ArtistSection(
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
private fun TitleSection(video: HanimeVideo) {
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
private fun MetaSection(
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
private fun MetaInfoItem(
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
private fun VideoRatingButtons(
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
            .clip(RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
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
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
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

@Composable
private fun ExpandableIntroductionSection(
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
private fun ActionSection(
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
private fun VideoActionButton(
    iconRes: DrawableResource,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = rememberHapticFeedback()
    Column(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
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
private fun TagsSection(
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
private fun PlaylistSection(
    playlist: HanimeVideo.Playlist,
    initialIndex: Int?,
    onOpenVideo: (HanimeInfo) -> Unit,
    onShowAllPlaylist: (() -> Unit)?,
    onPlaylistScrollChange: (Int) -> Unit,
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(Res.string.series_video),
            subtitle = playlist.playlistName,
            actionText = if (onShowAllPlaylist != null) stringResource(Res.string.more) else null,
            onActionClick = onShowAllPlaylist,
        )
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

@Composable
internal fun RelatedVideosSection(
    videos: List<HanimeInfo>,
    onOpenVideo: (HanimeInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNormal = videos.firstOrNull()?.itemType == HanimeInfo.NORMAL
    val minCardWidth = if (isNormal) VideoNormalCardMinWidth else VideoSimplifiedCardMinWidth
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCardWidth),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
        verticalArrangement = Arrangement.spacedBy(SpacingNormal),
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
private fun RelatedVideoCard(
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
private fun SectionHeader(
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
