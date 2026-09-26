package lovehan1me.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.data.DatabaseRepo
import java.io.File

// Desktop 真实现（Gate4-4）：DB 行 + 文件存在性双判定。
// 路径语义与下载引擎一致（`DesktopDownloadWorkController` 落盘即写绝对路径进
// `videoUri`，`importDownloaded` 反解的也是绝对路径），故直接 stat 该字段；
// 文件被外部删掉但 DB 行残留时返回 null（播下载项页呈 NoContent），不给死路径。
internal actual fun videoCacheStore(): VideoCacheStore = DesktopVideoCacheStore

private object DesktopVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String): Flow<HanimeVideo?> = flow {
        val entity = DatabaseRepo.HanimeDownload.find(videoCode)
        if (entity != null && File(entity.videoUri).isFile) {
            emit(entity.toCachedVideo())
        } else {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)
}

internal actual fun downloadWorkController(): DownloadWorkController = DesktopDownloadWorkController
