package lovehan1me.logic.dao

import lovehan1me.logic.dao.Han1meDatabaseContext.appContext

/**
 * Android：路径与 :app 原 5 个 XxxDatabaseInstance.kt 完全一致（getDatabasePath），
 * 文件名不变保证老用户数据无缝升级。Context 依赖 Han1meDatabaseContext（已在
 * HanimeApplication.onCreate 经 DataStoreManager.initialize(context) 注入）。
 */
actual object Han1meDatabases {
    private fun dbPath(fileName: String): String =
        appContext.getDatabasePath(fileName).absolutePath

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
