package lovehan1me.feature.danmaku

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLocation
import lovehan1me.data.danmaku.DanmakuSource

/**
 * 双源合并（[mergeDanmakuItems]）的回归：乱序必须排好、不可见位置必须滤掉、
 * 任一源为空都不断链。引擎游标靠 `(playTimeMillis, id)` 升序二分，乱序输入会漏放。
 */
class DanmakuMergeTest {

    private fun item(
        id: Long,
        ms: Long,
        location: DanmakuLocation = DanmakuLocation.SCROLL,
        source: DanmakuSource = DanmakuSource.REMOTE,
    ) = DanmakuItem(id = id, playTimeMillis = ms, text = "t$id", color = 0xFFFFFF, location = location, source = source)

    private val allVisible = DanmakuLocation.entries.toSet()

    @Test
    fun `乱序双源输入按时间加id升序`() {
        val dandan = listOf(item(3, 3000), item(1, 1000))
        val comment = listOf(item(2, 2000, source = DanmakuSource.COMMENT), item(4, 1000, source = DanmakuSource.COMMENT))
        val merged = mergeDanmakuItems(dandan, comment, allVisible)
        assertEquals(listOf(1L, 4L, 2L, 3L), merged.map { it.id })
    }

    @Test
    fun `不可见位置被滤掉`() {
        val items = listOf(
            item(1, 1000, DanmakuLocation.TOP),
            item(2, 2000, DanmakuLocation.SCROLL),
        )
        val merged = mergeDanmakuItems(items, emptyList(), setOf(DanmakuLocation.SCROLL))
        assertEquals(listOf(2L), merged.map { it.id })
    }

    @Test
    fun `双空为空不断链`() {
        assertTrue(mergeDanmakuItems(emptyList(), emptyList(), allVisible).isEmpty())
    }

    @Test
    fun `评论空时弹弹独撑`() {
        val dandan = listOf(item(7, 5000))
        val merged = mergeDanmakuItems(dandan, emptyList(), allVisible)
        assertEquals(listOf(7L), merged.map { it.id })
    }

    @Test
    fun `弹弹空时评论独撑`() {
        val comment = listOf(item(9, 9000, source = DanmakuSource.COMMENT))
        val merged = mergeDanmakuItems(emptyList(), comment, allVisible)
        assertEquals(listOf(9L), merged.map { it.id })
    }
}
