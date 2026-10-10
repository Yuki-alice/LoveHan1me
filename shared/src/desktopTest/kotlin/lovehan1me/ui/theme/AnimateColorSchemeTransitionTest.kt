package lovehan1me.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Image
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 主题过渡守卫：钉住 [animateColorScheme] 两条路径的正确性（2026-10-10，P0 回归修复验收）。
 *
 * 背景：`a07bba4` 把 48 个 animateColorAsState 重写为"单进度动画 + lerp"，但起点捕获
 * 公式把端点写成了新目标 —— 静置后切换塌缩为硬切（0 过渡帧）、打断跳变 123 通道级
 * （虚拟时钟逐帧探针实测，正/负对照见 git 历史）。本守卫把两条路径永久钉死：
 *
 * 1. **静置后切换**（最常见路径）：首帧**不得**已是终点色（反硬切哨兵）、过渡中段
 *    ≥ 6 个中间帧（动画真实推进）、尾帧到达终点。
 * 2. **过渡中打断**：切换前后相邻两帧的最大通道差 ≤ [JUMP_LIMIT]（16ms 步进在
 *    tween(300) 下的峰值步进约 30，回归时的跳变是 123 —— 上限把两者干净分开）。
 *
 * 手法：虚拟时钟 + [ImageComposeScene] 离屏采样（场景 = 纯色 Box，采样背景色即
 * scheme.background）。显式传 `tween(300)` 隔离默认 spec（spec 档位的选择属 M3E 语义，
 * 由 KDoc 与评审记录约束，不在本守卫范围）。全虚拟时钟，并行跑确定性复现。
 */
class AnimateColorSchemeTransitionTest {

    @Test
    fun `静置后切换_逐帧过渡不硬切`() {
        val light = lightColorScheme(background = Color(0xFFF8F8F8))
        val dark = darkColorScheme(background = Color(0xFF121212))
        var target by mutableStateOf(light)

        val scene = ImageComposeScene(40, 40, density = Density(1f)) {
            val scheme = animateColorScheme(target, tween(300))
            Box(Modifier.fillMaxSize().background(scheme.background))
        }
        try {
            var t = 0L
            fun step(): Int {
                t += 16_000_000L
                return sample(scene.render(t))
            }

            // 静置足量帧：让首段（from == to 的常量段）完成，progress 归 1。
            repeat(25) { step() }
            val before = step()
            assertTrue(similar(before, LIGHT_BG), "静置帧应为浅色底（实测 ${hex(before)}）：场景混入了别的动画？")

            target = dark
            val seq = MutableList(24) { step() }
            val first = seq.first()
            val last = seq.last()
            val intermediates = seq.count { !similar(it, LIGHT_BG) && !similar(it, DARK_BG) }

            println(
                "ANIMATE_SCHEME_OUT: idle-switch first=${hex(first)} last=${hex(last)} " +
                    "intermediates=$intermediates seq=${seq.joinToString(" ") { hex(it) }}",
            )

            // 反硬切哨兵：起点捕获公式一旦又用错端点，首帧会直接跳到终点色。
            assertTrue(
                !similar(first, DARK_BG),
                "切换后首帧已是终点色（${hex(first)}）= 硬切：起点捕获公式用错端点（新目标）了？",
            )
            assertTrue(
                intermediates >= 6,
                "过渡中间帧仅 $intermediates 个（期望 ≥6）：动画没有真实推进（硬切或被反复重启）？",
            )
            assertTrue(similar(last, DARK_BG), "过渡未到达终点（实测 ${hex(last)}）：动画被冻结在中途？")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `过渡中打断_以当前显示值为起点不跳变`() {
        val a = lightColorScheme(background = Color(0xFFF8F8F8))
        val b = darkColorScheme(background = Color(0xFF121212))
        val c = lightColorScheme(background = Color(0xFF3355FF))
        var target by mutableStateOf(a)

        val scene = ImageComposeScene(40, 40, density = Density(1f)) {
            val scheme = animateColorScheme(target, tween(300))
            Box(Modifier.fillMaxSize().background(scheme.background))
        }
        try {
            var t = 0L
            fun step(): Int {
                t += 16_000_000L
                return sample(scene.render(t))
            }

            repeat(25) { step() }
            target = b
            val midSeq = MutableList(8) { step() } // b 过渡进行中（约 128ms / 300ms）
            val mid = midSeq.last()
            assertTrue(
                !similar(mid, LIGHT_BG) && !similar(mid, DARK_BG),
                "打断前应处于过渡中段（实测 ${hex(mid)}）：前置动画没跑起来，本用例失去前提",
            )

            target = c
            val afterSeq = MutableList(24) { step() }
            val jump = maxChannelDelta(mid, afterSeq.first())

            println(
                "ANIMATE_SCHEME_OUT: interrupt mid=${hex(mid)} firstAfter=${hex(afterSeq.first())} " +
                    "last=${hex(afterSeq.last())} jump=$jump " +
                    "after=${afterSeq.joinToString(" ") { hex(it) }}",
            )

            assertTrue(
                jump <= JUMP_LIMIT,
                "打断跳变 $jump（上限 $JUMP_LIMIT）：新起点不是当前显示值 —— 起点捕获公式用错端点了？",
            )
            assertTrue(
                similar(afterSeq.last(), BLUE_BG),
                "打断后未到达新终点（实测 ${hex(afterSeq.last())}）：动画被打断后卡死？",
            )
        } finally {
            scene.close()
        }
    }

    // —— 采样与判读工具 ——

    private fun sample(img: Image): Int {
        val px = img.peekPixels() ?: error("peekPixels() 返回 null")
        return px.getColor(20, 20) and 0xFFFFFF
    }

    private fun similar(c: Int, ref: Int): Boolean = maxChannelDelta(c, ref) <= 2

    private fun maxChannelDelta(x: Int, y: Int): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        return maxOf(
            abs(ch(x, 16) - ch(y, 16)),
            abs(ch(x, 8) - ch(y, 8)),
            abs(ch(x, 0) - ch(y, 0)),
        )
    }

    private fun hex(argb: Int): String = "%06X".format(argb)

    private companion object {
        // 注意：参考色统一 6 位十六进制 —— 采样值已 and 0xFFFFFF；
        // Kotlin 里 ≥ 0x80000000 的字面量是 Long，8 位写法会挂 Int 形参。
        const val LIGHT_BG = 0xF8F8F8
        const val DARK_BG = 0x121212
        const val BLUE_BG = 0x3355FF

        /** 16ms 步进下 tween(300) 的单步峰值通道变化约 30；回归实测跳变 123。上限取两者中点。 */
        const val JUMP_LIMIT = 48
    }
}
