package lovehan1me.feature.home.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lovehan1me.Res
import lovehan1me.app.navigation.main.ArtistRoute
import lovehan1me.app.navigation.main.SitePlaylistRoute
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.VideoItemType
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.retry
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.appbar.HanimeTopAppBar
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberVideoGridColumns
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.unknown_error
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lovehan1me.data.NetworkRepo
import lovehan1me.site.hanime1.SitePlaylistDetail

/**
 * 站内系列清单卡片（路由 DTO：`HanimeInfo` 带接口成员，不可直序列化）。
 */
@Serializable
data class SitePlaylistVideo(
    val videoCode: String,
    val title: String,
    val coverUrl: String,
    val duration: String? = null,
    val views: String? = null,
    val isPlaying: Boolean = false,
)

private val playlistJson = Json { ignoreUnknownKeys = true }

/** 内嵌清单 → 路由 JSON（失败返回 null，调用方回退不导航）。 */
fun encodeSitePlaylist(videos: List<SitePlaylistVideo>): String? =
    runCatching { playlistJson.encodeToString(videos) }.getOrNull()

fun decodeSitePlaylist(json: String): List<SitePlaylistVideo> =
    runCatching { playlistJson.decodeFromString<List<SitePlaylistVideo>>(json) }.getOrDefault(emptyList())

/** 独立页 href（`/playlist?list=976998…`）→ listId，取不到返回 null（调用方走内嵌兜底）。 */
fun playlistListId(listUrl: String?): String? =
    listUrl?.let { Regex("list=(\\d+)").find(it)?.groupValues?.get(1) }

fun HanimeInfo.toSitePlaylistVideo(isPlaying: Boolean = false) = SitePlaylistVideo(
    videoCode = videoCode,
    title = title,
    coverUrl = coverUrl,
    duration = duration,
    views = views,
    isPlaying = isPlaying,
)

/** G2-1b-2：独立拉取 VM（`listId` 直达；内嵌 JSON 路径不经过这里）。 */
class SitePlaylistViewModel : ViewModel() {
    private val _detail = MutableStateFlow<WebsiteState<SitePlaylistDetail>?>(null)
    val detail = _detail.asStateFlow()

    fun load(listId: String, sort: String?) {
        _detail.value = null
        viewModelScope.launch {
            NetworkRepo.getSitePlaylist(listId, sort).collect { _detail.value = it }
        }
    }
}

/**
 * 系列清单页：`listId` 在走独立拉取（头部 + 排序 pills + 全量），
 * 否则渲染详情页内嵌清单（v1 路径，零请求）。
 */
@Composable
fun SitePlaylistScreen(
    route: SitePlaylistRoute,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenArtistRoute: (ArtistRoute) -> Unit = {},
) {
    if (route.listId == null) {
        val videos = remember(route.videosJson) { decodeSitePlaylist(route.videosJson) }
        PlaylistGrid(
            title = route.name,
            onBack = onBack,
            videos = videos,
            header = null,
            onNavigateToVideo = onNavigateToVideo,
        )
        return
    }
    val viewModel: SitePlaylistViewModel =
        lovehan1me.app.sharedViewModel(::SitePlaylistViewModel)
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    LaunchedEffect(route.listId, route.sort) { viewModel.load(route.listId, route.sort) }
    when (val state = detail) {
        null -> PlaylistLoading(title = route.name, onBack = onBack)
        is WebsiteState.Error -> PlaylistError(
            title = route.name,
            onBack = onBack,
            onRetry = { viewModel.load(route.listId, route.sort) },
        )
        is WebsiteState.Success -> {
            val info = state.info
            PlaylistGrid(
                title = info.name,
                onBack = onBack,
                videos = info.videos.map { it.toSitePlaylistVideo() },
                header = {
                    PlaylistHeader(
                        detail = info,
                        onOpenAuthor = { authorId, authorName ->
                            onOpenArtistRoute(
                                ArtistRoute(userId = authorId, name = authorName)
                            )
                        },
                        onSelectSort = { sort ->
                            viewModel.load(route.listId, sort)
                        },
                    )
                },
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        else -> PlaylistLoading(title = route.name, onBack = onBack)
    }
}

@Composable
private fun PlaylistLoading(title: String, onBack: () -> Unit) {
    Scaffold(topBar = { HanimeTopAppBar(title = title, onBack = onBack) }) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

@Composable
private fun PlaylistError(title: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Scaffold(topBar = { HanimeTopAppBar(title = title, onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(Res.string.unknown_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}

@Composable
private fun PlaylistHeader(
    detail: SitePlaylistDetail,
    onOpenAuthor: (authorId: String, authorName: String) -> Unit,
    onSelectSort: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (detail.authorId != null && detail.authorName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                detail.authorAvatarUrl?.let {
                    HanimeAsyncImage(
                        model = it,
                        contentDescription = detail.authorName,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                TextButton(onClick = { onOpenAuthor(detail.authorId, detail.authorName) }) {
                    Text(detail.authorName)
                }
            }
        }
        detail.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detail.availableSorts.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.availableSorts.forEach { sort ->
                    AssistChip(
                        onClick = { onSelectSort(sort.value) },
                        label = { Text(sort.label) },
                        enabled = sort.value != detail.activeSort,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistGrid(
    title: String,
    onBack: () -> Unit,
    videos: List<SitePlaylistVideo>,
    header: (@Composable () -> Unit)?,
    onNavigateToVideo: (String) -> Unit,
) {
    val videoColumns = rememberVideoGridColumns()
    Scaffold(
        topBar = { HanimeTopAppBar(title = title, onBack = onBack) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(videoColumns),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HanimeDefaults.Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
        ) {
            if (header != null) {
                item(span = { GridItemSpan(videoColumns) }) { header() }
            }
            items(items = videos, key = { it.videoCode }) { video ->
                VideoCardItem(
                    videoItem = SitePlaylistCard(video),
                    isPlaying = video.isPlaying,
                    onClickVideosItem = { onNavigateToVideo(video.videoCode) },
                    onLongClickVideosItem = { _, _ -> },
                )
            }
        }
    }
}

/** 路由 DTO → 卡片模型的最小适配（`HanimeInfo` 带接口成员，不可直序列化）。 */
private class SitePlaylistCard(private val video: SitePlaylistVideo) : VideoItemType {
    override val title: String get() = video.title
    override val coverUrl: String get() = video.coverUrl
    override val videoCode: String get() = video.videoCode
    override val duration: String? get() = video.duration
    override val views: String? get() = video.views
    override val reviews: String? get() = null
    override val currentArtist: String? get() = null
    override val uploadTime: String? get() = null
}
