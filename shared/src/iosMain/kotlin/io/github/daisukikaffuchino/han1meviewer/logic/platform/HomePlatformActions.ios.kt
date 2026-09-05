package io.github.daisukikaffuchino.han1meviewer.logic.platform

import io.github.daisukikaffuchino.han1meviewer.logic.model.AppLanguage

// P6d-4：iOS 平台面降级（沙盒缓存目录/NSBundle 版本号随 P7 收口）
actual suspend fun getCacheDirSize(): Long = 0L

actual suspend fun clearCacheDir(): Boolean = false

actual fun applyAppLanguage(language: AppLanguage) {
}

actual fun switchLauncherIcon(alias: String) {
}

actual fun appVersionDisplay(): String = "1.0"

actual fun supportsPerAppLinks(): Boolean = false

actual suspend fun openBackupSink(uri: String): okio.Sink? = null
actual suspend fun openBackupSource(uri: String): okio.Source? = null

actual fun appVersionNameRaw(): String = "1.0"
actual fun appVersionCodeRaw(): Int = 1

actual suspend fun writeBackupText(uri: String, content: String): Boolean = false
actual suspend fun readBackupText(uri: String): String? = null
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
