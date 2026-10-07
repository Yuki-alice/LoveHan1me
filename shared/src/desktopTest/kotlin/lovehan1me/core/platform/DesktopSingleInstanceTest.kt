package lovehan1me.core.platform

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 单实例锁守卫：首次获取成功、同 JVM 重复获取不死锁、陈旧 tmp 只清 datastore 下的。
//
// 诚实边界：false 路径（别的进程持有）只能双开实测，单测里造不出来 ——
// 同 JVM 先加锁再调抛的是 OverlappingFileLockException（按"已持有"处理），
// 而不是跨进程时的 tryLock() == null。
class DesktopSingleInstanceTest {

    private val tempRoots = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        DesktopSingleInstance.releaseForTest()
        tempRoots.forEach { it.deleteRecursively() }
        tempRoots.clear()
    }

    private fun tempHome(): String =
        File(System.getProperty("java.io.tmpdir"), "han1me-lock-${System.nanoTime()}")
            .also { it.mkdirs(); tempRoots += it }.absolutePath

    @Test
    fun `首次获取成功_重复获取不死锁_释放后可重拿`() {
        val home = tempHome()
        val env: (String) -> String? = { null }

        assertTrue(
            DesktopSingleInstance.tryAcquireAppLock("Linux", home, env),
            "首次获取锁应成功",
        )
        assertTrue(
            DesktopSingleInstance.tryAcquireAppLock("Linux", home, env),
            "同 JVM 重复获取应直接返回 true，不能死锁",
        )

        DesktopSingleInstance.releaseForTest()
        assertTrue(
            DesktopSingleInstance.tryAcquireAppLock("Linux", home, env),
            "释放后应能重新获取",
        )
    }

    @Test
    fun `陈旧tmp只清datastore下_有效文件不动`() {
        val home = tempHome()
        val env: (String) -> String? = { null }
        val root = DesktopAppPaths.userDataRoot("Linux", home, env)
        val store = File(root, "datastore").also { it.mkdirs() }
        File(store, "settings.preferences_pb").writeText("valid")
        File(store, "auth.preferences_pb").writeText("valid")
        File(store, "settings.preferences_pb.tmp").writeText("stale")
        File(store, "auth.preferences_pb.tmp").writeText("stale")
        File(root, "outside.tmp").writeText("keep")

        assertTrue(DesktopSingleInstance.tryAcquireAppLock("Linux", home, env))

        assertFalse(File(store, "settings.preferences_pb.tmp").exists(), "残留 tmp 应被清掉")
        assertFalse(File(store, "auth.preferences_pb.tmp").exists(), "残留 tmp 应被清掉")
        assertTrue(File(store, "settings.preferences_pb").exists(), "有效存档不能动")
        assertTrue(File(store, "auth.preferences_pb").exists(), "有效存档不能动")
        assertTrue(File(root, "outside.tmp").exists(), "datastore 之外的文件不能动")
    }
}
