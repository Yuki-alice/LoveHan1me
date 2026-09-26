package lovehan1me.feature.danmaku

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 帧时刻平滑的性质回归。
 *
 * 断言的是**性质**不是曲线形状：平滑算法一旦调参就会偏离任何写死的输出数列，
 * 而这几条性质是它存在的前提 —— 不脱钩、不跑飞、不制造新抖动、不倒流。
 *
 * 跑法：`:video:ui:desktopTest --tests "lovehan1me.feature.danmaku.FrameTimeSmootherTest" --offline`
 */
class FrameTimeSmootherTest {

    private fun smoother() = FrameTimeSmoother()

    /** 按给定间隔表生成帧时刻（第一项是首帧的绝对时刻）。 */
    private fun frames(deltas: List<Long>): List<Long> {
        var t = 1_000_000_000L
        val out = mutableListOf(t)
        deltas.forEach { t += it; out += t }
        return out
    }

    private fun smoothedOf(frames: List<Long>): List<Long> {
        val smoother = smoother()
        return frames.map { smoother.smooth(it) }
    }

    private fun deltas(frames: List<Long>) =
        (1 until frames.size).map { frames[it] - frames[it - 1] }

    @Test
    fun `前两帧原样透传_启动期不引入滞后`() {
        val frames = frames(listOf(FRAME, FRAME, FRAME))
        val smoothed = smoothedOf(frames)
        assertEquals(frames[0], smoothed[0], "首帧被改动了：起始锚点必须与输入一致")
        assertEquals(frames[1], smoothed[1], "第二帧被改动了：此时还没有间隔可估计")
    }

    @Test
    fun `稳定节拍下完全跟随_不产生稳态偏差`() {
        val frames = frames(List(500) { FRAME })
        val smoothed = smoothedOf(frames)
        assertEquals(
            frames.last(),
            smoothed.last(),
            "500 帧稳定节拍后仍与真实时刻差 " +
                "${(frames.last() - smoothed.last()) / 1_000_000}ms：时钟与视频脱钩了",
        )
    }

    @Test
    fun `丢一帧时那一跳被摊开_且不制造反面跳变`() {
        // 第 21 帧本该 16.7ms，实际隔了 33.3ms（掉帧）。这一跳正是屏上"顿一下"的来源。
        val frames = frames(List(20) { FRAME } + (2 * FRAME) + List(30) { FRAME })
        val smoothed = smoothedOf(frames)

        val rawJump = deltas(frames)[20]
        val smoothedJump = deltas(smoothed)[20]
        assertTrue(
            smoothedJump < rawJump,
            "掉帧那一跳没被摊开：输入 $rawJump ns，输出 $smoothedJump ns",
        )
        // 摊开的代价是随后几帧略快一点（收敛回原频率），但必须**收敛**：
        // 若每帧都比输入快一点，那是在持续放大，不是平滑。
        val tail = deltas(smoothed).drop(21)
        assertTrue(
            abs(tail.last() - FRAME) < 2_000_000L,
            "掉帧之后节拍没回到原频率：末帧仍比输入快 ${(tail.last() - FRAME) / 1_000_000}ms",
        )
        assertTrue(
            tail.none { it > smoothedJump },
            "掉帧之后的每帧间隔反而比那一跳还大：修正量在放大而不是收敛",
        )
    }

    @Test
    fun `与真实时刻的偏差不超过一帧预算`() {
        // 混合负载：稳定段 + 随机抖动 + 掉帧。逐帧核对硬上界。
        val deltas = buildList {
            repeat(30) { add(FRAME) }
            add(2 * FRAME)
            repeat(30) { add(FRAME + if (it % 3 == 0) 4_000_000L else -2_000_000L) }
            add(3 * FRAME)
            repeat(30) { add(FRAME) }
        }
        val frames = frames(deltas)
        val smoothed = smoothedOf(frames)
        frames.indices.forEach { index ->
            val deviation = smoothed[index] - frames[index]
            assertTrue(
                deviation in -BUDGET..BUDGET,
                "第 $index 帧偏差 ${deviation / 1_000_000}ms 超出 ±${BUDGET / 1_000_000}ms 预算",
            )
        }
    }

    @Test
    fun `长卡顿当帧跟上而不是滑动收敛`() {
        val frames = frames(List(50) { FRAME } + 500_000_000L + List(50) { FRAME })
        val smoothed = smoothedOf(frames)
        val stallIndex = 51
        assertEquals(
            frames[stallIndex],
            smoothed[stallIndex],
            "窗口最小化那种长卡顿被当成抖动平滑了：弹幕会慢半拍才追上",
        )
        // 跟上之后不许拿那个大跳当间隔用（否则输出每帧往前跑 500ms）
        assertEquals(
            frames.last(),
            smoothed.last(),
            "长卡顿之后节拍没锁回去：输出与真实时刻跑散了",
        )
    }

    @Test
    fun `输出单调不倒流`() {
        val frames = frames(
            List(20) { FRAME } +
                2_000_000L + // 比一帧短得多的一次调度提前
                FRAME +
                List(20) { FRAME + (if (it % 2 == 0) 3_000_000L else -1_000_000L) },
        )
        val smoothed = smoothedOf(frames)
        smoothed.zipWithNext { previous, next ->
            assertTrue(next >= previous, "帧时刻倒流了：$previous -> $next")
        }
    }

    private companion object {
        /** 60Hz 一帧。 */
        const val FRAME = 16_666_666L

        /** 允许领先/滞后真实帧时刻的上限：一帧。 */
        const val BUDGET = 16_666_666L
    }
}