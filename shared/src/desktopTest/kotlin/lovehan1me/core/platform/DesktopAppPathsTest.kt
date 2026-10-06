package lovehan1me.core.platform

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 数据目录守卫：OS 分支 + 老数据迁移（只搬 db/datastore，不碰 cache 等旁路）。
class DesktopAppPathsTest {

    private val tempRoots = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        tempRoots.forEach { it.deleteRecursively() }
        tempRoots.clear()
    }

    private fun tempHome(): File =
        File(System.getProperty("java.io.tmpdir"), "han1me-paths-${System.nanoTime()}")
            .also { it.mkdirs(); tempRoots += it }

    @Test
    fun `mac走Library_Windows走APPDATA_Linux走XDG`() {
        val home = tempHome().absolutePath
        assertEquals(
            File(home, "Library/Application Support/LoveHan1me").absolutePath,
            DesktopAppPaths.userDataRoot(osName = "Mac OS X", home = home).absolutePath,
        )
        assertEquals(
            File("D:/Data", "LoveHan1me").absolutePath,
            DesktopAppPaths.userDataRoot(
                osName = "Windows 11",
                home = home,
                env = { if (it == "APPDATA") "D:/Data" else null },
            ).absolutePath,
        )
        assertEquals(
            File(home, "AppData/Roaming/LoveHan1me").absolutePath,
            DesktopAppPaths.userDataRoot(
                osName = "Windows 10",
                home = home,
                env = { null },
            ).absolutePath,
        )
        assertEquals(
            File("/xdg", "LoveHan1me").absolutePath,
            DesktopAppPaths.userDataRoot(
                osName = "Linux",
                home = home,
                env = { if (it == "XDG_DATA_HOME") "/xdg" else null },
            ).absolutePath,
        )
        assertEquals(
            File(home, ".local/share/LoveHan1me").absolutePath,
            DesktopAppPaths.userDataRoot(osName = "Linux", home = home, env = { null }).absolutePath,
        )
    }

    @Test
    fun `老数据只搬db与datastore_旁路不动_幂等`() {
        val home = tempHome().absolutePath
        val legacy = File(home, ".lovehan1me").also { it.mkdirs() }
        File(legacy, "db").mkdirs()
        File(legacy, "db/history.db").writeText("db-bytes")
        File(legacy, "datastore").mkdirs()
        File(legacy, "datastore/settings.preferences_pb").writeText("prefs-bytes")
        File(legacy, "cache").mkdirs()
        File(legacy, "cache/junk").writeText("junk")

        val env: (String) -> String? = { null }
        assertTrue(DesktopAppPaths.ensureMigratedFromLegacy("Linux", home, env))

        val root = DesktopAppPaths.userDataRoot("Linux", home, env)
        assertEquals("db-bytes", File(root, "db/history.db").readText())
        assertEquals("prefs-bytes", File(root, "datastore/settings.preferences_pb").readText())
        // 旁路不搬。
        assertFalse(File(root, "cache").exists())
        // 老目录原样保留（回滚余地）。
        assertTrue(File(legacy, "db/history.db").exists())
        // 幂等：第二次是 no-op。
        assertFalse(DesktopAppPaths.ensureMigratedFromLegacy("Linux", home, env))
    }

    @Test
    fun `新位置已有数据不覆盖`() {
        val home = tempHome().absolutePath
        val legacy = File(home, ".lovehan1me").also { it.mkdirs() }
        File(legacy, "db").mkdirs()
        File(legacy, "db/history.db").writeText("old-bytes")

        val env: (String) -> String? = { null }
        val root = DesktopAppPaths.userDataRoot("Linux", home, env)
        File(root, "db").mkdirs()
        File(root, "db/history.db").writeText("new-bytes")

        DesktopAppPaths.ensureMigratedFromLegacy("Linux", home, env)
        // 以新为准，老文件不动。
        assertEquals("new-bytes", File(root, "db/history.db").readText())
    }

    @Test
    fun `全新安装直接建标记不报错`() {
        val home = tempHome().absolutePath
        val env: (String) -> String? = { null }
        assertFalse(DesktopAppPaths.ensureMigratedFromLegacy("Linux", home, env))
        assertTrue(File(DesktopAppPaths.userDataRoot("Linux", home, env), ".migrated-from-legacy").exists())
    }
}
