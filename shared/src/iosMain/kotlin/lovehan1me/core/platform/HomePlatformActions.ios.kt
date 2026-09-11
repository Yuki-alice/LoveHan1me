package lovehan1me.core.platform

import lovehan1me.core.domain.model.AppLanguage

// P6d-4：iOS 平台面降级（沙盒缓存目录/NSBundle 版本号随 P7 收口）
actual suspend fun getCacheDirSize(): Long = 0L

actual suspend fun clearCacheDir(): Boolean = false

actual fun applyAppLanguage(language: AppLanguage) {
}

actual fun switchLauncherIcon(alias: String) {
}

actual fun appVersionDisplay(): String = "1.0"

actual fun supportsPerAppLinks(): Boolean = false

actual suspend fun openBackupSink(uri: String): okio.Sink? = runCatching {
    // 全量备份导出：内存攒，close() 落临时文件并弹系统导出窗（fire-and-forget，
    // 详见 IosBackupFiles.ExportOnCloseSink）。
    ExportOnCloseSink(sanitizeBackupFileName(uri))
}.getOrNull()

actual suspend fun openBackupSource(uri: String): okio.Source? = runCatching {
    // 导入 launcher 回传的已是临时目录路径，直接读。
    TempFileSource(uri)
}.getOrNull()

actual fun appVersionNameRaw(): String = "1.0"
actual fun appVersionCodeRaw(): Int = 1

actual suspend fun writeBackupText(uri: String, content: String): Boolean {
    // lists 导出：落临时文件后挂起等用户在系统导出窗确认/取消，返回值准确。
    val path = writeTempBackupBytes(
        sanitizeBackupFileName(uri),
        content.encodeToByteArray(),
    ) ?: return false
    return presentBackupExportAndAwait(path)
}

actual suspend fun readBackupText(uri: String): String? {
    return readBackupFileBytes(uri)?.decodeToString()
}
actual fun applySecureMode(enabled: Boolean) {
}
actual fun recreateActivity() {
}
actual fun openPerAppLinksSettings() {
}

actual fun isPipPermissionGranted(): Boolean = true
actual fun openPipPermissionSettings() {
}
