package io.github.daisukikaffuchino.han1meviewer.logic.model

import io.github.daisukikaffuchino.utils.LanguageHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// P6c：迁自 :app，去 @Parcelize/:Parcelable（nav3 @Serializable 路由不经 Bundle 传 ReportReason）
@Suppress("EqualsOrHashCode")
@Serializable
data class ReportReason(
    @SerialName("lang")
    val lang: Language? = null,
    @SerialName("reason_key")
    val reasonKey: String? = null
) {
    @Serializable
    data class Language(
        @SerialName("zh-rCN")
        val zhrCN: String? = null,
        @SerialName("zh-rTW")
        val zhrTW: String? = null,
        @SerialName("en")
        val en: String? = null,
        @SerialName("ja")
        val ja: String? = null,
    )

    override fun hashCode(): Int = reasonKey?.hashCode() ?: 0

    val value: String
        get() {
            if (lang == null) return reasonKey.orEmpty()

            val pl = LanguageHelper.preferredLanguage
            // P6c：java.util.Locale 常量 → 字面量（zh/en/ja + CN 区分简繁）
            return when (pl.language) {
                "zh" -> when (pl.country) {
                    "CN" -> lang.zhrCN
                    else -> lang.zhrTW
                }
                "en" -> lang.en
                "ja" -> lang.ja
                else -> lang.zhrTW
            } ?: lang.zhrTW.orEmpty()
        }
}
