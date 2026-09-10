package lovehan1me.logic.platform

import kotlinx.coroutines.flow.flowOf

// iOS：no-op（TODO P5 播放器 / P7 下载，接入真实实现时替换）
private object NoOpVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String) = flowOf<lovehan1me.logic.model.HanimeVideo?>(null)
}

internal actual fun videoCacheStore(): VideoCacheStore = NoOpVideoCacheStore
internal actual fun downloadWorkController(): DownloadWorkController = IosDownloadWorkController
