package lovehan1me.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.data.DatabaseRepo
import platform.Foundation.NSFileManager

// iOS 真实现（Gate4-4）：与桌面版同规则（DB 行 + 文件存在性双判定，见该文件注释）。
// 路径语义与 `IosDownloadWorkController` 一致（落盘即写绝对路径进 `videoUri`）。
// 调度用 Default：Kotlin/Native 没有 Dispatchers.IO（internal），DB 查询与
// NSFileManager 判定都是短操作，Default 足够。
internal actual fun videoCacheStore(): VideoCacheStore = IosVideoCacheStore

private object IosVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String): Flow<HanimeVideo?> = flow {
        val entity = DatabaseRepo.HanimeDownload.find(videoCode)
        val exists = entity != null &&
            NSFileManager.defaultManager.fileExistsAtPath(entity.videoUri)
        if (entity != null && exists) {
            emit(entity.toCachedVideo())
        } else {
            emit(null)
        }
    }.flowOn(Dispatchers.Default)
}

internal actual fun downloadWorkController(): DownloadWorkController = IosDownloadWorkController
