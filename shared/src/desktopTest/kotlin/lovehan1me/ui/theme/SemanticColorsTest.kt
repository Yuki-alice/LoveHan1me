package lovehan1me.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 补充语义色（延迟色阶 success / warning 双档）的**不变量**断言。
 *
 * 与 [ThemeColorAuditTest] 的分工：那边"只打印、不断言"，因为比的是生成的槽位配色
 * （设计取值，会随种子 / materialkolor 版本漂移）；本测试比的是**手挑的四个常量**
 * 对**两个固定锚底色**的对比度 —— 六个字面量全是常量，对比度是它们的纯函数，
 * 断言不会 flaky，且能挡住"随手改一个 hex 把 AA 改没了"。
 *
 * 锚定的约定（改 `HanimeDefaults.Colors` 的 success / warning 取值前先过这里）：
 * 浅色档对浅底最坏情况 ≥ 4.5:1、深色档对深底最坏情况 ≥ 4.5:1（WCAG AA 正文）。
 * 锚底色取 M3 基线 Dialog 容器色（`surfaceContainerHigh`：浅 ≈ #E6E0E9 / 深 ≈ #2B2930）——
 * 延迟列表就渲染在 AlertDialog 里，这是它实际会遇到的底色。
 *
 * 重跑：`./gradlew :shared:desktopTest --tests "lovehan1me.ui.theme.SemanticColorsTest"`
 */
class SemanticColorsTest {

    private fun relLum(c: Color): Double {
        fun ch(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val l1 = maxOf(relLum(a), relLum(b))
        val l2 = minOf(relLum(a), relLum(b))
        return (l1 + 0.05) / (l2 + 0.05)
    }

    private fun assertAa(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue(r >= 4.5, "$name 对锚底色须 ≥4.5:1，实为 %.2f".format(r))
    }

    @Test
    fun lightTiersMeetAaOnLightAnchor() {
        val anchor = Color(0xFFE6E0E9)
        assertAa("successLight", HanimeDefaults.Colors.successLight, anchor)
        assertAa("warningLight", HanimeDefaults.Colors.warningLight, anchor)
    }

    @Test
    fun darkTiersMeetAaOnDarkAnchor() {
        val anchor = Color(0xFF2B2930)
        assertAa("successDark", HanimeDefaults.Colors.successDark, anchor)
        assertAa("warningDark", HanimeDefaults.Colors.warningDark, anchor)
    }
}
