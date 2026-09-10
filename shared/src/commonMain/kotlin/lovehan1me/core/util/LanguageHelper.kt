package lovehan1me.core.util

/**
 * 语言三元表示（P6a：commonMain 无 java.util.Locale 的轻量替代）。
 * "zh-CN" → language="zh"、country="CN"；"en" → language="en"。
 */
data class PreferredLanguage(
    val language: String,
    val country: String,
) {
    fun toLanguageTag(): String =
        if (country.isEmpty()) language else "$language-$country"

    companion object {
        fun fromTag(tag: String): PreferredLanguage {
            val parts = tag.split('-')
            return PreferredLanguage(
                language = parts.firstOrNull().orEmpty(),
                country = parts.getOrNull(1).orEmpty(),
            )
        }
    }
}

/**
 * 应用首选语言（P6a：KMP 化）。
 * 旧实现直接暴露 java.util.Locale（Android 专属），commonMain 改为轻量三元表示；
 * 仅 TagLocalizer/DisplayTextLocalizer/SearchOption/ReportReason 消费（均已随 P6a 迁 shared）。
 */
object LanguageHelper {
    val preferredLanguage: PreferredLanguage
        get() = PreferredLanguage.fromTag(currentAppLanguage())
}

/** 当前应用语言（IETF tag）。android 走 AppCompatDelegate 覆盖，desktop/ios 读设置 + 系统回退。 */
internal expect fun currentAppLanguage(): String
