package lovehan1me.ui.component

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A1.5 配置守卫：桌面 Coil 单例注册必须委派共享出口，且不得复用 API/HTML 出口。
 *
 * ## 为什么扫源码而不是跑行为
 * 这条契约的对象是"**桌面入口怎么接线**"，不是一个能在 desktopTest 里跑出来的运行时行为 ——
 * `Main.kt` 是 :desktopApp 的 Application 入口，拉起它要一整个 Compose 桌面运行时。
 * 沿用 :video:contract 的 `ModuleLayeringTest` 的定位法：这类"接线正确性"用源码扫描来钉，
 * 改坏当场失败、且失败点直指违规文本。
 *
 * ## 它防的是什么
 * 改之前桌面 `setSingletonImageLoaderFactory` 自建 `ImageLoader.Builder + ktor3 取图器`：
 *  1. 与 `rememberHanimeImageLoader` 不是同一实例 ⇒ 进程级单例在桌面名存实亡（两套缓存）；
 *  2. 出口复用 `createHanimeHttpClient()`（API/HTML 出口、带 `hanime1_session`）
 *     ⇒ 登录态被绑到图床 host（`vdownload.hembed.com`）发出去。
 * 这两点都不会让编译失败，只能靠本用例拦。
 *
 * ## 反向验证（已实测）
 * 把 `Main.kt` 的单例注册改回 `KtorNetworkFetcherFactory(httpClient = { createHanimeHttpClient() })`，
 * `桌面 Coil 单例注册委派共享出口且不复用 API 出口` 立即转红。
 *
 * 落 desktopTest 是因为要读源码树，common 源集没有文件 API。
 */
class DesktopCoilSingletonWiringTest {

    @Test
    fun `桌面 Coil 单例注册委派共享出口且不复用 API 出口`() {
        // 只看**代码**：注释里出现旧标识符（如说明"此前复用 X、现改为 Y"）是正常的，
        // 不该误报；真正要拦的是代码又接回去。
        val code = stripComments(mainKtFile().readText())

        assertTrue(
            code.contains("setSingletonImageLoaderFactory"),
            "Main.kt 不再注册 Coil 单例：桌面图片会完全加载不出来",
        )
        assertTrue(
            code.contains("sharedHanimeImageLoader("),
            "桌面 Coil 单例没有委派共享出口 sharedHanimeImageLoader()：" +
                "它会与 rememberHanimeImageLoader 各持一份 ImageLoader，进程级单例名存实亡",
        )
        assertFalse(
            code.contains("createHanimeHttpClient"),
            "桌面图片出口复用了 createHanimeHttpClient()（API/HTML 出口）：" +
                "会把 hanime1_session 绑到图床 host（vdownload.hembed.com）发出去",
        )
        assertFalse(
            code.contains("KtorNetworkFetcherFactory"),
            "桌面图片又挂回了 ktor3 取图器：与共享出口（createCdnFetchClient 的 OkHttp 取图器）分叉，" +
                "两条出口配置会各自漂移",
        )
    }

    /** 去掉块注释与行注释，只留下代码行。 */
    private fun stripComments(source: String): String =
        source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .lineSequence()
            .joinToString("\n") { it.substringBefore("//") }

    /** 桌面入口源码。从测试工作目录向上找仓库根，杜绝硬编码绝对路径（与 ModuleLayeringTest 同法）。 */
    private fun mainKtFile(): File {
        val file = File(repoRoot(), "desktopApp/src/main/kotlin/lovehan1me/desktop/Main.kt")
        assertTrue(file.isFile, "找不到桌面入口 Main.kt：${file.path}")
        return file
    }

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("从 ${File(".").absoluteFile} 向上找不到 settings.gradle.kts")
    }
}
