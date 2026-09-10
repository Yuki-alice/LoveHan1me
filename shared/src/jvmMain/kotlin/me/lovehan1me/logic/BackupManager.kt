package me.lovehan1me.logic

import me.lovehan1me.logic.datastore.DataStoreManager
import me.lovehan1me.logic.dao.Han1meDatabases
import me.lovehan1me.logic.platform.appVersionCodeRaw
import me.lovehan1me.logic.platform.appVersionNameRaw
import me.lovehan1me.logic.platform.applyAppLanguage
import me.lovehan1me.logic.platform.openBackupSource
import me.lovehan1me.logic.platform.downloadWorkController
import okio.buffer
import me.lovehan1me.logic.platform.openBackupSink
import me.lovehan1me.logic.platform.switchLauncherIcon
import me.lovehan1me.ui.viewmodel.updateCheckInWidget
import me.lovehan1me.logic.network.HanimeNetwork
import me.lovehan1me.logic.network.HProxySelector
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.logic.dao.CheckInRecordDatabase
import me.lovehan1me.logic.dao.DownloadDatabase
import me.lovehan1me.logic.dao.HistoryDatabase
import me.lovehan1me.logic.dao.MiscellanyDatabase
import me.lovehan1me.logic.entity.HKeyframeEntity
import me.lovehan1me.logic.entity.CheckInRecordEntity
import me.lovehan1me.logic.entity.WatchHistoryEntity
import me.lovehan1me.logic.entity.download.DownloadCategoryEntity
import me.lovehan1me.logic.entity.download.DownloadGroupEntity
import me.lovehan1me.logic.entity.download.HanimeCategoryCrossRef
import me.lovehan1me.logic.entity.download.HanimeDownloadEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStream

object BackupManager {
    private const val BACKUP_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Serializable
    private data class BackupData(
        val version: Int = BACKUP_VERSION,
        val appVersionCode: Int = appVersionCodeRaw(),
        val appVersionName: String = appVersionNameRaw(),
        val exportedAt: Long = currentEpochMillis(),
        val settings: Map<String, PreferenceValue>? = null,
        val hKeyframes: List<HKeyframeEntity>? = null,
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
        openBackupSink(uri)?.buffer()?.use { sink ->
            exportTo(sink.outputStream())
        } ?: error("Unable to open backup file")
    }

    suspend fun importFrom(uri: String) {
        val backup = openBackupSource(uri)?.buffer()?.use { source ->
            json.decodeFromString<BackupData>(source.readUtf8())
        } ?: error("Unable to open backup file")

        backup.hKeyframes?.let { hKeyframes ->
            Han1meDatabases.miscellany.hKeyframeDao.apply {
                deleteAll()
                insertAll(hKeyframes)
            }
        }

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
            HProxySelector.rebuildNetwork()
            HanimeNetwork.rebuildNetwork()
            downloadWorkController().updateDownloadLimit(SettingsRepository.current.downloadCountLimit)
            switchLauncherIcon(SettingsRepository.current.fakeLauncherIcon)
        }

        runCatching { updateCheckInWidget() }
    }

    private suspend fun exportTo(outputStream: OutputStream) {
        val backup = BackupData(
            settings = DataStoreManager.exportBackup().mapValuesNotNull { (_, value) ->
                value.toPreferenceValue()
            },
            hKeyframes = Han1meDatabases.miscellany.hKeyframeDao.getAll(),
            checkInRecords = Han1meDatabases.checkInRecord.checkInDao().getAllRecords(),
            watchHistories = Han1meDatabases.history.watchHistory.getAll(),
            downloadGroups = Han1meDatabases.download.downloadGroupDao.getAllGroupsOnce(),
            downloads = Han1meDatabases.download.hanimeDownloadDao.getAll(),
            downloadCategories = Han1meDatabases.download.downloadCategoryDao.getAllCategoriesOnce(),
            downloadCategoryCrossRefs = Han1meDatabases.download.downloadCategoryDao.getAllCrossRefs(),
        )
        outputStream.bufferedWriter().use { writer ->
            writer.write(json.encodeToString(backup))
        }
    }

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
