package me.lovehan1me.logic

import me.lovehan1me.utils.LogUtil
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.logic.dao.Han1meDatabases
import me.lovehan1me.logic.entity.HKeyframeEntity
import me.lovehan1me.logic.entity.HKeyframeHeader
import me.lovehan1me.logic.entity.HKeyframeType
import me.lovehan1me.logic.entity.SearchHistoryEntity
import me.lovehan1me.logic.entity.WatchHistoryEntity
import me.lovehan1me.logic.entity.download.DownloadGroupEntity
import me.lovehan1me.logic.entity.download.HanimeDownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/22 022 23:00
 */
object DatabaseRepo {

    object HKeyframe {
        private val hKeyframeDao = Han1meDatabases.miscellany.hKeyframeDao

        fun loadAll(keyword: String? = null) =
            if (keyword != null) hKeyframeDao.loadAll(keyword)
            else hKeyframeDao.loadAll()

        // #issue-106: 剧集分类
        fun loadAllShared(): Flow<List<HKeyframeType>> = flow {
            // P6a：走 composeResources/files（清单文件 index.txt 于构建期生成；三端同源）
            val fileNames = readAssetText("h_keyframes/index.txt")
                ?.lineSequence()
                ?.filter { it.isNotBlank() && it.endsWith(".json") }
                ?.toList()
                .orEmpty()
            val loaded = ArrayList<HKeyframeEntity>(fileNames.size)
            for (fileName in fileNames) {
                try {
                    val bytes = readAssetBytes("h_keyframes/$fileName") ?: continue
                    loaded.add(Json.decodeFromString<HKeyframeEntity>(bytes.decodeToString()))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val res = loaded
                .sortedWith(
                    compareBy<HKeyframeEntity> { it.group }.thenBy { it.episode }
                )
                .groupBy { it.group ?: "???" }
                .flatMap { (group, entities) ->
                    listOf(HKeyframeHeader(title = group, attached = entities)) + entities
                }
            emit(res)
        }

        suspend fun findBy(videoCode: String) =
            hKeyframeDao.findBy(videoCode)

        fun observe(videoCode: String): Flow<HKeyframeEntity?> {
            if (SettingsRepository.sharedHKeyframesEnable) {
                return flow t@{
                    val find = hKeyframeDao.findBy(videoCode)
                    if (find == null || SettingsRepository.sharedHKeyframesUseFirst) {
                        val bytes = readAssetBytes("h_keyframes/$videoCode.json")
                        if (bytes == null) {
                            // 资源缺失（android 读 assets 找不到文件；desktop/ios 无资源目录）
                            LogUtil.w("HKeyframe", "未找到关键帧文件: $videoCode.json")
                        } else {
                            runCatching {
                                val entity = Json.decodeFromString<HKeyframeEntity>(bytes.decodeToString())
                                this@t.emit(entity)
                            }.onFailure { e ->
                                LogUtil.e("HKeyframe", "读取关键帧失败: ${e.message}", e)
                            }
                        }
                    } else {
                        hKeyframeDao.observe(videoCode).collect {
                            this@t.emit(it)
                        }
                    }
                }.catch t@{ e ->
                    e.printStackTrace()
                    hKeyframeDao.observe(videoCode).collect {
                        this@t.emit(it)
                    }
                }
            }
            return hKeyframeDao.observe(videoCode)
        }

        suspend fun insert(entity: HKeyframeEntity) = hKeyframeDao.insert(entity)

        suspend fun update(entity: HKeyframeEntity) = hKeyframeDao.update(entity)

        suspend fun delete(entity: HKeyframeEntity) =
            hKeyframeDao.delete(entity)

        suspend fun modifyKeyframe(
            videoCode: String,
            oldKeyframe: HKeyframeEntity.Keyframe, keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.modifyKeyframe(videoCode, oldKeyframe, keyframe)

        suspend fun appendKeyframe(
            videoCode: String, title: String,
            keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.appendKeyframe(videoCode, title, keyframe)

        suspend fun removeKeyframe(
            videoCode: String,
            keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.removeKeyframe(videoCode, keyframe)
    }

    object SearchHistory {
        private val searchHistoryDao = Han1meDatabases.history.searchHistory

        fun loadAll(keyword: String? = null) =
            if (keyword.isNullOrBlank()) searchHistoryDao.loadAll()
            else searchHistoryDao.loadAll(keyword)

        suspend fun delete(history: SearchHistoryEntity) =
            searchHistoryDao.delete(history)

        suspend fun insert(history: SearchHistoryEntity) =
            searchHistoryDao.insertOrUpdate(history)

        suspend fun deleteByKeyword(query: String) =
            searchHistoryDao.deleteByKeyword(query)
    }

    object WatchHistory {
        private val watchHistoryDao = Han1meDatabases.history.watchHistory

        fun loadAll() =
            watchHistoryDao.loadAll()

        suspend fun delete(history: WatchHistoryEntity) =
            watchHistoryDao.delete(history)

        suspend fun deleteAll() =
            watchHistoryDao.deleteAll()

        suspend fun update(history: WatchHistoryEntity) =
            watchHistoryDao.update(history)

        suspend fun updateProgress(videoCode: String,progress: Long) =
            watchHistoryDao.updateProgress(videoCode, progress)

        suspend fun insert(history: WatchHistoryEntity) =
            watchHistoryDao.insertOrUpdate(history)

        suspend fun findBy(videoCode: String) =
            watchHistoryDao.findBy(videoCode)

        suspend fun getWatched(resultList: List<String>) =
            watchHistoryDao.getWatchedCodes(resultList)
    }

    object HanimeDownload {
        private val hanimeDownloadDao = Han1meDatabases.download.hanimeDownloadDao
        private val downloadGroupDao = Han1meDatabases.download.downloadGroupDao
        fun loadAllDownloadingHanime() =
            hanimeDownloadDao.loadAllDownloadingHanime()

        /**
         * 查询所有视频，并且每个视频要有当前他在的分类
         */
        fun loadAllDownloadedHanime(
            sortedBy: HanimeDownloadEntity.SortedBy,
            ascending: Boolean,
        ) = when (sortedBy) {
            HanimeDownloadEntity.SortedBy.TITLE ->
                hanimeDownloadDao.loadAllDownloadedHanimeByTitle(ascending)

            HanimeDownloadEntity.SortedBy.ID ->
                hanimeDownloadDao.loadAllDownloadedHanimeById(ascending)
        }
        suspend fun delete(videoCode: String, quality: String) =
            hanimeDownloadDao.delete(videoCode, quality)

        suspend fun delete(videoCode: String) =
            hanimeDownloadDao.delete(videoCode)

        suspend fun pauseAll() =
            hanimeDownloadDao.pauseAll()

        suspend fun delete(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.delete(entity)

        suspend fun insert(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.insert(entity)

        suspend fun update(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.update(entity)

        suspend fun find(videoCode: String, quality: String) =
            hanimeDownloadDao.find(videoCode, quality)

        suspend fun find(videoCode: String) =
            hanimeDownloadDao.find(videoCode)

        suspend fun insertDefaultGroup() =
            downloadGroupDao.insertDefaultGroup()

        fun getAllGroups()=
            downloadGroupDao.getAllGroups()

        suspend fun getGroupById(id: Int)=
            downloadGroupDao.getGroupById(id)

        suspend fun updateVideoGroup(videoCode: String, newGroupId: Int)=
            hanimeDownloadDao.updateVideoGroup(videoCode, newGroupId)

        suspend fun createNewGroup(name: String): Long{
            val maxIndex = downloadGroupDao.getMaxOrderIndex() ?: 0
            val newIndex = maxIndex + 1
            val newGroup = DownloadGroupEntity(
                name = name,
                orderIndex = newIndex
            )
            return downloadGroupDao.insert(newGroup)
        }

        suspend fun getOrCreateGroup(name: String): Int =
            downloadGroupDao.getOrCreateGroup(name)

        suspend fun deleteGroup(group: DownloadGroupEntity) {
            downloadGroupDao.deleteGroup(group)
        }

        suspend fun updateGroup(group: DownloadGroupEntity)=
            downloadGroupDao.update(group)
    }
}
