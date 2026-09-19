package lovehan1me.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import lovehan1me.data.database.entity.DanmakuCacheEntity
import lovehan1me.data.database.entity.DanmakuMappingEntity
import kotlinx.coroutines.flow.Flow

/**
 * 弹幕关联记录 + 弹幕缓存（第 5 库，见 [DanmakuDatabase]）。
 *
 * 两类数据的生命周期完全不同，故分开：
 *  - mapping：用户资产，只随用户操作增删，永不被 TTL 回收。
 *  - cache：可随时丢弃的镜像，按 [evictOlderThan] 回收。
 */
@Dao
interface DanmakuDao {

    // ---------- 关联记录 ----------

    @Query("SELECT * FROM DanmakuMappingEntity WHERE videoCode = :videoCode LIMIT 1")
    suspend fun getMapping(videoCode: String): DanmakuMappingEntity?

    @Query("SELECT * FROM DanmakuMappingEntity WHERE videoCode = :videoCode LIMIT 1")
    fun observeMapping(videoCode: String): Flow<DanmakuMappingEntity?>

    /** 视频详情页要显示「已关联哪一集」，所以列表也要能读。 */
    @Query("SELECT * FROM DanmakuMappingEntity")
    suspend fun getAllMappings(): List<DanmakuMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapping(entity: DanmakuMappingEntity)

    @Query("DELETE FROM DanmakuMappingEntity WHERE videoCode = :videoCode")
    suspend fun deleteMapping(videoCode: String)

    // ---------- 弹幕缓存 ----------

    @Query(
        """
        SELECT * FROM DanmakuCacheEntity
        WHERE providerId = :providerId AND episodeId = :episodeId
        ORDER BY playTimeMillis ASC, cid ASC
        """
    )
    suspend fun getDanmaku(providerId: String, episodeId: String): List<DanmakuCacheEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM DanmakuCacheEntity
            WHERE providerId = :providerId AND episodeId = :episodeId LIMIT 1
        )
        """
    )
    suspend fun hasCache(providerId: String, episodeId: String): Boolean

    /** 这批弹幕是什么时候拉回来的；空缓存返回 null。TTL 判断用。 */
    @Query(
        """
        SELECT MAX(fetchedAt) FROM DanmakuCacheEntity
        WHERE providerId = :providerId AND episodeId = :episodeId
        """
    )
    suspend fun cachedAt(providerId: String, episodeId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDanmaku(entities: List<DanmakuCacheEntity>)

    /**
     * 整集替换用的删除。
     *
     * 与 [insertDanmaku] 之间**没有事务**（本 Dao 是接口，装不了 `@Transaction`），
     * 由调用方保证成对执行；中途进程被杀只会导致下次重拉，不会读到半集。
     */
    @Query("DELETE FROM DanmakuCacheEntity WHERE providerId = :providerId AND episodeId = :episodeId")
    suspend fun clearEpisode(providerId: String, episodeId: String)

    @Query("DELETE FROM DanmakuCacheEntity WHERE fetchedAt < :cutoffMillis")
    suspend fun evictOlderThan(cutoffMillis: Long)
}
