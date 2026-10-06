@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.core.platform

import lovehan1me.core.domain.model.AppLanguage
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURLCache
import platform.Foundation.NSUserDefaults

/**
 * P0-2：iOS 清缓存生效（此前恒返回 0/false，按钮是摆设）。
 *
 * iOS 侧可清的有两处：`NSTemporaryDirectory()`（备份中转、图片解码临时文件等）
 * 与 `NSURLCache.sharedURLCache`（Ktor Darwin 经 NSURLSession 落盘的 HTTP 缓存，
 * 响应头里可能带 Set-Cookie ——不清它等于"清缓存"不清登录痕迹）。
 * Room/NSString 数据目录（`Documents/db`、`Documents/datastore`）与下载目录一律不动。
 */
actual suspend fun getCacheDirSize(): Long = runCatching {
    tmpDirSize() + NSURLCache.sharedURLCache().currentDiskUsage.toLong()
}.getOrDefault(0L)

actual suspend fun clearCacheDir(): Boolean = runCatching {
    val fm = NSFileManager.defaultManager
    val tmp = NSTemporaryDirectory()
    fm.subpathsOfDirectoryAtPath(tmp, null)?.forEach { relative ->
        fm.removeItemAtPath("$tmp/$relative", null)
    }
    NSURLCache.sharedURLCache().removeAllCachedResponses()
    true
}.getOrDefault(false)

private fun tmpDirSize(): Long {
    val fm = NSFileManager.defaultManager
    val tmp = NSTemporaryDirectory()
    var total = 0L
    fm.subpathsOfDirectoryAtPath(tmp, null)?.forEach { relative ->
        val attrs = fm.attributesOfItemAtPath("$tmp/$relative", null) as? Map<*, *>
        total += (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    }
    return total
}

/**
 * iOS 语言生效（M5-2：对齐 Android 的启动时应用）。
 *
 * 平台事实：iOS 的"应用语言"由 `NSUserDefaults` 的 `AppleLanguages` 决定，
 * 而 Foundation 在**进程启动时**读取它 —— 所以**本次运行的界面不会立即变**，
 * **下次启动才生效**（与 Android 的 AppCompatDelegate 即时重建不同，这是平台语义，不是实现偷懒）。
 * 仍然照 Android 一样在启动时应用一次，保证"设置过的语言下次启动准生效"。
 *
 * ⚠️ **未经编译验证**（Windows 编不了 Kotlin/Native，见项目约定）。
 * 首次 CI 编译若报符号形态不符，备用写法是
 * `NSUserDefaults.standardUserDefaults.setValue(listOf(tag), forKey = APPLE_LANGUAGES_KEY)`
 * （KMP/Native 两种绑定都存在；本文件用的是 `setObject`）。
 */
actual fun applyAppLanguage(language: AppLanguage) {
    val tag = language.code ?: return
    NSUserDefaults.standardUserDefaults.setObject(listOf(tag), forKey = APPLE_LANGUAGES_KEY)
}

private const val APPLE_LANGUAGES_KEY = "AppleLanguages"

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
