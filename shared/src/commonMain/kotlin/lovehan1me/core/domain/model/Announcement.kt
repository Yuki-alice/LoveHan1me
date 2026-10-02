package lovehan1me.core.domain.model

import lovehan1me.core.constant.LOCAL_DATE_TIME_FORMAT
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * 公告的阻断级别，决定它占哪一档呈现位。
 *
 * 三档语义（见 `docs/decisions.md` 的「公告系统分层」一条）：
 * - [Blocking]：站点维护/故障这类「必须先看到」的通告，走独占页，不可关闭。
 * - [Normal]：一般公告，首页一条可关闭的卡片，点开看详情。
 * - [Info]：历史公告，不占首页，只进列表。
 *
 * 解析时**未知值一律降级到 [Normal]**：远端写错一个词就整屏锁死用户是不可接受的，
 * 而少锁一次只是少一次提示。宁可漏阻断，不可误阻断。
 */
enum class AnnouncementSeverity {
    Blocking,
    Normal,
    Info;

    companion object {
        fun fromWire(value: String?): AnnouncementSeverity = when (value?.trim()?.lowercase()) {
            "blocking", "block" -> Blocking
            "info" -> Info
            else -> Normal
        }
    }
}

/**
 * 站点公告。
 *
 * 本类**只描述数据**，不碰 Compose —— 「正文里的 URL 变成可点链接」是展示逻辑，
 * 已移到 `lovehan1me.ui.component.LinkifiedText`。此前它挂在模型上的
 * `@Composable getFormatedContent()` 让 domain 层反向依赖了 UI 框架。
 *
 * 字段语义：
 * - [id]：远端稳定标识，**去重与「已读」都认它**。留空时由仓储按内容生成回退 id。
 * - [timestamp] / [expiresAt]：均为 **epoch 秒**（与 [getFormattedDate] 的换算一致）。
 *   [expiresAt] 为 0 表示永不过期。
 * - [priority]：数值越小越靠前；同 [severity] 内按它排。
 * - [isActive]：远端开关，false 的条目直接丢弃。
 */
data class Announcement(
    val title: String,
    val content: String,
    val id: String = "",
    val positiveText: String ? = null,
    val negativeText: String ? = null,
    val timestamp: Long = 0,
    val priority: Int = 1,
    val imageUrl: String ? = null,
    val isActive: Boolean = false,
    val severity: AnnouncementSeverity = AnnouncementSeverity.Normal,
    val expiresAt: Long = 0,
) {
    @OptIn(ExperimentalTime::class)
    fun getFormattedDate(): String {
        return kotlin.time.Instant
            .fromEpochSeconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .format(LOCAL_DATE_TIME_FORMAT)
    }

    /**
     * 相对 [nowSeconds]（epoch 秒）是否已过期。
     *
     * [expiresAt] 为 0 或负数视为「永不过期」：远端忘了填时效时，公告应当留下而不是消失。
     */
    fun isExpiredAt(nowSeconds: Long): Boolean = expiresAt > 0 && nowSeconds >= expiresAt

    /**
     * 去重与「已读」共用的稳定键。
     *
     * 远端没给 [id] 时不能直接跳过该条（那样一条公告都显示不出来），
     * 也不能用 hashCode（跨进程不稳定，重启后「已读」就丢了），
     * 故回退成 title+content 的确定性摘要。
     *
     * 回退值会把换行、逗号、竖线压成空格：它要经 `DataStoreManager` 用逗号拼接落盘
     * （见 `AppSettings.readAnnouncementKeys`），正文里带逗号或换行不压掉的话，
     * 存下去是 1 条、读回来会变成好几条。
     */
    val stableKey: String
        get() = id.ifBlank {
            "auto:${title.toKeyPart()}|${content.toKeyPart()}"
        }

    private fun String.toKeyPart(): String =
        replace('\n', ' ').replace('\r', ' ').replace(',', ' ')
            .replace('|', ' ')
            .split(' ').filter(String::isNotEmpty).joinToString(" ")
}
