package lovehan1me.core.domain.model

import kotlinx.serialization.Serializable

/**
 * 用户命名的筛选预设。
 *
 * 与「高级搜索历史」的分工：
 * - 历史是**自动**记的（每次点搜索写一条，最多 10 条，同条件去重），用户不能命名；
 * - 预设是**手工**存/命名/删除的，是用户主动沉淀下来的常用组合。
 *
 * 存储：整个列表序列化成 JSON 放在 DataStore 的一个字符串键上（见 `DataStoreManager`），
 * 这样 `BackupManager` 导出 preferences 时**自动带上**预设，不需要额外的备份接线。
 *
 * [snapshot] 复用 [SearchFilterSnapshot]，恢复时与历史走同一条路径。
 */
@Serializable
data class SearchFilterPreset(
    val id: String,
    val name: String,
    val createdAt: Long = 0,
    val snapshot: SearchFilterSnapshot = SearchFilterSnapshot(),
)
