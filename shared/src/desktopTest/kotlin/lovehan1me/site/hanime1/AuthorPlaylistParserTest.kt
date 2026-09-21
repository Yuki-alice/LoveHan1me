package lovehan1me.site.hanime1

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState

/**
 * G2-1b-2：作者/系列解析回归（夹具 `.workbuddy/g2b2_*.html`，gitignore 不入库）。
 *
 * 夹具缺失时跳过（换机器 clone 下来本来就没有），与 `JavchuHomePageParseTest` 同一约定。
 * 夹具用 `SitePageCaptureLiveTest` 重抓。
 */
class AuthorPlaylistParserTest {

    private fun fixture(name: String): String? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, ".workbuddy/$name")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        println("[skip] 未找到 .workbuddy/$name —— 跳过作者/系列解析回归")
        return null
    }

    @Test
    fun `作者首页头部与近期作品`() {
        val html = fixture("g2b2_user.html") ?: return
        val page = Parser.authorPage("367299", html) as? WebsiteState.Success
            ?: error("作者页解析失败")
        val header = page.info.header
        assertEquals("ピンクパイナップル", header.name)
        assertTrue(header.avatarUrl.contains("avatar/367299"), "头像应含作者 id：${header.avatarUrl}")
        // 活计数器只断言量级（精确值随时间漂移，见 152394→152395）。
        assertTrue((header.subscriberCount ?: 0) > 100000, "订阅数=${header.subscriberCount}")
        assertTrue((header.videoCount ?: 0) > 400, "视频数=${header.videoCount}")
        assertTrue(page.info.recentVideos.size >= 10, "近期作品数=${page.info.recentVideos.size}")
        assertTrue(page.info.hasUploadedTab)
        assertTrue(page.info.hasPlaylistsTab)
    }

    @Test
    fun `作者首页含清单preview区`() {
        val html = fixture("g2b2_user_367290.html") ?: return
        val page = Parser.authorPage("367290", html) as? WebsiteState.Success
            ?: error("作者页解析失败")
        assertEquals("ばにぃうぉ～か～", page.info.header.name)
        assertTrue(page.info.recentVideos.size >= 10, "近期作品数=${page.info.recentVideos.size}")
        val previews = page.info.homePlaylists
        assertEquals(12, previews.size)
        assertTrue(previews.map { it.listId }.containsAll(listOf("921339", "963446")))
        assertTrue(previews.all { it.name.isNotBlank() && it.coverUrl.isNotBlank() })
    }

    @Test
    fun `作者全部作品与分页`() {
        val html = fixture("g2b2_user_uploaded.html") ?: return
        val state = Parser.authorVideosPage(html) as? PageLoadingState.Success
            ?: error("作者作品页解析失败")
        assertTrue(state.info.size >= 30, "单页作品数=${state.info.size}")
        assertTrue(state.info.all { it.videoCode.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun `作者系列清单一览`() {
        val html = fixture("g2b2_user_playlists.html") ?: return
        val state = Parser.authorPlaylistsPage(html) as? PageLoadingState.Success
            ?: error("作者清单一览解析失败")
        val summaries = state.info.summaries
        assertTrue(summaries.size >= 5, "清单数=${summaries.size}")
        val first = summaries.first()
        assertTrue(first.listId.all { it.isDigit() }, "listId=${first.listId}")
        assertTrue(first.name.isNotBlank())
        assertTrue(first.coverUrl.isNotBlank())
    }

    @Test
    fun `独立系列清单页`() {
        val html = fixture("g2b2_playlist.html") ?: return
        val detail = Parser.sitePlaylistPage("976998", html) as? WebsiteState.Success
            ?: error("系列清单页解析失败")
        val info = detail.info
        assertTrue(info.name.isNotBlank())
        assertEquals("367299", info.authorId)
        assertEquals("ピンクパイナップル", info.authorName)
        assertEquals("latest", info.activeSort)
        assertTrue(info.availableSorts.map { it.value }.containsAll(listOf("latest", "popular", "oldest")))
        assertEquals(2, info.videos.size)
        assertTrue(info.videos.all { it.videoCode.isNotBlank() })
    }
}
