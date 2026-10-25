package lovehan1me.core.platform

import lovehan1me.core.constant.DEF_VIDEO_TYPE
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import lovehan1me.site.hanime1.HanimeLink
import kotlinx.coroutines.flow.Flow

interface VideoCacheStore {
    fun load(videoCode: String): Flow<HanimeVideo?>
}

// DB 行 → 可播信息（Gate4-4，桌面/iOS 与 Android 同形）。
//
// 与 Android `HanimeCacheManager.loadHanimeVideoInfo` 的实体兜底分支逐字段一致，
// 唯二差别：① 不读 info.json（只有 Android 写它，桌面/iOS 从不落盘，读了也命中不了）；
// ② 调用方须先确认文件仍在（本函数是纯构造，不管文件死活）：删掉文件但留着 DB 行时，
// 直接 emit 会让播放器拿到死路径进错误卡，不如 NoContent。
//
// 本地绝对路径（桌面/iOS `videoUri` 落盘语义）统一转成 `file://` URI 再交给引擎：
// mpv/Exo 两边都认，iOS AVPlayer 只认 file://（裸绝对路径进 AVURLAsset 起播失败）。
fun HanimeDownloadEntity.toCachedVideo(): HanimeVideo = HanimeVideo(
    title = title,
    coverUrl = coverUri ?: coverUrl,
    chineseTitle = null,
    introduction = null,
    uploadTime = null,
    videoUrls = linkedMapOf(quality to HanimeLink(videoUri.toPlayableLocalUri(), DEF_VIDEO_TYPE)),
    tags = emptyList(),
)

/**
 * 本地可播 URI 归一化（下载页 → 播放器链路共用）。
 *
 * - `content://` / `file://` / `http(s)://` 原样透传；
 * - 桌面/iOS 落盘的裸绝对路径（`/Users/x/….mp4`）补 `file://` 前缀；
 * - 遗留占位 `local://<code>` 原样返回（调用方须先经各端 resolver 还原真实路径，
 *   不可直接送引擎——mpv/AVPlayer 都不认识该 scheme）。
 */
fun String.toPlayableLocalUri(): String = when {
    startsWith("content://") || startsWith("file://") ||
        startsWith("http://") || startsWith("https://") -> this
    startsWith("/") -> "file://$this"
    else -> this
}
