package lovehan1me.core.util

import lovehan1me.core.domain.model.SearchOption
import lovehan1me.core.util.LanguageHelper
import lovehan1me.core.util.decodeComposeAsset

object TagLocalizer {

    private const val SEARCH_PREFIX = "search."

    private data class TagMappings(
        val labels: Map<String, String>,
        val searchKeys: Map<String, String>,
    )

    private val tagOptions: List<SearchOption> by lazy {
        // P6c：loadAssetAs → decodeComposeAsset（同步，维持 by lazy 语义；path 带 files/ 前缀）
        decodeComposeAsset<Map<String, List<SearchOption>>>("files/search_options/tags.json")
            .orEmpty()
            .values
            .flatten() + decodeComposeAsset<List<SearchOption>>("files/search_options/genre.json").orEmpty()
    }

    private var cachedLanguageTag: String? = null
    private var cachedMappings: TagMappings? = null

    private val tagMappings: TagMappings
        get() {
            val languageTag = LanguageHelper.preferredLanguage.toLanguageTag()
            val mappings = cachedMappings
            if (cachedLanguageTag == languageTag && mappings != null) return mappings
            return buildTagMappings(tagOptions).also {
                cachedLanguageTag = languageTag
                cachedMappings = it
            }
        }

    fun localizeTags(tags: List<String>): List<String> {
        if (tags.isEmpty()) return tags
        return tags.map(::localizeTag)
    }

    fun localizeTag(tag: String): String {
        val normalizedTag = tag.normalizeTag()
        return tagMappings.labels[normalizedTag] ?: normalizedTag
    }

    fun resolveSearchKey(tag: String): String {
        val normalizedTag = tag.normalizeTag()
        return tagMappings.searchKeys[normalizedTag] ?: normalizedTag
    }

    /**
     * 丢弃语言缓存，下次查表时按当前 [LanguageHelper.preferredLanguage] 重建。
     *
     * `tagOptions`（JSON 资产）**不重建** —— 它只随 App 版本变化，运行期恒定。
     * 需要重置的是 [cachedLanguageTag] / [cachedMappings] 这对"语言快照"。
     *
     * 调用时机：数据源热切换（见 `SiteSwitcher`）。切站会重建整棵 composition，
     * 但本 object 活过重组，若不失效就会把旧站语种下算出的映射带进新站。
     */
    fun invalidate() {
        cachedLanguageTag = null
        cachedMappings = null
    }

    private fun buildTagMappings(options: List<SearchOption>): TagMappings {
        val labels = mutableMapOf<String, String>()
        val searchKeys = mutableMapOf<String, String>()
        options.forEach { option ->
            val label = option.value.normalizeTag().takeIf { it.isNotBlank() } ?: return@forEach
            val searchKey = option.searchKey
                ?.normalizeTag()
                ?.takeIf { it.isNotBlank() }
                ?: return@forEach
            listOfNotNull(
                option.searchKey,
                option.name,
                option.lang?.zhrCN,
                option.lang?.zhrTW,
                option.lang?.en,
                option.lang?.ja,
            ).forEach { rawTag ->
                val normalizedTag = rawTag.normalizeTag()
                if (!labels.containsKey(normalizedTag)) labels[normalizedTag] = label
                if (!searchKeys.containsKey(normalizedTag)) searchKeys[normalizedTag] = searchKey
            }
        }
        return TagMappings(labels = labels, searchKeys = searchKeys)
    }

    private fun String.normalizeTag(): String = removePrefix(SEARCH_PREFIX)
}
