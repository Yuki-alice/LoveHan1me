package lovehan1me.feature.search

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
 * P1 接线 #10 验收：发布日期「具体年月 / 大致范围」模式分段的离屏渲染。
 *
 * 两档选中（0 / 1）× 明暗两态同图对比：
 * - 选中段 = container 填充，当前档一眼可辨（旧实现两个同貌按钮的缺口）；
 * - 两段等宽（weight）；连接形左右外圆角。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.search.ReleaseDateModeSelectorRenderTest"`
 * 产物：shared/build/selector-renders 目录下的 PNG（测试日志打印绝对路径）。
 */
class ReleaseDateModeSelectorRenderTest {

    @Test
    fun `日期模式分段_两档选中与明暗_离屏渲染`() {
        val out = renderScene("release-date-mode-selector", 800, 560) {
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
        println("RELEASE_DATE_MODE_SELECTOR_RENDER_OUT: ${out.absolutePath}")
    }

    @Composable
    private fun SelectorStates() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReleaseDateModeSelector(selectedTab = 0, onSelectTab = {})
            ReleaseDateModeSelector(selectedTab = 1, onSelectTab = {})
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
            println("RELEASE_DATE_MODE_SELECTOR_RENDER_OUT: $name inkedPixels=$inked")
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "$name 几乎是空的（落墨采样点 $inked）：模式分段没画东西出来",
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
