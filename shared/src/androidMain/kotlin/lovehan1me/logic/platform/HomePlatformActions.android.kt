package lovehan1me.logic.platform

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import lovehan1me.logic.dao.Han1meDatabaseContext
import lovehan1me.logic.model.AppLanguage
import java.io.File
import okio.sink
import okio.source

private fun File?.folderSize(): Long {
    var size = 0L
    this?.listFiles()?.forEach { file -> size += if (file.isDirectory) file.folderSize() else file.length() }
    return size
}

actual suspend fun getCacheDirSize(): Long = Han1meDatabaseContext.appContext.cacheDir.folderSize()

actual suspend fun clearCacheDir(): Boolean = Han1meDatabaseContext.appContext.cacheDir?.deleteRecursively() == true

// 原 AppLanguageManager.setAppLanguage 照搬（P6a：LanguageHelper KMP 化后 Android 保持 AppCompatDelegate 语义）
actual fun applyAppLanguage(language: AppLanguage) {
    val locales = language.code?.let(LocaleListCompat::forLanguageTags)
        ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
}

actual fun switchLauncherIcon(alias: String) {
    val context = Han1meDatabaseContext.appContext
    val pm = context.packageManager
    val allAliases = listOf(
        "lovehan1me.LauncherAliasDefault",
        "lovehan1me.LauncherFakeCalc",
        "lovehan1me.LauncherFakeCornhub",
        "lovehan1me.LauncherFakeXxt"
    )
    allAliases.forEach { a ->
        val state = if (a == alias)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, a),
            state,
            PackageManager.DONT_KILL_APP
        )
    }
}

actual fun appVersionDisplay(): String {
    val context = Han1meDatabaseContext.appContext
    val pm = context.packageManager
    val info = pm.getPackageInfo(context.packageName, 0)
    return "${info.versionName}(${info.longVersionCode})"
}

actual fun supportsPerAppLinks(): Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

actual suspend fun openBackupSink(uri: String): okio.Sink? =
    Han1meDatabaseContext.appContext.contentResolver.openOutputStream(android.net.Uri.parse(uri))
        ?.sink()

actual suspend fun openBackupSource(uri: String): okio.Source? =
    Han1meDatabaseContext.appContext.contentResolver.openInputStream(android.net.Uri.parse(uri))
        ?.source()

actual fun appVersionNameRaw(): String {
    val context = Han1meDatabaseContext.appContext
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
}

actual fun appVersionCodeRaw(): Int {
    val context = Han1meDatabaseContext.appContext
    return context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
}

actual suspend fun writeBackupText(uri: String, content: String): Boolean = runCatching {
    Han1meDatabaseContext.appContext.contentResolver.openOutputStream(android.net.Uri.parse(uri))
        ?.bufferedWriter()?.use { it.write(content) } ?: error("Unable to open output file")
    true
}.getOrDefault(false)

actual suspend fun readBackupText(uri: String): String? = runCatching {
    Han1meDatabaseContext.appContext.contentResolver.openInputStream(android.net.Uri.parse(uri))
        ?.bufferedReader()?.use { it.readText() } ?: error("Unable to open input file")
}.getOrNull()

actual fun applySecureMode(enabled: Boolean) {
    val a = CurrentActivityHolder.activity ?: return
    if (enabled) a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    else a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
}

actual fun recreateActivity() {
    CurrentActivityHolder.activity?.recreate()
}
actual fun openPerAppLinksSettings() {
    val context = Han1meDatabaseContext.appContext
    val activity = CurrentActivityHolder.activity ?: return
    val intent = android.content.Intent().apply {
        action = android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
        addCategory(android.content.Intent.CATEGORY_DEFAULT)
        data = android.net.Uri.parse("package:${context.packageName}")
        flags = android.content.Intent.FLAG_ACTIVITY_NO_HISTORY or android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
    }
    runCatching { activity.startActivity(intent) }
        .onFailure { it.printStackTrace() }
}

actual fun isPipPermissionGranted(): Boolean {
    val context = Han1meDatabaseContext.appContext
    val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
    val mode = appOps.unsafeCheckOpNoThrow(
        android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
        android.os.Process.myUid(),
        context.packageName,
    )
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

actual fun openPipPermissionSettings() {
    val context = Han1meDatabaseContext.appContext
    val intent = android.content.Intent(
        "android.settings.PICTURE_IN_PICTURE_SETTINGS",
        android.net.Uri.parse("package:${context.packageName}")
    )
    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

actual fun isDeviceSecure(): Boolean {
    val context = Han1meDatabaseContext.appContext
    val km = context.getSystemService(android.app.KeyguardManager::class.java)
    return km.isDeviceSecure
}
