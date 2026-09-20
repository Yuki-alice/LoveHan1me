package lovehan1me.core.platform

import kotlinx.coroutines.flow.flowOf

// Desktop VideoCacheStore 暂为 no-op（Gate3-平台能力：真实现接入时替换；下载引擎见 DesktopDownloadWorkController）
private object NoOpVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String) = flowOf<lovehan1me.core.domain.model.HanimeVideo?>(null)
}

private object NoOpDownloadWorkController : DownloadWorkController {
    override fun prune() {}
    override suspend fun initialize() {}
    override fun runningCount() = flowOf(0)
}

internal actual fun videoCacheStore(): VideoCacheStore = NoOpVideoCacheStore
internal actual fun downloadWorkController(): DownloadWorkController = DesktopDownloadWorkController
