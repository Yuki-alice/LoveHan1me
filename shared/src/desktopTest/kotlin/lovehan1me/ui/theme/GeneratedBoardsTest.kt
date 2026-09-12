package lovehan1me.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 预生成色表的数值体检（P0/G2 验收的数据版，体检页是人眼版）。
 *
 * 冻结基线（2026-09-12 实测）：
 * - 卡片（containerHigh）/页底分离度 ≥ 1.12（旧 surfaceBright 浅色为 1.000）；
 * - primary 配对 ≥ 4.5、body 配对 ≥ 7（AA 线）；
 * - B′ 槽表面须近中性（由 cardSep 上限侧面约束，不在此断言，靠体检页人眼）。
 */
class GeneratedBoardsTest {

    private fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
        val l1 = a.luminance()
        val l2 = b.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    @Test
    fun boardMetricsMeetG2() {
        val boards = listOf("sakura", "take", "sou", "yuzu", "midnight", "nord", "mono", "system")
        for (id in boards) {
            for (dark in listOf(false, true)) {
                for (contrast in listOf(0.0, 0.5, 1.0)) {
                    val s = GeneratedThemeBoards.scheme(id, dark, contrast)
                    val cardSep = ratio(s.surfaceContainerHigh, s.surface)
                    val pri = ratio(s.onPrimary, s.primary)
                    val body = ratio(s.onSurface, s.surface)
                    println(
                        "BOARD $id dark=$dark c=$contrast cardSep=${"%.3f".format(cardSep)} " +
                            "primary=${"%.2f".format(pri)} body=${"%.2f".format(body)}",
                    )
                    assertTrue(cardSep >= 1.12f, "$id dark=$dark c=$contrast cardSep=$cardSep")
                    assertTrue(pri >= 4.5f, "$id dark=$dark c=$contrast primary=$pri")
                    assertTrue(body >= 7f, "$id dark=$dark c=$contrast body=$body")
                }
            }
        }
    }
}
