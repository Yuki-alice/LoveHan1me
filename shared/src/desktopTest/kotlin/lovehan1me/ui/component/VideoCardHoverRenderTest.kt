package lovehan1me.ui.component

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lovehan1me.core.domain.model.VideoItemType
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P1 #7 守卫：`VideoCardItem` 的**桌面悬停反馈存在性**。
 *
 * 核验结论（2026-10-09）：悬停反馈由 `indication`（LocalIndication 默认值 = M3 `ripple()`）
 * 的 hover 状态层提供 —— `internal.ripple.RippleNode` 订阅 [HoverInteraction]、
 * `hoveredAlpha ≈ 8% onSurface`；**不需要也不允许自绘 overlay**（会叠加成 ~16% 双重状态层）。
 * 本测试守护的即"悬停时必须出现可见的状态层变化"这一用户可见事实。
 *
 * 手法：同一场景先静置到"连续两帧零差"（排掉首帧后其它自身收尾的动画），拍基线帧；
 * 再向注入的 interactionSource 手动发 [HoverInteraction.Enter]，等状态层动画走完拍第二帧；
 * 两帧逐像素差 > 阈值即证明悬停反馈存在（实测 ≈16.8 万像素）。
 * 反向验证（已实测）：把生产侧 `indication` 临时置 null（悬停反馈被拆掉）→ 差异归零 → 用例转红。
 *
 * 明暗各拍一组，PNG 落盘（`hover-before/after` 四张）供肉眼比对。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.ui.component.VideoCardHoverRenderTest"`
 * 产物：shared/build/card-hover-renders 目录（测试日志打印绝对路径与差异像素数）。
 */
class VideoCardHoverRenderTest {

    @Test
    fun `卡片hover_明暗两态_离屏渲染`() {
        val light = capturePair("video-card-hover-light", isDark = false)
        val dark = capturePair("video-card-hover-dark", isDark = true)
        println("VIDEO_CARD_HOVER_RENDER_OUT: light diffPixels=${light.diffPixels}")
        println("VIDEO_CARD_HOVER_RENDER_OUT: dark diffPixels=${dark.diffPixels}")
        assertTrue(
            light.diffPixels > MIN_DIFF_PIXELS,
            "浅色：悬停后与基线帧无差异（diff=${light.diffPixels}）—— 悬停反馈缺失（状态层没画出来）",
        )
        assertTrue(
            dark.diffPixels > MIN_DIFF_PIXELS,
            "深色：悬停后与基线帧无差异（diff=${dark.diffPixels}）—— 悬停反馈缺失（状态层没画出来）",
        )
    }

    private class Pair(val diffPixels: Int)

    private fun capturePair(name: String, isDark: Boolean): Pair {
        val source = MutableInteractionSource()
        val scene = ImageComposeScene(
            width = 560,
            height = 640,
            density = Density(2f),
            content = {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize(), isDark = isDark) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.padding(20.dp)) {
                            VideoCardItem(
                                modifier = Modifier.width(220.dp),
                                videoItem = FakeCardVideo,
                                isHorizontalCard = true,
                                interactionSource = source,
                                onClickVideosItem = {},
                            )
                        }
                    }
                }
            },
        )
        try {
            // 静置到"连续两帧零差"再拍基线帧：首帧之后还有其它自身收尾的动画
            // （焦点指示层的淡入、图片管线换图等），不收稳就把它们全算进 hover 的账上。
            scene.render(0L)
            var t = 0L
            var prev = scene.render(t)
            var stable = 0
            var guard = 0
            while (stable < 2 && guard < 60) {
                t += 100_000_000L
                val cur = scene.render(t)
                if (countDiffPixels(prev, cur) == 0) stable++ else stable = 0
                prev = cur
                guard++
            }
            println("VIDEO_CARD_HOVER_RENDER_OUT: $name settled at t=${t / 1_000_000}ms stable=$stable guard=$guard")
            val before = prev

            // 手动注入 hover 事件；等颜色动画（spring ~300ms）走完再拍第二帧。
            source.tryEmit(HoverInteraction.Enter())
            repeat(3) {
                t += 100_000_000L
                scene.render(t)
            }
            val after = scene.render(t)

            val diff = countDiffPixels(before, after)
            val outDir = File("build/card-hover-renders").also { it.mkdirs() }
            writePng(before, File(outDir, "$name-before.png"))
            writePng(after, File(outDir, "$name-after.png"))
            println("VIDEO_CARD_HOVER_RENDER_OUT: $name diff=$diff saved=${outDir.absolutePath}")
            return Pair(diff)
        } finally {
            scene.close()
        }
    }

    private fun writePng(image: Image, file: File) {
        val data = image.encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG 编码失败：${file.name}")
        file.writeBytes(data.bytes)
    }

    /** 两帧逐像素计数：逐通道差超 [COLOR_EPSILON] 记为差异像素。 */
    private fun countDiffPixels(before: Image, after: Image): Int {
        val pa = before.peekPixels() ?: return 0
        val pb = after.peekPixels() ?: return 0
        var count = 0
        for (y in 0 until before.height) {
            for (x in 0 until before.width) {
                if (colorDistance(pa.getColor(x, y), pb.getColor(x, y)) > COLOR_EPSILON) {
                    count++
                }
            }
        }
        return count
    }

    private fun colorDistance(a: Int, b: Int): Int = maxOf(
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)),
        abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

    private object FakeCardVideo : VideoItemType {
        override val title = "示範影片標題（看看換行）"
        override val coverUrl = ""
        override val videoCode = "fake-001"
        override val duration = "12:34"
        override val views = "1.2萬"
        override val reviews = "345"
        override val currentArtist = "サークル名"
        override val uploadTime = "2026-10-09"
    }

    private companion object {
        /** 8% onSurface 叠加 ≈ 每通道 15-20 的差，取一半再宽一点当判据。 */
        const val COLOR_EPSILON = 8

        /** 整卡背景区在 440×~500px 量级；hover 生效时差异像素以万计，取宽松下界防"完全没生效"。 */
        const val MIN_DIFF_PIXELS = 5_000
    }
}
