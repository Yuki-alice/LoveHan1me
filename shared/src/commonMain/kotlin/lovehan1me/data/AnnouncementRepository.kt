package lovehan1me.data

import lovehan1me.core.domain.model.Announcement
import lovehan1me.core.domain.model.AnnouncementSeverity
import lovehan1me.core.platform.performAnnouncementJsonRequest
import lovehan1me.core.util.LogUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 站点公告的唯一入口。
 *
 * 职责边界：**只负责把两个远端来源合并成"这一刻该展示的公告"**，不碰 Compose、不碰导航。
 * 呈现分档（独占页 / 首页卡 / 只进列表）由 UI 按 [AnnouncementSeverity] 决定。
 *
 * 两个来源：
 * 1. **announcement.json**（主）—— 支持多条、带 id / severity / 时效。
 * 2. **update.json 的 `announcement` 字段**（legacy）—— 迁移期保留。
 *    删掉它会让线上那条反诈提示在远端新文件部署前**直接消失**，
 *    所以先并存：新文件一旦可用，legacy 自然被去重规则盖住（见 [resolveVisible]）。
 *
 * 纯逻辑集中在 [resolveVisible]，IO 只在 [load] 里薄薄一层，便于桌面单测。
 */
object AnnouncementRepository {
    private const val TAG = "AnnouncementRepository"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    /**
     * 拉取 + 降级 + 解析，返回该展示的公告。
     *
     * 失败降级：拉取抛异常时退回 [SettingsRepository.cachedAnnouncementJson]；
     * 缓存也没有就只剩 legacy —— **不回空**，否则一次网络抖动会让首页公告整块闪没。
     */
    @OptIn(ExperimentalTime::class)
    suspend fun load(legacyAnnouncement: Announcement? = null): List<Announcement> {
        val fetched = runCatching { performAnnouncementJsonRequest() }
            .onFailure { LogUtil.e(TAG, "Failed to fetch announcements", it) }
            .getOrNull()

        if (fetched != null) SettingsRepository.setCachedAnnouncementJson(fetched)
        val json = fetched ?: SettingsRepository.cachedAnnouncementJson

        return resolveVisible(
            remoteJson = json,
            legacyAnnouncement = legacyAnnouncement,
            readKeys = SettingsRepository.readAnnouncementKeys,
            nowSeconds = Clock.System.now().toEpochMilliseconds() / 1000,
        )
    }

    /**
     * 用**已缓存**的远端内容重算可见列表，不发网络请求。
     *
     * 标记已读后需要立刻把条目从屏上撤掉；此时再拉一次远端既慢又可能失败
     * （结果是"点了没反应"）。重算走纯逻辑，保证瞬时生效。
     */
    @OptIn(ExperimentalTime::class)
    fun visibleFromCache(legacyAnnouncement: Announcement? = null): List<Announcement> =
        resolveVisible(
            remoteJson = SettingsRepository.cachedAnnouncementJson,
            legacyAnnouncement = legacyAnnouncement,
            readKeys = SettingsRepository.readAnnouncementKeys,
            nowSeconds = Clock.System.now().toEpochMilliseconds() / 1000,
        )

    /**
     * 纯逻辑：解析 → 补 legacy → 去重 → 剔过期 → 剔已读 → 排序。
     *
     * 顺序不能换：
     * - **先合并再剔已读**，否则同一条公告在两个来源里各出现一次时，
     *   只标记了「已读」的那个来源会漏过过滤（另一个来源的同一条仍会显示）。
     * - **先去重再排序**，否则同 key 的两条会按各自优先级插到不同位置，
     *   出现"同一公告在列表里出现两次"。
     *
     * @param remoteJson `announcement.json` 原文，null/空/坏 JSON 一律视为"没有远端公告"。
     * @param readKeys 已读键（[Announcement.stableKey]）。
     * @param nowSeconds 判定过期的"现在"（epoch 秒），由调用方注入以便单测。
     */
    internal fun resolveVisible(
        remoteJson: String?,
        legacyAnnouncement: Announcement?,
        readKeys: Collection<String>,
        nowSeconds: Long,
    ): List<Announcement> {
        val readSet = readKeys.toSet()
        return (parseRemote(remoteJson) + listOfNotNull(legacyAnnouncement?.normalized()))
            .filter { it.content.isNotBlank() }
            .distinctBy { it.stableKey }
            .filterNot { it.isExpiredAt(nowSeconds) }
            .filterNot { it.stableKey in readSet }
            .sortedWith(ANNOUNCEMENT_ORDER)
    }

    /** 排序：先按阻断级别，再按 priority 升序，最后新的在前。 */
    private val ANNOUNCEMENT_ORDER = compareBy<Announcement>(
        { it.severity.ordinal },
        { it.priority },
        { -it.timestamp },
    )

    /**
     * 解析远端 JSON。
     *
     * 兼容两种顶层形状：`{"announcements":[…]}`（约定格式）与裸数组 `[…]`
     * （手写远端文件时最容易写成这样）。两种都认，坏 JSON 记日志并当空处理 ——
     * 远端文件发坏不该让首页崩或让旧缓存失效。
     */
    private fun parseRemote(remoteJson: String?): List<Announcement> {
        if (remoteJson.isNullOrBlank()) return emptyList()
        val text = remoteJson.trim()
        return runCatching {
            if (text.startsWith("[")) {
                jsonParser.decodeFromString<List<AnnouncementDto>>(text).map { it.toDomain() }
            } else {
                jsonParser.decodeFromString<AnnouncementPayload>(text).announcements.map { it.toDomain() }
            }
        }.onFailure {
            LogUtil.e(TAG, "Invalid announcement JSON", it)
        }.getOrDefault(emptyList())
    }

    /**
     * legacy 来源归一化。
     *
     * `isActive` 显式为 false 的一律丢弃（`AppUpdateChecker` 只在
     * `isShowAnnouncement=true` 时才构造，但这条守卫要能独立成立）；
     * 拿到的条目一律置 `isActive = true`，让下游 `HomePageContent` 的过滤语义保持"能拿到就是能显示"。
     */
    private fun Announcement.normalized(): Announcement? =
        if (!isActive) null else copy(isActive = true)

    @Serializable
    private data class AnnouncementPayload(
        val announcements: List<AnnouncementDto> = emptyList(),
    )

    /**
     * 远端 DTO。
     *
     * 全部字段带默认值：远端少写一个键只应导致该字段走默认，不该整条解析失败。
     * `isActive` 刻意**不在 DTO 里** —— 文件里出现即代表启用，多一个开关只会多一种"发了但没显示"的故障。
     */
    @Serializable
    private data class AnnouncementDto(
        val id: String = "",
        val title: String = "",
        val content: String = "",
        val severity: String? = null,
        val priority: Int = 1,
        val timestamp: Long = 0,
        val expiresAt: Long = 0,
        val imageUrl: String? = null,
        val positiveText: String? = null,
        val negativeText: String? = null,
    ) {
        fun toDomain() = Announcement(
            id = id.trim(),
            title = title.trim(),
            content = content.trim(),
            severity = AnnouncementSeverity.fromWire(severity),
            priority = priority,
            timestamp = timestamp,
            expiresAt = expiresAt,
            imageUrl = imageUrl?.trim()?.takeIf(String::isNotBlank),
            positiveText = positiveText?.takeIf(String::isNotBlank),
            negativeText = negativeText?.takeIf(String::isNotBlank),
            isActive = true,
        )
    }
}
