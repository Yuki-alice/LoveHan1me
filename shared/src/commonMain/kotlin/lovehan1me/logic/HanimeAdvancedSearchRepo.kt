package lovehan1me.logic

import lovehan1me.logic.dao.Han1meDatabases
import lovehan1me.logic.entity.HanimeAdvancedSearchHistoryEntity
import lovehan1me.logic.model.SearchOption

/**
 * 高级搜索历史仓库。
 *
 * P4b：原为 [DatabaseRepo] 的嵌套 object；DatabaseRepo 下沉 commonMain 时因其依赖
 * SearchOption（:app 专属、P6 迁移），按 SearchViewModel 同等理由留在 :app（包名/行为不变，
 * 仅从嵌套改顶层，调用点由 `DatabaseRepo.HanimeAdvancedSearchRepo` 改为 `HanimeAdvancedSearchRepo`）。
 */
object HanimeAdvancedSearchRepo {
    private val dao = Han1meDatabases.history.hanimeAdvancedSearchHistory

    suspend fun saveSearch(
        query: String?,
        genre: String?,
        sort: String?,
        broad: Boolean?,
        date: String?,
        duration: String?,
        tags: Set<SearchOption>?,
        brands: Set<SearchOption>?
    ) {
        val entity = HanimeAdvancedSearchHistoryEntity(
            query = query,
            genre = genre,
            sort = sort,
            broad = broad,
            date = date,
            duration = duration,
            tags = tags?.toDbString(),
            brands = brands?.toDbString()
        )
        dao.insertHistory(entity)
    }

    fun getSearchHistories(limit: Int = 20) = dao.loadHistories(limit)
    suspend fun deleteHistory(id: Long) = dao.deleteHistory(id)
    fun Set<SearchOption>.toDbString(): String =
        mapNotNull { it.searchKey }.joinToString(",")
    fun String.toSearchOptionSet(): Set<SearchOption> =
        if (isBlank()) emptySet()
        else split(",").map { SearchOption(searchKey = it) }.toSet()
}
