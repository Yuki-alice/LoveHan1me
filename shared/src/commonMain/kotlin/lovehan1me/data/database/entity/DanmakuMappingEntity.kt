package lovehan1me.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 「这一集对应弹幕库里的哪一集」的用户级关联记录。
 *
 * hanime1 侧没有 bgm.tv subject id 可作锚点，自动匹配对里番的命中率极低，
 * 所以**人工选集 + 记住关联**才是主路径：用户手动检索关联一次，之后每次播放
 * 该 videoCode 都直接按这条记录去拉弹幕，不再重复搜索。
 *
 * 这张表存的是**用户资产**（重新关联的成本远高于重新拉取弹幕），
 * 因此不随缓存过期清理、也不纳入备份导出。
 */
@Entity(tableName = "DanmakuMappingEntity")
data class DanmakuMappingEntity(
    /** hanime1 视频页编号，一集一条（站内没有"季/部"的层级，页面即集）。 */
    @PrimaryKey
    val videoCode: String,
    val providerId: String,
    val subjectId: String,
    val subjectTitle: String,
    val episodeId: String,
    val episodeTitle: String,
    /**
     * true = 用户人工选定的。自动匹配的结果只允许在 `manual = false` 时被覆盖，
     * 否则用户辛苦挑对的集会在下次进页面时被猜错的顶掉。
     */
    val manual: Boolean,
    val createdAt: Long,
)
