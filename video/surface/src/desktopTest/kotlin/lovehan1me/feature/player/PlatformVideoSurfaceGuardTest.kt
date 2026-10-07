package lovehan1me.feature.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `:video:surface` 最小冒烟：它是 mediamp × Compose 唯一共存层，此前零测试。
 *
 * 钉两条模块级契约（见 `video/surface/build.gradle.kts` 头注释）：
 * 1. 公开签名纯洁：`PlatformVideoSurface` 的 expect 声明不得出现 mediamp 类型，
 *    否则会漏进 `:shared` 的 iOS framework export；
 * 2. 三端 actual 齐备：android/desktop/ios 各有一个 `actual fun PlatformVideoSurface`。
 *
 * 手段是源码扫描（沿用 `ModuleLayeringTest` 的定位法）：渲染面挂载本身要真机，
 * headless 能钉的是"契约没被悄悄打破"。每个断言都先断非空，防止扫描失效变假绿。
 */
class PlatformVideoSurfaceGuardTest {

    @Test
    fun `公开签名不含mediamp类型`() {
        val expectFile = surfaceFile(
            "src/commonMain/kotlin/lovehan1me/feature/player/PlatformVideoSurface.kt",
        )
        assertTrue(expectFile.isFile, "找不到 expect 声明：${expectFile.path}")

        val text = stripComments(expectFile.readText())
        assertTrue(
            text.contains("expect fun PlatformVideoSurface"),
            "expect 声明缺失或改名，:shared 的唯一渲染面接口断了",
        )
        assertTrue(
            text.contains("engine: PlaybackEngine"),
            "首参必须是契约层 PlaybackEngine，不能换成具体引擎类型",
        )
        // 只查去注释后的正文：KDoc 里提 mediamp 是文档，不进 iOS export；
        // 真正会泄漏的是 import 与签名里的类型引用。
        assertTrue(
            !text.contains("mediamp"),
            "公开签名混入 mediamp 类型：会漏进 :shared 的 iOS framework export",
        )
    }

    @Test
    fun `三端actual齐备`() {
        val actuals = listOf("androidMain", "desktopMain", "iosMain").map { set ->
            surfaceFile("src/$set")
        }
        assertTrue(actuals.size == 3, "扫描逻辑已失效")

        val missing = actuals.filter { dir ->
            dir.walkTopDown().none { f ->
                f.isFile && f.extension == "kt" &&
                    f.readText().contains("actual fun PlatformVideoSurface")
            }
        }
        assertTrue(
            missing.isEmpty(),
            "以下平台源集缺 actual fun PlatformVideoSurface：${missing.map { it.path }}",
        )
    }

    /** 去掉块注释（保留换行数）与行注释，避免 KDoc 里的字样污染签名断言。 */
    private fun stripComments(source: String): String {
        val noBlock = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).replace(source) { m ->
            "\n".repeat(m.value.count { it == '\n' })
        }
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }

    private fun surfaceFile(relative: String): File {        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").isFile) {
                return File(dir, "video/surface/$relative")
            }
            dir = dir.parentFile
        }
        error("从 ${File(".").absoluteFile} 向上找不到 settings.gradle.kts")
    }
}
