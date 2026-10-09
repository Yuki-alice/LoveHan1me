package lovehan1me.video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lovehan1me.video.player.ui.support.LocalPlatform
import lovehan1me.video.player.ui.support.Platform
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P1 接线 #7 验收：`PlayerTooltipBox` 的离屏渲染（提示气泡可视化 + 平台门控）。
 *
 * 静态渲染无法悬停，于是走 `rememberTooltipState` 的可控入口：`initialIsVisible = true`
 * 让提示直接出图。两个断言：
 * - **桌面**（测试运行环境即桌面）：图标按钮 + 提示气泡都画出（落墨 + PNG）；
 * - **门控**：把 `LocalPlatform` 覆写成 Android 后渲染同一组合，气泡必须消失 ——
 *   落墨应严格少于桌面态（移动端不弹提示的契约，静态可证）。
 *
 * 跑法：`:video:ui:desktopTest --tests "lovehan1me.video.ui.PlayerTooltipRenderTest"`
 * 产物：video/ui/build/tooltip-renders 目录下的 PNG（测试日志打印绝对路径与落墨）。
 */
class PlayerTooltipRenderTest {

    @Test
    fun `桌面tooltip_可见态与移动门控_离屏渲染`() {
        val desktop = renderScene("player-tooltip-desktop", Platform.MacOS)
        val android = renderScene("player-tooltip-android", Platform.Android)
        println("PLAYER_TOOLTIP_RENDER_OUT: desktop inked=${desktop.inked} android inked=${android.inked}")
        assertTrue(
            desktop.inked > MIN_INKED_SAMPLES,
            "桌面态几乎是空的（落墨 ${desktop.inked}）：图标或提示气泡没画出来",
        )
        assertTrue(
            desktop.inked > android.inked,
            "Android 覆写后落墨（${android.inked}）未少于桌面态（${desktop.inked}）：" +
                "平台门控失效，移动端也被包上了 TooltipBox",
        )
    }

    private class Result(val inked: Int)

    private fun renderScene(name: String, platform: Platform): Result {
        val scene = ImageComposeScene(
            width = 640,
            height = 400,
            density = Density(2f),
            content = {
                // 播放器强制深色皮肤的最小同构：深色 scheme + 任意画面底。
                MaterialTheme(colorScheme = darkColorScheme()) {
                    CompositionLocalProvider(
                        LocalPlatform provides platform,
                        // 播放器皮肤的内容色约定（真机由 VideoScaffold 提供）；测试同构，
                        // 否则 IconButton 默认 contentColor 兜底为黑，图标在深底上看不见。
                        LocalContentColor provides Color(0xFFE6E1E5),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF3A3A3A)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(Modifier.padding(bottom = 28.dp)) {
                                PlayerTooltipBox(
                                    text = "下一集",
                                    state = rememberTooltipState(
                                        initialIsVisible = true,
                                        isPersistent = true,
                                    ),
                                ) {
                                    IconButton(onClick = {}) {
                                        Icon(
                                            Icons.Rounded.SkipNext,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
        try {
            scene.render(0L)
            repeat(8) { scene.render(80_000_000L * (it + 1)) }
            val image = scene.render(800_000_000L)
            val inked = inkedPixelCount(image)
            val outDir = File("build/tooltip-renders").also { it.mkdirs() }
            val out = File(outDir, "$name.png")
            val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG 编码失败：$name")
            out.writeBytes(data.bytes)
            println("PLAYER_TOOLTIP_RENDER_OUT: $name inked=$inked saved=${out.absolutePath}")
            return Result(inked)
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
