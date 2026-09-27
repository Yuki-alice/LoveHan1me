package lovehan1me.feature.danmaku

import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLocation
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DanmakuEngine] 的行为锁定。
 *
 * 钉的是四条支点各自的失败方向：
 *  - **闭式解算**：位置必须是入场时刻与自身速度的线性函数，不能有"每帧重算"的残留；
 *  - **游标 + 二分**：从靠后位置起播 / seek 之后，不许回头逐条跳过几十万条历史；
 *  - **回看铺屏**：起播与 seek 时把回看窗口内"该在屏上"的弹幕按已飞距离放上来，
 *    窗口外的连文本宽度都不量；
 *  - **追尾按到达时间判**：同车道两条在任何时刻都不许重叠，哪怕后一条更快。
 *
 * 绝大多数用例把**两条时间轴喂成同一个数**（等价于常速播放）：那样"视频里已经过了多久"
 * 就是"屏上已经飞了多久"，几何能手算核对。两轴分道扬镳（倍速）另有专门用例。
 *
 * 几何全部取整数，速度基准刻意配成"20 个字符 = 基准宽度"，于是默认文本的
 * 速度倍率恰好是 1，`1 px/ms` 量级的算式能一眼看出。浮点比较处留 1px 容差 ——
 * 车道判断用的是 Float 除法，逐位相等在这里没有意义。
 */
class DanmakuEngineTest {

    /** 基准速度 100px/s、基准文本 200px ⇒ 默认文本正好 0.1px/ms。 */
    private val viewport = DanmakuViewport(
        widthPx = 1_000f,
        heightPx = 300f,
        lineHeightPx = 30f,
        baseSpeedPxPerSecond = 100f,
        minLaneGapPx = DEFAULT_GAP_PX,
        baseSpeedTextWidthPx = 200f,
    )

    /** 抖动清零：布局类用例要可复现，抖动本身另有专门用例。 */
    private val config = DanmakuEngineConfig(speedFluctuation = 0f)

    private fun item(
        timeMs: Long,
        location: DanmakuLocation = DanmakuLocation.SCROLL,
        chars: Int = 20,
    ) = DanmakuItem(
        id = timeMs * 100L + chars,
        playTimeMillis = timeMs,
        text = "x".repeat(chars),
        color = 0xFFFFFFFF.toInt(),
        location = location,
    )

    /** 每个字符 10px，故默认文本 20 字符 = 200px = 基准宽度。 */
    private val measure: (DanmakuItem) -> Float = { it.text.length * 10f }

    /** 位置一律按测试视口解算：只读 `widthPx` 与槽位自身的速度，换个视口就换个基准。 */
    private fun DanmakuEngine.leftOf(slot: DanmakuSlot, danmakuNowMs: Long): Float =
        leftEdgeOf(slot, danmakuNowMs, viewport)

    private fun engine(random: Random = Random.Default): DanmakuEngine =
        DanmakuEngine(config, random)

    /** 常速节拍：两条轴喂同一个数，等价于 `playbackSpeed = 1`。 */
    private fun DanmakuEngine.tickAt(atMs: Long) {
        tick(atMs, atMs, viewport, measure)
    }

    /**
     * 按真实的数据节拍（20Hz）把时间推进到 [toMs]。
     *
     * 一次跨很久的 tick 只会在末尾补算，中途到期的弹幕连布局机会都没有 —— 那是
     * 回看铺屏用例要测的行为，其余用例必须按节拍推进，否则测到的是补算而不是布局。
     */
    private fun DanmakuEngine.tickThrough(
        fromMs: Long,
        toMs: Long,
        viewport: DanmakuViewport,
        stepMs: Long = 50L,
    ) {
        var at = fromMs
        while (at <= toMs) {
            tick(at, at, viewport, measure)
            at += stepMs
        }
    }

    // ---------- 发射与回看 ----------

    @Test
    fun `只发射到期的弹幕`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L), item(1_000L), item(2_000L)))

        danmaku.tickAt(0L)
        assertEquals(listOf(0L), danmaku.activeScrollSlots.map { it.placedAtMs })
        assertEquals(1, danmaku.cursorIndex)

        danmaku.tickAt(1_000L)
        danmaku.tickAt(2_000L)
        assertEquals(listOf(0L, 1_000L, 2_000L), danmaku.activeScrollSlots.map { it.placedAtMs })
        assertEquals(3, danmaku.cursorIndex)
        assertEquals(0, danmaku.droppedCount)
    }

    @Test
    fun `从靠后位置起播时游标二分落点而不是回扫历史`() {
        val danmaku = engine()
        // 0..999s，每秒一条
        val items = (0L until 1_000L).map { item(it * 1_000L) }

        danmaku.setItems(items, videoNowMs = 500_000L)
        // 游标落在"回看窗口起点"上，而不是 0
        assertEquals(480, danmaku.cursorIndex)

        danmaku.tickAt(500_000L)
        // 480s..489s 那 10 条早已飞出左沿：计入丢弃，但一条也没排版
        assertEquals(10, danmaku.droppedCount)
        assertEquals(11, danmaku.emittedCount)
        assertEquals(501, danmaku.cursorIndex)
    }

    @Test
    fun `回看窗口之外的弹幕根本不进扫描范围`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L), item(1_000L)), videoNowMs = 60_000L)

        danmaku.tickAt(60_000L)
        assertEquals(0, danmaku.emittedCount)
        assertEquals(0, danmaku.droppedCount)
        // seek 点之前 20s 起算，30s 前的两条压根没被看过
        assertEquals(2, danmaku.cursorIndex)
    }

    @Test
    fun `卡顿之后老弹幕按已飞距离出现在屏上而不是重排一遍`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L), item(1_000L)))

        // 卡顿到 5s 才回到画面：两条都还在飞，位置该是"入场后 5s / 4s"的地方
        danmaku.tickAt(5_000L)
        assertEquals(2, danmaku.emittedCount)
        val byTime = danmaku.activeScrollSlots.associate { it.placedAtMs to danmaku.leftOf(it, 5_000L) }
        assertEquals(500f, byTime.getValue(0L), 1f)
        assertEquals(600f, byTime.getValue(1_000L), 1f)
    }

    @Test
    fun `飘出左沿的老弹幕连宽度都不量`() {
        val measured = mutableListOf<String>()
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L), item(1_000L)), videoNowMs = 0L)

        // 30s：两条都在回看窗口内被扫到，但最短可能也早就飞完了 —— 不必排版
        danmaku.tick(30_000L, 30_000L, viewport) { item ->
            measured += item.text
            measure(item)
        }
        assertTrue(measured.isEmpty(), "已离场 $measured 还被排版了：回看扫描会拖垮首帧")
        assertEquals(2, danmaku.droppedCount)
        assertEquals(0, danmaku.emittedCount)
    }

    // ---------- 速度与加权 ----------

    @Test
    fun `车道数由可用高度与行高决定`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L)))
        danmaku.tickAt(0L)
        assertEquals(10, danmaku.laneCountIndex)

        val half = engine()
        half.setItems(listOf(item(0L)))
        half.tick(0L, 0L, viewport.copy(displayAreaRatio = 0.5f), measure)
        assertEquals(5, half.laneCountIndex)
    }

    /** 倍率 = 1.14^log2(字宽/基准字宽)，所以 400px 那条正好快 14%。 */
    @Test
    fun `长弹幕飞得更快而短弹幕不减速`() {
        val danmaku = engine()
        danmaku.setItems(
            listOf(
                item(0L, chars = 40),   // 400px → 1.14×
                item(0L, chars = 20),   // 200px → 1×
                item(0L, chars = 10),   // 100px → 短于基准宽，仍是 1×
            ).sortedBy { it.id },
        )
        danmaku.tickAt(0L)
        danmaku.tickAt(1_000L)

        val travelled = danmaku.activeScrollSlots.associate {
            it.item.text.length to viewport.widthPx - danmaku.leftOf(it, 1_000L)
        }
        assertEquals(114f, travelled.getValue(40), 1f)
        assertEquals(100f, travelled.getValue(20), 1f)
        assertEquals(100f, travelled.getValue(10), 1f)
    }

    @Test
    fun `逐条速度抖动不超过配置区间`() {
        val jitter = DanmakuEngine(DanmakuEngineConfig(), Random(7))
        jitter.setItems((0L until 20L).map { item(it * 500L) })
        jitter.tickThrough(0L, 9_500L, viewport)

        assertTrue(jitter.emittedCount > 0, "一条也没发射，用例白写")
        jitter.activeScrollSlots.forEach { slot ->
            // 默认文本正好基准宽 ⇒ 加权倍率 1，抖动就是它离 1 的距离
            assertTrue(
                abs(slot.speedMultiplier - 1f) <= DanmakuEngineConfig().speedFluctuation + 1e-4f,
                "倍率 ${slot.speedMultiplier} 跳出 ±${DanmakuEngineConfig().speedFluctuation}",
            )
        }
    }

    @Test
    fun `同样的输入总是得到同样的布局`() {
        val items = (0L until 40L).map { item(it * 150L) }

        fun layout() = engine(Random(42)).apply {
            setItems(items)
            tickThrough(0L, items.last().playTimeMillis, viewport)
        }
        val first = layout()
        val second = layout()

        assertEquals(
            first.activeScrollSlots.map { it.item.id to it.lane },
            second.activeScrollSlots.map { it.item.id to it.lane },
        )
        assertEquals(first.emittedCount, second.emittedCount)
        assertEquals(first.droppedCount, second.droppedCount)
        assertTrue(first.droppedCount > 0, "这份输入没触发丢弃，用例白写")
    }

    // ---------- 车道占用 ----------

    @Test
    fun `同车道前后两条在任何时刻都不重叠`() {
        val danmaku = engine(Random(1))
        // 长短混排 ⇒ 同车道前后两条速度不同，正是追尾判定的用武之地
        val items = (0L until 30L).map { item(it * 300L, chars = 10 + (it.toInt() % 5) * 12) }
        danmaku.setItems(items)
        danmaku.tickThrough(0L, items.last().playTimeMillis, viewport)
        assertTrue(danmaku.activeScrollSlots.isNotEmpty())

        // 入场那一刻不重叠是显然的；隔 2s 再查一次才说明"按到达左沿的时间"判过
        listOf(items.last().playTimeMillis, items.last().playTimeMillis + 2_000L).forEach { at ->
            danmaku.activeScrollSlots.groupBy { it.lane }.forEach { (lane, slots) ->
                val ordered = slots.sortedBy { danmaku.leftOf(it, at) }
                for (index in 1 until ordered.size) {
                    val rear = ordered[index]
                    val front = ordered[index - 1]
                    assertTrue(
                        danmaku.leftOf(rear, at) >= danmaku.leftOf(front, at) + front.widthPx + DEFAULT_GAP_PX - 1f,
                        "车道 $lane 上 ${front.item.text.length} 字与 ${rear.item.text.length} 字" +
                            "在 ${at}ms 时重叠",
                    )
                }
            }
        }
    }

    @Test
    fun `屏幕装不下时丢弃而不是排队`() {
        val danmaku = engine()
        // 同一时刻 11 条，只有 10 条车道
        danmaku.setItems((0L until 11L).map { item(0L) })
        danmaku.tickAt(0L)

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
            (0L until 3L).map { item(it * 10L, DanmakuLocation.TOP, chars = 1) } +
                item(30L, DanmakuLocation.TOP, chars = 1) +
                item(6_000L, DanmakuLocation.TOP, chars = 1),
        )

        danmaku.tick(30L, 30L, rows, measure)
        assertEquals(listOf(0, 1, 2), danmaku.activeFixedSlots.map { it.lane })
        assertEquals(1, danmaku.droppedCount)  // 第 4 条：三行都还占着，丢

        // 驻留 5s 到期后，第 5 条复用最早释放的那一行
        danmaku.tick(6_000L, 6_000L, rows, measure)
        val last = danmaku.activeFixedSlots.single()
        assertEquals(6_000L, last.placedAtMs)
        assertEquals(0, last.lane)
    }

    @Test
    fun `顶部与底部互不占用对方的行`() {
        val danmaku = engine()
        danmaku.setItems(
            listOf(
                item(0L, DanmakuLocation.TOP, chars = 1),
                item(0L, DanmakuLocation.BOTTOM, chars = 1),
            ),
        )
        danmaku.tickAt(0L)

        val top = danmaku.activeFixedSlots.first { it.item.location == DanmakuLocation.TOP }
        val bottom = danmaku.activeFixedSlots.first { it.item.location == DanmakuLocation.BOTTOM }
        assertEquals(0, top.lane)
        assertEquals(0, bottom.lane)
        assertEquals(0f, danmaku.topEdgeOf(top, viewport), 0.01f)
        // 底部那行贴的是**画面**下沿，不是显示区下沿
        assertEquals(270f, danmaku.bottomEdgeOf(bottom, viewport), 0.01f)
    }

    @Test
    fun `重铺与 seek 之后顶底仍有行可放`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L, DanmakuLocation.TOP, chars = 1)))
        danmaku.tickAt(0L)
        assertEquals(1, danmaku.activeFixedSlots.size)

        // 尺寸没变 ⇒ tick 不会再进重铺车道那个分支，行表必须由清空动作自己保住长度
        danmaku.setItems(listOf(item(0L, DanmakuLocation.TOP, chars = 1)))
        danmaku.tickAt(0L)
        assertEquals(1, danmaku.activeFixedSlots.size)

        danmaku.seekTo(1_000L)
        danmaku.tickAt(1_000L)
        assertEquals(1, danmaku.activeFixedSlots.size)
    }

    @Test
    fun `车道间隙来自视口并决定一条道多久后被复用`() {
        // 10 条同时入场占满 10 条道，第 11 条 3s 后才到
        val items = List(10) { item(0L) } + item(3_000L)

        // 间隙 36px：一条 200px 的占到左沿外要 (200+36)/0.1 = 2360ms，3s 时 0 号道已放行
        val defaultGap = engine().apply {
            setItems(items)
            tick(0L, 0L, viewport, measure)
            tick(3_000L, 3_000L, viewport, measure)
        }
        assertEquals(11, defaultGap.emittedCount)
        assertEquals(0, defaultGap.droppedCount)

        // 间隙拉到 1000px（约一屏宽）：占用变成 12000ms，第 11 条无处可去 —— 静默丢弃。
        // ⚠️ 宽间隙视口要从第一次 tick 就用上：中途换几何会连车道一起重铺（那是另一条用例），
        // 那样测的就不是"间隙挤不挤"而是"重铺后能不能入场"了。
        val wide = viewport.copy(minLaneGapPx = 1_000f)
        val wideGap = engine().apply {
            setItems(items)
            tick(0L, 0L, wide, measure)
            tick(3_000L, 3_000L, wide, measure)
        }
        assertEquals(10, wideGap.emittedCount)
        assertEquals(1, wideGap.droppedCount)
    }

    // ---------- 解算 ----------

    @Test
    fun `滚动位置是入场时刻的线性函数`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L)))
        danmaku.tickAt(0L)
        val slot = danmaku.activeScrollSlots.single()

        assertEquals(viewport.widthPx, danmaku.leftOf(slot, 0L), 0.01f)
        assertEquals(0f, danmaku.leftOf(slot, 10_000L), 0.01f)
        // 走完"视口宽 + 自身宽 + 间隙"那一刻，正好整条离屏
        assertEquals(-(200f + DEFAULT_GAP_PX), danmaku.leftOf(slot, slot.exitAtMs), 1f)
    }

    @Test
    fun `离场后槽位被回收`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L)))
        danmaku.tickAt(0L)
        assertTrue(danmaku.hasActiveSlots())

        danmaku.tickAt(13_000L)
        assertFalse(danmaku.hasActiveSlots())
    }

    // ---------- 扰动 ----------

    @Test
    fun `seek后清空屏上并从新位置回看重建游标`() {
        val danmaku = engine()
        val items = (0L until 100L).map { item(it * 1_000L) }
        danmaku.setItems(items)
        danmaku.tickAt(5_050L)
        assertTrue(danmaku.hasActiveSlots())

        danmaku.seekTo(50_000L)
        assertFalse(danmaku.hasActiveSlots())
        assertEquals(30, danmaku.cursorIndex)  // 50s - 20s 回看窗口

        val droppedBefore = danmaku.droppedCount
        danmaku.tickAt(50_000L)
        // 屏上直接是 40s..50s 那一段在飞的状态，而不是等下一条弹幕入场才有字
        assertTrue(danmaku.activeScrollSlots.isNotEmpty())
        assertTrue(danmaku.activeScrollSlots.all { it.placedAtMs >= 39_000L })
        // 只有"窗口内但已飞完"的那 10 条被记为丢弃，历史没有被逐条判迟到
        assertEquals(droppedBefore + 10, danmaku.droppedCount)
    }

    @Test
    fun `改速度或间隙时屏上弹幕停在原地而不是整屏跳一下`() {
        val danmaku = engine()
        danmaku.setItems((0L until 6L).map { item(it * 1_000L) })
        danmaku.tickAt(5_050L)
        val cursorBefore = danmaku.cursorIndex
        val before = danmaku.activeScrollSlots.associate { it.item.id to danmaku.leftOf(it, 5_050L) }
        assertTrue(before.size >= 3, "屏上弹幕太少，测不出位置跳不跳")

        // 速度翻倍（拖速度滑杆）：入场时刻要跟着平移，使得已飞距离分毫不差
        danmaku.tick(
            danmakuNowMs = 5_050L,
            videoNowMs = 5_050L,
            viewport = viewport.copy(baseSpeedPxPerSecond = 200f),
            measureWidth = measure,
        )
        assertEquals(cursorBefore, danmaku.cursorIndex)  // 没回头重放
        val after = danmaku.activeScrollSlots.associate { it.item.id to danmaku.leftOf(it, 5_050L) }
        before.forEach { (id, x) ->
            after[id]?.let { newX ->
                assertTrue(
                    abs(newX - x) <= 1f,
                    "弹幕 $id 从 $x 跳到 $newX：重铺没保住已飞距离",
                )
            }
        }
        assertTrue(after.size >= before.size - 1, "换个速度就几乎清空屏上弹幕")
    }

    @Test
    fun `尺寸未就绪时不动作`() {
        val danmaku = engine()
        danmaku.setItems(listOf(item(0L)))

        danmaku.tick(0L, 0L, viewport.copy(widthPx = 0f, heightPx = 0f), measure)
        assertEquals(0, danmaku.emittedCount)
        assertEquals(0, danmaku.droppedCount)
        assertEquals(0, danmaku.cursorIndex)

        // 量出尺寸后正常入场，且不算"迟到"
        danmaku.tickAt(0L)
        assertEquals(1, danmaku.emittedCount)
    }

    // ---------- 两条时间轴 ----------

    /**
     * 倍速不改飞行速度。
     *
     * 钉的是"弹幕飞行跟不跟视频倍速"这个取舍。跟随倍速时飞行位置由播放位置驱动，
     * 而播放位置是 250ms 一跳的采样外推来的 —— 那个修正台阶会被倍速放大成整屏抖动。
     * 分轴之后飞行只读弹幕时钟：同一条墙钟里 4 倍速只让它多飞 1 秒的量。
     */
    @Test
    fun `视频倍速不改变一条弹幕的飞行速度`() {
        val baseTime = 60_000L
        val items = listOf(item(baseTime + 1_000L))

        // 常速：视频 1s 后到点，弹幕时钟也走了 1s
        val normal = engine(Random(3)).apply {
            setItems(items, videoNowMs = baseTime)
            tick(baseTime + 1_000L, baseTime + 1_000L, viewport, measure)
            tick(baseTime + 2_000L, baseTime + 2_000L, viewport, measure)
        }
        // 4 倍速：视频 0.25s 就到点，此后再走 1s 墙钟
        val fast = engine(Random(3)).apply {
            setItems(items, videoNowMs = baseTime)
            tick(baseTime + 250L, baseTime + 1_000L, viewport, measure)
            tick(baseTime + 1_250L, baseTime + 5_000L, viewport, measure)
        }

        val normalSlot = normal.activeScrollSlots.single()
        val fastSlot = fast.activeScrollSlots.single()
        assertEquals(normalSlot.speedPxPerMs, fastSlot.speedPxPerMs, 1e-6f)
        assertEquals(
            normal.leftOf(normalSlot, baseTime + 2_000L),
            fast.leftOf(fastSlot, baseTime + 1_250L),
            1f,
            "倍速让同一条弹幕多飞了：位置不再是弹幕时钟的函数",
        )
    }

    /** 倍速只加密度：同一段墙钟里，4 倍速扫过 4 倍的条数。 */
    @Test
    fun `视频倍速只增加上屏密度`() {
        val baseTime = 60_000L
        val items = (1L..40L).map { item(baseTime + it * 1_000L) }

        fun scannedAt(speedFactor: Long): Int {
            val danmaku = engine(Random(3)).apply {
                setItems(items, videoNowMs = baseTime)
                var at = 0L
                while (at <= 4_000L) {
                    tick(baseTime + at, baseTime + speedFactor * at, viewport, measure)
                    at += 50L
                }
            }
            return danmaku.cursorIndex
        }

        assertEquals(4, scannedAt(1L))
        assertEquals(16, scannedAt(4L))
    }

    /** 入场折算：视频里 8s 前就该上场的，此刻正好在"已经飞了 8s"的地方。 */
    @Test
    fun `迟到入场按时长折算而不是按倍速放大`() {
        val danmaku = engine()
        // 60s 那条直到视频时间 68s 才被扫到（卡顿或 seek 后回看）
        danmaku.setItems(listOf(item(60_000L)), videoNowMs = 68_000L)
        danmaku.tick(
            danmakuNowMs = 1_000_000L,
            videoNowMs = 68_000L,
            viewport = viewport,
            measureWidth = measure,
        )

        val slot = danmaku.activeScrollSlots.single()
        assertEquals(992_000L, slot.placedAtMs)
        // 已飞 8s × 0.1px/ms = 800px
        assertEquals(200f, danmaku.leftOf(slot, 1_000_000L), 1f)
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "期望 $expected，实际 $actual（容差 $tolerance）",
        )
    }

    private companion object {
        /** 与 [viewport] 里显式写死的间隙同一个数：追尾用例直接拿它算间隔。 */
        const val DEFAULT_GAP_PX = 36f
    }
}
