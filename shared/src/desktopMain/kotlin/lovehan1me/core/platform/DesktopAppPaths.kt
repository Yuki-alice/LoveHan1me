package lovehan1me.core.platform

import lovehan1me.core.util.LogUtil
import java.io.File

/**
 * 桌面用户数据目录（数据库 + 设置）。
 *
 * 放安装路径不可行：jpackage 产物装在只读位置（macOS `/Applications` 带
 * translocation、Windows Program Files 需提权、Linux `/opt` 属 root），
 * 首启即因权限被拒绝崩溃，且多用户/自动更新都会炸。故按各 OS 规范走
 * **用户数据目录**（此前 `~/.lovehan1me` 藏在隐藏夹，看不见也不好备份）：
 * - macOS：`~/Library/Application Support/LoveHan1me`
 * - Windows：`%APPDATA%/LoveHan1me`
 * - Linux/其它：`$XDG_DATA_HOME/LoveHan1me`，未设则 `~/.local/share/LoveHan1me`
 *
 * 只搬 `db/`（Room 五库）与 `datastore/`（设置）：`cache/`、`runtime/`（网关解包）、
 * `cf-browser-profile`、`pictures/`、`http_cache` 是可再生的旁路，搬它们只加风险。
 * 下载目录默认 `~/LoveHan1me/downloads` 本就可见，不动（改路径走下载设置页）。
 */
internal object DesktopAppPaths {
    private const val TAG = "DesktopPaths"
    private const val APP_DIR_NAME = "LoveHan1me"
    private const val LEGACY_DIR_NAME = ".lovehan1me"
    private const val MIGRATED_MARKER = ".migrated-from-legacy"

    /**
     * 用户数据根（`db/`、`datastore/` 的父目录）。
     *
     * 参数全可注入：`os.name` 在各 OS 上长得不一样（"Mac OS X" / "Windows 11" /
     * "Linux"），单测按名断分支，不依赖本机系统。
     */
    internal fun userDataRoot(
        osName: String = System.getProperty("os.name", ""),
        home: String = System.getProperty("user.home", ""),
        env: (String) -> String? = System::getenv,
    ): File = when {
        osName.startsWith("Windows", ignoreCase = true) ->
            File(
                env("APPDATA")?.takeIf { it.isNotBlank() }
                    ?: File(home, "AppData/Roaming").absolutePath,
                APP_DIR_NAME,
            )
        osName.startsWith("Mac OS X", ignoreCase = true) ->
            File(home, "Library/Application Support/$APP_DIR_NAME")
        else ->
            File(
                env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    ?: File(home, ".local/share").absolutePath,
                APP_DIR_NAME,
            )
    }

    internal fun legacyRoot(
        home: String = System.getProperty("user.home", ""),
    ): File = File(home, LEGACY_DIR_NAME)

    /** `db` / `datastore` 子目录（建好才返回）。 */
    fun dataDir(
        sub: String,
        osName: String = System.getProperty("os.name", ""),
        home: String = System.getProperty("user.home", ""),
        env: (String) -> String? = System::getenv,
    ): File = File(userDataRoot(osName, home, env), sub).also {
        ensureMigratedFromLegacy(osName, home, env)
        if (!it.exists()) it.mkdirs()
    }

    /**
     * 老数据一次性迁移（`~/.lovehan1me/{db,datastore}` → 新位置）。
     *
     * 触发条件（缺一不可）：新位置无迁移标记、老位置确有东西。
     * 新位置已有数据时**不覆盖**（用户可能两边各有一份，以新为准，老目录原样保留）。
     * 幂等：成功后写标记，重复调用直接返回。
     */
    @Synchronized
    internal fun ensureMigratedFromLegacy(
        osName: String = System.getProperty("os.name", ""),
        home: String = System.getProperty("user.home", ""),
        env: (String) -> String? = System::getenv,
    ): Boolean {
        val root = userDataRoot(osName, home, env)
        if (File(root, MIGRATED_MARKER).exists()) return false
        val legacy = legacyRoot(home)
        val wanted = listOf("db", "datastore")
        if (wanted.none { File(legacy, it).exists() }) {
            // 老位置连这两个目录都没有：全新安装，写标记免得以后每次都看一遍。
            root.mkdirs()
            File(root, MIGRATED_MARKER).createNewFile()
            return false
        }
        var moved = false
        for (name in wanted) {
            val src = File(legacy, name)
            val dst = File(root, name)
            if (!src.exists() || dst.exists()) continue
            runCatching {
                dst.parentFile?.mkdirs()
                src.copyRecursively(dst)
                moved = true
            }.onFailure { LogUtil.e(TAG, "migrate $name failed", it) }
        }
        if (moved) {
            runCatching { File(root, MIGRATED_MARKER).createNewFile() }
            LogUtil.i(TAG, "app data migrated to ${root.absolutePath} (legacy kept)")
        }
        return moved
    }
}
