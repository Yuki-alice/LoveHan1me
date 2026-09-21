package lovehan1me.site.hanime1

import com.fleeksoft.ksoup.Ksoup
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState

/**
 * G2-1b-2：作者页与系列清单页解析（实测 DOM 见 `.workbuddy/g2b2_*.html`）。
 *
 * 视频卡片与搜索页同构（`video-item-container`/`horizontal-card`），直接复用
 * [Parser.hanimeNormalItemVer2]；系列清单条目是同一张卡套了 `playlist-item-row` 壳。
 */
data class SiteAuthorHeader(
    val userId: String,
    val name: String,
    val avatarUrl: String,
    /** 原样展示用（如 "152,394 位订阅者 • 479 个视频"）。 */
    val statsText: String,
    val subscriberCount: Int?,
    val videoCount: Int?,
)

data class SiteAuthorPage(
    val header: SiteAuthorHeader,
    /** 首页"影片"区近期作品（完整列表走 `authorVideosPage`）。 */
    val recentVideos: List<HanimeInfo>,
    /** 首页"播放清单"区 preview（完整一览走 `authorPlaylistsPage`；无此区作者为空）。 */
    val homePlaylists: List<SitePlaylistSummary>,
    val hasUploadedTab: Boolean,
    val hasPlaylistsTab: Boolean,
)

data class SiteAuthorPlaylists(
    val summaries: List<SitePlaylistSummary>,
    val activeSort: String,
    val availableSorts: List<SitePlaylistSort>,
)

data class SitePlaylistSummary(
    val listId: String,
    val name: String,
    val coverUrl: String,
    /** 原样展示用（如 "3 部影片"）。 */
    val videoCountText: String,
    val updatedText: String?,
)

data class SitePlaylistSort(
    val value: String,
    val label: String,
)

data class SitePlaylistDetail(
    val listId: String,
    val name: String,
    val authorId: String?,
    val authorName: String?,
    val authorAvatarUrl: String?,
    val videoCount: Int?,
    val description: String?,
    val activeSort: String,
    val availableSorts: List<SitePlaylistSort>,
    val videos: List<HanimeInfo>,
)

/** "152,394 位订阅者 • 479 个视频" → (152394, 479)，对不上返回 null（只展示 statsText）。 */
internal fun parseAuthorStats(stats: String): Pair<Int?, Int?> {
    val nums = Regex("[\\d,]+").findAll(stats).map {
        it.value.replace(",", "").toIntOrNull()
    }.toList()
    return Pair(nums.getOrNull(0), nums.getOrNull(1))
}

fun Parser.authorPage(userId: String, body: String): WebsiteState<SiteAuthorPage> {
    val doc = Ksoup.parse(body).body()
    val name = doc.selectFirst("h1.profile-display-name")?.text()?.trim()
        ?: return WebsiteState.Error(IllegalStateException("找不到作者名"))
    val avatar = doc.selectFirst(".profile-avatar-wrapper img")?.attr("src").orEmpty()
    val statsText = doc.selectFirst(".profile-sub-stats-new-line")?.text()?.trim().orEmpty()
    val (subs, videos) = parseAuthorStats(statsText)
    val recent = doc.select("div.video-item-container").mapNotNull { card ->
        runCatching { hanimeNormalItemVer2(card) }.getOrNull()
    }
    return WebsiteState.Success(
        SiteAuthorPage(
            header = SiteAuthorHeader(userId, name, avatar, statsText, subs, videos),
            recentVideos = recent,
            homePlaylists = parsePlaylistSummaries(doc),
            hasUploadedTab = doc.selectFirst("a[href$=/user/$userId/uploaded]") != null,
            hasPlaylistsTab = doc.selectFirst("a[href$=/user/$userId/playlists]") != null,
        )
    )
}

/** 清单卡列表（作者首页 preview 与一览页共用同一张卡；视频卡无 playlist href 自动跳过）。 */
internal fun parsePlaylistSummaries(doc: com.fleeksoft.ksoup.nodes.Element): List<SitePlaylistSummary> =
    doc.select("div.video-item-container").mapNotNull { card ->
        runCatching {
            val link = card.selectFirst("a.video-link")?.attr("href") ?: return@mapNotNull null
            val listId = Regex("playlist\\?list=(\\d+)").find(link)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val title = card.selectFirst("div.title")?.text()?.trim() ?: return@mapNotNull null
            val cover = card.selectFirst("img.main-thumb")?.attr("src") ?: return@mapNotNull null
            val count = card.select("div.stat-item").firstOrNull()?.text()?.trim().orEmpty()
            val updated = card.selectFirst("div.subtitle-time")?.text()?.trim()
                ?.replace("\u00a0", " ")?.trim()?.removePrefix("• ")?.trim()
            SitePlaylistSummary(listId, title, cover, count, updated?.takeIf { it.isNotBlank() })
        }.getOrNull()
    }

/** 作者全部作品（`/user/{id}/uploaded?page=N`，与搜索页同构卡片）。 */
fun Parser.authorVideosPage(body: String): PageLoadingState<MutableList<HanimeInfo>> {
    val doc = Ksoup.parse(body).body()
    val cards = doc.select("div.video-item-container")
    if (cards.isEmpty()) return PageLoadingState.NoMoreData
    val list = cards.mapNotNull { card -> runCatching { hanimeNormalItemVer2(card) }.getOrNull() }
    return PageLoadingState.Success(ArrayList(list))
}

/** 作者的系列清单一览（`/user/{id}/playlists`，卡片 `a.video-link` 直指 `/playlist?list=`）。 */
fun Parser.authorPlaylistsPage(body: String): PageLoadingState<SiteAuthorPlaylists> {
    val doc = Ksoup.parse(body).body()
    val cards = doc.select("div.video-item-container")
    if (cards.isEmpty()) return PageLoadingState.NoMoreData
    val list = parsePlaylistSummaries(doc)
    val (sorts, active) = parseSortPills(doc)
    return PageLoadingState.Success(SiteAuthorPlaylists(list, active, sorts))
}

internal fun parseSortPills(doc: com.fleeksoft.ksoup.nodes.Element): Pair<List<SitePlaylistSort>, String> {
    val sorts = doc.select("a.filter-pill").mapNotNull { pill ->
        val href = pill.attr("href")
        val value = Regex("sort=([a-z]+)").find(href)?.groupValues?.get(1) ?: return@mapNotNull null
        SitePlaylistSort(value, pill.text().trim())
    }.distinctBy { it.value }
    val active = doc.select("a.filter-pill.active").firstOrNull()?.let { pill ->
        Regex("sort=([a-z]+)").find(pill.attr("href"))?.groupValues?.get(1)
    } ?: sorts.firstOrNull()?.value.orEmpty()
    return Pair(sorts, active)
}

/** 独立系列清单页（`/playlist?list=&sort=`）。条目内层卡片与搜索页同构。 */
fun Parser.sitePlaylistPage(listId: String, body: String): WebsiteState<SitePlaylistDetail> {
    val doc = Ksoup.parse(body).body()
    val name = doc.selectFirst("h1.playlist-title")?.text()?.trim()
        ?: return WebsiteState.Error(IllegalStateException("找不到清单标题"))
    val authorAnchor = doc.selectFirst(".playlist-author-info a[href*=/user/]")
    val authorHref = authorAnchor?.attr("href").orEmpty()
    val authorId = Regex("/user/(\\d+)").find(authorHref)?.groupValues?.get(1)
    val count = doc.selectFirst("#sidebar-video-count")?.text()?.trim()?.toIntOrNull()
    val description = doc.selectFirst("p.playlist-description")?.text()?.trim()?.takeIf { it.isNotBlank() }
    val (sorts, activeSort) = parseSortPills(doc)
    val videos = doc.select(".playlist-video-card").mapNotNull { card ->
        runCatching { hanimeNormalItemVer2(card) }.getOrNull()
    }
    return WebsiteState.Success(
        SitePlaylistDetail(
            listId = listId,
            name = name,
            authorId = authorId,
            authorName = authorAnchor?.text()?.trim(),
            authorAvatarUrl = doc.selectFirst("img.author-avatar")?.attr("src"),
            videoCount = count,
            description = description,
            activeSort = activeSort,
            availableSorts = sorts,
            videos = videos,
        )
    )
}
