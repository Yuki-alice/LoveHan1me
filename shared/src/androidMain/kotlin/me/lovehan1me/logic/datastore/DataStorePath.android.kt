package me.lovehan1me.logic.datastore

import me.lovehan1me.logic.dao.Han1meDatabaseContext

/**
 * Android：复用共享层已有的 Context holder（[Han1meDatabaseContext]），
 * 路径与旧 `preferencesDataStoreFile(name)` 保持一致：filesDir/datastore/<fileName>。
 */
actual fun dataStoreFilePath(fileName: String): String {
    val dir = Han1meDatabaseContext.appContext.filesDir.resolve("datastore")
    if (!dir.exists()) dir.mkdirs()
    return dir.resolve(fileName).absolutePath
}
