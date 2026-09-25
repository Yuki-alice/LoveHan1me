package lovehan1me.feature.danmaku

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLocation
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

// 弹幕绘制基线（Gate4-3：先有数，不定线）。
//
// 背景：Gate3-10 的"开超分卡顿 + 弹幕抖"缺的从来不是优化点子，而是"各档位 ×
// 各分辨率到底多少毫秒"的数据。本测试在 headless 离屏渲染里连渲 60 帧，
// 打印总耗时/均值/p95/最大值 —— 机器相关故**不断言时间**（CI 机器与开发者机器
// 差一个数量级是常态，定了线就是 flaky），只断言"60 帧真跑完了"。
// 用法：改弹幕/超分/渲染管线前后各跑一次，对比打印值；真机帧时间另见 Gate3 §4 QA 清单。
//
// 跑法：`:player:desktopTest --tests "lovehan1me.feature.danmaku.DanmakuFrameBaselineTest" --offline`
class DanmakuFrameBaselineTest {

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

    @Test
    fun `dense弹幕60帧绘制基线`() {
        // 密集段：同屏常驻 20+ 条，碰撞与栅格化全开，压出绘制上限附近的值。
        val items = List(30) { index ->
            DanmakuItem(
                id = index.toLong(),
                playTimeMillis = 1_000L + index * 100L,
                text = "基线弹幕第${index}条：长度适中看测量开销",
                color = 0xFFFFFFFF.toInt(),
                location = DanmakuLocation.SCROLL,
            )
        }
        val driver = FakeDriver(items)
        val scene = ImageComposeScene(
            width = 1_280,
            height = 720,
            density = Density(2f),
            content = {
                Box(modifier = Modifier.fillMaxSize()) {
                    DanmakuLayer(driver = driver, modifier = Modifier.fillMaxSize())
                }
            },
        )
        try {
            val frames = 60
            val costs = LongArray(frames)
            repeat(frames) { frame ->
                val start = System.nanoTime()
                scene.render(1_000_000_000L + frame * 16_666_666L).close()
                costs[frame] = System.nanoTime() - start
            }
            val sorted = costs.sorted()
            val avgMs = costs.average() / 1_000_000.0
            val p95Ms = sorted[(frames * 0.95).toInt()].toDouble() / 1_000_000.0
            val maxMs = sorted.last().toDouble() / 1_000_000.0
            println("[BASELINE] danmaku-720p-dense-60f avg=${"%.2f".format(avgMs)}ms " +
                "p95=${"%.2f".format(p95Ms)}ms max=${"%.2f".format(maxMs)}ms " +
                "emitted=${driver.engine.emittedCount}")
            assertTrue(driver.engine.emittedCount > 0, "一帧都没发射，基线无效")
        } finally {
            scene.close()
        }
    }
}
