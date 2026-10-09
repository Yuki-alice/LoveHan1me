package lovehan1me.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * P1 接线 #5 验收：赞 / 踩操作组的离屏渲染（`ImageComposeScene`，无窗口 / 无设备）。
 *
 * 三个状态（未评 / 已赞 / 已踩）× 明暗两态同图对比：
 * - 形状：连接组外圆角 + 选中态全圆（CornerFull）是否符合 M3E 规格；
 * - 颜色：未选中是静默 chip，选中后走 primary / error 语义；
 * - 布局：与 32dp 行高的 `MetaInfoItem` 同高（`MetaSection` 内并排时不吃线）。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.video.VideoRatingButtonsRenderTest"`
 * 产物：shared/build/video-renders 目录下的 PNG（测试日志打印绝对路径）。
 */
class VideoRatingButtonsRenderTest {

    @Test
    fun `赞踩组_三态与明暗_离屏渲染`() {
        val out = renderScene("video-rating-buttons", 1120, 340) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                RatingStatesRow()
                HanimePreviewTheme(isDark = true) {
                    RatingStatesRow()
                }
            }
        }
        println("VIDEO_RATING_RENDER_OUT: ${out.absolutePath}")
    }

    @Composable
    private fun RatingStatesRow() {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            VideoRatingButtons(video = sampleVideo(isFav = false, isUnlike = false), onRateVideo = {})
            VideoRatingButtons(video = sampleVideo(isFav = true, isUnlike = false), onRateVideo = {})
            VideoRatingButtons(video = sampleVideo(isFav = false, isUnlike = true), onRateVideo = {})
        }
    }

    /** 展示为「90% (1234)」的样本；只喂渲染需要的字段。 */
    private fun sampleVideo(isFav: Boolean, isUnlike: Boolean) = HanimeVideo(
        title = "樣板",
        coverUrl = "",
        chineseTitle = null,
        introduction = null,
        uploadTime = null,
        videoUrls = emptyMap(),
        tags = emptyList(),
        favTimes = 1111,
        isFav = isFav,
        unlikesCount = 123,
        isUnlike = isUnlike,
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
            println("VIDEO_RATING_RENDER_OUT: $name inkedPixels=$inked")
            // 白屏 / 空组合回归：操作组任一状态没画出来即红（阈值与 PlayerBarRenderTest 同口径）。
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "$name 几乎是空的（落墨采样点 $inked）：赞踩操作组没画东西出来",
            )
            val outDir = File("build/video-renders").also { it.mkdirs() }
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
