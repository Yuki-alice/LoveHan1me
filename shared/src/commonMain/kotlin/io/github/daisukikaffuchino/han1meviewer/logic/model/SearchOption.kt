package io.github.daisukikaffuchino.han1meviewer.logic.model

import io.github.daisukikaffuchino.utils.LanguageHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * P6c：迁自 :app，去掉 Android 耦合：
 *  - @Parcelize/:Parcelable 删除（nav3 @Serializable 路由不经 Bundle 传 SearchOption）
 *  - SparseArray.flatten → Map<Int, Set<SearchOption>>.flatten（仅收 values）
 *  - R.string 版 operator get(scopeNameRes: Int) 删除；调用方直接用 scope 名（如
 *    "video_attributes"）索引 tags Map
 *  - java.util.Locale 判断 → LanguageHelper 的语言三元解析（P6a-C）
 */
@Suppress("EqualsOrHashCode")
@Serializable
data class SearchOption(
    @SerialName("lang")
    val lang: Language? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("search_key")
    val searchKey: String? = null,
) {

    companion object {
        fun Map<Int, Set<SearchOption>>.flatten(): Set<String> = buildSet {
            values.forEach { options ->
                val res = options.mapNotNullTo(mutableSetOf()) { it.searchKey }
                addAll(res)
            }
        }
    }

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

    override fun hashCode(): Int = searchKey.hashCode()

    val value: String
        get() = when {
            lang == null -> name.orEmpty()
            else -> LanguageHelper.preferredLanguage.let { pl ->
                when (pl.language) {
                    // P6c：原 Locale.CHINESE/ENGLISH/JAPANESE/SIMPLIFIED_CHINESE 常量 → 字面量
                    "zh" -> when (pl.country) {
                        "CN" -> lang.zhrCN
                        else -> lang.zhrTW
                    }
                    "en" -> lang.en
                    "ja" -> lang.ja
                    else -> lang.zhrTW
                }
            } ?: lang.zhrTW.orEmpty()
        }
}
