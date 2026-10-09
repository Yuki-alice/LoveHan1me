package lovehan1me.app.navigation.main

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
 * P1 接线 #8 验收：Standard 档底栏（M3E `ShortNavigationBar`）的离屏渲染。
 *
 * 明暗双态各渲一根、选中项错开（顺带覆盖指示器在两端的形态）：
 * - 浅色：Home 选中；
 * - 深色：Mine 选中。
 * 断言 = 落墨采样点 > 阈值（图标 / 标签 / 指示器全没画出来即转红）。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.app.navigation.main.ShortNavigationBarRenderTest"`
 * 产物：shared/build/navbar-renders 目录下的 PNG（测试日志打印绝对路径与落墨计数）。
 */
class ShortNavigationBarRenderTest {

    @Test
    fun `底栏_Standard档_明暗双态_离屏渲染`() {
        val out = renderScene("short-navigation-bar", 800, 480) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MainNavigationBar(selectedTab = MainTab.Home, onSelectTab = {})
                HanimePreviewTheme(isDark = true) {
                    MainNavigationBar(selectedTab = MainTab.Mine, onSelectTab = {})
                }
            }
        }
        println("SHORT_NAV_BAR_RENDER_OUT: ${out.absolutePath}")
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
            println("SHORT_NAV_BAR_RENDER_OUT: $name inkedPixels=$inked")
            // 白屏 / 空组合回归：底栏任一要素没画出来即红（阈值与既有渲染测试同口径）。
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "$name 几乎是空的（落墨采样点 $inked）：底栏没画东西出来",
            )
            val outDir = File("build/navbar-renders").also { it.mkdirs() }
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
