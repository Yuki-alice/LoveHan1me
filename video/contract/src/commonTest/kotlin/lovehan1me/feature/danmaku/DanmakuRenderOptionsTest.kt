package lovehan1me.feature.danmaku

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 设置页那四个观感量到"引擎听得懂的单位"之间的折算。
 *
 * 这一层是整个弹幕功能里最容易**悄悄漂移**的地方：滑杆摆的是百分比，
 * 引擎吃的是 px/s 与比例，中间只有一处换算（[DanmakuRenderOptions] 与
 * [danmakuBaseSpeedPxPerSecond]）。一旦这处换算写错或被绕过，症状是"拖了没反应"
 * 或"拖到最慢反而最快"，而两边各自的单测都会绿。
 */
class DanmakuRenderOptionsTest {

    // ---------- 速度 ----------

    @Test
    fun `标准速度就是基准弹幕每秒 88 物理像素`() {
        assertEquals(88f, danmakuBaseSpeedPxPerSecond(100, 1f), 0.01f)
    }

    /**
     * 百分比是**倍率**：数值越大越快。写成"乘 100 除百分比"那种反比就会
     * 让"调快"变成"飘得更慢"，而且不会有任何报错。
     */
    @Test
    fun `速度百分比与像素速度成正比`() {
        assertEquals(44f, danmakuBaseSpeedPxPerSecond(50, 1f), 0.01f)
        assertEquals(176f, danmakuBaseSpeedPxPerSecond(200, 1f), 0.01f)
    }

    /**
     * 基准速度是物理速度：手机上密度 3，px/s 也要跟着涨三倍，
     * 否则同一份设置在手机上会慢成桌面上看着那样。
     */
    @Test
    fun `密度把物理速度换算成像素速度`() {
        assertEquals(264f, danmakuBaseSpeedPxPerSecond(100, 3f), 0.01f)
        assertEquals(528f, danmakuBaseSpeedPxPerSecond(200, 3f), 0.01f)
    }

    /** 盘上可能是历史版本写下的 0 或负数：除零与反向都在这一步收敛掉。 */
    @Test
    fun `越界的速度值夹到最近可用档`() {
        assertEquals(danmakuBaseSpeedPxPerSecond(DANMAKU_SPEED_MIN_PERCENT, 1f),
            danmakuBaseSpeedPxPerSecond(0, 1f), 0.01f)
        assertEquals(danmakuBaseSpeedPxPerSecond(DANMAKU_SPEED_MIN_PERCENT, 1f),
            danmakuBaseSpeedPxPerSecond(-300, 1f), 0.01f)
        assertEquals(danmakuBaseSpeedPxPerSecond(DANMAKU_SPEED_MAX_PERCENT, 1f),
            danmakuBaseSpeedPxPerSecond(9_999, 1f), 0.01f)
    }

    /** 密度小于 1 会把弹幕拖成慢动作：夹到 1，"再小的屏也不许比桌面慢"。 */
    @Test
    fun `异常低的密度不会把速度压到标准以下`() {
        assertEquals(88f, danmakuBaseSpeedPxPerSecond(100, 0.5f), 0.01f)
    }

    // ---------- 夹取 ----------

    @Test
    fun `磁盘上的越界观感值进引擎前一律夹回`() {
        val options = DanmakuRenderOptions(
            fontSizeSp = 0,
            opacityPercent = 150,
            displayAreaPercent = 1,
            speedPercent = 999,
        )
        assertEquals(DANMAKU_FONT_SIZE_RANGE.first, options.clampedFontSizeSp)
        assertClose(1f, options.opacity, "超出上限的不透明度应夹成完全不透明")
        // 显示区域下限 25%：再小就只剩一两行车道，弹幕基本等于不显示
        assertClose(0.25f, options.displayAreaRatio)
        assertEquals(
            danmakuBaseSpeedPxPerSecond(DANMAKU_SPEED_MAX_PERCENT, 1f),
            options.baseSpeedPxPerSecond(1f),
            0.01f,
        )
    }

    @Test
    fun `区间内的值原样通过不被偷偷改写`() {
        val options = DanmakuRenderOptions(
            fontSizeSp = 20,
            opacityPercent = 65,
            displayAreaPercent = 80,
            speedPercent = 150,
        )
        assertEquals(20, options.clampedFontSizeSp)
        assertClose(0.65f, options.opacity)
        assertClose(0.8f, options.displayAreaRatio)
        // 150% ⇒ 88×1.5 = 132 px/s
        assertEquals(132f, options.baseSpeedPxPerSecond(1f), 0.01f)
    }

    /**
     * 设置页与绘制层共用区间 ⇒ 滑杆能拖到的值不可能被绘制层再夹一次。
     * 端点各测一次：把某一侧的区间改窄，这里就会红。
     */
    @Test
    fun `滑杆区间端点折算出来就是可用极值`() {
        val endpoints = DanmakuRenderOptions(
            fontSizeSp = DANMAKU_FONT_SIZE_RANGE.last,
            opacityPercent = DANMAKU_OPACITY_RANGE.first,
            displayAreaPercent = DANMAKU_DISPLAY_AREA_RANGE.last,
            speedPercent = DANMAKU_SPEED_RANGE.last,
        )
        assertEquals(DANMAKU_FONT_SIZE_RANGE.last, endpoints.clampedFontSizeSp)
        assertClose(0.3f, endpoints.opacity)
        assertClose(1f, endpoints.displayAreaRatio)
        assertEquals(
            danmakuBaseSpeedPxPerSecond(DANMAKU_SPEED_RANGE.last, 2f),
            endpoints.baseSpeedPxPerSecond(2f),
            0.01f,
        )

        val opposite = DanmakuRenderOptions(
            fontSizeSp = DANMAKU_FONT_SIZE_RANGE.first,
            opacityPercent = DANMAKU_OPACITY_RANGE.last,
            displayAreaPercent = DANMAKU_DISPLAY_AREA_RANGE.first,
            speedPercent = DANMAKU_SPEED_RANGE.first,
        )
        assertEquals(DANMAKU_FONT_SIZE_RANGE.first, opposite.clampedFontSizeSp)
        assertClose(1f, opposite.opacity)
        // 默认显示区域就是区间下限：一屏四分之一
        assertClose(0.25f, opposite.displayAreaRatio)
        assertEquals(DANMAKU_DEFAULT_DISPLAY_AREA_PERCENT, opposite.displayAreaPercent)
        assertEquals(
            danmakuBaseSpeedPxPerSecond(DANMAKU_SPEED_RANGE.first, 2f),
            opposite.baseSpeedPxPerSecond(2f),
            0.01f,
        )
    }

    private fun assertClose(
        expected: Float,
        actual: Float,
        hint: String = "",
        tolerance: Float = 0.0001f,
    ) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= tolerance,
            "$hint 期望 $expected，实际 $actual（容差 $tolerance）",
        )
    }
}
