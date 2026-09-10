package lovehan1me.core.util

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

// Android：保持现状语义 —— AppCompat 覆盖语言优先，未设置回退系统 Locale
internal actual fun currentAppLanguage(): String =
    AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
        ?: Locale.getDefault().toLanguageTag()
