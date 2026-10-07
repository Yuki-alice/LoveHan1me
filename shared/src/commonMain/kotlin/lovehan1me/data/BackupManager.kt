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
import lovehan1me.core.domain.model.ListsExport
import lovehan1me.data.database.entity.CheckInRecordEntity
import lovehan1me.data.database.entity.DanmakuMappingEntity
import lovehan1me.data.database.entity.WatchHistoryEntity
import lovehan1me.data.database.entity.download.DownloadCategoryEntity
import lovehan1me.data.database.entity.download.DownloadGroupEntity
import lovehan1me.data.database.entity.download.HanimeCategoryCrossRef
import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
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
        // 编解码都变慢。
        prettyPrint = false
        encodeDefaults = true
    }

    @Serializable
    internal data class BackupData(
        val version: Int = BACKUP_VERSION,
        // 这两个默认值刻意**不调平台函数**：解码（含上游包导入）不该有副作用。
        // Android 的 appVersionCodeRaw() 需要 `Han1meDatabaseContext` 已初始化，
        // 拿它当构造默认值会让"不带该字段的 JSON"在无 context 的环境（Android host 测试）
        // 解码即抛 `IllegalArgumentException`。真实值只在导出侧 [buildBackup] 求值注入。
        val appVersionCode: Int = 0,
        val appVersionName: String = "",
        val exportedAt: Long = currentEpochMillis(),
        // 设置值以原始 JSON 存（而非多态 PreferenceValue）：`PreferenceValue` 的
        // 多态鉴别名含包名，上游包与本包不同，直接解码上游备份必炸。读侧按
        // [decodeSettingValue] 宽容还原（后缀名匹配 + 形状兜底），写侧形状不变，
        // 故本机新旧包互读、上游包读入都成立。
        val settings: Map<String, JsonElement>? = null,
        val checkInRecords: List<CheckInRecordEntity>? = null,
        val watchHistories: List<WatchHistoryEntity>? = null,
        val downloadGroups: List<DownloadGroupEntity>? = null,
        val downloads: List<HanimeDownloadEntity>? = null,
        val downloadCategories: List<DownloadCategoryEntity>? = null,
        val downloadCategoryCrossRefs: List<HanimeCategoryCrossRef>? = null,
        /** 本机清单（稍后看/喜欢/播放列表）。上游包无此键，读入时跳过。 */
        val localLists: ListsExport? = null,
        /** 弹幕人工关联。缓存表（可再生）不进备份。 */
        val danmakuMappings: List<DanmakuMappingEntity>? = null,
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

    /** 上游包兼容的解码入口（测试与未来"选择上游包导入"共用；写盘走 DB 不走这里）。 */
    internal fun decodeBackupJson(text: String): BackupData = json.decodeFromString(text)

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
            DataStoreManager.restoreBackup(decodeSettingsValues(settings))
            applyAppLanguage(SettingsRepository.current.appLanguage)
            rebuildSystemProxy()
            downloadWorkController().updateDownloadLimit(SettingsRepository.current.downloadCountLimit)
        }

        backup.localLists?.let { lists ->
            LocalListRepository.importLocalLists(lists, merge = false)
        }

        backup.danmakuMappings?.let { mappings ->
            Han1meDatabases.danmaku.danmakuDao.apply {
                deleteAllMappings()
                mappings.forEach { upsertMapping(it) }
            }
        }
    }

    private suspend fun buildBackup(): BackupData = BackupData(
        // 应用版本只在这一处（导出）求值：解码侧的默认值保持惰性、无副作用。
        appVersionCode = appVersionCodeRaw(),
        appVersionName = appVersionNameRaw(),
        settings = DataStoreManager.exportBackup().mapValuesNotNull { (_, value) ->
            value.toPreferenceValue()?.let { pv: PreferenceValue ->
                json.encodeToJsonElement(PreferenceValue.serializer(), pv)
            }
        },
        checkInRecords = Han1meDatabases.checkInRecord.checkInDao().getAllRecords(),
        watchHistories = Han1meDatabases.history.watchHistory.getAll(),
        downloadGroups = Han1meDatabases.download.downloadGroupDao.getAllGroupsOnce(),
        downloads = Han1meDatabases.download.hanimeDownloadDao.getAll(),
        downloadCategories = Han1meDatabases.download.downloadCategoryDao.getAllCategoriesOnce(),
        downloadCategoryCrossRefs = Han1meDatabases.download.downloadCategoryDao.getAllCrossRefs(),
        localLists = LocalListRepository.exportLocalLists(),
        danmakuMappings = Han1meDatabases.danmaku.danmakuDao.getAllMappings(),
    )

    /**
     * 设置值的宽容解码（上游备份兼容的核心）。
     *
     * 两类输入都成立：本机包（鉴别名是本包 FQN）与上游包（鉴别名是上游 FQN）。
     * 判据只看 `type` 的后缀简单名（`BooleanValue` 等六个，两边同名），包名差异忽略；
     * `type` 缺失或不可辨时按 `value` 形状兜底。单键失败只丢该键，不连累整包。
     * Int/Long 严格按声明还原（错位会让读侧落到默认值，比"值对类型错"更糟）。
     */
    internal fun decodeSettingsValues(raw: Map<String, JsonElement>): Map<String, Any> =
        raw.mapNotNull { (name, element) ->
            decodeSettingValue(element)?.let { name to it }
        }.toMap()

    internal fun decodeSettingValue(element: JsonElement): Any? {
        val obj = element as? JsonObject ?: return null
        val value = obj["value"] ?: return null
        val type = (obj["type"] as? JsonPrimitive)?.content?.substringAfterLast('.')
        return when (type) {
            "BooleanValue" -> (value as? JsonPrimitive)?.booleanOrNull
            "IntValue" -> (value as? JsonPrimitive)?.content?.toIntOrNull()
            "LongValue" -> (value as? JsonPrimitive)?.content?.toLongOrNull()
            "FloatValue" -> (value as? JsonPrimitive)?.content?.toFloatOrNull()
            "StringValue" -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            "StringSetValue" -> (value as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { e -> e.isString }?.content }
                ?.toSet()
            // 无 type 或不可辨：按形状兜底（手写包/未来类型）。
            else -> sniffSettingValue(value)
        }
    }

    private fun sniffSettingValue(value: JsonElement): Any? = when (value) {
        is JsonPrimitive -> when {
            value.isString -> value.content
            value.booleanOrNull != null -> value.boolean
            else -> value.content.toLongOrNull()?.let { l ->
                if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
            } ?: value.floatOrNull
        }
        is kotlinx.serialization.json.JsonArray -> value
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { e -> e.isString }?.content }
            .toSet()
        else -> null
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

}
