package lovehan1me.site.hanime1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.domain.model.VideoComments
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.data.SettingsRepository

/**
 * 评论解析的容错边界 + 举报结果判定。夹具手写，不依赖 `.workbuddy/` 抓取件。
 *
 * 站点把 HTML 塞在 JSON 字符串里返回，所以夹具一律用单引号写属性（省掉 JSON 侧的转义），
 * 并且拼信封前要先去掉换行 —— JSON 字符串里不许有裸换行。
 */
class CommentParserTest {

    init {
        // `logIfParseNull(loginNeeded = true)` 会读 SettingsRepository.isAlreadyLogin；
        // 不装个 store 就是 lateinit 未初始化直接炸，那是跟解析无关的噪声。
        SettingsRepository.install(InMemorySettingsStore())
    }

    private fun commentsEnvelope(html: String): String =
        """{"comments":"${html.replace("\n", "")}"}"""

    /** 站点每条评论固定 4 个子元素，`comments()` 按 4 个一组切块；id 取 wrapper id 尾段。 */
    private fun commentBlock(
        id: String,
        avatarTag: String,
        tail: String = "",
    ) = """
        |<div class='comment-index-text'><a href='#'>用户$id</a><span>$id 個月前</span></div>
        |<div class='comment-index-text'>正文$id</div>
        |<div>$avatarTag</div>
        |<div id='reply-section-wrapper-$id'>$tail</div>
    """.trimMargin()

    @Test
    fun `单条畸形评论只让它自己消失`() {
        val html = """
            <input name='_token' value='tok'>
            <input name='comment-user-id' value='42'>
            <div id='comment-start'>
                ${commentBlock("111", "<img src='https://cdn.example/a.png'>")}
                ${commentBlock("222", "")}
                ${commentBlock("333", "<img src='https://cdn.example/b.png'>")}
            </div>
        """
        val info = asSuccess(Parser.comments(commentsEnvelope(html)))

        // 中间那条没有头像 —— parseCommentElement 在那里抛 ParseException。
        assertEquals(listOf("111", "333"), info.videoComment.map { it.id }, "畸形那条应被跳过")
        assertEquals("tok", info.csrfToken)
        assertEquals("42", info.currentUserId)
    }

    @Test
    fun `未登录时可选字段缺失不该丢整条`() {
        // 未登录拿不到 foreign_id 与点赞输入，这些都该落成 null，而不是让这条评论消失。
        val html = """
            <div id='comment-start'>
                ${commentBlock("111", "<img src='https://cdn.example/a.png'>")}
            </div>
        """
        val comment = asSuccess(Parser.comments(commentsEnvelope(html))).videoComment.single()

        assertEquals("111", comment.id)
        assertEquals(null, comment.thumbUp)
        assertEquals(null, comment.post.foreignId)
        assertTrue(comment.content.contains("正文"))
    }

    @Test
    fun `回覆数与举报字段照常解析`() {
        val html = """
            <div id='comment-start'>
                ${
            commentBlock(
                "111",
                "<img src='https://cdn.example/a.png'>",
                "<div class='load-replies-btn'>查看全部 3 條回覆</div>" +
                        "<span class='report-btn' data-reportable-id='r1' data-reportable-type='Comment'></span>",
            )
        }
            </div>
        """
        val comment = asSuccess(Parser.comments(commentsEnvelope(html))).videoComment.single()

        assertTrue(comment.hasMoreReplies, "有 load-replies-btn 就该给入口")
        assertEquals(3, comment.replyCount)
        assertEquals("r1", comment.reportableId)
        assertEquals("Comment", comment.reportableType)
    }

    @Test
    fun `回覆解析同样逐条容错`() {
        val html = """
            <div id='reply-start'>
                <div class='comment-basic'>
                    <div class='comment-index-text'><a href='#'>甲</a><span>1 天前</span></div>
                    <div class='comment-index-text'>回覆正文</div>
                    <div><img src='https://cdn.example/a.png'></div>
                </div>
                <div class='comment-post'></div>
                <div class='comment-basic'>
                    <div class='comment-index-text'><a href='#'>乙</a><span>2 天前</span></div>
                </div>
                <div class='comment-post'></div>
            </div>
        """
        val info = asSuccess(Parser.commentReply("""{"replies":"${html.replace("\n", "")}"}"""))

        assertEquals(1, info.videoComment.size, "第二条缺头像与正文，该跳过而不是炸整批")
        assertTrue(info.videoComment.single().isChildComment)
    }

    @Test
    fun `举报失败不再报成功`() {
        val failed = Parser.reportCommentResponse("<div id='error'>您已經檢舉過該則評論</div>")
        assertEquals("您已經檢舉過該則評論", assertIs<WebsiteState.Error>(failed).throwable.message)

        // #error 是空容器时不算失败：站点会带一个藏起来的壳。
        assertIs<WebsiteState.Success<*>>(Parser.reportCommentResponse("<div id='error'>   </div>"))
        assertIs<WebsiteState.Success<*>>(Parser.reportCommentResponse("<div class='page'></div>"))
    }

    private fun asSuccess(state: WebsiteState<VideoComments>): VideoComments =
        assertIs<WebsiteState.Success<VideoComments>>(state).info

    // 嵌在类里：同包已有同名的文件级 private 类（`JavchuHomePageParseTest`），顶层会撞。
    private class InMemorySettingsStore : SettingsStore {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }
}
