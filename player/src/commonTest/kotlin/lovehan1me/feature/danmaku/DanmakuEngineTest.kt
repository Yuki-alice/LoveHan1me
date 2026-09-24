package lovehan1me.feature.danmaku

import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DanmakuEngine] 的行为锁定。
 *
 * 这里钉的是三条支点各自的失败方向：
 *  - **闭式解算**：位置必须是入场时刻的线性函数，不能有"每帧重算速度"的残留；
 *  - **游标 + 二分**：从靠后位置起播 / seek 之后，不许回头逐条跳过几十万条历史；
 *  - **满载静默丢弃**：屏幕装不下就是装不下，丢弃必须可数、且不污染后续布局。
 *
 * 尺寸与时间全是整数，浮点比较处留了 1px 容差 —— 车道判断用的是 Float 除法，
 * 逐位相等在这里没有意义。
 */
class DanmakuEngineTest {

    private val viewport = DanmakuViewport(widthPx = 1_000f, heightPx = 300f, lineHeightPx = 30f)
    private val config = DanmakuEngineConfig()

    /** 恒定像素速度：视口宽 / 走完视口宽所需时间。 */
    private val speedPxPerMs: Float get() = viewport.widthPx / viewport.scrollTraverseMs

    private fun item(
        timeMs: Long,
        location: DanmakuLocation = DanmakuLocation.SCROLL,
        text: String = "x".repeat(20),
    ) = DanmakuItem(
        id = timeMs,
        playTimeMillis = timeMs,
        text = text,
        color = 0xFFFFFFFF.toInt(),
        location = location,
    )

    /** 每个字符 10px，故默认文本 20 字符 = 200px。 */
    private val measure: (DanmakuItem) -> Float = { it.text.length * 10f }

    private fun engine(): DanmakuEngine = DanmakuEngine(config)

    /**
     * 按真实的数据节拍（20Hz）把时间推进到 [toMs]。
     *
     * 一次跨很久的 tick 会把中途到期的弹幕全判成"迟到"——那是丢家用例要测的行为，
     * 其余用例必须按节拍推进，否则断言的其实是丢弃逻辑而不是布局逻辑。
     */
    private fun DanmakuEngine.tickThrough(
        fromMs: Long,
        toMs: Long,
        viewport: DanmakuViewport,
        stepMs: Long = 50L,
    ) {
        var at = fromMs
        while (at <= toMs) {
            tick(at, viewport, measure)
            at += stepMs
        }
    }

    // ---------- 发射 ----------

    @Test
    fun `只发射到期的弹幕`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L), item(1_000L), item(2_000L)))

        danmaku.tick(nowMs = 0L, viewport = viewport, measureWidth = measure)
        assertEquals(listOf(0L), danmaku.activeScrollSlots.map { it.startMs })
        assertEquals(1, danmaku.cursorIndex)

        danmaku.tick(nowMs = 1_000L, viewport = viewport, measureWidth = measure)
        danmaku.tick(nowMs = 2_000L, viewport = viewport, measureWidth = measure)
        assertEquals(listOf(0L, 1_000L, 2_000L), danmaku.activeScrollSlots.map { it.startMs })
        assertEquals(3, danmaku.cursorIndex)
        assertEquals(0, danmaku.droppedCount)
    }

    @Test
    fun `从靠后位置起播时游标二分落点而不是回扫历史`() {
        val danmaku = engine()
        val items = (0L until 1_000L).map { item(it * 1_000L) }  // 0..999s，每秒一条

        danmaku.setItems(items, startPositionMs = 500_000L)
        assertEquals(500, danmaku.cursorIndex)

        danmaku.tick(nowMs = 500_000L, viewport = viewport, measureWidth = measure)
        // 关键是 0：线性跳过前 500 条会把它们全记成"迟到丢弃"
        assertEquals(0, danmaku.droppedCount)
        assertEquals(1, danmaku.emittedCount)
    }

    @Test
    fun `错过入场窗口的弹幕静默丢弃`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L), item(1_000L)))

        // 卡顿到 5s 才回到画面：两条都早就该出现了
        danmaku.tick(nowMs = 5_000L, viewport = viewport, measureWidth = measure)
        assertEquals(0, danmaku.emittedCount)
        assertEquals(2, danmaku.droppedCount)
        assertFalse(danmaku.hasActiveSlots())
    }

    // ---------- 车道 ----------

    @Test
    fun `车道数由可用高度与行高决定`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L)))
        danmaku.tick(0L, viewport, measure)
        assertEquals(10, danmaku.laneCountIndex)

        val half = engine()
        half.setItems(listOf(item(0L)))
        half.tick(0L, viewport.copy(displayAreaRatio = 0.5f), measure)
        assertEquals(5, half.laneCountIndex)
    }

    @Test
    fun `同车道前后两条永不追尾`() {
        val danmaku = engine()
        // 300ms 一条，连发 30 条：足够让车道轮转好几圈
        val items = (0L until 30L).map { item(it * 300L) }
        danmaku.setItems(items)
        danmaku.tickThrough(0L, items.last().playTimeMillis, viewport)

        val laneWidthMs = ((200f + viewport.minLaneGapPx) / speedPxPerMs).toLong()  // 一条 200px 的占用时长
        assertTrue(danmaku.activeScrollSlots.isNotEmpty())
        danmaku.activeScrollSlots.groupBy { it.lane }.forEach { (_, slots) ->
            val ordered = slots.sortedBy { it.startMs }
            for (index in 1 until ordered.size) {
                val gap = ordered[index].startMs - ordered[index - 1].startMs
                assertTrue(
                    gap >= laneWidthMs - 1,
                    "车道 ${ordered[index].lane} 上两条只隔 ${gap}ms，小于一条的占用 ${laneWidthMs}ms",
                )
            }
        }
    }

    @Test
    fun `同样的输入总是得到同样的布局`() {
        val items = (0L until 40L).map { item(it * 150L) }  // 10 车道每 224ms 才放行一条，必然有丢弃

        val first = engine().apply {
            setItems(items)
            tickThrough(0L, items.last().playTimeMillis, viewport)
        }
        val second = engine().apply {
            setItems(items)
            tickThrough(0L, items.last().playTimeMillis, viewport)
        }

        assertEquals(
            first.activeScrollSlots.map { it.item.id to it.lane },
            second.activeScrollSlots.map { it.item.id to it.lane },
        )
        assertEquals(first.emittedCount, second.emittedCount)
        assertEquals(first.droppedCount, second.droppedCount)
        assertTrue(first.droppedCount > 0, "这份输入没触发丢弃，用例白写")
    }

    @Test
    fun `屏幕装不下时丢弃而不是排队`() {
        val danmaku = engine()
        // 同一时刻 11 条，只有 10 条车道
        danmaku.setItems((0L until 11L).map { item(0L) })
        danmaku.tick(nowMs = 0L, viewport = viewport, measureWidth = measure)

        assertEquals(10, danmaku.emittedCount)
        assertEquals(1, danmaku.droppedCount)
        assertEquals(10, danmaku.activeScrollSlots.size)
        assertEquals((0 until 10).toList(), danmaku.activeScrollSlots.map { it.lane })
    }

    @Test
    fun `顶底堆叠满了就丢并在驻留时间后释放`() {
        // 90px 高 / 30px 行高 = 3 行，4 条同时在场必然装不下
        val rows = viewport.copy(heightPx = 90f)
        val danmaku = engine()
        danmaku.setItems(
            (0L until 3L).map { item(it * 10L, DanmakuLocation.TOP) } +
                item(30L, DanmakuLocation.TOP) +
                item(6_000L, DanmakuLocation.TOP),
        )

        danmaku.tick(nowMs = 30L, viewport = rows, measureWidth = measure)
        assertEquals(listOf(0, 1, 2), danmaku.activeFixedSlots.map { it.lane })
        assertEquals(1, danmaku.droppedCount)  // 第 4 条：三行都还占着，丢

        // 驻留 5s 到期后，第 5 条复用最早释放的那一行
        danmaku.tick(nowMs = 6_000L, viewport = rows, measureWidth = measure)
        val last = danmaku.activeFixedSlots.single()
        assertEquals(6_000L, last.startMs)
        assertEquals(0, last.lane)
    }

    @Test
    fun `顶部与底部互不占用对方的行`() {
        val danmaku = engine()
        danmaku.setItems(
            listOf(
                item(0L, DanmakuLocation.TOP),
                item(0L, DanmakuLocation.BOTTOM),
            ),
        )
        danmaku.tick(nowMs = 0L, viewport = viewport, measureWidth = measure)

        val top = danmaku.activeFixedSlots.first { it.item.location == DanmakuLocation.TOP }
        val bottom = danmaku.activeFixedSlots.first { it.item.location == DanmakuLocation.BOTTOM }
        assertEquals(0, top.lane)
        assertEquals(0, bottom.lane)
        assertEquals(0f, danmaku.topEdgeOf(top, viewport))
        assertEquals(270f, danmaku.bottomEdgeOf(bottom, viewport))
    }

    @Test
    fun `车道间隙来自视口并决定一条道多久后被复用`() {
        // 10 条同时入场占满 10 条道，第 11 条 3s 后才到
        val items = List(10) { item(0L) } + item(3_000L)

        // 默认间隙 24px：一条 200px 的占到 (200+24)/0.1 = 2240ms，3s 时 0 号道已释放
        val defaultGap = engine().apply {
            setItems(items)
            tick(0L, viewport, measure)
            tick(3_000L, viewport, measure)
        }
        assertEquals(11, defaultGap.emittedCount)
        assertEquals(0, defaultGap.droppedCount)

        // 间隙拉到 1000px（约一屏宽）：占用变成 12000ms，第 11 条无处可去 —— 静默丢弃。
        // ⚠️ 宽间隙视口要从第一次 tick 就用上：中途换间隙会连车道一起清零（那是另一条用例），
        // 那样测的就不是"间隙挤不挤"而是"重置后能不能入场"了。
        val wide = viewport.copy(minLaneGapPx = 1_000f)
        val wideGap = engine().apply {
            setItems(items)
            tick(0L, wide, measure)
            tick(3_000L, wide, measure)
        }
        assertEquals(10, wideGap.emittedCount)
        assertEquals(1, wideGap.droppedCount)
    }

    // ---------- 解算 ----------

    @Test
    fun `滚动位置是入场时刻的线性函数`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L)))
        danmaku.tick(nowMs = 0L, viewport = viewport, measureWidth = measure)
        val slot = danmaku.activeScrollSlots.single()

        assertEquals(viewport.widthPx, danmaku.leftEdgeOf(slot, 0L, viewport), 0.01f)
        assertEquals(0f, danmaku.leftEdgeOf(slot, viewport.scrollTraverseMs, viewport), 0.01f)
        // 走完"视口宽 + 自身宽"后正好从左沿消失
        assertEquals(-200f, danmaku.leftEdgeOf(slot, slot.exitAtMs, viewport), 1f)
    }

    @Test
    fun `离场后槽位被回收`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L)))
        danmaku.tick(nowMs = 0L, viewport = viewport, measureWidth = measure)
        assertTrue(danmaku.hasActiveSlots())

        danmaku.tick(nowMs = 13_000L, viewport = viewport, measureWidth = measure)
        assertFalse(danmaku.hasActiveSlots())
    }

    // ---------- 扰动 ----------

    @Test
    fun `seek后清空屏上并从新位置重建游标`() {
        val danmaku = engine()
        val items = (0L until 100L).map { item(it * 1_000L) }
        danmaku.setItems(items)
        danmaku.tick(nowMs = 5_050L, viewport = viewport, measureWidth = measure)
        assertEquals(listOf(5_000L), danmaku.activeScrollSlots.map { it.startMs })

        danmaku.seekTo(50_000L)
        assertFalse(danmaku.hasActiveSlots())
        assertEquals(50, danmaku.cursorIndex)

        val droppedBefore = danmaku.droppedCount
        danmaku.tick(nowMs = 50_000L, viewport = viewport, measureWidth = measure)
        assertEquals(listOf(50_000L), danmaku.activeScrollSlots.map { it.startMs })
        // 清空后重建，而不是把 0..49s 的历史逐条判成迟到
        assertEquals(droppedBefore, danmaku.droppedCount)
    }

    @Test
    fun `视口变化只重置屏上弹幕不重放历史`() {
        val danmaku = engine()
        val items = (0L until 20L).map { item(it * 1_000L) }
        danmaku.setItems(items)
        danmaku.tick(nowMs = 5_050L, viewport = viewport, measureWidth = measure)
        val cursorBefore = danmaku.cursorIndex
        assertTrue(danmaku.hasActiveSlots())

        danmaku.tick(nowMs = 6_050L, viewport = viewport.copy(heightPx = 200f), measureWidth = measure)
        assertEquals(cursorBefore + 1, danmaku.cursorIndex)  // 只前进了这一条，没有回头
        assertEquals(6, danmaku.laneCountIndex)
        // 尺寸一变，属于旧几何的弹幕全部作废，只剩这一 tick 新入场的那条
        assertEquals(listOf(6_000L), danmaku.activeScrollSlots.map { it.startMs })
    }

    @Test
    fun `只改速度也要让旧布局作废而非让弹幕按新速度乱跳`() {
        val danmaku = engine()
        val items = (0L until 20L).map { item(it * 1_000L) }
        danmaku.setItems(items)
        danmaku.tick(nowMs = 5_050L, viewport = viewport, measureWidth = measure)
        val cursorBefore = danmaku.cursorIndex
        assertTrue(danmaku.hasActiveSlots())

        // 宽高与行高分毫未动，只有速度变了：旧槽位的 exitAtMs 是按旧速度算的，
        // 留着它们就会让一条弹幕凭空提前（或延后）消失。
        danmaku.tick(
            nowMs = 6_050L,
            viewport = viewport.copy(scrollTraverseMs = 5_000L),
            measureWidth = measure,
        )
        assertEquals(cursorBefore + 1, danmaku.cursorIndex)
        assertEquals(listOf(6_000L), danmaku.activeScrollSlots.map { it.startMs })
    }

    @Test
    fun `尺寸未就绪时不动作`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L)))

        danmaku.tick(nowMs = 0L, viewport = viewport.copy(widthPx = 0f, heightPx = 0f), measureWidth = measure)
        assertEquals(0, danmaku.emittedCount)
        assertEquals(0, danmaku.droppedCount)
        assertEquals(0, danmaku.cursorIndex)

        // 量出尺寸后正常入场，且不算"迟到"
        danmaku.tick(nowMs = 0L, viewport = viewport, measureWidth = measure)
        assertEquals(1, danmaku.emittedCount)
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= tolerance,
            "期望 $expected，实际 $actual（容差 $tolerance）",
        )
    }
}
