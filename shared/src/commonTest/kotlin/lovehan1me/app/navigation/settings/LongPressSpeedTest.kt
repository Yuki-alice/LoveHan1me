package lovehan1me.app.navigation.settings

import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.normalizeLongPressSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 长按速播：选项表 + 读侧钳制 + `%.1f` 等价格式化。
 *
 * 三者任一漂移的用户可见症状：对话框默认档不选中、历史 1x 读出后
 * 引擎按旧倍率长按快进、行值/摘要重新变回 `%.1f倍`。
 */
class LongPressSpeedTest {

    @Test
    fun `选项表为 2_0 到 5_0 步进 0_5 共七档`() {
        assertEquals(
            listOf(2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f),
            LONG_PRESS_SPEED_CHOICES,
        )
    }

    /**
     * 选项值必须与存储值的 `toString()` 逐字符相同
     * （ChoiceDialog 用 `selectedValue == value` 判选中）。
     */
    @Test
    fun `选项值与存储值 toString 逐字符一致`() {
        assertEquals(
            listOf("2.0", "2.5", "3.0", "3.5", "4.0", "4.5", "5.0"),
            LONG_PRESS_SPEED_CHOICES.map { it.toString() },
        )
    }

    @Test
    fun `默认值 3f 在选项表内`() {
        assertTrue(AppSettings().longPressSpeedTime in LONG_PRESS_SPEED_CHOICES)
    }

    @Test
    fun `读侧钳制边界`() {
        assertEquals(2f, normalizeLongPressSpeed(1.9f), "低于下界落到 2.0")
        assertEquals(2f, normalizeLongPressSpeed(1f), "历史 1x 落到 2.0")
        assertEquals(5f, normalizeLongPressSpeed(5.1f), "高于上界落到 5.0")
        assertEquals(3f, normalizeLongPressSpeed(3f), "默认值原样")
        assertEquals(3.5f, normalizeLongPressSpeed(3.5f), "表内值原样")
    }

    @Test
    fun `formatSpeedTimes 与一位小数格式一致`() {
        assertEquals("3.0", formatSpeedTimes(3f))
        assertEquals("2.5", formatSpeedTimes(2.5f))
        assertEquals("5.0", formatSpeedTimes(5f))
        assertEquals("2.0", formatSpeedTimes(2f))
        assertEquals("4.5", formatSpeedTimes(4.5f))
        assertEquals("2.8", formatSpeedTimes(2.8f))
    }
}
