package lovehan1me.core.platform

import lovehan1me.core.domain.model.AppLanguage
import lovehan1me.core.util.LogUtil
import java.io.File
import java.util.Locale
import okio.sink
import okio.source

// P6d-4：桌面缓存目录约定 ~/.lovehan1me/cache（与 DataStore 的 ~/.lovehan1me 同根）；
// 精细的缓存分区与语言实时切换随 P7 收口
private val desktopCacheDir = File(System.getProperty("user.home"), ".lovehan1me/cache")

private fun File?.folderSize(): Long {
    var size = 0L
    this?.listFiles()?.forEach { file -> size += if (file.isDirectory) file.folderSize() else file.length() }
    return size
}

actual suspend fun getCacheDirSize(): Long = desktopCacheDir.folderSize()

actual suspend fun clearCacheDir(): Boolean = desktopCacheDir.deleteRecursively()

/**
 * 系统默认 Locale 的原始值。`SYSTEM` 档要还原成**它**，而不是"当前值"——
 * 否则用户从 English 切回"跟随系统"就切不回去了。
 */
private val systemDefaultLocale: Locale = Locale.getDefault()

/**
 * 桌面语言生效（M5-2：对齐 Android 的 `AppLanguageManager.applyStoredLanguage`）。
 *
 * Android 走 `AppCompatDelegate.setApplicationLocales`；桌面没有那套框架，
 * 但 **Compose Resources（`Res.string.*`）按 `Locale.getDefault()` 解析**，
 * 所以在启动早期设默认 Locale，`.cvr` 就会落到正确源集
 * （values / values-zh-rCN / values-zh-rTW）——这正是"语言设置看起来没生效"的根因：
 * 桌面此前只有 [LanguageHelper] 那条链路读设置，资源字符串仍跟系统语言。
 *
 * 只改 **DISPLAY** 类别（`Locale.getDefault()` 即取 DISPLAY），不动 FORMAT：
 * 避免顺带改掉数字/日期格式的解析行为。
 *
 * ⚠️ 必须在**任何资源读取之前**调用（桌面入口 `Main.kt` 的初始化段里已经调了）；
 * 运行中切换语言对已缓存的资源不保证重取，属 P7 的实时切换议题。
 */
actual fun applyAppLanguage(language: AppLanguage) {
    val target = language.code?.let { Locale.forLanguageTag(it) } ?: systemDefaultLocale
    if (Locale.getDefault() == target) return
    Locale.setDefault(Locale.Category.DISPLAY, target)
    LogUtil.d("Language", "桌面默认 Locale -> $target（appLanguage=${language.preferenceValue}）")
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
