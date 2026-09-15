package lovehan1me.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.video_might_not_exist
import lovehan1me.related_video
import lovehan1me.load_failed_retry
import lovehan1me.data.database.entity.CheckInRecordEntity
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.component.content.LoadingContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.component.rememberRandomLoadingHint
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.adaptive.PageMetrics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.sure_to_download
import lovehan1me.sure_to_redownload
import lovehan1me.watch_later
import lovehan1me.sure
import lovehan1me.subscribed
import lovehan1me.subscribe
import lovehan1me.share
import lovehan1me.series_video
import lovehan1me.s_view_times
import lovehan1me.quick_checkin
import lovehan1me.quality_with_colon
import lovehan1me.original_comic
import lovehan1me.now_playing
import lovehan1me.no_custom_playlist_hint
import lovehan1me.no
import lovehan1me.name_with_colon
import lovehan1me.more
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
import lovehan1me.data.LocalListRepository
import lovehan1me.ui.component.ExpandableRichText
import lovehan1me.ui.component.TagChipGroup
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.lazy.LazyRow
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberCardResponsiveWidth
import lovehan1me.ui.adaptive.rememberVideoCardMinWidth
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.DisplayTextLocalizer
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.core.util.SonnerToast
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import lovehan1me.feature.home.dailycheckin.formatHm
import lovehan1me.feature.home.dailycheckin.today
import kotlin.time.Clock

internal val previewSafeDateFormat = LocalDate.Formats.ISO

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
internal fun VideoIntroductionContent(
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
        // 容器内已有实宽，直接用局部宽度分档（比全局内容宽度更准）
        val relatedMinCardWidth =
            PageMetrics.videoCardMinWidthFor(maxWidth, simplified = !relatedIsNormal)
        val relatedAvailableWidth = (maxWidth - 12.dp).coerceAtLeast(0.dp)
        val relatedColumns = maxOf(
            2,
            ((relatedAvailableWidth + HanimeDefaults.Spacing.medium) / (relatedMinCardWidth + HanimeDefaults.Spacing.medium)).toInt(),
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
                        horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
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
