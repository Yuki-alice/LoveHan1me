package lovehan1me.feature.library

import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.state.PagingGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MyList 分页并发守卫：**判重**、**淘汰陈旧响应（不并入、不回退页数）**、**刷新打断在途**、
 * **finish 只释放有效凭证**。
 *
 * ## 测的是生产路径，不是测试内的复刻
 * 用例直接调用两个 **commonMain 生产函数**——`nextPageOrNull`（判重的页号决策）与
 * `applyPageResponse`（一页响应的应用；[MyListSubViewModel] 的收集分支直接调用它）——以及
 * 生产用的 [PagingGate]。`MyListSubViewModel` 本身依赖 `NetworkRepo` 单例，headless 下起不来，
 * 所以把决策逻辑抽成纯逻辑后在此钉死（同 `PagingGateTest` / `SearchViewModel.mergeSearchPage`）。
 *
 * ## 不用"列表无重复"当验收
 * `applyPageResponse` 内部有 `distinctBy(videoCode)`，它会**掩盖**重复条目。因此本文件钉的是
 * **顺序**（追加落尾部 / 刷新替换）与 **`loadedPageCount` 不回退**，而不是"有没有重复"。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.library.MyListPagingTest" --offline`
 */
class MyListPagingTest {

    private fun item(code: String) = HanimeInfo(
        title = code,
        coverUrl = "",
        videoCode = code,
        itemType = HanimeInfo.NORMAL,
    )

    private fun page(index: Int, size: Int = 3): List<HanimeInfo> =
        List(size) { i -> item("p$index-$i") }

    // ───────────────────────── ① 判重 ─────────────────────────

    @Test
    fun `在途时再次触底被拒`() {
        val gate = PagingGate()

        // 第一次触底（无在途）：允许，页号由 1 推进到 2。
        val first = nextPageOrNull(inFlight = gate.inFlight, loadedPageCount = 1)
        assertNotNull(first, "无在途加载时 nextPageOrNull 应给出下一页")
        assertEquals(2, first, "页号推进错误")

        val token = gate.tryBegin()
        assertNotNull(token, "无在途加载时 tryBegin 应领到凭证")
        assertTrue(gate.inFlight, "领到凭证后闸门应被占用")

        // 第一个请求还没回来，此时再次触底：必须被拒。
        val second = nextPageOrNull(inFlight = gate.inFlight, loadedPageCount = first)
        assertNull(
            second,
            "在途时 nextPageOrNull 仍给出页号：判重失效，会重复请求同一页并追加重复页",
        )
        assertNull(gate.tryBegin(), "在途时 tryBegin 仍放行：判重失效，会同时存在两个在途请求")
        assertEquals(2, first, "判重失败不该推进页号")
    }

    // ─────────── ② 陈旧响应：不并入列表、不回退 loadedPageCount ───────────

    @Test
    fun `过期凭证的响应既不并入列表也不回退 loadedPageCount`() {
        val gate = PagingGate()
        var items = emptyList<HanimeInfo>()
        var loadedPageCount = 0

        // 第 1 页正常加载（刷新语义 → 替换）。
        val t1 = gate.tryBegin()!!
        val first = applyPageResponse(
            gate = gate, token = t1, page = 1, incoming = page(1),
            prevItems = items, loadedPageCount = loadedPageCount, isRefresh = true,
        )
        assertNotNull(first, "首个请求被误判为过期")
        items = first.items
        loadedPageCount = first.loadedPageCount
        gate.finish(t1)
        assertEquals(3, items.size, "第 1 页未正常并入")
        assertEquals(1, loadedPageCount)

        // 第 2 页请求在途……随后被作废（清屏 / 刷新打断）。
        val t2 = gate.tryBegin()!!
        gate.cancel()

        // 第 2 页的响应**晚到**：必须被丢弃。
        val stale = applyPageResponse(
            gate = gate, token = t2, page = 2, incoming = page(2),
            prevItems = items, loadedPageCount = loadedPageCount, isRefresh = false,
        )
        assertNull(stale, "过期凭证的响应通过了闸门：会并入旧页 / 回退 loadedPageCount")
        // 调用方在 stale == null 时不改任何状态（复刻 ViewModel 收集分支）。
        assertEquals(3, items.size, "过期响应改动了列表")
        assertEquals(1, loadedPageCount, "过期响应回退了 loadedPageCount")
        assertTrue(items.none { it.videoCode.startsWith("p2-") }, "过期第 2 页的条目混入了列表")
    }

    @Test
    fun `有效响应的页数只增不减_且刷新为替换而非追加`() {
        val gate = PagingGate()

        // 刷新（isRefresh=true）：基准为空 → 替换；页数 = max(0,1) = 1。
        val t1 = gate.tryBegin()!!
        val p1 = applyPageResponse(
            gate = gate, token = t1, page = 1, incoming = page(1),
            prevItems = listOf(item("stale-x")), loadedPageCount = 0, isRefresh = true,
        )!!
        gate.finish(t1)
        assertEquals(listOf("p1-0", "p1-1", "p1-2"), p1.items.map { it.videoCode }, "刷新应替换而非追加")
        assertEquals(1, p1.loadedPageCount)

        // 翻页（isRefresh=false）：追加在第 1 页之后 → 末条是 p2-2；页数推进到 2。
        val t2 = gate.tryBegin()!!
        val p2 = applyPageResponse(
            gate = gate, token = t2, page = 2, incoming = page(2),
            prevItems = p1.items, loadedPageCount = p1.loadedPageCount, isRefresh = false,
        )!!
        gate.finish(t2)
        assertEquals(6, p2.items.size)
        assertEquals(listOf("p2-0", "p2-1", "p2-2"), p2.items.takeLast(3).map { it.videoCode }, "第 2 页应按序追加在尾部")
        assertEquals(2, p2.loadedPageCount)

        // 迟到的第 1 页响应（仍有效、page < 当前）：**不得**把页数往回拨。
        val tLate = gate.tryBegin()!!
        val late = applyPageResponse(
            gate = gate, token = tLate, page = 1, incoming = page(1),
            prevItems = p2.items, loadedPageCount = p2.loadedPageCount, isRefresh = false,
        )!!
        gate.finish(tLate)
        assertEquals(2, late.loadedPageCount, "陈旧页把 loadedPageCount 往回拨了")
    }

    // ─────────── ③ refresh 打断在途 + 新请求可正常开始 ───────────

    @Test
    fun `refresh 能打断在途请求且新请求可正常开始`() {
        val gate = PagingGate()

        // 翻页请求在途。
        val inFlight = gate.tryBegin()!!
        assertTrue(gate.inFlight, "前置：闸门应被占用")

        // 下拉刷新：restart 必须**打断**在途并立刻给出新凭证。
        val fresh = gate.restart()
        assertFalse(gate.isCurrent(inFlight), "restart 之后旧凭证仍有效：在途请求不会被丢弃")
        assertTrue(gate.isCurrent(fresh), "restart 未给出有效新凭证")
        assertTrue(gate.inFlight, "restart 之后闸门应被新一轮占用")

        // 新一轮正常结束，之后可再次开始。
        gate.finish(fresh)
        assertFalse(gate.inFlight, "新一轮结束后闸门未释放")
        assertNotNull(gate.tryBegin(), "新一轮结束后无法开始下一页：会永久卡死")
    }

    // ─────────── ④ finish 只释放仍有效的凭证 ───────────

    @Test
    fun `finish 只释放仍有效的凭证_旧请求晚到不误放闸门`() {
        val gate = PagingGate()

        val stale = gate.tryBegin()!! // 第 1 轮
        gate.cancel()                 // 作废在途（清屏）
        val fresh = gate.tryBegin()!! // 第 2 轮接管
        assertTrue(gate.isCurrent(fresh))

        // 第 1 轮的加载此刻才结束：**不能**误释放第 2 轮的闸门。
        gate.finish(stale)
        assertTrue(
            gate.inFlight,
            "旧凭证的 finish 误释放了新轮闸门：会同时存在两个在途请求",
        )
        assertTrue(gate.isCurrent(fresh), "旧凭证的 finish 不应影响新轮凭证的有效性")

        // 第 2 轮自己结束时才真正释放。
        gate.finish(fresh)
        assertFalse(gate.inFlight, "新轮自身 finish 后闸门仍未释放")
        assertNotNull(gate.tryBegin())
    }
}
