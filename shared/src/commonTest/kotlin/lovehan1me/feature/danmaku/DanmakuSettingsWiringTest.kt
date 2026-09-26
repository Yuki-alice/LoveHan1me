package lovehan1me.feature.danmaku

import lovehan1me.core.domain.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 设置 → 观感映射的接线测试（Gate3-P1 后留守 :shared）。
// 纯折算测试已随 DanmakuRenderOptions 进 :video:contract；这里只钉"同源"：
// 默认值必须只有一个来源，四个字段必须各自流向对应的观感量。
class DanmakuSettingsWiringTest {

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
