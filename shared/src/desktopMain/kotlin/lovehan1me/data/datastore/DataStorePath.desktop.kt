package lovehan1me.data.datastore

import java.io.File

// Desktop(JVM)：~/.han1meviewer/datastore/<fileName>
actual fun dataStoreFilePath(fileName: String): String {
    val dir = File(System.getProperty("user.home"), ".han1meviewer/datastore")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, fileName).absolutePath
}
