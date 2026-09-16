package lovehan1me.core.domain.model

import kotlinx.serialization.Serializable

/**
 * 一组「已生效筛选条件」的不可变快照。
 *
 * 字段与站点搜索的 wire 参数一一对应，并刻意与
 * `lovehan1me.data.database.entity.HanimeAdvancedSearchHistoryEntity` 保持同形 ——
 * 高级搜索历史（Room 表）与命名筛选预设（DataStore）都先归约到这一个形状，
 * 于是「把条件恢复进搜索态」这段逻辑（`SearchViewModel.restoreSearchMap`）只需要一份。
 * 新增一个筛选维度时，改动落在两处转换函数上，而不是两套恢复流程。
 *
 * [tags] / [brands] 存的是 wire 层的逗号分隔串（如 `"巨乳,中出"`），**不是**解析后的集合：
 * 解析统一放在恢复处（`HanimeAdvancedSearchRepo.toSearchOptionSet()`），
 * 免得分隔符约定出现第二份实现。null 与空串都表示「该维度无筛选」。
 */
@Serializable
data class SearchFilterSnapshot(
    val query: String? = null,
    val genre: String? = null,
    val sort: String? = null,
    val broad: Boolean = false,
    val date: String? = null,
    val duration: String? = null,
    val tags: String? = null,
    val brands: String? = null,
) {
    /**
     * 一个维度都没选。
     *
     * 给「保存当前筛选」按钮的可用性判断用：空条件存成预设没有意义。
     * 注意 [genre]/[sort] 的"全部"选项在 wire 层是空串而非 null，所以这里两者都当空处理。
     */
    val isEmpty: Boolean
        get() = query.isNullOrBlank() &&
                genre.isNullOrBlank() &&
                sort.isNullOrBlank() &&
                !broad &&
                date.isNullOrBlank() &&
                duration.isNullOrBlank() &&
                tags.isNullOrBlank() &&
                brands.isNullOrBlank()
}
