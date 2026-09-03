package io.github.daisukikaffuchino.han1meviewer.logic.dao

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS：`Documents/db/<文件名>`（参照 DataStore 的 Documents/datastore 姿势，P2b 先例）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object Han1meDatabases {
    private fun dbPath(fileName: String): String {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).first() as String
        val dir = "$documents/db"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        return "$dir/$fileName"
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
