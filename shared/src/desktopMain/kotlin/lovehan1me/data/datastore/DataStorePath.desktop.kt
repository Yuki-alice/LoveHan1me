package lovehan1me.data.datastore

import java.io.File

// Desktop(JVM)：~/.lovehan1me/datastore/<fileName>
actual fun dataStoreFilePath(fileName: String): String {
    val dir = File(System.getProperty("user.home"), ".lovehan1me/datastore")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, fileName).absolutePath
}
