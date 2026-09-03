package io.github.daisukikaffuchino.han1meviewer.util

import io.github.daisukikaffuchino.han1meviewer.logic.model.SearchOption
import io.github.daisukikaffuchino.utils.LanguageHelper
import io.github.daisukikaffuchino.utils.loadAssetAs

object TagLocalizer {

    private const val SEARCH_PREFIX = "search."

    private data class TagMappings(
        val labels: Map<String, String>,
        val searchKeys: Map<String, String>,
    )

    private val tagOptions: List<SearchOption> by lazy {
        loadAssetAs<Map<String, List<SearchOption>>>("search_options/tags.json")
            .orEmpty()
            .values
            .flatten() + loadAssetAs<List<SearchOption>>("search_options/genre.json").orEmpty()
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
                labels.putIfAbsent(normalizedTag, label)
                searchKeys.putIfAbsent(normalizedTag, searchKey)
            }
        }
        return TagMappings(labels = labels, searchKeys = searchKeys)
    }

    private fun String.normalizeTag(): String = removePrefix(SEARCH_PREFIX)
}
