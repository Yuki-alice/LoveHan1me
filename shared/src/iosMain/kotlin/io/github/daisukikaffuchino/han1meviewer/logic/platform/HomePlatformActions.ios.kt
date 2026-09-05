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
