package lovehan1me.feature.danmaku

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 播放稳态不重组回归（Gate3-P2，钉 f4c2e4a 那类事故）。
//
// 那次事故的根因是每帧重建 movableContentOf：落墨数完全正常（字照样画），
// 但组合次数随帧线性增长，顺带把 Skia 撑崩。落墨断言抓不到它，
// 只能数字符——稳态播放时组合树每帧重组就是 bug。
//
// 跑法：`:video:ui:desktopTest --tests "lovehan1me.feature.danmaku.DanmakuLayerSteadyTest" --offline`
class DanmakuLayerSteadyTest {

    // 假驱动：时钟由 render() 的帧时刻推进，不依赖播放快照与数据源。
    // 语义与 DanmakuSession.advance 一致（返回本次位置，null 即不画）。
    private class FakeDriver(items: List<DanmakuItem>) : DanmakuFrameDriver {
        override val engine = DanmakuEngine().also {
            if (items.isNotEmpty()) it.setItems(items)
        }

        override fun advance(
            frameNanos: Long,
            viewport: DanmakuViewport,
            measureWidth: (DanmakuItem) -> Float,
        ): Long? {
            val nowMs = frameNanos / 1_000_000L
            engine.tick(nowMs, viewport, measureWidth)
            return nowMs
        }
    }

    private fun item(id: Int, timeMs: Long, text: String) = DanmakuItem(
        id = id.toLong(),
        playTimeMillis = timeMs,
        text = text,
        color = 0xFFFFFFFF.toInt(),
        location = DanmakuLocation.SCROLL,
    )

    // 渲 N 帧，数字符：父作用域的组合次数。
    // frameNanos 是 DanmakuLayer 内部状态，只触发重绘不触发重组；
    // 稳态下父树组合恰好一次（首帧）。每帧重建 movableContentOf 那类写法
    // 会让这个数跟着帧数走，测试即红。
    private fun countCompositions(driver: DanmakuFrameDriver, frames: Int): Int {
        var compositions = 0
        val scene = ImageComposeScene(
            width = 1_280,
            height = 720,
            density = Density(2f),
            content = {
                SideEffect { compositions++ }
                // 必须给尺寸：零尺寸视口下引擎 tick 直接返回，测不到发射路径。
                Box(modifier = Modifier.fillMaxSize()) {
                    DanmakuLayer(driver = driver, modifier = Modifier.fillMaxSize())
                }
            },
        )
        try {
            repeat(frames) { frame ->
                scene.render(1_000_000_000L + frame * 16_666_666L).close()
            }
        } finally {
            scene.close()
        }
        return compositions
    }

    @Test
    fun `空屏连渲30帧_组合树只组合一次`() {
        val compositions = countCompositions(FakeDriver(emptyList()), frames = 30)
        assertEquals(1, compositions, "稳态空屏重组了 $compositions 次：帧循环把状态漏到了组合期")
    }

    @Test
    fun `有弹幕连渲30帧_组合树只组合一次`() {
        // 时刻对齐测试时钟（首帧即 1000ms）：早于此时钟的条目会被引擎按迟到丢弃，
        // 一条都发不出去（行为正确，但测不到发射路径）。
        val driver = FakeDriver(
            listOf(
                item(1, 1_000L, "第一条弹幕"),
                item(2, 1_100L, "第二条弹幕稍微长一点看换行"),
                item(3, 1_200L, "第三条"),
            ),
        )
        val compositions = countCompositions(driver, frames = 30)
        assertEquals(1, compositions, "有弹幕时重组了 $compositions 次：发射/消退走了组合而非绘制")
        assertTrue(driver.engine.emittedCount > 0, "30 帧里一条也没发射，测试本身没跑起来")
    }
}
