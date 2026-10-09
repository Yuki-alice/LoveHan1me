package lovehan1me.video.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P1 接线 #11 验收：播放器缓冲浮层（ContainedLoadingIndicator）的离屏渲染。
 *
 * 两块"视频画面"底色（亮灰 / 暗灰，模拟不同画面帧）× 对应明暗配色：
 * - contained 容器（38dp）给指示器一个稳定对比底座 —— 这是"叠在内容上"的规格用法，
 *   裸转圈在亮画面帧上会糊掉；
 * - 文本仍走 `onSurface`，整套在两类底色上都应可辨。
 *
 * 跑法：`:video:ui:desktopTest --tests "lovehan1me.video.player.ui.VideoLoadingIndicatorRenderTest"`
 * 产物：video/ui/build/loading-indicator-renders 目录下的 PNG（测试日志打印绝对路径）。
 */
class VideoLoadingIndicatorRenderTest {

    @Test
    fun `缓冲浮层_明暗两态_离屏渲染`() {
        val out = renderScene("video-loading-indicator", 720, 560) {
            Column(modifier = Modifier.fillMaxSize()) {
                BufferingTile(
                    isDark = false,
                    videoBackdrop = Color(0xFFBDBDBD),
                    modifier = Modifier.weight(1f),
                )
                BufferingTile(
                    isDark = true,
                    videoBackdrop = Color(0xFF3A3A3A),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        println("VIDEO_LOADING_INDICATOR_RENDER_OUT: ${out.absolutePath}")
    }

    /** 一块"视频画面"上叠缓冲浮层；底色模拟视频像素（刻意硬编码，不属主题色）。 */
    @Composable
    private fun BufferingTile(
        isDark: Boolean,
        videoBackdrop: Color,
        modifier: Modifier = Modifier,
    ) {
        MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
            Box(
                modifier = modifier.fillMaxWidth().background(videoBackdrop),
                contentAlignment = Alignment.Center,
            ) {
                VideoLoadingIndicator(
                    showProgress = true,
                    text = { Text("缓冲中…") },
                )
            }
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
            content = content,
        )
        try {
            scene.render(0L)
            scene.render(500_000_000L)
            val image = scene.render(1_000_000_000L)
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG 编码失败：$name")
            val inked = inkedPixelCount(image)
            println("VIDEO_LOADING_INDICATOR_RENDER_OUT: $name inkedPixels=$inked")
            // 白屏 / 空组合回归：浮层没画出来即红（阈值与其他渲染测试同口径）。
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "$name 几乎是空的（落墨采样点 $inked）：缓冲浮层没画东西出来",
            )
            val outDir = File("build/loading-indicator-renders").also { it.mkdirs() }
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
