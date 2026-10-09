package lovehan1me.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.toHct
import java.io.File
import kotlin.math.pow
import kotlin.test.Test

/**
 * 主题配色审计：**只打印、不断言**。
 *
 * 为什么不断言：配色是设计取值，不是不变量。给 `surfaceContainer` 的色度加硬阈值
 * 会变成 flaky 测试（换种子、换 materialkolor 版本、换 ColorSpec 都会动），
 * 与"性能基线只打印、不定线"同理。
 *
 * 它回答三个只有实测才能回答的问题：
 *
 * 1. 每个槽位**线上到底算出什么色** —— 直接调 [boardColorSchemeValue]（与 UI 同一条通路），
 *    所以输出就是真实落地值，不是另写一份推算；
 * 2. 表面家族是"灰"还是"有色" —— 由中性源（[PaletteStyle.Neutral] vs
 *    [PaletteStyle.TonalSpot]）决定的 HCT 色度差，肉眼无法量化；
 * 3. 对比度档位动了什么、没动什么，以及会不会把文字层级压平。
 *
 * 输出位置：`${java.io.tmpdir}/theme-audit.txt`。
 *
 * 重跑：`./gradlew :shared:desktopTest --tests "lovehan1me.ui.theme.ThemeColorAuditTest"`
 */
class ThemeColorAuditTest {

    private fun hex(c: Color): String = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private fun hct(c: Color): String {
        val h = c.toHct()
        return "H%.0f C%.1f T%.0f".format(h.hue, h.chroma, h.tone)
    }

    /** WCAG 相对亮度。 */
    private fun relLum(c: Color): Double {
        fun ch(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    /** WCAG 对比率。 */
    private fun ratio(a: Color, b: Color): Double {
        val l1 = maxOf(relLum(a), relLum(b))
        val l2 = minOf(relLum(a), relLum(b))
        return (l1 + 0.05) / (l2 + 0.05)
    }

    /** 绕开 UI 的单风格色算，只用于"假如换个中性源会怎样"的对照。 */
    private fun raw(seed: Long, isDark: Boolean, style: PaletteStyle, contrast: Double = 0.0) =
        dynamicColorScheme(
            seedColor = Color(seed),
            isDark = isDark,
            style = style,
            contrastLevel = contrast,
        )

    @Test
    fun audit() {
        val out = StringBuilder()

        out.appendLine("===== 1. 线上落地：每个槽位在浅色 / 标准对比度下的实际取值 =====")
        out.appendLine("导航栏 = ColorScheme.surfaceContainer（Bars.navigationContainerColor）")
        out.appendLine("页面底 = ColorScheme.surfaceContainerLowest（Colors.pageSurface）")
        out.appendLine("次要文字 = ColorScheme.onSurfaceVariant")
        out.appendLine()
        out.appendLine(
            "%-10s %-22s %-22s %-22s".format("槽位", "导航栏", "页面底", "次要文字")
        )
        for (board in ThemeBoard.entries) {
            // ⚠️ 系统槽位受平台取色影响，桌面/CI 上取不到，这里传 null 走回落分支。
            val s = boardColorSchemeValue(
                board = board,
                isDark = false,
                contrastLevel = 0.0,
                systemSeed = null,
            )
            out.appendLine(
                "%-10s %-22s %-22s %-22s".format(
                    board.title,
                    "${hex(s.surfaceContainer)} ${hct(s.surfaceContainer)}",
                    "${hex(s.surfaceContainerLowest)} ${hct(s.surfaceContainerLowest)}",
                    hex(s.onSurfaceVariant),
                )
            )
        }

        out.appendLine()
        out.appendLine("===== 2. 改色依据：中性源 Neutral 与 TonalSpot 的表面差异 =====")
        out.appendLine("Neutral = 改动前的中性源（色度约 2）；TonalSpot = 现状（色度约 6）。")
        for (board in ThemeBoard.entries.filter { it.neutralSurfaces }) {
            out.appendLine()
            out.appendLine("--- ${board.title} (#%06X) 浅色 ---".format(board.seedArgb))
            for ((tag, style) in listOf(
                "改前 Neutral " to PaletteStyle.Neutral,
                "现状 TonalSpot" to PaletteStyle.TonalSpot,
            )) {
                val s = raw(board.seedArgb, false, style)
                out.appendLine(
                    "  %s  surfaceContainer=%s(%s)  onSurfaceVariant=%s(%s)".format(
                        tag,
                        hex(s.surfaceContainer), hct(s.surfaceContainer),
                        hex(s.onSurfaceVariant), hct(s.onSurfaceVariant),
                    ),
                )
            }
        }

        out.appendLine()
        out.appendLine("===== 3. 对比度档位的实际效果（WCAG 对比率）=====")
        out.appendLine("参考：WCAG AA 正文 4.5、大字 3.0；AAA 正文 7.0。")
        val levels = listOf("standard" to 0.0, "high" to 1.0)
        val seeds = listOf(
            "Sakura 粉" to 0xFFF596AA,
            "Fuji 紫" to 0xFF7E57C2,
            "Sou 蓝" to 0xFF03A9F4,
        )
        for ((sname, seed) in seeds) {
            for (dark in listOf(false, true)) {
                out.appendLine("--- $sname ${if (dark) "深色" else "浅色"} ---")
                for ((lname, lv) in levels) {
                    val s = raw(seed, dark, PaletteStyle.TonalSpot, lv)
                    out.appendLine(
                        "  %-9s onSurface/surface=%5.2f  onSurfaceVariant/surface=%5.2f  onPrimaryContainer/primaryContainer=%5.2f".format(
                            lname,
                            ratio(s.onSurface, s.surface),
                            ratio(s.onSurfaceVariant, s.surface),
                            ratio(s.onPrimaryContainer, s.primaryContainer),
                        ),
                    )
                }
            }
        }

        out.appendLine()
        out.appendLine("===== 4. 对比度档位改变了哪些角色（浅色 Sakura）=====")
        for ((lname, lv) in levels) {
            val s = raw(0xFFF596AA, false, PaletteStyle.TonalSpot, lv)
            out.appendLine(
                "  %-9s surface=%s(%s)  surfaceContainer=%s(%s)  primary=%s(%s)".format(
                    lname, hex(s.surface), hct(s.surface),
                    hex(s.surfaceContainer), hct(s.surfaceContainer),
                    hex(s.primary), hct(s.primary),
                ),
            )
        }

        out.appendLine()
        out.appendLine("===== 5. 高档是否压平文字层级 =====")
        for (dark in listOf(false, true)) {
            out.appendLine("--- ${if (dark) "深色" else "浅色"} ---")
            for ((lname, lv) in levels) {
                val s = raw(0xFFF596AA, dark, PaletteStyle.TonalSpot, lv)
                out.appendLine(
                    "  %-9s onSurface=%s(%s)  onSurfaceVariant=%s(%s)  同色=%s".format(
                        lname,
                        hex(s.onSurface), hct(s.onSurface),
                        hex(s.onSurfaceVariant), hct(s.onSurfaceVariant),
                        if (s.onSurface == s.onSurfaceVariant) "是" else "否",
                    ),
                )
            }
        }

        out.appendLine()
        out.appendLine("===== 6. 补充语义色（固定常量，不随槽位色算）=====")
        out.appendLine("延迟指示用 success / warning；硬断言在 SemanticColorsTest（这里只打印实测值）。")
        val semanticChecks = listOf(
            "successLight vs 浅底锚" to (HanimeDefaults.Colors.successLight to Color(0xFFE6E0E9)),
            "warningLight vs 浅底锚" to (HanimeDefaults.Colors.warningLight to Color(0xFFE6E0E9)),
            "successDark vs 深底锚" to (HanimeDefaults.Colors.successDark to Color(0xFF2B2930)),
            "warningDark vs 深底锚" to (HanimeDefaults.Colors.warningDark to Color(0xFF2B2930)),
        )
        for ((label, pair) in semanticChecks) {
            out.appendLine(
                "  %-24s %s  对比率=%5.2f".format(label, hex(pair.first), ratio(pair.first, pair.second)),
            )
        }

        val file = File(System.getProperty("java.io.tmpdir"), "theme-audit.txt")
        file.writeText(out.toString())
        println("theme audit written to ${file.absolutePath}")
    }
}
