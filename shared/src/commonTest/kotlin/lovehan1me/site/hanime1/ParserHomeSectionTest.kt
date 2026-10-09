package lovehan1me.site.hanime1

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * P0-1：首页栏目语料门禁。
 *
 * 用最小合成 HTML（非线上抓包，避免大 blob 进仓）钉两条：
 * ① 索引布局未变时零漂移（与旧 getOrNull 逐字一致）；
 * ② 中间插一行时语义锚把栏目找回（改版不串台）。
 */
class ParserHomeSectionTest {

    private fun rowsHtml(titles: List<String>): String = buildString {
        append("<div id=\"home-rows-wrapper\">")
        titles.forEachIndexed { i, t ->
            append("<div id=\"row$i\"><h3>$t</h3><div class=\"horizontal-card\"></div></div>")
        }
        append("</div>")
    }

    private fun parseRows(titles: List<String>) =
        Ksoup.parse(rowsHtml(titles)).body().select("div[id=home-rows-wrapper] > div")

    @Test
    fun `索引布局未变_零漂移`() {
        // 与 Parser.homePageVer2 旧索引一致的 14 行骨架（4/9 为占位）。
        val titles = listOf(
            "最新上市", "最新上傳", "里番", "泡面番", "占位4",
            "Motion Anime", "3DCG", "2.5D", "2D", "占位9",
            "AI生成", "MMD", "Cosplay", "他们在看",
        )
        val sections = Parser.resolveHomeSections(parseRows(titles))
        assertEquals("row0", sections["latestRelease"]?.attr("id"))
        assertEquals("row1", sections["latestUpload"]?.attr("id"))
        assertEquals("row2", sections["ecchi"]?.attr("id"))
        assertEquals("row5", sections["motion"]?.attr("id"))
        assertEquals("row13", sections["watchingNow"]?.attr("id"))
    }

    @Test
    fun `中间插一行_语义锚找回栏目`() {
        val titles = listOf(
            "最新上市", "最新上傳", "里番", "泡面番", "占位4",
            "新增运营位",
            "Motion Anime", "3DCG", "2.5D", "2D", "占位9",
            "AI生成", "MMD", "Cosplay", "他们在看",
        )
        val sections = Parser.resolveHomeSections(parseRows(titles))
        // Motion 被挤到 row6，语义必须跟过去，而不是死守索引 5 拿走运营位。
        assertEquals("row6", sections["motion"]?.attr("id"))
        assertNotNull(sections["latestRelease"])
    }

    @Test
    fun `栏目缺失_返回null由调用方按空列表处理`() {
        val sections = Parser.resolveHomeSections(parseRows(listOf("最新上市")))
        assertNotNull(sections["latestRelease"])
        // 只有一行时其余栏目无语义命中、无索引位 → null，不断言抛错。
        assertNull(sections["watchingNow"])
    }
}
