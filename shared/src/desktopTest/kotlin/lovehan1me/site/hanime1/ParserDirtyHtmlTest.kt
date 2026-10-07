package lovehan1me.site.hanime1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.data.SettingsRepository

/**
 * 搜索/列表解析的脏 HTML 容错（内联夹具，不依赖 `.workbuddy/` 抓取件）。
 *
 * 钉三条失败路径：缺字段卡片被跳过（`logIfParseNull`）、空页面不抛、
 * 空容器回 `NoMoreData`。站点一改版，"某块空了"必须表现为局部缺失，
 * 而不是整页白屏或抛异常。`hanimeSearch` 是公开入口，夹具直接喂 HTML 字符串。
 */
class ParserDirtyHtmlTest {

    init {
        runCatching { SettingsRepository.install(InMemorySettingsStore()) }
    }

    private fun card(
        title: String?,
        href: String?,
        cover: String?,
    ): String {
        val titleDiv = if (title != null) "<div class='title'>$title</div>" else ""
        val link = if (href != null) "<a href='$href'>link</a>" else ""
        val img = if (cover != null) "<img src='$cover'>" else ""
        return """
            |<div class='horizontal-card-x'>
            |  $titleDiv
            |  $img
            |  $link
            |  <div class='thumb-container-x'><div class='duration-x'>12:34</div></div>
            |  <div class='meta-author'><a>作者A</a></div>
            |  <div class='meta-stats'><span>2024-01-01</span></div>
            |</div>
        """.trimMargin()
    }

    @Test
    fun `缺字段卡片被跳过_好卡片保留`() {
        val html = """
            <div class='content-padding-new'>
                ${card("好标题", "https://hanime1.me/watch?v=111", "https://cdn.example/cover1.jpg")}
                ${card(null, null, "https://cdn.example/cover2.jpg")}
                ${card("标题3无链接", null, "https://cdn.example/cover3.jpg")}
            </div>
        """
        val list = asSuccess(Parser.hanimeSearch(html))

        assertEquals(1, list.size, "缺标题/缺链接的两条应被跳过，不能炸整批")
        assertEquals("好标题", list.single().title)
        assertEquals("111", list.single().videoCode)
        assertTrue(list.single().itemType == HanimeInfo.NORMAL)
    }

    @Test
    fun `空页面与垃圾HTML不抛只给空列表`() {
        val empty = asSuccess(Parser.hanimeSearch("<html><body>nothing here</body></html>"))
        assertTrue(empty.isEmpty(), "无任何列表容器时应为空列表而不是抛异常")

        val garbage = asSuccess(Parser.hanimeSearch("{{{ not html at all"))
        assertTrue(garbage.isEmpty(), "垃圾输入同样不应抛")
    }

    @Test
    fun `有容器无条目回NoMoreData`() {
        val state = Parser.hanimeSearch("<div class='content-padding-new'></div>")
        assertIs<PageLoadingState.NoMoreData>(
            state,
            "容器存在但零条目是翻到底信号，不应伪装成成功空列表",
        )
    }

    private fun asSuccess(state: PageLoadingState<MutableList<HanimeInfo>>): MutableList<HanimeInfo> =
        assertIs<PageLoadingState.Success<MutableList<HanimeInfo>>>(state).info

    // 嵌在类里：同包已有同名的文件级 private 类，顶层会撞（见 CommentParserTest）。
    private class InMemorySettingsStore : SettingsStore {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }
}
