package lovehan1me.core.domain.state

import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.feature.search.mergeSearchPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 分页闸门守卫：**判重**、**淘汰陈旧响应**、**作废在途请求**。
 *
 * ## 它防的是什么
 * 修之前分页序号由 UI 回调持有（`{ viewModel.page++; executeSearch() }`），ViewModel 侧
 * 既没有在途判重、也没有请求标识：快速滑动时该回调可被连续触发，`page` 被连加、多个请求
 * 同时在途，先发的响应**后到也会照常并入列表** —— 表现为跳页 / 重复条目 / 顺序错乱。
 *
 * 抽成 [PagingGate] 就是为了让这三件事可以被确定性断言：它不依赖网络、不依赖协程调度，
 * 而 `SearchViewModel` 依赖 `NetworkRepo` 单例，端到端测不可行也不稳定。
 *
 * ## 为什么第 2 组用例要走真实的 mergeSearchPage
 * 只断言 `isCurrent(token) == false` 太弱 —— 它只证明"闸门会说谎与否"，不证明"过期响应
 * 真的没进列表"。所以 [committedAfter] 完整复刻 `SearchViewModel.launchSearch` 的提交策略
 * （先过闸门、再过 `mergeSearchPage`），让"不入列表"这条端到端语义被真的钉住。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.core.domain.state.PagingGateTest" --offline`
 */
class PagingGateTest {

    private fun item(code: String) = HanimeInfo(
        title = code,
        coverUrl = "",
        videoCode = code,
        itemType = HanimeInfo.NORMAL,
    )

    private fun page(index: Int, size: Int = 3): List<HanimeInfo> =
        List(size) { i -> item("p$index-$i") }

    /**
     * 复刻 `SearchViewModel.launchSearch` 的提交策略：**先过闸门，过期直接丢**，
     * 有效才走 `mergeSearchPage` 并入。
     */
    private fun committedAfter(
        committed: List<HanimeInfo>,
        gate: PagingGate,
        token: PagingGate.Token,
        incoming: List<HanimeInfo>,
    ): List<HanimeInfo> {
        if (!gate.isCurrent(token)) return committed
        return mergeSearchPage(committed, incoming)
    }

    // ───────────────────────── ① 判重 ─────────────────────────

    @Test
    fun `在途加载未结束时再次 tryBegin 被拒`() {
        val gate = PagingGate()

        val first = gate.tryBegin()
        assertNotNull(first, "首次 tryBegin 应当领到凭证")

        // 快速滑动/连点：同一个加载还没回来又触发了一次。
        val second = gate.tryBegin()
        assertNull(second, "已有在途加载却仍能开始新加载：判重失效，两个请求会赛跑")
        assertTrue(gate.inFlight, "有在途加载时 inFlight 应为 true")
    }

    @Test
    fun `上一次加载结束后才能再次开始`() {
        val gate = PagingGate()

        val first = gate.tryBegin()!!
        gate.finish(first)
        assertFalse(gate.inFlight, "finish 之后闸门应释放")

        val second = gate.tryBegin()
        assertNotNull(second, "闸门已释放却仍被拒：翻页会永久卡死")
    }

    // ─────────────────── ② 淘汰陈旧响应（端到端语义） ───────────────────

    @Test
    fun `刷新作废旧请求_旧响应晚到不并入列表`() {
        val gate = PagingGate()
        var committed = emptyList<HanimeInfo>()

        // 第 1 页正常请求，正常并入。
        val staleToken = gate.tryBegin()!!
        committed = committedAfter(committed, gate, staleToken, page(1))
        assertEquals(3, committed.size, "首个请求应当正常并入")

        // 用户下拉刷新：作废在途、重新从第 1 页开始。
        val freshToken = gate.restart()

        // 旧请求的响应**晚到**（刷新之后才回来）：必须被丢弃。
        val afterStale = committedAfter(committed, gate, staleToken, page(2))
        assertEquals(
            committed,
            afterStale,
            "过期响应被并进了列表：这就是跳页 / 顺序错乱的来源",
        )

        // 新请求的响应正常生效：第 3 页 3 条**追加**在第 1 页之后（mergeSearchPage 不去重跨页，
        // 只去重同页重复项），故总数为 6、末条是 p3-2，且第 3 页整段都在。
        val afterFresh = committedAfter(committed, gate, freshToken, page(3))
        assertEquals(6, afterFresh.size, "新一轮的响应没生效：闸门把正常请求也挡掉了")
        assertEquals("p3-2", afterFresh.last().videoCode, "第 3 页未被追加到列表尾部")
        assertEquals(
            listOf("p3-0", "p3-1", "p3-2"),
            afterFresh.takeLast(3).map { it.videoCode },
            "第 3 页整段应当按序落在列表尾部",
        )
        // 被丢弃的过期第 2 页（p2-*）一条都不能混进来。
        assertTrue(
            afterFresh.none { it.videoCode.startsWith("p2-") },
            "过期第 2 页的条目混进了最终列表",
        )
    }

    @Test
    fun `旧 token 在 restart 之后被判过期`() {
        val gate = PagingGate()
        val stale = gate.tryBegin()!!
        val fresh = gate.restart()

        assertFalse(gate.isCurrent(stale), "restart 之后旧凭证仍然有效：陈旧响应会被放行")
        assertTrue(gate.isCurrent(fresh), "restart 给出的新凭证无效")
    }

    // ─────────────────── ③ 作废在途 + 新请求可开始 ───────────────────

    @Test
    fun `cancel 之后旧 token 失效且新请求可开始`() {
        val gate = PagingGate()
        val stale = gate.tryBegin()!!

        gate.cancel()

        assertFalse(gate.isCurrent(stale), "cancel 之后旧凭证仍然有效")
        assertFalse(gate.inFlight, "cancel 应同时释放闸门")

        val fresh = gate.tryBegin()
        assertNotNull(fresh, "cancel 之后无法开始新请求：刷新会被判重挡死")
        assertTrue(gate.isCurrent(fresh))
    }

    @Test
    fun `reset 与 cancel 同义`() {
        val gate = PagingGate()
        val stale = gate.tryBegin()!!

        gate.reset()

        assertFalse(gate.isCurrent(stale))
        assertFalse(gate.inFlight)
        assertNotNull(gate.tryBegin(), "reset 之后应当能重新开始")
    }

    @Test
    fun `restart 作废在途的同时立刻给出新凭证`() {
        val gate = PagingGate()
        val stale = gate.tryBegin()!!

        val fresh = gate.restart()

        // 刷新路径必须能**打断**在途请求：不能被 tryBegin 的判重挡死，
        // 也不能出现"作废了却没领到凭证"的中间态。
        assertNotNull(fresh, "restart 没给出新凭证")
        assertFalse(gate.isCurrent(stale), "restart 之后旧凭证仍然有效")
        assertTrue(gate.isCurrent(fresh))
        assertTrue(gate.inFlight, "restart 之后闸门应当是占用的（新一轮已经在跑）")
    }

    // ─────────────── 释放闸门的边界：不能误释放新一轮 ───────────────

    @Test
    fun `过期凭证的 finish 不会误释放新一轮的闸门`() {
        val gate = PagingGate()
        val stale = gate.tryBegin()!!

        // 刷新打断：新一轮接管闸门。
        val fresh = gate.restart()
        assertTrue(gate.isCurrent(fresh))

        // 旧加载此时才结束（finally/finish）：**不能**把新一轮的闸门解锁掉，
        // 否则新一轮会与随后的一次翻页同时在途。
        gate.finish(stale)

        assertTrue(gate.inFlight, "旧加载的 finish 误释放了新一轮的闸门")
        assertNull(gate.tryBegin(), "闸门被误释放：此时本不该允许开始第二次加载")

        // 新一轮自己结束时才真正释放。
        gate.finish(fresh)
        assertFalse(gate.inFlight)
        assertNotNull(gate.tryBegin())
    }
}
