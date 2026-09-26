package lovehan1me.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 弹幕层落位：品红块走真实 [VideoPlayerUi] 插槽，断言它与画面同矩形。
 *
 * 背景故事：线上曾出现"字从窗口顶部开始飘，画面却在下面"的错位。
 * 引擎的轨道算式再对，也防不住**层摆错地方** —— 这一测只认像素：
 * 容器与画面宽高比不一致时（桌面双栏的日常），品红必须一像素不差地
 * 盖住画面区，且一像素都不进黑边。
 *
 * 手法：不传 `videoSurface`（默认 null）走画面占位分支 —— 占位与真画面用的是
 * **同一个** `videoModifier`，测占位就等于测真机；弹幕插槽里放纯色块，
 * 模拟路由层 `DanmakuLayer(modifier = fillMaxSize())` 的契约。
 * `showControls = false` 藏掉顶栏/底栏/手势 HUD，画面里只剩三样东西：
 * 根底色、画面占位（被品红盖住）、品红弹幕层。
 */
class DanmakuPlacementTest {

    private fun renderPlacement(
        name: String,
        containerW: Int,
        containerH: Int,
        videoAspect: Float,
    ): Image {
        val scene = ImageComposeScene(
            width = containerW,
            height = containerH,
            // 1:1：断言的像素行就是 dp 行，不用换算
            density = Density(1f),
            content = {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                    VideoPlayerUi(
                        modifier = Modifier.fillMaxSize(),
                        title = "",
                        currentTime = "",
                        totalTime = "",
                        progress = 0f,
                        bufferedProgress = 0f,
                        currentVolume = 0f,
                        currentBrightness = 0f,
                        showControls = false,
                        superResolutionLabel = "",
                        videoAspectRatio = videoAspect,
                        danmakuLayer = {
                            Box(Modifier.fillMaxSize().background(Color.Magenta))
                        },
                    )
                }
            },
        )
        try {
            repeat(SETTLE_FRAMES) { frame ->
                scene.render(BASE_NANOS + frame * FRAME_STEP_NANOS).close()
            }
            val image = scene.render(BASE_NANOS + SETTLE_FRAMES * FRAME_STEP_NANOS)
            val out = File(File("build/danmaku-renders").also { it.mkdirs() }, "$name.png")
            out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("DANMAKU_PLACEMENT_OUT: ${out.absolutePath}")
            return image
        } finally {
            scene.close()
        }
    }

    private fun isMagenta(argb: Int): Boolean {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return abs(r - 255) < COLOR_EPSILON && g < COLOR_EPSILON && abs(b - 255) < COLOR_EPSILON
    }

    /** 中心列从上往下第一个品红行；-1 = 整列没有（层根本没画出来）。 */
    private fun firstMagentaRow(image: Image, x: Int): Int {
        val pixels = image.peekPixels() ?: return -1
        for (y in 0 until image.height) {
            if (isMagenta(pixels.getColor(x, y))) return y
        }
        return -1
    }

    /** 中心列从下往上第一个品红行。 */
    private fun lastMagentaRow(image: Image, x: Int): Int {
        val pixels = image.peekPixels() ?: return -1
        for (y in image.height - 1 downTo 0) {
            if (isMagenta(pixels.getColor(x, y))) return y
        }
        return -1
    }

    /** 某行从左往右第一个品红列。 */
    private fun firstMagentaCol(image: Image, y: Int): Int {
        val pixels = image.peekPixels() ?: return -1
        for (x in 0 until image.width) {
            if (isMagenta(pixels.getColor(x, y))) return x
        }
        return -1
    }

    /** 某行从右往左第一个品红列。 */
    private fun lastMagentaCol(image: Image, y: Int): Int {
        val pixels = image.peekPixels() ?: return -1
        for (x in image.width - 1 downTo 0) {
            if (isMagenta(pixels.getColor(x, y))) return x
        }
        return -1
    }

    private fun assertNear(expected: Int, actual: Int, what: String) {
        assertTrue(
            abs(expected - actual) <= ROUNDING_PX,
            "$what 期望 $expected，实际 $actual（容差 ±$ROUNDING_PX，超出即错位）",
        )
    }

    @Test
    fun `高容器弹幕不进上下黑边`() {
        // 1280×980（双栏日常：比 16:9 高）→ 画面 1280×720 居中，上下各 130 黑边
        val image = renderPlacement("placement-tall", 1280, 980, 16f / 9f)
        try {
            val first = firstMagentaRow(image, 640)
            val last = lastMagentaRow(image, 640)
            assertTrue(first >= 0, "中心列整列无品红：弹幕层根本没画出来")
            assertNear(130, first, "品红首行（画面上沿）")
            assertNear(849, last, "品红末行（画面下沿）")
            // 上黑边正中不能有品红：这是线上事故的原样（字飘进黑边）
            val pixels = image.peekPixels()!!
            assertTrue(
                !isMagenta(pixels.getColor(640, 65)),
                "上黑边正中 (640,65) 有品红：弹幕铺满了整个容器而不是画面矩形",
            )
            assertTrue(
                !isMagenta(pixels.getColor(640, 915)),
                "下黑边正中 (640,915) 有品红：弹幕铺满了整个容器而不是画面矩形",
            )
        } finally {
            image.close()
        }
    }

    @Test
    fun `宽容器弹幕不进左右黑边`() {
        // 1440×634（封顶 55% 窗高的双栏播放器：比 16:9 宽）→ 画面 1128×634 居中
        val image = renderPlacement("placement-wide", 1440, 634, 16f / 9f)
        try {
            val midY = 317
            val first = firstMagentaCol(image, midY)
            val last = lastMagentaCol(image, midY)
            assertTrue(first >= 0, "中间行整行无品红：弹幕层根本没画出来")
            assertNear(156, first, "品红首列（画面左沿）")
            assertNear(1283, last, "品红末列（画面右沿）")
            val pixels = image.peekPixels()!!
            assertTrue(
                !isMagenta(pixels.getColor(78, midY)),
                "左黑边正中 (78,$midY) 有品红：弹幕铺满了整个容器而不是画面矩形",
            )
            assertTrue(
                !isMagenta(pixels.getColor(1361, midY)),
                "右黑边正中 (1361,$midY) 有品红：弹幕铺满了整个容器而不是画面矩形",
            )
        } finally {
            image.close()
        }
    }

    @Test
    fun `正好16比9时铺满容器`() {
        // 1280×720：无黑边，品红应盖住每一行每一列（回归基线：不断言错位，只断言层在）
        val image = renderPlacement("placement-exact", 1280, 720, 16f / 9f)
        try {
            assertEquals(0, firstMagentaRow(image, 640))
            assertEquals(719, lastMagentaRow(image, 640))
            assertEquals(0, firstMagentaCol(image, 360))
            assertEquals(1279, lastMagentaCol(image, 360))
        } finally {
            image.close()
        }
    }

    private companion object {
        const val BASE_NANOS = 1_000_000_000L
        const val FRAME_STEP_NANOS = 16_000_000L
        const val SETTLE_FRAMES = 3

        /** 单通道容差：纯色块本该逐位相等，留 24 防色彩空间换算的微差。 */
        const val COLOR_EPSILON = 24

        /** aspectRatio 浮点换算到整像素的舍入容差。 */
        const val ROUNDING_PX = 2
    }
}
