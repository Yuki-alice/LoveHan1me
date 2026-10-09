package lovehan1me.feature.home.homepage

import lovehan1me.Res
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HomePage
import lovehan1me.latest_hanime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun video(
    code: String,
    title: String = "t$code",
    artist: String = "社A",
    views: String = "10万次",
    time: String = "3天前",
) = HanimeInfo(
    title = title,
    coverUrl = "https://cdn/$code.jpg",
    videoCode = code,
    duration = "10:00",
    currentArtist = artist,
    views = views,
    uploadTime = time,
    itemType = HanimeInfo.NORMAL,
)

private fun emptyPage(banner: HomePage.Banner? = null) = HomePage(
    csrfToken = null, avatarUrl = null, username = null, banner = banner,
    latestHanime = mutableListOf(), latestRelease = mutableListOf(),
    ecchiAnime = mutableListOf(), shortEpisodeAnime = mutableListOf(),
    twoPointFiveDAnime = mutableListOf(), threeDCG = mutableListOf(),
    motionAnime = mutableListOf(), twoDAnime = mutableListOf(),
    aiGenerated = mutableListOf(), mmd = mutableListOf(),
    cosplay = mutableListOf(), watchingNow = mutableListOf(),
    newAnimeTrailer = mutableListOf(), userId = "",
)

private fun cat(key: String, vararg codes: String) = HomeCategory(
    key = key, titleRes = Res.string.latest_hanime,
    videos = codes.map(::video),
)

/** 单测用副标题：原文拼接，不读语言设置（生产默认走 DisplayTextLocalizer）。 */
private val rawSubtitle: (String?, String?, String?) -> String? = { a, v, t ->
    listOfNotNull(
        a?.takeIf { it.isNotBlank() },
        v?.takeIf { it.isNotBlank() },
        t?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
}

/**
 * Hero 1+6 合成门禁（移植 misaka 语义，纯函数）。
 *
 * 钉住：运营位首位 / 在看优先 / 去重运营位 / 上限6+1 / 空字段不拼出"·"。
 */
class HomeHeroItemsTest {

    @Test
    fun `运营位首位_视频补足_上限6加1`() {
        val banner = HomePage.Banner("官", "述", "https://cdn/banner.jpg", "b0")
        val watching = cat(HOME_CATEGORY_WATCHING_NOW, "w1", "w2")
        val latest = cat(HOME_CATEGORY_LATEST_UPLOAD, "l1", "l2", "l3", "l4", "l5", "l6")
        val items = buildHomeHeroItems(emptyPage(banner), listOf(latest, watching), rawSubtitle)
        assertEquals("b0", items.first().videoCode)
        assertEquals(1 + HERO_VIDEO_ITEM_COUNT, items.size)
        // 在看优先：b0 之后是 w1 w2，再是 l 系。
        assertEquals(listOf("b0", "w1", "w2", "l1", "l2", "l3", "l4"), items.map { it.videoCode })
    }

    @Test
    fun `与运营位重复的视频被跳过`() {
        val banner = HomePage.Banner("官", null, "https://cdn/b.jpg", "dup")
        val items = buildHomeHeroItems(
            emptyPage(banner),
            listOf(cat(HOME_CATEGORY_WATCHING_NOW, "dup", "w1")),
            rawSubtitle,
        )
        assertTrue(items.none { it !== items.first() && it.videoCode == "dup" })
        assertEquals("dup", items.first().videoCode)
    }

    @Test
    fun `无运营位时纯视频成池_副标题含作者观看时间`() {
        val items = buildHomeHeroItems(
            emptyPage(null),
            listOf(cat(HOME_CATEGORY_LATEST_UPLOAD, "l1")),
            rawSubtitle,
        )
        assertEquals(1, items.size)
        val subtitle = items[0].subtitle
        assertEquals("社A · 10万次 · 3天前", subtitle)
    }

    @Test
    fun `全空字段subtitle为空_不拼分隔符`() {
        val v = HanimeInfo(
            title = "x", coverUrl = "https://cdn/x.jpg", videoCode = "x",
            itemType = HanimeInfo.SIMPLIFIED,
        )
        val items = buildHomeHeroItems(
            emptyPage(null),
            listOf(HomeCategory(HOME_CATEGORY_LATEST_UPLOAD, Res.string.latest_hanime, videos = listOf(v))),
            rawSubtitle,
        )
        assertEquals(null, items[0].subtitle)
    }

    @Test
    fun `空输入返回空_调用方不渲染`() {
        assertEquals(emptyList(), buildHomeHeroItems(emptyPage(null), emptyList()))
    }
}
