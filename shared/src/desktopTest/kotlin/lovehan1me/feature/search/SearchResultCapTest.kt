package lovehan1me.feature.search

import lovehan1me.core.domain.model.HanimeInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// B3 守卫：搜索结果封顶。
//
// 背景：`_searchFlow` 原本无界累加（每页 `base + updatedList` 再整表 `distinctBy`），
// 常驻内存随页数线性上涨，且单页成本随累计量涨 —— 翻到第 N 页时累计是平方级。
// `mergeSearchPage` 是 B3 的全部语义（去重 + 封顶），抽成纯函数就是为了能在这里钉住：
// SearchViewModel 依赖 DB / 网络，headless 下起不来。
//
// 取舍（有意为之）：超出上限丢**最早**的一批，用户往上翻看不到最初几页。
// 见 SEARCH_RESULT_LIMIT 的注释与 docs/plan/性能打磨.md B3 节。
//
// 用例成对设计：①是正向对照（不超限必须全留），否则②的"被截断"可能只是用例自己
// 写错了、把"全丢"当成"封顶"。
//
// 跑法：`:shared:desktopTest --tests "lovehan1me.feature.search.SearchResultCapTest" --offline`
class SearchResultCapTest {

    private fun item(code: String, watched: Boolean = false) = HanimeInfo(
        title = code,
        coverUrl = "",
        videoCode = code,
        itemType = HanimeInfo.NORMAL,
        watched = watched,
    )

    /** 第 [index] 页，每页 [size] 条，code 形如 `p<index>-<序号>`。 */
    private fun page(index: Int, size: Int = PAGE_SIZE): List<HanimeInfo> =
        List(size) { i -> item("p$index-$i") }

    @Test
    fun `不超限时去重后全保留`() {
        val prev = page(0)
        val incoming = page(1)

        val merged = mergeSearchPage(prev, incoming, limit = 1_000)

        assertEquals(PAGE_SIZE * 2, merged.size, "未超限却被截断了")
        assertEquals("p0-0", merged.first().videoCode)
        assertEquals("p1-${PAGE_SIZE - 1}", merged.last().videoCode)
    }

    @Test
    fun `超限时丢最早的保最新的`() {
        val limit = 40
        val prev = page(0)
        val incoming = page(1) // 共 48 条 > 40

        val merged = mergeSearchPage(prev, incoming, limit = limit)

        assertEquals(limit, merged.size, "超限时应当恰好剩 $limit 条")
        assertEquals("p1-${PAGE_SIZE - 1}", merged.last().videoCode, "最新一条必须还在")
        assertFalse(
            merged.any { it.videoCode == "p0-0" },
            "最早的一条应当被丢掉，封顶没生效",
        )
    }

    @Test
    fun `重复videoCode保留先出现的那条`() {
        val prev = listOf(item("dup", watched = false), item("unique"))
        val incoming = listOf(item("dup", watched = true))

        val merged = mergeSearchPage(prev, incoming, limit = 1_000)

        assertEquals(2, merged.size, "重复项没被去重")
        assertEquals(
            "unique",
            merged.last().videoCode,
            "新页的非重复项应当追加在后面",
        )
        assertFalse(
            merged.first().watched == true,
            "去重应当保留先出现的那条（distinctBy 语义），不能让新页覆盖",
        )
    }

    @Test
    fun `连翻30页后常驻封顶在上限`() {
        // 30 页 × 24 条 = 720，远超上限；不加封顶的话这里会是 720 并且一直涨。
        var acc = emptyList<HanimeInfo>()
        repeat(30) { index -> acc = mergeSearchPage(acc, page(index)) }

        assertEquals(
            SEARCH_RESULT_LIMIT,
            acc.size,
            "连翻 30 页后常驻量应当封顶在 $SEARCH_RESULT_LIMIT，实际 ${acc.size}",
        )
        assertEquals("p29-${PAGE_SIZE - 1}", acc.last().videoCode, "最新一条必须还在")
        assertTrue(
            acc.none { it.videoCode.startsWith("p0-") },
            "最早那一页应当已被丢弃",
        )
    }

    private companion object {
        const val PAGE_SIZE = 24
    }
}
