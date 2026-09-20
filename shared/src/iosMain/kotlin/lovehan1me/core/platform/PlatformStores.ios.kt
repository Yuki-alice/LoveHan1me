package lovehan1me.core.platform

import kotlinx.coroutines.flow.flowOf

// iOS VideoCacheStore 暂为 no-op（Gate3-平台能力：真实现接入时替换）
private object NoOpVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String) = flowOf<lovehan1me.core.domain.model.HanimeVideo?>(null)
}

internal actual fun videoCacheStore(): VideoCacheStore = NoOpVideoCacheStore
internal actual fun downloadWorkController(): DownloadWorkController = IosDownloadWorkController
