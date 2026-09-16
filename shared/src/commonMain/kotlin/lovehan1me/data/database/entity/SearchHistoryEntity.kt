package lovehan1me.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import lovehan1me.core.domain.model.SearchFilterSnapshot
import kotlin.time.Clock

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/22 022 18:16
 */
@Entity
data class SearchHistoryEntity(
    val query: String,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)

@Entity(tableName = "HanimeAdvancedSearchHistory")
data class HanimeAdvancedSearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String? = null,
    val genre: String? = null,
    val sort: String? = null,
    val broad: Boolean? = null,
    val date: String? = null,
    val duration: String? = null,
    val tags: String? = null,
    val brands: String? = null,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * 归约成 [SearchFilterSnapshot]，让「历史恢复」与「预设恢复」共用同一条路径。
 *
 * 放在 data 层（而不是 domain）是为了保持依赖方向：domain 不该知道 Room 实体。
 * [broad] 在表里可空（历史遗留），快照里归一成非空。
 */
fun HanimeAdvancedSearchHistoryEntity.toSnapshot(): SearchFilterSnapshot = SearchFilterSnapshot(
    query = query,
    genre = genre,
    sort = sort,
    broad = broad == true,
    date = date,
    duration = duration,
    tags = tags,
    brands = brands,
)
