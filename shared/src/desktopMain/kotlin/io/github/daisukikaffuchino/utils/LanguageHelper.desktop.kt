package io.github.daisukikaffuchino.utils

import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.model.AppLanguage
import java.util.Locale

// Desktop(JVM)：读共享设置 appLanguage（SYSTEM 回退系统 Locale）
internal actual fun currentAppLanguage(): String = when (val lang = SettingsRepository.current.appLanguage) {
    AppLanguage.SYSTEM -> Locale.getDefault().toLanguageTag()
    else -> lang.code.orEmpty().ifEmpty { Locale.getDefault().toLanguageTag() }
}
