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
fun HanimeDownloadEntity.toCachedVideo(): HanimeVideo = HanimeVideo(
    title = title,
    coverUrl = coverUri ?: coverUrl,
    chineseTitle = null,
    introduction = null,
    uploadTime = null,
    videoUrls = linkedMapOf(quality to HanimeLink(videoUri, DEF_VIDEO_TYPE)),
    tags = emptyList(),
)
