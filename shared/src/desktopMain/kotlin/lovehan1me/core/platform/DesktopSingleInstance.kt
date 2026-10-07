package lovehan1me.core.platform

import lovehan1me.core.util.LogUtil
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * 桌面单实例锁。
 *
 * ## 为什么需要它
 * DataStore 只保证**进程内**单实例（`DataStoreManager` 的 `initialized` 旗 +
 * `withInitLock`），管不了**进程外**：两个系统进程同时写同一个
 * `settings.preferences_pb`，DataStore 的 tmp + 改名就会撞车，崩溃长这样——
 * `Unable to rename ... settings.preferences_pb.tmp to ... settings.preferences_pb.
 * This likely means that there are multiple instances of DataStore for this file.`
 * 进程里再怎么找也找不到第二个创建点，因为第二个在另一个进程里
 * （安装版和 `./gradlew :desktopApp:run` 同时开、僵尸进程没退，都会触发）。
 *
 * ## 语义
 * - 拿到锁返回 true，锁由本进程持有直到退出（channel 常驻，不关）。
 * - `tryLock()` 返回 null = 别的进程正持有 → 返回 false，调用方直接退出，
 *   不再往下走（也就不会去碰 DataStore 的文件）。
 * - 同一 JVM 重复调用返回 true（`OverlappingFileLockException` 即本进程已持有）。
 * - 其它异常（权限/只读盘）**放行 true**：拿不到锁不等于"别人在跑"，
 *   拦启动只会把真正的权限错误藏成"已在运行"，让 DataStore 自己报错更有用。
 *
 * ## 测试边界（诚实说明）
 * false 路径只能由**双进程**触发，单测里造不出来（同 JVM 先加锁再调，
 * 抛的是 `OverlappingFileLockException` 而不是返回 null）。单测只钉：
 * 首次获取成功、重复获取不死锁、陈旧 tmp 清理。false 路径靠双开实测。
 */
object DesktopSingleInstance {
    private const val LOCK_FILE_NAME = ".app-lock"

    private var lockChannel: FileChannel? = null
    private var fileLock: FileLock? = null

    @Synchronized
    fun tryAcquireAppLock(
        osName: String = System.getProperty("os.name", ""),
        home: String = System.getProperty("user.home", ""),
        env: (String) -> String? = System::getenv,
    ): Boolean {
        if (fileLock?.isValid == true) return true
        val root = DesktopAppPaths.userDataRoot(osName, home, env).also { it.mkdirs() }
        return try {
            val channel = RandomAccessFile(File(root, LOCK_FILE_NAME), "rw").channel
            val lock = channel.tryLock()
            if (lock == null) {
                // 别的进程正持有：什么都别碰（尤其不能清 tmp，那是活进程的半写文件），直接报 false。
                runCatching { channel.close() }
                false
            } else {
                lockChannel = channel
                fileLock = lock
                // 锁在手，没有别的活进程在写 —— 此时清上次崩溃残留的半写 tmp 才安全。
                clearStaleDatastoreTmp(root)
                true
            }
        } catch (e: OverlappingFileLockException) {
            true
        } catch (e: Exception) {
            LogUtil.w("SingleInstance", "app lock unavailable, proceeding without it", e)
            true
        }
    }

    /**
     * 清上次崩溃残留的 DataStore 半写文件（`datastore` 目录下的 `.tmp` 文件）。
     *
     * 只能在持有单实例锁后调：tmp 名是固定的（`<name>.preferences_pb.tmp`），
     * 无锁清理会删掉别的活进程正在写的半成品。
     */
    internal fun clearStaleDatastoreTmp(root: File) {
        val dir = File(root, "datastore")
        if (!dir.isDirectory) return
        dir.listFiles { f -> f.isFile && f.name.endsWith(".tmp") }?.forEach { tmp ->
            runCatching { tmp.delete() }
                .onFailure { LogUtil.w("SingleInstance", "delete stale tmp failed: ${tmp.name}", it) }
        }
    }

    /** 释放锁。**仅供测试**（生产代码进程常驻，不释放）。 */
    internal fun releaseForTest() {
        runCatching { fileLock?.release() }
        runCatching { lockChannel?.close() }
        fileLock = null
        lockChannel = null
    }
}
