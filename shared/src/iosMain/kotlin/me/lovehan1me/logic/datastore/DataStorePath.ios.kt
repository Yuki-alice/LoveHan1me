package me.lovehan1me.logic.datastore

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

// iOS：Documents/datastore/<fileName>
@OptIn(ExperimentalForeignApi::class)
actual fun dataStoreFilePath(fileName: String): String {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).first() as String
    val dir = "$documents/datastore"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return "$dir/$fileName"
}
