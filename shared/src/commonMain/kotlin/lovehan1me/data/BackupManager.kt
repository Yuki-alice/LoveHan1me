package lovehan1me.data

import lovehan1me.data.datastore.DataStoreManager
import lovehan1me.data.database.dao.Han1meDatabases
import lovehan1me.core.platform.appVersionCodeRaw
import lovehan1me.core.platform.appVersionNameRaw
import lovehan1me.core.platform.applyAppLanguage
import lovehan1me.core.platform.openBackupSource
import lovehan1me.core.platform.downloadWorkController
import okio.buffer
import lovehan1me.core.platform.openBackupSink
import lovehan1me.core.platform.rebuildSystemProxy
import lovehan1me.data.SettingsRepository
import lovehan1me.data.database.dao.CheckInRecordDatabase
import lovehan1me.data.database.dao.DownloadDatabase
import lovehan1me.data.database.dao.HistoryDatabase
import lovehan1me.data.database.entity.CheckInRecordEntity
import lovehan1me.data.database.entity.WatchHistoryEntity
import lovehan1me.data.database.entity.download.DownloadCategoryEntity
import lovehan1me.data.database.entity.download.DownloadGroupEntity
import lovehan1me.data.database.entity.download.HanimeCategoryCrossRef
import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
// okio 桥接：1.11.0 里叫 encodeToBufferedSink / decodeFromBufferedSource（收
// BufferedSink / BufferedSource，不是裸 Sink / Source）——写错名字会"能解析依赖、
// 但符号找不到"，排查时别被"依赖下下来了"骗过去。
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import lovehan1me.core.platform.currentEpochMillis

object BackupManager {
    private const val BACKUP_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        // B7：备份是纯机器读写的交换格式，缩进对人毫无用处，却让体积翻倍、
        // 编解码都变慢。同款改动一并落在 LocalListRepository / OnlineListsBackup。
        prettyPrint = false
        encodeDefaults = true
    }

    @Serializable
    private data class BackupData(
        val version: Int = BACKUP_VERSION,
        val appVersionCode: Int = appVersionCodeRaw(),
        val appVersionName: String = appVersionNameRaw(),
        val exportedAt: Long = currentEpochMillis(),
        val settings: Map<String, PreferenceValue>? = null,
        val checkInRecords: List<CheckInRecordEntity>? = null,
        val watchHistories: List<WatchHistoryEntity>? = null,
        val downloadGroups: List<DownloadGroupEntity>? = null,
        val downloads: List<HanimeDownloadEntity>? = null,
        val downloadCategories: List<DownloadCategoryEntity>? = null,
        val downloadCategoryCrossRefs: List<HanimeCategoryCrossRef>? = null,
    )

    @Serializable
    private sealed interface PreferenceValue {
        @Serializable
        data class BooleanValue(val value: Boolean) : PreferenceValue

        @Serializable
        data class FloatValue(val value: Float) : PreferenceValue

        @Serializable
        data class IntValue(val value: Int) : PreferenceValue

        @Serializable
        data class LongValue(val value: Long) : PreferenceValue

        @Serializable
        data class StringValue(val value: String) : PreferenceValue

        @Serializable
        data class StringSetValue(val value: Set<String>) : PreferenceValue
    }

    suspend fun exportTo(uri: String) {
        val sink = openBackupSink(uri)?.buffer() ?: error("Unable to open backup file")
        try {
            // B7：直接编码进 okio Sink，不再先 encodeToString 攒出整份 JSON 字符串。
            // 万级记录下那个字符串是峰值内存的大头（UTF-16 驻留，约为落盘字节的两倍），
            // 而且它跟实体列表同时在堆上。流式写掉之后，峰值只取决于实体列表本身。
            json.encodeToBufferedSink(buildBackup(), sink)
        } finally {
            sink.close()
        }
    }

    suspend fun importFrom(uri: String) {
        val source = openBackupSource(uri)?.buffer() ?: error("Unable to open backup file")
        val backup = try {
            // B7：同上，不再 readUtf8() 全读成字符串再解析 —— 直接从 Source 解码。
            json.decodeFromBufferedSource<BackupData>(source)
        } finally {
            source.close()
        }
        applyBackup(backup)
    }

    private suspend fun applyBackup(backup: BackupData) {
        backup.checkInRecords?.let { checkInRecords ->
            Han1meDatabases.checkInRecord.checkInDao().apply {
                deleteAll()
                insertAll(checkInRecords)
            }
        }

        backup.watchHistories?.let { watchHistories ->
            Han1meDatabases.history.watchHistory.apply {
                deleteAll()
                insertAll(watchHistories)
            }
        }

        if (backup.downloadGroups != null || backup.downloads != null ||
            backup.downloadCategories != null || backup.downloadCategoryCrossRefs != null
        ) {
            val downloadGroups = backup.downloadGroups.orEmpty()
            val groupIds = downloadGroups.mapTo(mutableSetOf()) { it.id } +
                    DownloadGroupEntity.DEFAULT_GROUP_ID
            val downloads = backup.downloads.orEmpty().map { download ->
                if (download.groupId in groupIds) {
                    download
                } else {
                    download.copy(groupId = DownloadGroupEntity.DEFAULT_GROUP_ID)
                }
            }
            val downloadCategories = backup.downloadCategories.orEmpty()
            val downloadIds = downloads.mapTo(mutableSetOf()) { it.id }
            val categoryIds = downloadCategories.mapTo(mutableSetOf()) { it.id }
            val crossRefs = backup.downloadCategoryCrossRefs.orEmpty().filter { crossRef ->
                crossRef.videoId in downloadIds && crossRef.categoryId in categoryIds
            }

            Han1meDatabases.download.apply {
                downloadCategoryDao.deleteAllCrossRefs()
                hanimeDownloadDao.deleteAll()
                downloadCategoryDao.deleteAllCategories()
                downloadGroupDao.deleteAll()
                downloadGroupDao.insertAll(downloadGroups)
                downloadGroupDao.insertDefaultGroup()
                downloadCategoryDao.insertAllCategories(downloadCategories)
                hanimeDownloadDao.insertAll(downloads)
                downloadCategoryDao.insertAllCrossRefs(crossRefs)
            }
        }

        backup.settings?.let { settings ->
            DataStoreManager.restoreBackup(settings.mapValues { (_, value) -> value.rawValue })
            applyAppLanguage(SettingsRepository.current.appLanguage)
            rebuildSystemProxy()
            downloadWorkController().updateDownloadLimit(SettingsRepository.current.downloadCountLimit)
        }
    }

    private suspend fun buildBackup(): BackupData = BackupData(
        settings = DataStoreManager.exportBackup().mapValuesNotNull { (_, value) ->
            value.toPreferenceValue()
        },
        checkInRecords = Han1meDatabases.checkInRecord.checkInDao().getAllRecords(),
        watchHistories = Han1meDatabases.history.watchHistory.getAll(),
        downloadGroups = Han1meDatabases.download.downloadGroupDao.getAllGroupsOnce(),
        downloads = Han1meDatabases.download.hanimeDownloadDao.getAll(),
        downloadCategories = Han1meDatabases.download.downloadCategoryDao.getAllCategoriesOnce(),
        downloadCategoryCrossRefs = Han1meDatabases.download.downloadCategoryDao.getAllCrossRefs(),
    )

    private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(
        transform: (Map.Entry<K, V>) -> R?
    ): Map<K, R> {
        return mapNotNull { entry -> transform(entry)?.let { entry.key to it } }.toMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.toPreferenceValue(): PreferenceValue? {
        return when (this) {
            is Boolean -> PreferenceValue.BooleanValue(this)
            is Float -> PreferenceValue.FloatValue(this)
            is Int -> PreferenceValue.IntValue(this)
            is Long -> PreferenceValue.LongValue(this)
            is String -> PreferenceValue.StringValue(this)
            is Set<*> -> PreferenceValue.StringSetValue(this.filterIsInstance<String>().toSet())
            else -> null
        }
    }

    private val PreferenceValue.rawValue: Any
        get() = when (this) {
            is PreferenceValue.BooleanValue -> value
            is PreferenceValue.FloatValue -> value
            is PreferenceValue.IntValue -> value
            is PreferenceValue.LongValue -> value
            is PreferenceValue.StringSetValue -> value
            is PreferenceValue.StringValue -> value
        }

}
