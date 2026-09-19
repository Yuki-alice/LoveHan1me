package lovehan1me.feature.danmaku

import lovehan1me.core.domain.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 设置页那四个观感量到"引擎听得懂的单位"之间的折算。
 *
 * 这一层是整个弹幕功能里最容易**悄悄漂移**的地方：滑杆摆的是百分比，
 * 引擎吃的是毫秒与比例，中间只有一处换算（[DanmakuRenderOptions] 的派生属性）。
 * 一旦这处换算写错或被绕过，症状是"拖了没反应"或"拖到最慢反而最快"，
 * 而两边各自的单测都会绿。
 */
class DanmakuRenderOptionsTest {

    // ---------- 速度 ----------

    @Test
    fun `标准速度就是 10 秒走完一屏`() {
        assertEquals(10_000L, danmakuTraverseMs(100))
    }

    /**
     * 百分比是**倍率**不是时长：数值越大越快，所以换算取反比。
     * 写成正比的话"调快"会变成"飘得更慢"，而且不会有任何报错。
     */
    @Test
    fun `速度百分比与所需时长成反比`() {
        assertEquals(20_000L, danmakuTraverseMs(50))
        assertEquals(5_000L, danmakuTraverseMs(200))
    }

    /** 盘上可能是历史版本写下的 0 或负数：除零会直接崩，负数会把方向翻掉。 */
    @Test
    fun `越界的速度值夹到最近可用档`() {
        assertEquals(20_000L, danmakuTraverseMs(0))
        assertEquals(20_000L, danmakuTraverseMs(-300))
        assertEquals(5_000L, danmakuTraverseMs(9_999))
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
        assertEquals(5_000L, options.scrollTraverseMs)
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
        // 150% ⇒ 10000×100/150 = 6666.66…，整数除法取 6666
        assertEquals(6_666L, options.scrollTraverseMs)
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
        assertEquals(danmakuTraverseMs(DANMAKU_SPEED_RANGE.last), endpoints.scrollTraverseMs)

        val opposite = DanmakuRenderOptions(
            fontSizeSp = DANMAKU_FONT_SIZE_RANGE.first,
            opacityPercent = DANMAKU_OPACITY_RANGE.last,
            displayAreaPercent = DANMAKU_DISPLAY_AREA_RANGE.first,
            speedPercent = DANMAKU_SPEED_RANGE.first,
        )
        assertEquals(DANMAKU_FONT_SIZE_RANGE.first, opposite.clampedFontSizeSp)
        assertClose(1f, opposite.opacity)
        assertClose(0.25f, opposite.displayAreaRatio)
        assertEquals(danmakuTraverseMs(DANMAKU_SPEED_RANGE.first), opposite.scrollTraverseMs)
    }

    // ---------- 与设置链路的同源 ----------

    /**
     * 默认值必须**只有一个来源**。两处分别写死 18/80/50/100 时，
     * 改了 [AppSettings] 的那组只会让设置页的滑杆初始位置和实际观感对不上。
     */
    @Test
    fun `默认观感值与设置默认值同源`() {
        assertEquals(DanmakuRenderOptions(), AppSettings().danmakuRenderOptions())
    }

    @Test
    fun `设置里的四个字段各自流向对应的观感量`() {
        val options = AppSettings(
            danmakuFontSizeSp = 24,
            danmakuOpacityPercent = 45,
            danmakuDisplayAreaPercent = 70,
            danmakuSpeedPercent = 60,
        ).danmakuRenderOptions()
        assertEquals(24, options.clampedFontSizeSp)
        assertClose(0.45f, options.opacity)
        assertClose(0.7f, options.displayAreaRatio)
        assertEquals(16_666L, options.scrollTraverseMs)
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
