package lovehan1me.data.datastore

import java.io.File

// Desktop(JVM)：OS 规范用户数据目录（见 DesktopAppPaths；老 ~/.lovehan1me 自动迁移）。
actual fun dataStoreFilePath(fileName: String): String {
    val dir = lovehan1me.core.platform.DesktopAppPaths.dataDir("datastore")
    return File(dir, fileName).absolutePath
}
