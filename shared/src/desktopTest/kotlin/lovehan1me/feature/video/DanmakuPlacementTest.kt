package lovehan1me.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lovehan1me.feature.player.PlaybackEngineState
import lovehan1me.feature.player.PlaybackUiState
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 弹幕层落位：品红块走真实 [VideoPlayerShell] 插槽，断言它铺满**播放器区**。
 *
 * 钉的是这条不变量：弹幕区的上下沿 = 播放器容器上下沿各内缩 8dp，左右铺到容器边，
 * **与画面比例、画面尺寸无关**。弹幕跟着的是播放器这块地方，不是画面那块矩形 ——
 * 渲染面按画面比例留的黑边也在弹幕区之内，宽容器下弹幕因此能用到整条宽度。
 *
 * 手法：`videoSurface` 给一块纯黑 —— 渲染面在真机上就是"按画面比例自己留黑边"的那块，
 * 黑边与真画面的分界靠颜色就够分辨；弹幕插槽放纯色块，模拟路由层
 * `DanmakuLayer(modifier = fillMaxSize())` 的契约。`showControls = false` 把顶栏/底栏/
 * 手势 HUD 一并锁掉，画面里只剩三样东西：根底色、黑画面、品红弹幕层。
 *
 * 期望值怎么来的（[Density] 取 1f，dp 就是像素）：容器高 H → 品红行 8..H-9，列 0..W-1。
 */
class DanmakuPlacementTest {

    init {
        installInMemorySettingsStore()
    }

    private fun renderPlacement(
        name: String,
        containerW: Int,
        containerH: Int,
        videoWidth: Int,
        videoHeight: Int,
    ): Image {
        val engine = FakePlaybackEngine(
            PlaybackEngineState(
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                hasRenderedFirstFrame = true,
            ),
        )
        val scene = ImageComposeScene(
            width = containerW,
            height = containerH,
            // 1:1：断言的像素行就是 dp 行，不用换算
            density = Density(1f),
            content = {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                    VideoPlayerShell(
                        player = FakeMediampPlayer(
                            videoWidth = videoWidth,
                            videoHeight = videoHeight,
                        ),
                        controller = fakePlaybackController(engine),
                        playbackState = PlaybackUiState(
                            videoWidth = videoWidth,
                            videoHeight = videoHeight,
                            hasRenderedFirstFrame = true,
                        ),
                        videoSurface = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                        modifier = Modifier.fillMaxSize(),
                        expanded = true,
                        // 控件全锁：本用例只认弹幕那一层摆没摆对地方
                        showControls = false,
                        contentWindowInsets = WindowInsets(0.dp),
                        danmakuEnabled = true,
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
    fun `高容器弹幕铺满播放器区`() {
        // 1280×980（双栏日常：比 16:9 高）→ 画面 1280×720 居中，上下各 130 黑边；
        // 弹幕区仍是整块容器（上下各缩 8）：行 8..971，列 0..1279
        val image = renderPlacement("placement-tall", 1280, 980, 1600, 900)
        try {
            assertNear(8, firstMagentaRow(image, 640), "品红首行（容器上沿 + 留白）")
            assertNear(971, lastMagentaRow(image, 640), "品红末行（容器下沿 - 留白）")
            assertNear(0, firstMagentaCol(image, 490), "品红首列（容器左沿）")
            assertNear(1279, lastMagentaCol(image, 490), "品红末列（容器右沿）")
            // 上黑边正中必须有品红：画面之上的空地也是弹幕区
            val pixels = image.peekPixels()!!
            assertTrue(
                isMagenta(pixels.getColor(640, 65)),
                "上黑边正中 (640,65) 没有品红：弹幕区被夹到画面矩形了",
            )
            assertTrue(
                isMagenta(pixels.getColor(640, 915)),
                "下黑边正中 (640,915) 没有品红：弹幕区被夹到画面矩形了",
            )
        } finally {
            image.close()
        }
    }

    @Test
    fun `宽容器弹幕铺满整条宽度`() {
        // 1440×634（封顶 55% 窗高的双栏播放器：比 16:9 宽）→ 画面 1127×634 居中，
        // 左右各 156 黑边；弹幕照旧铺满 1440
        val image = renderPlacement("placement-wide", 1440, 634, 1600, 900)
        try {
            val midY = 317
            assertNear(0, firstMagentaCol(image, midY), "品红首列（容器左沿）")
            assertNear(1439, lastMagentaCol(image, midY), "品红末列（容器右沿）")
            assertNear(8, firstMagentaRow(image, 720), "品红首行（容器上沿 + 留白）")
            assertNear(625, lastMagentaRow(image, 720), "品红末行（容器下沿 - 留白）")
            val pixels = image.peekPixels()!!
            assertTrue(
                isMagenta(pixels.getColor(78, midY)),
                "左黑边正中 (78,$midY) 没有品红：弹幕区被画面左右黑边挤窄了",
            )
        } finally {
            image.close()
        }
    }

    @Test
    fun `正好16比9时铺满容器宽度`() {
        // 1280×720：无黑边，品红左右铺到边，上下只剩那 8dp 留白（回归基线：不断言错位，只断言层在）
        val image = renderPlacement("placement-exact", 1280, 720, 1600, 900)
        try {
            assertNear(8, firstMagentaRow(image, 640), "品红首行")
            assertNear(711, lastMagentaRow(image, 640), "品红末行")
            assertEquals(0, firstMagentaCol(image, 360))
            assertEquals(1279, lastMagentaCol(image, 360))
        } finally {
            image.close()
        }
    }

    @Test
    fun `画面比例不改变弹幕区`() {
        // 同一容器 1280×720 播 4:3（1200×900）→ 画面 960×720 居中，左右各 160 黑边。
        // 这一测盯着"引擎上报的比例不许夹窄弹幕区"：4:3 与 16:9 拿到的矩形必须相同。
        val image = renderPlacement("placement-four-thirds", 1280, 720, 1200, 900)
        try {
            assertNear(0, firstMagentaCol(image, 360), "品红首列（容器左沿）")
            assertNear(1279, lastMagentaCol(image, 360), "品红末列（容器右沿）")
            val pixels = image.peekPixels()!!
            assertTrue(
                isMagenta(pixels.getColor(80, 360)),
                "左黑边正中 (80,360) 没有品红：4:3 的画面比例把弹幕区夹窄了",
            )
            assertTrue(
                isMagenta(pixels.getColor(1200, 360)),
                "右黑边正中 (1200,360) 没有品红：4:3 的画面比例把弹幕区夹窄了",
            )
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
