package lovehan1me.data.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 远端弹幕的本地镜像（**不是**用户自发弹幕池 —— 本项目没有本地池）。
 *
 * 存在的理由是限流与冷启动：弹弹play 开放 API 已明确会对滥用者停用，
 * 而一部片子的弹幕在几分钟内不会变化，没必要每次进页面都重拉。
 * 行存（而非整集一个 blob）是为了 `ORDER BY playTimeMillis` 直接出有序数组 ——
 * 弹幕引擎的发射扫描靠游标 + 二分，前置条件就是有序。
 *
 * 字段刻意保持"数据源无关"：`location` 存 [lovehan1me.data.danmaku.DanmakuLocation]
 * 的枚举名而非弹弹play 的 mode 数字，换数据源时不必改 schema。
 */
@Entity(
    tableName = "DanmakuCacheEntity",
    primaryKeys = ["providerId", "episodeId", "cid"],
    indices = [Index(value = ["providerId", "episodeId", "playTimeMillis"])],
)
data class DanmakuCacheEntity(
    val providerId: String,
    val episodeId: String,
    /** 远端弹幕 id（弹弹play 的 cid）。 */
    val cid: Long,
    val playTimeMillis: Long,
    /** DanmakuLocation 枚举名：SCROLL / TOP / BOTTOM。 */
    val location: String,
    /** ARGB 整型（含 alpha）。 */
    val color: Int,
    val text: String,
    /** 发送者标识（弹弹play 用它区分 [BiliBili] 等来源，展示层可弱化）。 */
    val senderId: String = "",
    /** 本行写入时刻（毫秒），TTL 判断用。 */
    val fetchedAt: Long,
)
