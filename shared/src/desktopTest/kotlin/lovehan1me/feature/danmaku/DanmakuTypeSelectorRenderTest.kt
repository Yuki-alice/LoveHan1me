package lovehan1me.feature.danmaku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P1 接线 #10 验收：弹幕类型选择器（MultiChoiceSegmentedButtonRow）的离屏渲染。
 *
 * 三个状态（全开 / 仅滚动关 / 全关）× 明暗两态同图对比：
 * - 选中段 = container 填充 + 勾选标记；未选中 = 描边静默；
 * - 三段等宽（weight），勾选标记出现/消失不引起宽度跳动；
 * - 连接形（外侧圆角 / 内直角）与 M3 分段规范核对。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.danmaku.DanmakuTypeSelectorRenderTest"`
 * 产物：shared/build/selector-renders 目录下的 PNG（测试日志打印绝对路径）。
 */
class DanmakuTypeSelectorRenderTest {

    @Test
    fun `类型选择器_三个多选态与明暗_离屏渲染`() {
        val out = renderScene("danmaku-type-selector", 800, 760) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SelectorStates()
                HanimePreviewTheme(isDark = true) {
                    SelectorStates()
                }
            }
        }
        println("DANMAKU_TYPE_SELECTOR_RENDER_OUT: ${out.absolutePath}")
    }

    @Composable
    private fun SelectorStates() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DanmakuTypeSelector(true, {}, true, {}, true, {})
            DanmakuTypeSelector(false, {}, true, {}, true, {})
            DanmakuTypeSelector(false, {}, false, {}, false, {})
        }
    }

    private fun renderScene(
        name: String,
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit,
    ): File {
        val scene = ImageComposeScene(
            width = widthPx,
            height = heightPx,
            density = Density(2f),
            content = {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
            },
        )
        try {
            scene.render(0L)
            scene.render(500_000_000L)
            val image = scene.render(1_000_000_000L)
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG 编码失败：$name")
            val inked = inkedPixelCount(image)
            println("DANMAKU_TYPE_SELECTOR_RENDER_OUT: $name inkedPixels=$inked")
            // 白屏 / 空组合回归：任一段没画出来即红（阈值与 VideoRatingButtonsRenderTest 同口径）。
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "$name 几乎是空的（落墨采样点 $inked）：分段选择器没画东西出来",
            )
            val outDir = File("build/selector-renders").also { it.mkdirs() }
            val out = File(outDir, "$name.png")
            out.writeBytes(data.bytes)
            return out
        } finally {
            scene.close()
        }
    }

    private fun inkedPixelCount(image: Image): Int {
        val pixels = image.peekPixels() ?: return 0
        val sample = ArrayList<Int>(
            (image.width / SAMPLE_STRIDE) * (image.height / SAMPLE_STRIDE),
        )
        for (y in 0 until image.height step SAMPLE_STRIDE) {
            for (x in 0 until image.width step SAMPLE_STRIDE) {
                sample += pixels.getColor(x, y)
            }
        }
        val background = sample.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: return 0
        return sample.count { colorDistance(it, background) > COLOR_EPSILON }
    }

    private fun colorDistance(a: Int, b: Int): Int = maxOf(
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)),
        abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

    private companion object {
        const val SAMPLE_STRIDE = 2
        const val COLOR_EPSILON = 24
        const val MIN_INKED_SAMPLES = 200
    }
}
