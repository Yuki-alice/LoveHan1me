package lovehan1me.logic.platform

import lovehan1me.logic.model.AppLanguage
import java.io.File
import okio.sink
import okio.source

// P6d-4：桌面缓存目录约定 ~/.han1meviewer/cache（与 DataStore 的 ~/.han1meviewer 同根）；
// 精细的缓存分区与语言实时切换随 P7 收口
private val desktopCacheDir = File(System.getProperty("user.home"), ".han1meviewer/cache")

private fun File?.folderSize(): Long {
    var size = 0L
    this?.listFiles()?.forEach { file -> size += if (file.isDirectory) file.folderSize() else file.length() }
    return size
}

actual suspend fun getCacheDirSize(): Long = desktopCacheDir.folderSize()

actual suspend fun clearCacheDir(): Boolean = desktopCacheDir.deleteRecursively()

actual fun applyAppLanguage(language: AppLanguage) {
    // 偏好已由 SettingsRepository.setLanguage 持久化；桌面实时生效随 P7
}

actual fun switchLauncherIcon(alias: String) {
}

actual fun appVersionDisplay(): String = "0.1.0" // 与 desktopApp nativeDistributions.packageVersion 对齐

actual fun supportsPerAppLinks(): Boolean = false

actual suspend fun openBackupSink(uri: String): okio.Sink? =
    runCatching { java.io.File(uri).sink() }.getOrNull()

actual suspend fun openBackupSource(uri: String): okio.Source? =
    runCatching { java.io.File(uri).source() }.getOrNull()

actual fun appVersionNameRaw(): String = "0.1.0"
actual fun appVersionCodeRaw(): Int = 1

actual suspend fun writeBackupText(uri: String, content: String): Boolean = runCatching {
    java.io.File(uri).writeText(content)
    true
}.getOrDefault(false)

actual suspend fun readBackupText(uri: String): String? = runCatching {
    java.io.File(uri).readText()
}.getOrNull()

actual fun applySecureMode(enabled: Boolean) {
}

actual fun recreateActivity() {
}
actual fun openPerAppLinksSettings() {
}

actual fun isPipPermissionGranted(): Boolean = true
actual fun openPipPermissionSettings() {
}
actual fun isDeviceSecure(): Boolean = true
