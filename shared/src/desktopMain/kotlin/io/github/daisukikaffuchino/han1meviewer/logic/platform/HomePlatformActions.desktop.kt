package io.github.daisukikaffuchino.han1meviewer.logic.platform

import io.github.daisukikaffuchino.han1meviewer.logic.model.AppLanguage
import java.io.File

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
