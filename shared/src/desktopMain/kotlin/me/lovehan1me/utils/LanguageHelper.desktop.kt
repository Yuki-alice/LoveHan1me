package me.lovehan1me.utils

import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.logic.model.AppLanguage
import java.util.Locale

// Desktop(JVM)：读共享设置 appLanguage（SYSTEM 回退系统 Locale）
internal actual fun currentAppLanguage(): String = when (val lang = SettingsRepository.current.appLanguage) {
    AppLanguage.SYSTEM -> Locale.getDefault().toLanguageTag()
    else -> lang.code.orEmpty().ifEmpty { Locale.getDefault().toLanguageTag() }
}
