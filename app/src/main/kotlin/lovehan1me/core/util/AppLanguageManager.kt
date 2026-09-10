package lovehan1me.core.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import lovehan1me.logic.SettingsRepository
import lovehan1me.core.domain.model.AppLanguage

object AppLanguageManager {
    const val PREFERENCE_KEY = "app_language"

    fun current(@Suppress("UNUSED_PARAMETER") context: Context): AppLanguage =
        SettingsRepository.current.appLanguage

    fun applyStoredLanguage(context: Context) {
        val language = current(context)
        setAppLanguage(language)
    }

    suspend fun select(context: Context, language: AppLanguage) {
        SettingsRepository.setLanguage(language)
        setAppLanguage(language)
    }

    fun setAppLanguage(language: AppLanguage) {
        val locales = language.code?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
