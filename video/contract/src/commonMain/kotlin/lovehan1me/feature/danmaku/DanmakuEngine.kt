package lovehan1me.feature.danmaku

import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLocation
import kotlin.math.log
import kotlin.math.pow
import kotlin.random.Random

/**
 * 引擎运行参数。带 `Ms` 的字段分属两条时间轴，注释各自标明 ——
 * "弹幕什么时候上场"看视频时间，"上场之后怎么飞"看弹幕自己的时钟（见 [DanmakuEngine]）。
 */
data class DanmakuEngineConfig(
    /** 顶部/底部弹幕驻留时长（**弹幕时钟**毫秒：4 倍速时顶弹也不该一闪而过）。 */
    val fixedDurationMs: Long = 5_000L,
    /**
     * 起播与 seek 时往回看多久来铺屏（**视频**毫秒）。
     *
     * 位置是入场时刻的闭式函数，所以"若干秒前进场的弹幕现在飞到了哪"一算就知道 ——
     * 从中间点开播不必等弹幕重新排队，画面一到就是一片已经在飞的字。
     * 超出这条窗口的一律不回放（seek 前的历史不该在 seek 后重来）。
     */
    val repopulateWindowMs: Long = DANMAKU_REPOPULATE_WINDOW_MS,
    /** 弹幕长度每翻一倍，速度乘以这个底数。 */
    val speedMultiplierBase: Float = 1.14f,
    /**
     * 逐条随机速度波动（± 这个比例）。
     *
     * 不是装饰：同一车道上前后两条若速度完全相同，间距就永远等于入场时的间距，
     * 满屏时会看到整齐的"车队"。给一点抖动，车队自然散开。
     */
    val speedFluctuation: Float = 0.0875f,
)

/**
 * 弹幕绘制区（像素，由绘制层从布局尺寸换算而来）。
 *
 * 引擎只见 px 不见 Dp：它必须与最终落笔的坐标系严格一致，
 * 否则"间隙够不够"的判断会与实际重不重叠对不上。
 *
 * 速度也放在这里而不是引擎配置：基准速度是"每秒多少像素"，由密度与用户滑杆折算而来，
 * 加权基准宽度与行高则来自同一次文本实测。车道数、行高、速度全都绑在同一份尺寸上，
 * 分开放就会留下"换了行高却没换速度"这类说不清的中间态。
 */
data class DanmakuViewport(
    val widthPx: Float,
    val heightPx: Float,
    /** 一行弹幕占多高 —— 实测文本行高，不是字号乘一个魔数。 */
    val lineHeightPx: Float,
    /** 弹幕占用的画面高度比例（设置项）。滚动轨与顶/底堆叠共用这个上界。 */
    val displayAreaRatio: Float = 1f,
    /**
     * 基准像素速度（px 每秒，秒是**弹幕时钟**的秒）：
     * 宽度等于 [baseSpeedTextWidthPx] 的弹幕就按这个速度飞。
     */
    val baseSpeedPxPerSecond: Float = DANMAKU_BASE_SPEED_DP_PER_SECOND,
    /** 同车道两条弹幕之间的最小水平间隙。 */
    val minLaneGapPx: Float = DANMAKU_MIN_LANE_GAP_PX,
    /** 速度加权的基准文本宽度（与 [lineHeightPx] 同一次实测得到）。 */
    val baseSpeedTextWidthPx: Float = DANMAKU_BASE_SPEED_TEXT_WIDTH_PX,
)

/**
 * 速度百分比 → 基准像素速度（px/s）。
 *
 * 给用户的滑杆是"倍率"而不是 px/s：慢一点/快一点能理解，88 不能。
 * 密度也吃进来，因为基准速度是**物理速度**（88dp/s）：手机上缩放后与桌面上看着一样快，
 * 而"走完一个视口宽度要多久"这种几何速度会让大屏上的弹幕慢吞吞、小屏上糊成一片。
 * 夹取放在这里而不是各调用点，因为设置值来自磁盘，0 与负数都该收敛到最近可用档。
 */
fun danmakuBaseSpeedPxPerSecond(speedPercent: Int, density: Float): Float =
    DANMAKU_BASE_SPEED_DP_PER_SECOND * density.coerceAtLeast(1f) *
        speedPercent.coerceIn(DANMAKU_SPEED_MIN_PERCENT, DANMAKU_SPEED_MAX_PERCENT) / 100f

/** 基准速度：88 dp/s（滑杆 100% 那一档）。 */
const val DANMAKU_BASE_SPEED_DP_PER_SECOND = 88f

/** 回看铺屏窗口：20 秒 —— 比最宽画面上走完一趟还久，等于"把该在屏上的都放上来"。 */
const val DANMAKU_REPOPULATE_WINDOW_MS = 20_000L

/** 最小间隙与加权基准宽度的缺省值：绘制层永远按密度与实测覆盖，这里只求类型完整。 */
const val DANMAKU_MIN_LANE_GAP_PX = 36f
const val DANMAKU_BASE_SPEED_TEXT_WIDTH_PX = 72f

/** 速度滑杆区间（百分比）：50 = 慢一半，200 = 快一倍。 */
const val DANMAKU_SPEED_MIN_PERCENT = 50
const val DANMAKU_SPEED_MAX_PERCENT = 200

/**
 * 一条已在屏上的弹幕。
 *
 * [exitAtMs] 在入场那一刻算死（闭式解 + 每条恒定速度 ⇒ 之后无需回看），
 * 于是每帧清理是 O(屏上条数)，与总条数无关。
 */
class DanmakuSlot internal constructor(
    val item: DanmakuItem,
    /**
     * 入场时刻（**弹幕时钟**毫秒）。
     *
     * 它不等于 `item.playTimeMillis`：那条弹幕按视频时间到点，入场那一刻才折算成
     * "弹幕时钟此刻往前回退已流逝的时长"，之后位置只读弹幕时钟 ⇒ 视频倍速不改变飞行速度。
     */
    val placedAtMs: Long,
    val widthPx: Float,
    /** 车道 / 堆叠行下标（滚动、顶、底各自编号）。 */
    val lane: Int,
    val exitAtMs: Long,
    /** 本条的速度（px/弹幕毫秒）。同车道前后两条可以不同，所以追尾不能只看入场时刻。 */
    internal val speedPxPerMs: Float,
    /** 长度加权（含抖动）后的速度倍率：重铺时复算速度靠它，不重新掷随机数。 */
    internal val speedMultiplier: Float,
)

/**
 * 弹幕引擎：把"按时间排好的弹幕"变成"此刻屏上有哪些弹幕、各在哪"。
 *
 * 纯逻辑，不含 Compose 类型；文本宽度由调用方以函数注入（见 [tick]），
 * 这样引擎的碰撞判断与最终落笔用的是同一份测量缓存。
 *
 * ## 两条时间轴
 * - **视频时间**：只用来决定"哪条弹幕到点了"（[emitDue] 的游标）与"入场时它已经该飞多久"。
 * - **弹幕时钟**：入场之后位置的唯一自变量（[DanmakuSlot.placedAtMs] 到此刻的差）。
 *
 * 倍速只加快第一条轴，所以 4 倍速时弹幕上屏密度翻倍、飞行速度不变。
 * 单一时间轴做不到这点：拿视频时间驱动飞行 ⇒ 字在 4 倍速时飞 4 倍快，
 * 而视频位置由 250ms 一跳的采样外推而来，那个错跳会被速度放大成整屏抖动。
 *
 * ## 四条支点
 * 1. **每条一个恒定像素速度**，短的单倍速、长的成倍快：
 *    `速度 = 基准 × 1.14^log2(字宽 / 基准字宽)`，再加 ±8.75% 抖动。
 *    长弹幕早点开、早点开完，车道周转快，满屏时才挤得出层次。
 * 2. **追尾按"还要多久飞出左沿"判**，不按"入场时刻隔够没"判。因为同车道两条速度可以不同，
 *    此刻有空隙不代表一直有空隙：后一条更快就会一路追上去。把前一条的右边缘与后一条的
 *    左边缘各自飞到轨道左沿所需的时间比一下，前者更长 ⇒ 会撞 ⇒ 换道。
 *    位置仍是入场时刻的闭式解，逐帧不积分、不累计误差。
 * 3. **发射扫描是游标 + 二分**。输入按 `playTimeMillis` 升序（缓存侧 SQL 已 `ORDER BY`），
 *    每次 tick 只消费游标之后新到期的前缀；seek 时二分重建游标。任何路径都不每 tick 全表扫。
 * 4. **放不下就静默丢弃**。不排队、不补偿 —— 弹幕密度本就远超屏幕容量，堆积会让恢复
 *    播放的瞬间变成一片糊。已经飘过左沿的同样丢（它没有可呈现的位置了）。
 */
class DanmakuEngine internal constructor(
    private val config: DanmakuEngineConfig,
    private val random: Random,
) {

    constructor(config: DanmakuEngineConfig = DanmakuEngineConfig()) :
        this(config, Random.Default)

    private var items: List<DanmakuItem> = emptyList()

    /** 下一条待发射的下标：[items] 升序 ⇒ 游标之前的都已发射或丢弃。 */
    private var cursor = 0

    /** 每条滚动车道一个列表，按**此刻**的左边界升序（越靠前 = 越接近左沿）。 */
    private var scrollLanes: List<MutableList<DanmakuSlot>> = emptyList()

    /** 顶/底堆叠：每行至多一条，占位即锁到驻留时间结束。 */
    private var topRows: MutableList<DanmakuSlot?> = mutableListOf()
    private var bottomRows: MutableList<DanmakuSlot?> = mutableListOf()

    private var laneCount = 0
    private var appliedViewport: DanmakuViewport? = null

    /** 只给日志与单测：UI 不展示弹幕统计（v1 明确不做）。 */
    var emittedCount = 0
        private set
    var droppedCount = 0
        private set

    val cursorIndex: Int get() = cursor

    val activeScrollSlots: List<DanmakuSlot> get() = scrollLanes.flatten()

    val activeFixedSlots: List<DanmakuSlot>
        get() = buildList(topRows.size + bottomRows.size) {
            topRows.forEach { slot -> slot?.let { add(it) } }
            bottomRows.forEach { slot -> slot?.let { add(it) } }
        }

    /**
     * 屏上有没有弹幕。绘制层据此在空集时**一个 draw 调用都不发** ——
     * iOS 上层叠的半透明 Canvas 会迫使根层透明，空转也必须零成本。
     */
    fun hasActiveSlots(): Boolean =
        scrollLanes.any { it.isNotEmpty() } ||
            topRows.any { it != null } ||
            bottomRows.any { it != null }

    /**
     * 装载一集弹幕，并把游标落到 [videoNowMs] 往前回看 [repopulateWindowMs] 处。
     *
     * [items] **必须**按 `playTimeMillis` 升序：缓存读取本身就是 `ORDER BY`，
     * 数据源负责在返回前排序。这里不做防御性排序 —— 几万条排在进播放器的主线程上，
     * 代价可见，而不变量违约会在单测里立刻炸出来。
     */
    fun setItems(items: List<DanmakuItem>, videoNowMs: Long = 0L) {
        this.items = items
        clearSlots()
        emittedCount = 0
        droppedCount = 0
        rewindCursor(videoNowMs)
    }

    /** seek / 循环回绕：屏上旧弹幕全部作废（它们属于上一个时间点），游标回看重铺。 */
    fun seekTo(videoNowMs: Long) {
        clearSlots()
        rewindCursor(videoNowMs)
    }

    fun reset() {
        items = emptyList()
        clearSlots()
        cursor = 0
    }

    /**
     * 数据节拍调用（20Hz 以上）。
     *
     * @param danmakuNowMs 弹幕时钟此刻（[DanmakuClock] 的产物）：屏上位置的唯一自变量
     * @param videoNowMs 当前播放位置（[DanmakuPositionTracker.positionAt] 的闭式外推值）：
     *   只用来挑出到点的弹幕，并折算它们入场时"已经飞了多久"
     * @param measureWidth 文本 → 像素宽度，必须与绘制时共用同一份缓存
     */
    fun tick(
        danmakuNowMs: Long,
        videoNowMs: Long,
        viewport: DanmakuViewport,
        measureWidth: (DanmakuItem) -> Float,
    ) {
        if (viewport.widthPx <= 0f || viewport.heightPx <= 0f || viewport.lineHeightPx <= 0f) {
            // 尺寸还没量出来（首帧 0×0）：什么都不做，也别把弹幕当"已经飘过"丢掉
            return
        }
        if (viewport != appliedViewport) applyViewport(viewport, danmakuNowMs, measureWidth)
        expire(danmakuNowMs)
        emitDue(danmakuNowMs, videoNowMs, viewport, measureWidth)
    }

    // ---------- 解算（绘制层每帧直调） ----------

    /** 滚动条左边界：`placedAtMs` 时刻在视口右沿，之后按本条速度线性左移。 */
    fun leftEdgeOf(slot: DanmakuSlot, danmakuNowMs: Long, viewport: DanmakuViewport): Float =
        viewport.widthPx - (danmakuNowMs - slot.placedAtMs) * slot.speedPxPerMs

    /** 顶/底条左边界：水平居中。 */
    fun centeredLeftEdgeOf(slot: DanmakuSlot, viewportWidthPx: Float): Float =
        (viewportWidthPx - slot.widthPx) / 2f

    /** 滚动条与顶部条的上边界：从可用区顶部往下数第 lane 行。 */
    fun topEdgeOf(slot: DanmakuSlot, viewport: DanmakuViewport): Float =
        slot.lane * viewport.lineHeightPx

    /** 底部条的上边界：从**画面**底部往上数第 lane 行（不是从显示区底部）。 */
    fun bottomEdgeOf(slot: DanmakuSlot, viewport: DanmakuViewport): Float =
        viewport.heightPx - (slot.lane + 1) * viewport.lineHeightPx

    /** 当前几何下的滚动车道数（单测与日志用）。 */
    val laneCountIndex: Int get() = laneCount

    // ---------- 内部 ----------

    private fun rewindCursor(positionMs: Long) {
        cursor = lowerBound(items, positionMs - config.repopulateWindowMs)
    }

    /**
     * 清空屏上内容，但**保留车道结构**。
     *
     * 车道只在 [appliedViewport] 变化时重建，把行表清成零长就等于"这一集再也没有
     * 顶部/底部弹幕的位置可放"，而尺寸没变 ⇒ [tick] 不会重铺。
     */
    private fun clearSlots() {
        scrollLanes.forEach { it.clear() }
        for (index in topRows.indices) topRows[index] = null
        for (index in bottomRows.indices) bottomRows[index] = null
    }

    /**
     * 视口变化（转屏 / 拖窗口 / PiP 进出 / 拖滑杆）= 屏上弹幕重铺。
     *
     * 车道数、行高、像素速度全都绑在尺寸与字号上，逐条迁移既复杂又容易残留；
     * 这里保的是**这一帧的屏幕位置**：把每条的入场时刻按新旧速度比例平移，
     * 于是拖速度滑杆之后弹幕停在原地，不会整屏跳一下。只有车道要重挑
     * （挑不中的那条只能消失）。游标不动，所以不会重放已经过去的弹幕。
     */
    private fun applyViewport(
        viewport: DanmakuViewport,
        danmakuNowMs: Long,
        measureWidth: (DanmakuItem) -> Float,
    ) {
        val previous = appliedViewport
        appliedViewport = viewport
        val presentScroll = if (previous == null) emptyList() else activeScrollSlots
        val presentFixed = if (previous == null) emptyList() else activeFixedSlots

        resizeLanes(laneCount(viewport))

        presentScroll.forEach { slot ->
            val newSpeedPxPerMs = viewport.baseSpeedPxPerSecond * slot.speedMultiplier / 1_000f
            if (newSpeedPxPerMs <= 0f) return@forEach
            // 保住"此刻已飞过的像素距离"，反推新的入场时刻；新旧速度相同即原时刻不动
            val travelledPx = (danmakuNowMs - slot.placedAtMs) * slot.speedPxPerMs
            placeScroll(
                item = slot.item,
                placedAtMs = danmakuNowMs - (travelledPx / newSpeedPxPerMs).toLong(),
                widthPx = measureWidth(slot.item).coerceAtLeast(1f),
                speedMultiplier = slot.speedMultiplier,
                speedPxPerMs = newSpeedPxPerMs,
                viewport = viewport,
                danmakuNowMs = danmakuNowMs,
            )
        }
        presentFixed.forEach { slot ->
            placeFixed(
                item = slot.item,
                placedAtMs = slot.placedAtMs,
                widthPx = slot.widthPx,
                rows = if (slot.item.location == DanmakuLocation.TOP) topRows else bottomRows,
                danmakuNowMs = danmakuNowMs,
            )
        }
    }

    private fun resizeLanes(lanes: Int) {
        laneCount = lanes
        scrollLanes = MutableList(lanes) { mutableListOf<DanmakuSlot>() }
        topRows = MutableList(lanes) { null }
        bottomRows = MutableList(lanes) { null }
    }

    /** 车道数 = 显示区高度里塞得下几行。一屏几行本就是几行，不另设上限。 */
    private fun laneCount(viewport: DanmakuViewport): Int {
        val usableHeightPx = viewport.heightPx * viewport.displayAreaRatio.coerceIn(0.1f, 1f)
        return (usableHeightPx / viewport.lineHeightPx).toInt().coerceAtLeast(1)
    }

    private fun expire(danmakuNowMs: Long) {
        scrollLanes.forEach { lane -> lane.removeAll { it.exitAtMs <= danmakuNowMs } }
        // 逐下标置空，不用 `MutableList.replaceAll`：它在 Kotlin/Native 上要
        // `@ExperimentalNativeApi` 授权，而这里本来就是把一行换成 null。
        for (index in topRows.indices) {
            val slot = topRows[index]
            if (slot != null && slot.exitAtMs <= danmakuNowMs) topRows[index] = null
        }
        for (index in bottomRows.indices) {
            val slot = bottomRows[index]
            if (slot != null && slot.exitAtMs <= danmakuNowMs) bottomRows[index] = null
        }
    }

    private fun emitDue(
        danmakuNowMs: Long,
        videoNowMs: Long,
        viewport: DanmakuViewport,
        measureWidth: (DanmakuItem) -> Float,
    ) {
        val source = items
        while (cursor < source.size) {
            val item = source[cursor]
            // 到点与否只看视频时间：倍速时这里一次放出好几条，屏上密度跟着涨
            if (item.playTimeMillis > videoNowMs) return
            cursor++
            // "视频里这条早就该上场了"的时长，一比一转给弹幕时钟：倍速只增密度不增飞行距离
            val elapsedMs = videoNowMs - item.playTimeMillis
            // 回看窗口里那些早就飞完的：连一个字都没上屏，位置没有意义，量都不用量
            if (isAlreadyGone(elapsedMs, viewport)) {
                droppedCount++
                continue
            }
            val placed = place(
                item = item,
                placedAtMs = danmakuNowMs - elapsedMs,
                danmakuNowMs = danmakuNowMs,
                viewport = viewport,
                measureWidth = measureWidth,
            )
            if (!placed) droppedCount++
        }
    }

    /** 已流逝时长超过最短可能（一个字宽、不加权）的过屏时长 —— 再长只会更早消失。 */
    private fun isAlreadyGone(elapsedMs: Long, viewport: DanmakuViewport): Boolean {
        val baseSpeed = viewport.baseSpeedPxPerSecond
        if (baseSpeed <= 0f) return false
        val shortestCrossingMs =
            (viewport.widthPx + 1f + viewport.minLaneGapPx) / baseSpeed * 1_000f
        return elapsedMs > shortestCrossingMs.toLong()
    }

    /** @return false = 找不到车道 / 驻留期已过，静默丢弃。 */
    private fun place(
        item: DanmakuItem,
        placedAtMs: Long,
        danmakuNowMs: Long,
        viewport: DanmakuViewport,
        measureWidth: (DanmakuItem) -> Float,
    ): Boolean {
        val widthPx = measureWidth(item).coerceAtLeast(1f)
        val placed = if (item.location == DanmakuLocation.SCROLL) {
            val multiplier = speedMultiplier(widthPx, viewport)
            placeScroll(
                item = item,
                placedAtMs = placedAtMs,
                widthPx = widthPx,
                speedMultiplier = multiplier,
                speedPxPerMs = viewport.baseSpeedPxPerSecond * multiplier / 1_000f,
                viewport = viewport,
                danmakuNowMs = danmakuNowMs,
            )
        } else {
            placeFixed(
                item = item,
                placedAtMs = placedAtMs,
                widthPx = widthPx,
                rows = if (item.location == DanmakuLocation.TOP) topRows else bottomRows,
                danmakuNowMs = danmakuNowMs,
            )
        }
        if (placed) emittedCount++
        return placed
    }

    /**
     * 长度加权 + 抖动。等于基准字宽时恰好 1 倍；短于它不减速（[coerceAtLeast]），
     * 否则一屏短句会慢到互相踩着脚后跟。
     */
    private fun speedMultiplier(widthPx: Float, viewport: DanmakuViewport): Float {
        val ratio = widthPx / viewport.baseSpeedTextWidthPx.coerceAtLeast(1f)
        val weighted = config.speedMultiplierBase.pow(log(ratio, 2f)).coerceAtLeast(1f)
        val fluctuation = config.speedFluctuation
        return if (fluctuation <= 0f) weighted
        else weighted + (random.nextFloat() - 0.5f) * 2f * fluctuation
    }

    private fun placeScroll(
        item: DanmakuItem,
        placedAtMs: Long,
        widthPx: Float,
        speedMultiplier: Float,
        speedPxPerMs: Float,
        viewport: DanmakuViewport,
        danmakuNowMs: Long,
    ): Boolean {
        val exitAtMs = placedAtMs +
            ((viewport.widthPx + widthPx + viewport.minLaneGapPx) / speedPxPerMs).toLong()
        if (exitAtMs <= danmakuNowMs) return false

        for (laneIndex in scrollLanes.indices) {
            val lane = scrollLanes[laneIndex]
            val insertion = nonOverlappingIndex(
                candidateLeft = viewport.widthPx - (danmakuNowMs - placedAtMs) * speedPxPerMs,
                candidateWidthPx = widthPx,
                candidateSpeedPxPerMs = speedPxPerMs,
                lane = lane,
                viewport = viewport,
                danmakuNowMs = danmakuNowMs,
            ) ?: continue
            lane.add(
                insertion,
                DanmakuSlot(item, placedAtMs, widthPx, laneIndex, exitAtMs, speedPxPerMs, speedMultiplier),
            )
            return true
        }
        return false
    }

    private fun placeFixed(
        item: DanmakuItem,
        placedAtMs: Long,
        widthPx: Float,
        rows: MutableList<DanmakuSlot?>,
        danmakuNowMs: Long,
    ): Boolean {
        // 驻留期已经过了：这条没有还能呈现的时刻（回看窗口里的老顶/底弹幕即此类）
        if (danmakuNowMs - placedAtMs >= config.fixedDurationMs) return false
        val row = rows.indexOfFirst { it == null }
        if (row < 0) return false
        rows[row] = DanmakuSlot(
            item = item,
            placedAtMs = placedAtMs,
            widthPx = widthPx,
            lane = row,
            exitAtMs = placedAtMs + config.fixedDurationMs,
            speedPxPerMs = 0f,
            speedMultiplier = 1f,
        )
        return true
    }

    /**
     * 这条车道容不容得下候选弹幕：能则给出插入下标，不能则 null。
     *
     * [lane] 按左边界升序，所以二分找插入点、只需盯住左右邻居。
     * 判重不只是"此刻不重叠"，还要比"飞出左沿的时间" —— 后一条更快的话，
     * 此刻有空隙也不够，它会一路追尾上去。
     */
    private fun nonOverlappingIndex(
        candidateLeft: Float,
        candidateWidthPx: Float,
        candidateSpeedPxPerMs: Float,
        lane: List<DanmakuSlot>,
        viewport: DanmakuViewport,
        danmakuNowMs: Long,
    ): Int? {
        if (lane.isEmpty()) return 0
        val gap = viewport.minLaneGapPx
        fun leftOf(slot: DanmakuSlot): Float =
            viewport.widthPx - (danmakuNowMs - slot.placedAtMs) * slot.speedPxPerMs

        fun rightOf(slot: DanmakuSlot): Float = leftOf(slot) + slot.widthPx + gap

        // 前一条的右边缘与后一条的左边缘各自飞到轨道左沿所需时间：前者更久 ⇒ 会撞
        fun willClash(frontRightPx: Float, frontSpeed: Float, rearLeftPx: Float, rearSpeed: Float): Boolean =
            frontRightPx / frontSpeed > rearLeftPx / rearSpeed

        val last = lane.last()
        if (candidateLeft >= rightOf(last)) {
            return if (willClash(rightOf(last), last.speedPxPerMs, candidateLeft, candidateSpeedPxPerMs)) {
                null
            } else {
                lane.size
            }
        }

        val found = lane.binarySearch { leftOf(it).compareTo(candidateLeft) }
        val index = if (found < 0) -found - 1 else found
        if (index < lane.size && candidateLeft + candidateWidthPx + gap >= leftOf(lane[index])) return null
        if (index > 0 && candidateLeft <= rightOf(lane[index - 1])) return null

        lane.getOrNull(index - 1)?.let { front ->
            if (willClash(rightOf(front), front.speedPxPerMs, candidateLeft, candidateSpeedPxPerMs)) return null
        }
        lane.getOrNull(index)?.let { rear ->
            if (willClash(candidateLeft + candidateWidthPx + gap, candidateSpeedPxPerMs, leftOf(rear), rear.speedPxPerMs)) {
                return null
            }
        }
        return index
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
