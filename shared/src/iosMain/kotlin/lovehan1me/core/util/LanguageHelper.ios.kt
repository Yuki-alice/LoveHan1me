package lovehan1me.core.util

import lovehan1me.logic.SettingsRepository
import lovehan1me.core.domain.model.AppLanguage
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

// iOS：读共享设置 appLanguage（SYSTEM 回退系统首选语言）
internal actual fun currentAppLanguage(): String = when (val lang = SettingsRepository.current.appLanguage) {
    AppLanguage.SYSTEM -> NSLocale.currentLocale.languageCode ?: "en"
    else -> lang.code.orEmpty().ifEmpty { "en" }
}
