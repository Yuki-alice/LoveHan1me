package lovehan1me.feature.danmaku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import lovehan1me.data.danmaku.DanmakuEpisodeRef
import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLoadResult
import lovehan1me.data.danmaku.DanmakuLocation
import lovehan1me.data.danmaku.DanmakuProvider
import lovehan1me.data.danmaku.DanmakuSubject
import lovehan1me.core.domain.model.VideoComments

/**
 * Session 是「仓库 / 位置时钟 / 引擎」的接缝，所以这里刻意**不碰 Room、不碰 Compose**：
 * 仓库的三个动作注入假函数，作用域用 `Dispatchers.Unconfined` ——
 * 假日不真正挂起，`launch` 就地跑完，断言因此是同步的（也就不用引 coroutines-test）。
 */
class DanmakuSessionTest {

    private class FakeProvider : DanmakuProvider {
        var searchFailure: Throwable? = null

        override val id: String = "fake"

        override suspend fun autoMatch(rawTitle: String): DanmakuEpisodeRef? = null

        override suspend fun searchSubjects(keyword: String): List<DanmakuSubject> {
            searchFailure?.let { throw it }
            return listOf(DanmakuSubject(subjectId = "s-$keyword", title = keyword, episodeCount = 12))
        }

        override suspend fun searchEpisodes(subjectTitle: String): List<DanmakuEpisodeRef> =
            listOf(DanmakuEpisodeRef(episodeId = "e1", episodeTitle = "第 1 话", subjectTitle = subjectTitle))

        override suspend fun fetch(episodeId: String): List<DanmakuItem> = emptyList()
    }

    private class Fixture(
        firstResult: DanmakuLoadResult = DanmakuLoadResult.Ready(
            episode,
            listOf(scrollAt(0L, "开场")),
        ),
    ) {
        var resolveCalls = 0
        var linkCalls = 0
        var unlinkCalls = 0
        var nextResult: DanmakuLoadResult = firstResult
        var resolveFailure: Throwable? = null
        val provider = FakeProvider()

        /** Unconfined：`launch` 的函数体就地执行完，测试无需等待。 */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val session = DanmakuSession(
            videoCode = "vid-1",
            title = "[字幕组] 某番 #01",
            provider = provider,
            scope = scope,
            resolve = { _, _, _ ->
                resolveCalls++
                resolveFailure?.let { throw it }
                nextResult
            },
            linkTo = { _, _, _ -> linkCalls++; nextResult },
            unlinkFrom = { unlinkCalls++ },
        )

        val viewport = DanmakuViewport(widthPx = 1_000f, heightPx = 300f, lineHeightPx = 30f)
        val measure: (DanmakuItem) -> Float = { it.text.length * 10f }

        fun snapshot(
            positionMs: Long = 0L,
            durationMs: Long = 1_500_000L,
            playbackSpeed: Float = 1f,
            frozen: Boolean = false,
            reset: Boolean = false,
        ) = session.onPlaybackSnapshot(positionMs, durationMs, playbackSpeed, frozen, reset)

        /** suspend 动作在 Unconfined 作用域里跑完即返回。 */
        fun act(action: suspend DanmakuSession.() -> Unit) {
            scope.launch { session.action() }
        }
    }

    // ---------- 装载与状态 ----------

    @Test
    fun `首个播放快照触发装载并进入已关联`() {
        val fixture = Fixture()
        fixture.snapshot()
        assertEquals(DanmakuStatus.Linked(episode, 1), fixture.session.status.value)
        assertEquals(1, fixture.resolveCalls)
    }

    @Test
    fun `重复快照不重复请求`() {
        val fixture = Fixture()
        repeat(20) { fixture.snapshot(positionMs = 1_000L + it * 250L) }
        assertEquals(1, fixture.resolveCalls)
    }

    @Test
    fun `拉取失败进入暂不可用并允许下次重试`() {
        val fixture = Fixture(firstResult = DanmakuLoadResult.Failed("boom"))
        fixture.snapshot()
        assertEquals(DanmakuStatus.Unavailable, fixture.session.status.value)

        fixture.nextResult = DanmakuLoadResult.Ready(episode, listOf(scrollAt(0L, "开场")))
        fixture.snapshot()
        assertEquals(2, fixture.resolveCalls)
        assertEquals(DanmakuStatus.Linked(episode, 1), fixture.session.status.value)
    }

    @Test
    fun `仓库抛异常也只落成暂不可用不打断播放`() {
        val fixture = Fixture()
        fixture.resolveFailure = IllegalStateException("接口炸了")
        fixture.snapshot()
        assertEquals(DanmakuStatus.Unavailable, fixture.session.status.value)
    }

    @Test
    fun `未匹配不再自动重试`() {
        val fixture = Fixture(firstResult = DanmakuLoadResult.Unmatched)
        fixture.snapshot()
        fixture.snapshot()
        assertEquals(DanmakuStatus.Unmatched, fixture.session.status.value)
        // 关联没变，重跑只会得到同一个 Unmatched；出路是状态条上那个手动入口
        assertEquals(1, fixture.resolveCalls)
    }

    @Test
    fun `源里确实没弹幕时说暂无弹幕而不是未关联`() {
        val fixture = Fixture(firstResult = DanmakuLoadResult.Empty(episode))
        fixture.snapshot()
        assertEquals(DanmakuStatus.NoDanmaku, fixture.session.status.value)
        assertFalse(fixture.session.engine.hasActiveSlots())
    }

    // ---------- 总开关 ----------

    @Test
    fun `关掉总开关会清空画面并停止推进`() {
        val fixture = Fixture()
        fixture.snapshot()
        fixture.session.advance(1_000_000_000L, fixture.viewport, fixture.measure)
        assertTrue(fixture.session.engine.hasActiveSlots())

        fixture.session.setEnabled(false)
        assertEquals(DanmakuStatus.Disabled, fixture.session.status.value)
        assertFalse(fixture.session.engine.hasActiveSlots())
        assertNull(fixture.session.advance(2_000_000_000L, fixture.viewport, fixture.measure))
    }

    @Test
    fun `重新打开总开关后状态条不会卡在已关闭`() {
        val fixture = Fixture()
        fixture.snapshot()
        fixture.session.setEnabled(false)
        fixture.session.setEnabled(true)
        // `start()` 有 loadAttempted 幂等门：这里若不调 reload()，状态条就永远停在「已关闭」
        assertEquals(DanmakuStatus.Linked(episode, 1), fixture.session.status.value)
        assertEquals(2, fixture.resolveCalls)
    }

    // ---------- 播放态与帧循环 ----------

    @Test
    fun `没有位置锚点时帧循环什么都不画`() {
        val fixture = Fixture()
        assertNull(fixture.session.advance(1_000_000_000L, fixture.viewport, fixture.measure))
    }

    @Test
    fun `phase 离开 Ready 时整组丢掉并允许重装`() {
        val fixture = Fixture()
        fixture.snapshot()
        fixture.session.advance(1_000_000_000L, fixture.viewport, fixture.measure)
        assertTrue(fixture.session.engine.hasActiveSlots())

        fixture.snapshot(reset = true)
        assertFalse(fixture.session.engine.hasActiveSlots())
        // reset 之后旧的参考时刻全废，拿到新采样之前不该画任何东西
        assertNull(fixture.session.advance(2_000_000_000L, fixture.viewport, fixture.measure))

        fixture.snapshot(positionMs = 5_000L)
        assertEquals(2, fixture.resolveCalls)
    }

    @Test
    fun `seek 后画面按新位置重来而不是接着旧轨迹`() {
        val fixture = Fixture(
            firstResult = DanmakuLoadResult.Ready(
                episode,
                listOf(scrollAt(0L, "开场"), scrollAt(60_000L, "六十秒那条")),
            ),
        )
        fixture.snapshot(positionMs = 0L)
        assertEquals(0L, fixture.session.advance(1_000_000_000L, fixture.viewport, fixture.measure))
        assertEquals(listOf("开场"), fixture.session.engine.activeScrollSlots.map { it.item.text })

        // 位置跳变超过 seek 阈值：tracker 换代，session 让引擎 seekTo 而不是继续外推
        fixture.snapshot(positionMs = 60_000L)
        fixture.session.advance(2_000_000_000L, fixture.viewport, fixture.measure)
        assertEquals(
            listOf("六十秒那条"),
            fixture.session.engine.activeScrollSlots.map { it.item.text },
        )
    }

    @Test
    fun `暂停期间时钟停摆但画面不清空`() {
        val fixture = Fixture()
        fixture.snapshot(positionMs = 0L)
        fixture.session.advance(1_000_000_000L, fixture.viewport, fixture.measure)
        fixture.snapshot(positionMs = 0L, frozen = true)
        assertEquals(
            listOf("开场"),
            fixture.session.engine.activeScrollSlots.map { it.item.text },
        )
        // 冻结期间的帧推进不产生位移
        val before = fixture.session.advance(9_000_000_000L, fixture.viewport, fixture.measure)
        val after = fixture.session.advance(19_000_000_000L, fixture.viewport, fixture.measure)
        assertEquals(before, after)
    }

    // ---------- 人工选集 ----------

    @Test
    fun `手动关联的集立刻反映到状态与画面`() {
        val fixture = Fixture()
        val selected = DanmakuEpisodeRef(episodeId = "43", episodeTitle = "第 2 话")
        fixture.nextResult = DanmakuLoadResult.Ready(selected, listOf(scrollAt(0L, "第2话")))
        fixture.act { link(selected) }
        // 用户手点的这条路走 link，不再走自动匹配
        assertEquals(1, fixture.linkCalls)
        assertEquals(0, fixture.resolveCalls)
        assertEquals(DanmakuStatus.Linked(selected, 1), fixture.session.status.value)
    }

    @Test
    fun `取消关联会清空画面并要求重装`() {
        val fixture = Fixture()
        fixture.snapshot()
        fixture.act { unlink() }
        assertEquals(1, fixture.unlinkCalls)
        assertFalse(fixture.session.engine.hasActiveSlots())
        assertEquals(2, fixture.resolveCalls)
    }

    @Test
    fun `检索候选原样透传源的结果`() {
        val fixture = Fixture()
        var subjects: List<DanmakuSubject> = emptyList()
        fixture.act { subjects = searchSubjects("某番") }
        assertEquals("s-某番", subjects.single().subjectId)
    }

    @Test
    fun `检索失败不会被吞成没搜到`() {
        val fixture = Fixture()
        fixture.provider.searchFailure = IllegalStateException("没网")
        var caught: Throwable? = null
        var subjects: List<DanmakuSubject>? = null
        fixture.scope.launch {
            try {
                subjects = fixture.session.searchSubjects("某番")
            } catch (error: Throwable) {
                caught = error
            }
        }
        assertNull(subjects)
        assertTrue(caught is IllegalStateException)
    }

    @Test
    fun `dispose 只停掉在途请求不碰外部作用域`() {
        val fixture = Fixture(firstResult = DanmakuLoadResult.Unmatched)
        fixture.session.dispose()
        assertFalse(fixture.scope.coroutineContext[kotlinx.coroutines.Job]!!.isCancelled)
    }

    // ---------- 评论主源 ----------

    @Test
    fun `弹弹落空时评论顶上而不是未关联`() {
        val fixture = Fixture(firstResult = DanmakuLoadResult.Unmatched)
        fixture.session.setComments(
            listOf(comment("a", "好评", 10), comment("b", "顶", 3)),
        )
        fixture.snapshot(durationMs = 600_000L)
        assertEquals(DanmakuStatus.CommentOnly(2), fixture.session.status.value)
        assertEquals(1, fixture.resolveCalls)
    }

    @Test
    fun `弹弹与评论合并计数`() {
        val fixture = Fixture()
        fixture.session.setComments(
            listOf(comment("a", "好评", 10), comment("b", "顶", 3)),
        )
        fixture.snapshot(durationMs = 600_000L)
        assertEquals(DanmakuStatus.Linked(episode, 1, 2), fixture.session.status.value)
    }

    @Test
    fun `评论充足跳过弹弹自动匹配`() {
        val fixture = Fixture()
        fixture.session.setComments(
            (1..45).map { comment("u$it", "评论$it", likes = it) },
        )
        fixture.snapshot(durationMs = 600_000L)
        assertEquals(0, fixture.resolveCalls)
        assertEquals(DanmakuStatus.CommentOnly(45), fixture.session.status.value)
    }

    @Test
    fun `关掉评论投影回到纯弹弹`() {
        val fixture = Fixture()
        fixture.session.setComments(listOf(comment("a", "好评", 10)))
        fixture.snapshot(durationMs = 600_000L)
        assertEquals(DanmakuStatus.Linked(episode, 1, 1), fixture.session.status.value)

        fixture.session.setCommentEnabled(false)
        // 关掉主源等于换数据源组合：弹弹那路重走（仓库侧 TTL，基本命中缓存）
        assertEquals(DanmakuStatus.Linked(episode, 1, 0), fixture.session.status.value)
        assertEquals(2, fixture.resolveCalls)
    }

    @Test
    fun `片长未知时评论先存着等快照`() {
        val fixture = Fixture(firstResult = DanmakuLoadResult.Unmatched)
        fixture.session.setComments(listOf(comment("a", "好评", 10)))
        // durationMs = 0：映射推迟，状态仍是弹弹那路的结论
        fixture.snapshot(durationMs = 0L)
        assertEquals(DanmakuStatus.Unmatched, fixture.session.status.value)

        fixture.snapshot(durationMs = 600_000L)
        assertEquals(DanmakuStatus.CommentOnly(1), fixture.session.status.value)
    }

    @Test
    fun `按类型开关过滤上屏`() {
        val fixture = Fixture(
            firstResult = DanmakuLoadResult.Ready(
                episode,
                listOf(
                    DanmakuItem(
                        id = 1L,
                        playTimeMillis = 0L,
                        text = "滚动",
                        color = 0xFFFFFFFF.toInt(),
                        location = DanmakuLocation.SCROLL,
                    ),
                    DanmakuItem(
                        id = 2L,
                        playTimeMillis = 0L,
                        text = "顶部",
                        color = 0xFFFFFFFF.toInt(),
                        location = DanmakuLocation.TOP,
                    ),
                ),
            ),
        )
        fixture.snapshot()
        fixture.session.setLocationVisibility(scroll = true, top = false, bottom = false)
        fixture.session.advance(1_000_000_000L, fixture.viewport, fixture.measure)
        assertEquals(listOf("滚动"), fixture.session.engine.activeScrollSlots.map { it.item.text })
        assertTrue(fixture.session.engine.activeFixedSlots.isEmpty())

        fixture.session.setLocationVisibility(scroll = true, top = true, bottom = false)
        // 重铺后游标回到当前位置：重新快照锚定，避免"迟到"把刚放行的顶弹丢掉
        fixture.snapshot(positionMs = 0L)
        fixture.session.advance(1_050_000_000L, fixture.viewport, fixture.measure)
        assertEquals(listOf("滚动"), fixture.session.engine.activeScrollSlots.map { it.item.text })
        assertEquals(listOf("顶部"), fixture.session.engine.activeFixedSlots.map { it.item.text })
    }
}

/** 评论主源测试的构造器：点赞可空（未登录），子评论默认否。 */
private fun comment(
    user: String,
    text: String,
    likes: Int? = null,
) = VideoComments.VideoComment(
    avatar = "",
    username = user,
    date = "2026-01-01",
    content = text,
    thumbUp = likes,
    isChildComment = false,
    post = VideoComments.VideoComment.POST(),
)

/** 文件级而不是类成员：嵌套的 `Fixture` 摸不到测试类实例的成员。 */
private val episode = DanmakuEpisodeRef(episodeId = "42", episodeTitle = "第 1 话")

private fun scrollAt(timeMs: Long, text: String) = DanmakuItem(
    id = timeMs + 1L,
    playTimeMillis = timeMs,
    text = text,
    color = 0xFFFFFFFF.toInt(),
    location = DanmakuLocation.SCROLL,
)
