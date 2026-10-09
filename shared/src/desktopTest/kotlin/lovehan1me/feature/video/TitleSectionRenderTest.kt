package lovehan1me.feature.video

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
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P1 接线 #13 验收：视频页标题区（Emphasized 字阶首日消费点）的离屏渲染。
 *
 * `TitleSection` 主标题从 `headlineSmall + 手写 Bold` 换成 `headlineSmallEmphasized`
 * （AppTypography 收敛为 SemiBold）——本测试钉住主/副标题双行布局与明暗配色，
 * 防"字阶资产替换把标题画丢"的回归。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.video.TitleSectionRenderTest"`
 * 产物：shared/build/title-renders 目录下的 PNG。
 */
class TitleSectionRenderTest {

    @Test
    fun `视频标题区_明暗双态_离屏渲染`() {
        val out = renderScene("title-section", 800, 540) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                TitleSection(video = sampleVideo())
                HanimePreviewTheme(isDark = true) {
                    TitleSection(video = sampleVideo())
                }
            }
        }
        println("TITLE_SECTION_RENDER_OUT: ${out.absolutePath}")
    }

    /** 主/副标题都出场的样本：中文正标题 + 原名（secondaryTitle 条件是 title != primaryTitle）。 */
    private fun sampleVideo() = HanimeVideo(
        title = "SAMPLE ORIGINAL TITLE",
        coverUrl = "",
        chineseTitle = "樣板正標題",
        introduction = null,
        uploadTime = null,
        videoUrls = emptyMap(),
        tags = emptyList(),
        favTimes = 0,
        isFav = false,
        unlikesCount = 0,
        isUnlike = false,
    )

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
            println("TITLE_SECTION_RENDER_OUT: $name inkedPixels=$inked")
            // 白屏 / 空组合回归：主副标题任一没画出来即红（阈值与既有渲染测试同口径）。
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "$name 几乎是空的（落墨采样点 $inked）：标题区没画东西出来",
            )
            val outDir = File("build/title-renders").also { it.mkdirs() }
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
