package io.github.daisukikaffuchino.han1meviewer.logic.platform

import kotlinx.coroutines.flow.flowOf

// Desktop：no-op（TODO P5 播放器 / P7 下载，接入真实实现时替换）
private object NoOpVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String) = flowOf<io.github.daisukikaffuchino.han1meviewer.logic.model.HanimeVideo?>(null)
}

private object NoOpDownloadWorkController : DownloadWorkController {
    override fun prune() {}
    override suspend fun initialize() {}
    override fun runningCount() = flowOf(0)
}

internal actual fun videoCacheStore(): VideoCacheStore = NoOpVideoCacheStore
internal actual fun downloadWorkController(): DownloadWorkController = NoOpDownloadWorkController
