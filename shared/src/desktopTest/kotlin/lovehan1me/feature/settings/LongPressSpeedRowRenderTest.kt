package lovehan1me.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.app.navigation.settings.formatSpeedTimes
import lovehan1me.d_speed_times
import lovehan1me.ic_touch_long
import lovehan1me.long_press_speed_multiplier
import lovehan1me.long_press_speed_summary
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 设置页「长按快进速度」行的离屏渲染（`ImageComposeScene`，无窗口/无设备）：
 * 修复 `%.1f` 原样透出后，肉眼确认行值与摘要都吃到已格式化的倍率。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.settings.LongPressSpeedRowRenderTest"`
 * 产物：shared/build/settings-renders 目录下的 PNG（测试日志打印绝对路径）。
 */
class LongPressSpeedRowRenderTest {

    @Test
    fun `长按快进行_行值与摘要均正常`() {
        val out = renderScene("long-press-speed-row", 1080, 400) {
            // 与 PlayerSettingsRoute → PlayerSettingsScreen 同一条数据链路：
            // label 走 formatSpeedTimes + %1$s 模板，摘要吃 label。
            val label = stringResource(Res.string.d_speed_times, formatSpeedTimes(3f))
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SettingNavigationItem(
                    title = stringResource(Res.string.long_press_speed_multiplier),
                    summary = stringResource(Res.string.long_press_speed_summary, label),
                    valueText = label,
                    iconRes = Res.drawable.ic_touch_long,
                    onClick = {},
                )
            }
        }
        println("SETTINGS_RENDER_OUT: ${out.absolutePath}")
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
            println("SETTINGS_RENDER_OUT: $name inkedPixels=$inked")
            // 白屏/空组合回归（Gate3-P2）：行值摘要任一没画出来即红。
            // 阈值与 PlayerBarRenderTest 同口径（200），正常行是几千。
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "$name 几乎是空的（落墨采样点 $inked）：设置行没画东西出来",
            )
            val outDir = File("build/settings-renders").also { it.mkdirs() }
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
