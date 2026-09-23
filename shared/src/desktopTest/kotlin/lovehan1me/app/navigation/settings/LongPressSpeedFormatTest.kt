package lovehan1me.app.navigation.settings

import kotlinx.coroutines.runBlocking
import lovehan1me.Res
import lovehan1me.d_speed_times
import lovehan1me.long_press_speed_summary
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 最小复现（模板改前应红）：CMP 的 `getString`/`stringResource` 只替换 `%n$s`/`%n$d`
 * （`replaceWithArgs` 正则），printf 式 `%.1f` 不匹配 → 原样透出。
 * 症状与截图一致：行值显示 `%.1f倍`，摘要显示 `当前速度的 %.1f倍`。
 */
class LongPressSpeedFormatTest {

    @Test
    fun `d_speed_times 应被格式化而非原样透出格式串`() = runBlocking {
        val label = getString(Res.string.d_speed_times, 3f)
        assertFalse(label.contains("%"), "格式串原样透出: $label")
        assertTrue(label.contains("3.0"), "缺少倍率数值: $label")
    }

    @Test
    fun `摘要吃进已格式化的标签而非格式串`() = runBlocking {
        val label = getString(Res.string.d_speed_times, 3f)
        val summary = getString(Res.string.long_press_speed_summary, label)
        assertFalse(summary.contains("%"), "摘要含格式串: $summary")
        assertTrue(summary.contains("3.0"), summary)
    }
}
