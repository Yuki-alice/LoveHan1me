package lovehan1me.data.database.dao

import java.io.File

/**
 * Desktop(JVM)：`~/.han1meviewer/db/<文件名>`（对齐 DataStore 的 `~/.han1meviewer/datastore` 姿势）。
 * 桌面端为新部署，无 Android 历史数据迁移需求；文件名沿用 Android 命名便于将来互导。
 */
actual object Han1meDatabases {
    private fun dbPath(fileName: String): String {
        val dir = File(System.getProperty("user.home"), ".han1meviewer/db")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName).absolutePath
    }

    actual val miscellany: MiscellanyDatabase by lazy {
        createMiscellanyDatabase(dbPath("miscellany.db"))
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
}
