package lovehan1me.video.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 模块分层的可执行护栏。
 *
 * 为什么用源码扫描而不是靠人记：分层一旦破了（引擎里出现 Compose、契约层里出现
 * mediamp），编译照样过 —— 只有 iOS framework export 那一端会隔很久才炸，
 * 排查成本极高。这里让它当场失败。
 *
 * 落 desktopTest 是因为要读源码树，common 源集没有文件 API。
 */
class ModuleLayeringTest {

    @Test
    fun `T9 引擎模块不得依赖 Compose`() {
        val root = repoRoot()
        val engineDir = File(root, "video/engine/src")
        assertTrue(engineDir.isDirectory, "找不到引擎模块源码目录 video/engine/src")

        val violations = mainKotlinFiles(engineDir)
            .flatMap { file -> linesMatching(root, file, "androidx.compose") }

        assertTrue(
            violations.isEmpty(),
            "引擎模块不得依赖 Compose（分层倒置，UI 会反向被引擎污染）。违规 ${violations.size} 处：\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `契约层不得依赖 Compose 或 mediamp`() {
        val root = repoRoot()
        val contractDir = File(root, "video/contract/src")
        assertTrue(contractDir.isDirectory, "找不到契约层源码目录 video/contract/src")

        val compose = mainKotlinFiles(contractDir).flatMap { linesMatching(root, it, "androidx.compose") }
        assertTrue(compose.isEmpty(), "契约层不得依赖 Compose：\n" + compose.joinToString("\n"))

        val mediamp = mainKotlinFiles(contractDir).flatMap { linesMatching(root, it, "org.openani.mediamp") }
        assertTrue(
            mediamp.isEmpty(),
            "契约层不得依赖 mediamp（会漏进 iOS 导出框架）：\n" + mediamp.joinToString("\n"),
        )
    }

    @Test
    fun `领域与 UI 层不得出现 mediamp 类型`() {
        val root = repoRoot()
        // 渲染面（Compose + mediamp）单独放在 :video:surface，所以这里能同时守 shared 与 ui。
        val guardedRoots = listOf("shared/src", "video/ui/src").map { File(root, it) }
        guardedRoots.forEach { assertTrue(it.isDirectory, "找不到源码目录 ${it.path}") }

        val mediamp = guardedRoots.flatMap { guardRoot ->
            mainKotlinFiles(guardRoot).flatMap { file -> linesMatching(root, file, "org.openani.mediamp") }
        }
        assertTrue(
            mediamp.isEmpty(),
            "shared / :video:ui 不得依赖 mediamp（引擎模块对它只是 implementation，抬成 api 会漏进 iOS 导出框架）：\n" +
                mediamp.joinToString("\n"),
        )
    }

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("从 ${File(".").absoluteFile} 向上找不到 settings.gradle.kts")
    }

    /** 只扫 `*Main` 源集：测试源集里就写着这些包名字符串，扫进去会自己命中自己。 */
    private fun mainKotlinFiles(moduleSrc: File): List<File> =
        (moduleSrc.listFiles() ?: emptyArray())
            .filter { it.isDirectory && it.name.endsWith("Main") }
            .flatMap { dir ->
                dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }

    private fun linesMatching(root: File, file: File, needle: String): List<String> {
        val hits = mutableListOf<String>()
        file.useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (line.contains(needle)) {
                    hits += "${file.relativeTo(root).path}:${index + 1}: ${line.trim()}"
                }
            }
        }
        return hits
    }
}
