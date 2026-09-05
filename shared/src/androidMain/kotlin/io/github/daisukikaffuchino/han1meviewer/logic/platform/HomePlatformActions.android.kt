package io.github.daisukikaffuchino.han1meviewer.logic.platform

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabaseContext
import io.github.daisukikaffuchino.han1meviewer.logic.model.AppLanguage
import java.io.File

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
        "io.github.daisukikaffuchino.han1meviewer.LauncherAliasDefault",
        "io.github.daisukikaffuchino.han1meviewer.LauncherFakeCalc",
        "io.github.daisukikaffuchino.han1meviewer.LauncherFakeCornhub",
        "io.github.daisukikaffuchino.han1meviewer.LauncherFakeXxt"
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
