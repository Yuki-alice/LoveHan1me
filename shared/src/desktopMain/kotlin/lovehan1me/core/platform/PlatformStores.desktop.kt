package lovehan1me.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.data.DatabaseRepo
import lovehan1me.data.database.dao.Han1meDatabases

// Desktop 真实现（Gate4-4）：DB 行 + 文件存在性双判定。
// 路径语义：新行落盘即写绝对路径（见 DesktopDownloadWorkController.addTask/runDownload
// 回填）；历史 `local://` 占位行经 initialize 自愈，未自愈的由 resolver 目录扫描兜底；
// 文件被外部删掉但 DB 行残留时返回 null（播下载项页呈 NoContent），不给死路径。
internal actual fun videoCacheStore(): VideoCacheStore = DesktopVideoCacheStore

private object DesktopVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String): Flow<HanimeVideo?> = flow {
        val entity = DatabaseRepo.HanimeDownload.find(videoCode)
        val file = entity?.let {
            DesktopDownloadWorkController.resolveVideoFile(it.videoUri, it.quality, it.title)
        }
        if (entity != null && file != null) {
            // 自愈回填：解析走目录扫描命中的遗留行，下次直接走绝对路径。
            if (entity.videoUri.startsWith("local://")) {
                runCatching {
                    Han1meDatabases.download.hanimeDownloadDao.update(
                        entity.copy(videoUri = file.absolutePath)
                    )
                }
            }
            emit(entity.copy(videoUri = file.absolutePath).toCachedVideo())
        } else {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)
}

internal actual fun downloadWorkController(): DownloadWorkController = DesktopDownloadWorkController
