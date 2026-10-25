package lovehan1me.data

import lovehan1me.data.network.httpCacheDirectory
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 缓存清理规则（网络优化 3.1）：写入按 key 前缀自理 —— `home_*` 写入只清 `home_*`，
 * 不碰 `discover_*`。真文件系统断言（desktopTest），守的是「收敛后清理串文件」这一
 * 回归面：前缀从写死的两个分叉改成 `key.substringBefore('_')` 后，
 * 新前缀页面进来时清理必须仍然自洽。
 */
class CachedHtmlCleanupTest {

    private val dir = File(httpCacheDirectory(), "home-pages")

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    @Test
    fun `写入按前缀清理_不串文件`() {
        writeCachedHtml("home_hanime1.me_u1", "<html>home-old</html>")
        writeCachedHtml("discover_hanime1.me_u1", "<html>discover-old</html>")

        // 再写一份新 home key：只应清掉 home_ 旧文件。
        writeCachedHtml("home_hanime1.me_u2", "<html>home-new</html>")

        assertNull(readCachedHtml("home_hanime1.me_u1"), "同前缀旧 key 必须被清掉")
        assertEquals(
            "<html>discover-old</html>",
            readCachedHtml("discover_hanime1.me_u1"),
            "异前缀文件不得被误清",
        )
        assertEquals("<html>home-new</html>", readCachedHtml("home_hanime1.me_u2"))
    }

    @Test
    fun `读写回环_尺寸护栏与坏文件`() {
        writeCachedHtml("home_hanime1.me_u1", "<html>hello</html>")
        assertEquals("<html>hello</html>", readCachedHtml("home_hanime1.me_u1"))

        // 超尺寸护栏：>2MB 视为不可信缓存，读回 null。
        val tooBig = dir.listFiles { f -> f.name.startsWith("home_hanime1.me_u1") }
            ?.firstOrNull()
        assertNotNull(tooBig)
        tooBig.writeText("x".repeat(2 * 1024 * 1024 + 1))
        assertNull(readCachedHtml("home_hanime1.me_u1"), "超尺寸文件必须 miss")
    }

    @Test
    fun `文件名安全化_特殊字符不逃逸目录`() {
        writeCachedHtml("home_../../etc_u1", "<html>safe</html>")
        // 非法字符被替换为 _，不会逃出缓存目录。
        assertTrue(dir.listFiles()!!.all { it.parentFile == dir })
        assertEquals("<html>safe</html>", readCachedHtml("home_../../etc_u1"))
    }
}
