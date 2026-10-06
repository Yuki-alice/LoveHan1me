package lovehan1me.data.database.dao

import java.io.File

/**
 * Desktop(JVM)：OS 规范用户数据目录下的 `db/<文件名>`（见 DesktopAppPaths；
 * 老 `~/.lovehan1me/db` 自动迁移）。
 * 桌面端为新部署，无 Android 历史数据迁移需求；文件名沿用 Android 命名便于将来互导。
 */
actual object Han1meDatabases {
    private fun dbPath(fileName: String): String {
        val dir = lovehan1me.core.platform.DesktopAppPaths.dataDir("db")
        return File(dir, fileName).absolutePath
    }

    actual val history: HistoryDatabase by lazy {
        createHistoryDatabase(dbPath("history.db"))
    }
    actual val download: DownloadDatabase by lazy {
        createDownloadDatabase(dbPath("download.db"))
    }
    actual val checkInRecord: CheckInRecordDatabase by lazy {
        createCheckInRecordDatabase(dbPath("check_in_records"))
    }
    actual val localList: LocalListDatabase by lazy {
        createLocalListDatabase(dbPath("local_list.db"))
    }
    actual val danmaku: DanmakuDatabase by lazy {
        createDanmakuDatabase(dbPath("danmaku.db"))
    }
}
