package lovehan1me.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.data.DatabaseRepo
import lovehan1me.data.database.dao.Han1meDatabases

// iOS 真实现（Gate4-4）：与桌面版同规则（DB 行 + 文件存在性双判定，见该文件注释）。
// 路径语义：新行落盘即写绝对路径（见 IosDownloadWorkController.addTask/runDownload 回填）；
// 历史 `local://` 占位行经 initialize 自愈，未自愈的由 resolver 目录扫描兜底。
// 调度用 Default：Kotlin/Native 没有 Dispatchers.IO（internal），DB 查询与
// NSFileManager 判定都是短操作，Default 足够。
internal actual fun videoCacheStore(): VideoCacheStore = IosVideoCacheStore

private object IosVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String): Flow<HanimeVideo?> = flow {
        val entity = DatabaseRepo.HanimeDownload.find(videoCode)
        val path = entity?.let {
            IosDownloadWorkController.resolveVideoFile(it.videoUri, it.quality, it.title)
        }
        if (entity != null && path != null) {
            if (entity.videoUri.startsWith("local://")) {
                runCatching {
                    Han1meDatabases.download.hanimeDownloadDao.update(entity.copy(videoUri = path))
                }
            }
            emit(entity.copy(videoUri = path).toCachedVideo())
        } else {
            emit(null)
        }
    }.flowOn(Dispatchers.Default)
}

internal actual fun downloadWorkController(): DownloadWorkController = IosDownloadWorkController
