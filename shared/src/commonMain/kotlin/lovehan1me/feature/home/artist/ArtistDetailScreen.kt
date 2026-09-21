package lovehan1me.feature.home.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import lovehan1me.Res
import lovehan1me.artist_home_tab
import lovehan1me.ic_play_arrow
import lovehan1me.play_list
import lovehan1me.retry
import lovehan1me.share
import lovehan1me.subscribe
import lovehan1me.subscribed
import lovehan1me.unknown_error
import lovehan1me.video
import lovehan1me.view_all
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.app.navigation.main.ArtistRoute
import lovehan1me.app.navigation.main.SitePlaylistRoute
import lovehan1me.app.sharedViewModel
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.data.SettingsRepository
import lovehan1me.site.hanime1.SitePlaylistSummary
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.appbar.HanimeTopAppBar
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberVideoGridColumns
import lovehan1me.ui.theme.HanimeDefaults

/**
 * G2-1b-3：作者页重设计（对齐站内 `/user/{id}`：banner 头 + 首頁/影片/播放清單 tabs）。
 *
 * 首頁 = 近期作品 + 查看全部；影片 = 全量分页；播放清單 = 服务端排序 + 清单卡 → 独立页。
 */
@Composable
fun ArtistDetailScreen(
    route: ArtistRoute,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenPlaylist: (SitePlaylistRoute) -> Unit = {},
) {
    val viewModel: ArtistViewModel = sharedViewModel(::ArtistViewModel)
    val header by viewModel.header.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val homePlaylists by viewModel.homePlaylists.collectAsStateWithLifecycle()
    val works by viewModel.works.collectAsStateWithLifecycle()
    val worksLoading by viewModel.worksLoading.collectAsStateWithLifecycle()
    val worksError by viewModel.worksError.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistsLoading by viewModel.playlistsLoading.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val copyText = rememberCopyTextToClipboard()
    val videoColumns = rememberVideoGridColumns()
    val gridState = rememberLazyGridState()
    var selectedTab by rememberSaveable(route.userId) { mutableIntStateOf(0) }

    LaunchedEffect(route.userId) {
        viewModel.initSubscribed(route.isSubscribed)
        viewModel.load(route.userId)
    }
    // 影片 tab 滑到底自动续页（与订阅页同模式）。
    LaunchedEffect(gridState, selectedTab, works.size) {
        if (selectedTab != 1) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= works.size - 4 && works.isNotEmpty()) {
                    viewModel.loadMore()
                }
            }
    }

    val displayName = header?.name ?: route.name
    val displayAvatar = header?.avatarUrl?.takeIf { it.isNotBlank() } ?: route.avatarUrl
    val statsLine = listOfNotNull(
        "@ ${route.userId}",
        header?.statsText?.takeIf { it.isNotBlank() } ?: route.genre?.takeIf { it.isNotBlank() },
    ).joinToString(" • ")

    Scaffold(
        topBar = { HanimeTopAppBar(title = displayName, onBack = onBack) },
    ) { padding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(videoColumns),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HanimeDefaults.Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
        ) {
            // Banner 头：大头像 + 名 + @id·订阅数·视频数 + 订阅/分享。
            item(span = { GridItemSpan(videoColumns) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HanimeAsyncImage(
                        model = displayAvatar,
                        contentDescription = displayName,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = statsLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (route.postUserId != null && route.postArtistId != null) {
                                if (subscribed == true) {
                                    OutlinedButton(onClick = {
                                        viewModel.toggleSubscribe(route.postUserId, route.postArtistId)
                                    }) { Text(stringResource(Res.string.subscribed)) }
                                } else {
                                    Button(onClick = {
                                        viewModel.toggleSubscribe(route.postUserId, route.postArtistId)
                                    }) { Text(stringResource(Res.string.subscribe)) }
                                }
                            }
                            OutlinedButton(onClick = {
                                copyText("${SettingsRepository.baseUrl}user/${route.userId}")
                            }) { Text(stringResource(Res.string.share)) }
                        }
                    }
                }
            }
            // Tabs：首頁 / 影片 / 播放清單（对齐站内作者页 tab 结构）。
            item(span = { GridItemSpan(videoColumns) }) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(Res.string.artist_home_tab)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(Res.string.video)) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(Res.string.play_list)) },
                    )
                }
            }
            when (selectedTab) {
                0 -> {
                    item(span = { GridItemSpan(videoColumns) }) {
                        SectionHeader(
                            title = stringResource(Res.string.video),
                            actionLabel = stringResource(Res.string.view_all),
                            onAction = { selectedTab = 1 },
                        )
                    }
                    // Preview 截 2 行（列数与首页同一矩阵，宽屏 6 列即 12 张）。
                    items(items = recent.take(videoColumns * 2), key = { it.videoCode }) { video ->
                        VideoCardItem(
                            videoItem = video,
                            onClickVideosItem = { onNavigateToVideo(video.videoCode) },
                            onLongClickVideosItem = { _, _ -> },
                        )
                    }
                    if (homePlaylists.isNotEmpty()) {
                        item(span = { GridItemSpan(videoColumns) }) {
                            SectionHeader(
                                title = stringResource(Res.string.play_list),
                                actionLabel = stringResource(Res.string.view_all),
                                onAction = { selectedTab = 2 },
                            )
                        }
                        items(items = homePlaylists.take(videoColumns * 2), key = { it.listId }) { summary ->
                            PlaylistCard(
                                summary = summary,
                                onClick = {
                                    onOpenPlaylist(
                                        SitePlaylistRoute(
                                            name = summary.name,
                                            videosJson = "",
                                            listId = summary.listId,
                                        )
                                    )
                                },
                            )
                        }
                    }
                }
                1 -> {
                    items(items = works, key = { it.videoCode }) { video ->
                        VideoCardItem(
                            videoItem = video,
                            onClickVideosItem = { onNavigateToVideo(video.videoCode) },
                            onLongClickVideosItem = { _, _ -> },
                        )
                    }
                }
                else -> {
                    val sorts = playlists?.availableSorts.orEmpty()
                    if (sorts.size > 1) {
                        item(span = { GridItemSpan(videoColumns) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                sorts.forEach { sort ->
                                    AssistChip(
                                        onClick = { viewModel.loadPlaylists(route.userId, sort.value) },
                                        label = { Text(sort.label) },
                                        enabled = sort.value != playlists?.activeSort,
                                    )
                                }
                            }
                        }
                    }
                    items(
                        items = playlists?.summaries.orEmpty(),
                        key = { it.listId },
                    ) { summary ->
                        PlaylistCard(
                            summary = summary,
                            onClick = {
                                onOpenPlaylist(
                                    SitePlaylistRoute(
                                        name = summary.name,
                                        videosJson = "",
                                        listId = summary.listId,
                                    )
                                )
                            },
                        )
                    }
                    if (playlistsLoading && playlists == null) {
                        item(span = { GridItemSpan(videoColumns) }) {
                            LoadingRow()
                        }
                    }
                }
            }
            if (selectedTab == 1 && worksLoading) {
                item(span = { GridItemSpan(videoColumns) }) { LoadingRow() }
            }
            if (selectedTab == 1 && worksError != null && works.isEmpty()) {
                item(span = { GridItemSpan(videoColumns) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(Res.string.unknown_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { viewModel.retry() }) { Text(stringResource(Res.string.retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(
            onClick = onAction,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        ) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun LoadingRow() {
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** 系列清单卡（对齐站内：封面 + 播放数徽章 + 标题 + 更新时间）。 */
@Composable
private fun PlaylistCard(summary: SitePlaylistSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            HanimeAsyncImage(
                model = summary.coverUrl,
                contentDescription = summary.name,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            // 站内同款中央播放 overlay（圆形暗底 + 箭头）。
            Surface(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_play_arrow),
                        contentDescription = null,
                        tint = HanimeDefaults.Overlay.onScrim,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            if (summary.videoCountText.isNotBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
                ) {
                    Text(
                        text = summary.videoCountText,
                        style = MaterialTheme.typography.labelSmall,
                        color = lovehan1me.ui.theme.HanimeDefaults.Overlay.onScrim,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(
            text = summary.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        summary.updatedText?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
