package lovehan1me.feature.danmaku

import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLocation

/**
 * 引擎运行参数。全部以**视频时间**为单位 —— 倍速时弹幕与画面同步变快，
 * 这既符合"弹幕跟着剧情走"的心智模型，也让整条解算不必感知实时时钟。
 */
data class DanmakuEngineConfig(
    /** 顶部/底部弹幕驻留时间。 */
    val fixedDurationMs: Long = 5_000L,
    /**
     * 允许"迟到入场"多久。
     *
     * 数据节拍是 20Hz（50ms 一次），一条弹幕最多晚一两个周期被发现；再晚
     * （卡顿、主线程被占）说明它该出现的位置已经被人走过去了，直接丢 —— 不补、不排队。
     */
    val lateGraceMs: Long = 150L,
    /** 车道上限：超高分辨率 + 极小行高时防止数组与扫描失控。 */
    val maxLanes: Int = 40,
    /** 顶/底堆叠行上限，同样只是兜底。 */
    val maxFixedRows: Int = 12,
)

/**
 * 弹幕绘制区（像素，由绘制层从布局尺寸换算而来）。
 *
 * 引擎只见 px 不见 Dp：它必须与最终落笔的坐标系严格一致，
 * 否则"间隙够不够"的判断会与实际重不重叠对不上。
 *
 * 像素速度与间隙也放在这里（而不是引擎配置）：它们是**同一类东西** —— 都由
 * "字号 + 用户滑杆"折算成像素，改变时的处理方式也和尺寸变化一致（屏上旧布局整体作废）。
 * 留在配置里会留下一个说不清的中间态：换了宽度却没换速度，条目仍按旧速度算出的
 * `exitAtMs` 提前或延后消失。
 */
data class DanmakuViewport(
    val widthPx: Float,
    val heightPx: Float,
    val lineHeightPx: Float,
    /** 弹幕占用的画面高度比例（设置项）。滚动轨与顶/底堆叠共用这个上界。 */
    val displayAreaRatio: Float = 1f,
    /** 一条弹幕横向走完**一个视口宽度**所需的视频时间。走完自身宽度还要再加一点。 */
    val scrollTraverseMs: Long = DANMAKU_STANDARD_TRAVERSE_MS,
    /** 同车道两条弹幕之间的最小水平间隙。 */
    val minLaneGapPx: Float = 24f,
)

/**
 * 速度百分比 → 走完一个视口所需的毫秒（100 = 标准）。
 *
 * 给用户的滑杆是"倍率"而不是秒数：慢一点/快一点能理解，10000ms 不能。
 * 夹取放在这里而不是各调用点，因为设置值来自磁盘，0 与负数都该收敛到最近可用档。
 */
internal fun danmakuTraverseMs(speedPercent: Int): Long =
    DANMAKU_STANDARD_TRAVERSE_MS * 100L /
        speedPercent.coerceIn(DANMAKU_SPEED_MIN_PERCENT, DANMAKU_SPEED_MAX_PERCENT)

/** 标准速度：10 秒走完一个视口宽度。 */
internal const val DANMAKU_STANDARD_TRAVERSE_MS = 10_000L

/** 速度滑杆区间（百分比）：50 = 慢一倍（20 秒一屏），200 = 快一倍（5 秒一屏）。 */
internal const val DANMAKU_SPEED_MIN_PERCENT = 50
internal const val DANMAKU_SPEED_MAX_PERCENT = 200

/**
 * 一条已在屏上的弹幕。
 *
 * [exitAtMs] 在入场那一刻就算死了（闭式解 + 恒定像素速度 ⇒ 之后无需回看），
 * 于是每帧清理是 O(屏上条数)，与总条数无关。
 */
class DanmakuSlot internal constructor(
    val item: DanmakuItem,
    /** 入场时刻（视频毫秒）= 这条弹幕的 `playTimeMillis`。 */
    val startMs: Long,
    val widthPx: Float,
    /** 车道 / 堆叠行下标（滚动、顶、底各自编号）。 */
    val lane: Int,
    val exitAtMs: Long,
)

/**
 * 弹幕引擎：把"按时间排好的弹幕"变成"此刻屏上有哪些弹幕、各在哪"。
 *
 * 纯逻辑，不含 Compose 类型；文本宽度由调用方以函数注入（见 [tick]），
 * 这样引擎的碰撞判断与最终落笔用的是同一份测量缓存。
 *
 * ## 三条支点
 * 1. **恒定像素速度**。滚动弹幕左边界 `x = W × (1 − (t − start) / traverseMs)`，
 *    与文字长短无关 ⇒ 同车道前后两条的间隙恒定：入场时不撞，之后就**永远**不撞。
 *    所谓"预测式追尾检测"在这个模型下塌缩成一次对入场时刻的常数比较 ——
 *    注意比较的是 `playTimeMillis` 而不是 tick 的 `nowMs`，
 *    否则数据节拍的抖动会改变弹幕布局，屏幕就会"抖"。
 * 2. **发射扫描是游标 + 二分**。输入按 `playTimeMillis` 升序（缓存侧 SQL 已 `ORDER BY`），
 *    每次 tick 只消费游标之后新到期的前缀；seek 时二分重建游标。任何路径都不每 tick 全表扫。
 * 3. **满载静默丢弃**。找不到车道直接丢这条，不排队、不补偿 —— 弹幕密度本就远超屏幕
 *    容量，堆积会让恢复播放的瞬间变成一片糊。
 */
class DanmakuEngine(
    private val config: DanmakuEngineConfig = DanmakuEngineConfig(),
) {

    private var items: List<DanmakuItem> = emptyList()

    /** 下一条待发射的下标：[items] 升序 ⇒ 游标之前的都已发射或丢弃。 */
    private var cursor = 0

    private val mutableScrollSlots = mutableListOf<DanmakuSlot>()
    private val mutableFixedSlots = mutableListOf<DanmakuSlot>()

    /** 每条滚动车道：下一条**入场时刻**不早于此值才能用这条道。 */
    private var laneFreeAt = LongArray(0)

    /** 顶/底堆叠行占用到何时（同样按入场时刻比较）。 */
    private var topRowFreeAt = LongArray(0)
    private var bottomRowFreeAt = LongArray(0)

    private var laneCount = 0
    private var fixedRowCount = 0
    private var appliedTraverseMs = -1L
    private var appliedGapPx = -1f

    /** 只给日志与单测：UI 不展示弹幕统计（v1 明确不做）。 */
    internal var emittedCount = 0
        private set
    internal var droppedCount = 0
        private set

    internal val cursorIndex: Int get() = cursor

    val activeScrollSlots: List<DanmakuSlot> get() = mutableScrollSlots
    val activeFixedSlots: List<DanmakuSlot> get() = mutableFixedSlots

    /**
     * 屏上有没有弹幕。绘制层据此在空集时**一个 draw 调用都不发** ——
     * iOS 上层叠的半透明 Canvas 会迫使根层透明，空转也必须零成本。
     */
    fun hasActiveSlots(): Boolean =
        mutableScrollSlots.isNotEmpty() || mutableFixedSlots.isNotEmpty()

    /**
     * 装载一集弹幕，并把游标落到 [startPositionMs]。
     *
     * [items] **必须**按 `playTimeMillis` 升序：缓存读取本身就是 `ORDER BY`，
     * 数据源负责在返回前排序。这里不做防御性排序 —— 几万条排在进播放器的主线程上，
     * 代价可见，而不变量违约会在单测里立刻炸出来。
     */
    fun setItems(items: List<DanmakuItem>, startPositionMs: Long = 0L) {
        this.items = items
        clearSlots()
        emittedCount = 0
        droppedCount = 0
        cursor = lowerBound(items, startPositionMs)
    }

    /** seek / 循环回绕：屏上旧弹幕全部作废（它们属于上一个时间点），游标二分重建。 */
    fun seekTo(positionMs: Long) {
        clearSlots()
        cursor = lowerBound(items, positionMs)
    }

    fun reset() {
        items = emptyList()
        clearSlots()
        cursor = 0
    }

    /**
     * 数据节拍调用（20Hz）。
     *
     * @param nowMs 当前播放位置（[DanmakuPositionTracker.positionAt] 的闭式外推值）
     * @param measureWidth 文本 → 像素宽度，必须与绘制时共用同一份缓存
     */
    fun tick(nowMs: Long, viewport: DanmakuViewport, measureWidth: (DanmakuItem) -> Float) {
        if (viewport.widthPx <= 0f || viewport.heightPx <= 0f || viewport.lineHeightPx <= 0f) {
            // 尺寸还没量出来（首帧 0×0）：什么都不做，也别把弹幕当"迟到"丢掉
            return
        }
        applyViewport(viewport)
        expire(nowMs)
        emitDue(nowMs, viewport, measureWidth)
    }

    // ---------- 解算（绘制层每帧直调） ----------

    /** 滚动条左边界：`startMs` 时刻在视口右沿，之后线性左移。 */
    fun leftEdgeOf(slot: DanmakuSlot, nowMs: Long, viewport: DanmakuViewport): Float =
        viewport.widthPx * (1f - (nowMs - slot.startMs).toFloat() / viewport.scrollTraverseMs)

    /** 顶/底条左边界：水平居中。 */
    fun centeredLeftEdgeOf(slot: DanmakuSlot, viewportWidthPx: Float): Float =
        (viewportWidthPx - slot.widthPx) / 2f

    /** 滚动条与顶部条的上边界：从可用区顶部往下数第 lane 行。 */
    fun topEdgeOf(slot: DanmakuSlot, viewport: DanmakuViewport): Float =
        slot.lane * viewport.lineHeightPx

    /** 底部条的上边界：从可用区底部往上数第 lane 行。 */
    fun bottomEdgeOf(slot: DanmakuSlot, viewport: DanmakuViewport): Float =
        usableHeight(viewport) - (slot.lane + 1) * viewport.lineHeightPx

    /** 当前几何下的滚动车道数（单测与日志用）。 */
    internal val laneCountIndex: Int get() = laneCount

    // ---------- 内部 ----------

    private fun usableHeight(viewport: DanmakuViewport): Float =
        viewport.heightPx * viewport.displayAreaRatio.coerceIn(0.1f, 1f)

    private fun clearSlots() {
        mutableScrollSlots.clear()
        mutableFixedSlots.clear()
    }

    /**
     * 视口几何变化 = 屏上弹幕软重置。
     *
     * 车道数、行高、像素速度全都绑在尺寸上，逐条迁移既复杂又容易残留；
     * 而转屏 / 拖窗口 / PiP 进出这些事件本来就该让上一帧的布局作废。
     * 游标不动，所以不会重放已经过去的弹幕。
     */
    private fun applyViewport(viewport: DanmakuViewport) {
        val lanes = (usableHeight(viewport) / viewport.lineHeightPx).toInt()
            .coerceIn(1, config.maxLanes)
        val rows = lanes.coerceAtMost(config.maxFixedRows)
        if (lanes == laneCount && rows == fixedRowCount &&
            viewport.scrollTraverseMs == appliedTraverseMs &&
            viewport.minLaneGapPx == appliedGapPx
        ) {
            return
        }
        laneCount = lanes
        fixedRowCount = rows
        appliedTraverseMs = viewport.scrollTraverseMs
        appliedGapPx = viewport.minLaneGapPx
        laneFreeAt = LongArray(lanes)
        topRowFreeAt = LongArray(rows)
        bottomRowFreeAt = LongArray(rows)
        clearSlots()
    }

    private fun expire(nowMs: Long) {
        mutableScrollSlots.removeAll { it.exitAtMs <= nowMs }
        mutableFixedSlots.removeAll { it.exitAtMs <= nowMs }
    }

    private fun emitDue(
        nowMs: Long,
        viewport: DanmakuViewport,
        measureWidth: (DanmakuItem) -> Float,
    ) {
        val source = items
        while (cursor < source.size) {
            val item = source[cursor]
            if (item.playTimeMillis > nowMs) return
            cursor++
            // 迟到的不补
            if (nowMs - item.playTimeMillis > config.lateGraceMs) {
                droppedCount++
                continue
            }
            if (!place(item, viewport, measureWidth)) droppedCount++
        }
    }

    /** @return false = 找不到车道，静默丢弃。 */
    private fun place(
        item: DanmakuItem,
        viewport: DanmakuViewport,
        measureWidth: (DanmakuItem) -> Float,
    ): Boolean {
        val startMs = item.playTimeMillis
        val widthPx = measureWidth(item).coerceAtLeast(1f)
        if (item.location == DanmakuLocation.SCROLL) {
            val speedPxPerMs = viewport.widthPx / viewport.scrollTraverseMs
            val lane = pickFreeLane(startMs, laneFreeAt) ?: return false
            // 恒定速度 ⇒ 间隙恒定，所以这条道"何时可复用"只取决于本条的宽度
            laneFreeAt[lane] = startMs + ((widthPx + viewport.minLaneGapPx) / speedPxPerMs).toLong()
            mutableScrollSlots += DanmakuSlot(
                item = item,
                startMs = startMs,
                widthPx = widthPx,
                lane = lane,
                exitAtMs = startMs + ((viewport.widthPx + widthPx) / speedPxPerMs).toLong(),
            )
        } else {
            val rowFreeAt =
                if (item.location == DanmakuLocation.TOP) topRowFreeAt else bottomRowFreeAt
            val row = pickFreeLane(startMs, rowFreeAt) ?: return false
            rowFreeAt[row] = startMs + config.fixedDurationMs
            mutableFixedSlots += DanmakuSlot(
                item = item,
                startMs = startMs,
                widthPx = widthPx,
                lane = row,
                exitAtMs = startMs + config.fixedDurationMs,
            )
        }
        emittedCount++
        return true
    }

    /**
     * 挑一条在 [startMs] 时刻已经放行、且**最闲**的车道（比 first-fit 分布均匀，
     * 也避免所有弹幕总挤在同一行）。全占用返回 null。
     *
     * 比较的是入场时刻而非当前时刻：车道释放只取决于两条弹幕各自的 start，
     * 拿 nowMs 参与判断会让 20Hz 节拍的抖动渗进布局结果。
     */
    private fun pickFreeLane(startMs: Long, freeAt: LongArray): Int? {
        var best = -1
        var bestValue = Long.MAX_VALUE
        for (index in freeAt.indices) {
            if (freeAt[index] <= startMs && freeAt[index] < bestValue) {
                bestValue = freeAt[index]
                best = index
            }
        }
        return if (best < 0) null else best
    }

    /** 第一个 `playTimeMillis >= positionMs` 的下标；升序输入下 O(log n)。 */
    private fun lowerBound(source: List<DanmakuItem>, positionMs: Long): Int {
        var low = 0
        var high = source.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (source[mid].playTimeMillis < positionMs) low = mid + 1 else high = mid
        }
        return low
    }
}
