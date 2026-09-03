package io.github.daisukikaffuchino.utils

import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.model.AppLanguage
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

// iOS：读共享设置 appLanguage（SYSTEM 回退系统首选语言）
internal actual fun currentAppLanguage(): String = when (val lang = SettingsRepository.current.appLanguage) {
    AppLanguage.SYSTEM -> NSLocale.currentLocale.languageCode ?: "en"
    else -> lang.code.orEmpty().ifEmpty { "en" }
}
