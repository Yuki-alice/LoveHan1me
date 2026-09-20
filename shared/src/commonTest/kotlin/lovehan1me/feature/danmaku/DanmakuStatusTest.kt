package lovehan1me.feature.danmaku

import kotlin.test.Test
import kotlin.test.assertEquals
import lovehan1me.data.danmaku.DanmakuEpisodeRef

/**
 * 状态优先级（[resolveDanmakuStatus]）的回归：分支顺序即诚实度，
 * 有关联说关联、没关联有评论说评论、都没有才说没关联/不可用。
 */
class DanmakuStatusTest {

    private val episode = DanmakuEpisodeRef(episodeId = "e1", episodeTitle = "第 1 话", subjectTitle = "某番")

    private fun status(
        enabled: Boolean = true,
        episode: DanmakuEpisodeRef? = null,
        dandanCount: Int = 0,
        commentCount: Int = 0,
        dandanFailed: Boolean = false,
        loadAttempted: Boolean = false,
        jobActive: Boolean = false,
    ) = resolveDanmakuStatus(enabled, episode, dandanCount, commentCount, dandanFailed, loadAttempted, jobActive)

    @Test
    fun `总开关关了说什么都是Disabled`() {
        assertEquals(DanmakuStatus.Disabled, status(enabled = false, episode = episode, dandanCount = 3))
    }

    @Test
    fun `有关联且弹弹有数报Linked`() {
        assertEquals(DanmakuStatus.Linked(episode, 5, 2), status(episode = episode, dandanCount = 5, commentCount = 2))
    }

    @Test
    fun `有关联但弹弹为0评论顶着仍是Linked不断链`() {
        assertEquals(DanmakuStatus.Linked(episode, 0, 4), status(episode = episode, commentCount = 4))
    }

    @Test
    fun `有关联但双空是NoDanmaku不是Unmatched`() {
        assertEquals(DanmakuStatus.NoDanmaku, status(episode = episode))
    }

    @Test
    fun `没关联有评论是CommentOnly`() {
        assertEquals(DanmakuStatus.CommentOnly(3), status(commentCount = 3))
    }

    @Test
    fun `都没且失败过是Unavailable`() {
        assertEquals(DanmakuStatus.Unavailable, status(dandanFailed = true))
    }

    @Test
    fun `都没且没失败但已尝试过是Unmatched`() {
        assertEquals(DanmakuStatus.Unmatched, status(loadAttempted = true))
    }

    @Test
    fun `加载中是Loading`() {
        assertEquals(DanmakuStatus.Loading, status())
        assertEquals(DanmakuStatus.Loading, status(loadAttempted = true, jobActive = true))
    }
}
