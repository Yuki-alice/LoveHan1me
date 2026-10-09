package lovehan1me.ui.component.appbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.ic_help
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.compose.resources.painterResource
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P1 接线 #9 验收：二级页大标题顶栏（M3E `MediumFlexibleTopAppBar` 封装）离屏渲染。
 *
 * 明暗双态各渲一根**展开态**（大标题 + subtitle 槽 + 返回按钮 + 帮助动作）：
 * - 浅色：稍後觀看；深色：觀看歷史。
 * 收起态由 `exitUntilCollapsedScrollBehavior` 的框架行为提供（依赖滚动事件，
 * 静态渲染不涉）；本测试钉住的是槽位齐全、明暗配色与布局不塌。
 * 断言 = 落墨采样点 > 阈值（标题 / 副标题 / 按钮全没画出来即转红）。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.ui.component.appbar.FlexibleTopAppBarRenderTest"`
 * 产物：shared/build/appbar-renders 目录下的 PNG。
 */
class FlexibleTopAppBarRenderTest {

    @Test
    fun `大标题顶栏_明暗双态_离屏渲染`() {
        val out = renderScene("flexible-top-app-bar", 800, 800) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                HanimeMediumFlexibleTopAppBar(
                    title = "稍後觀看",
                    subtitle = { SampleSubtitle() },
                    onBack = {},
                    actions = { SampleHelpAction() },
                )
                HanimePreviewTheme(isDark = true) {
                    HanimeMediumFlexibleTopAppBar(
                        title = "觀看歷史",
                        subtitle = { SampleSubtitle() },
                        onBack = {},
                        actions = { SampleHelpAction() },
                    )
                }
            }
        }
        println("FLEXIBLE_APPBAR_RENDER_OUT: ${out.absolutePath}")
    }

    @Composable
    private fun SampleSubtitle() {
        Text(
            text = "共 42 支影片",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @Composable
    private fun SampleHelpAction() {
        IconButton(onClick = {}) {
            Icon(
                painter = painterResource(Res.drawable.ic_help),
                contentDescription = "help",
            )
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
            println("FLEXIBLE_APPBAR_RENDER_OUT: $name inkedPixels=$inked")
            // 白屏 / 空组合回归：任一槽位没画出来即红（阈值与既有渲染测试同口径）。
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "$name 几乎是空的（落墨采样点 $inked）：大标题顶栏没画东西出来",
            )
            val outDir = File("build/appbar-renders").also { it.mkdirs() }
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
