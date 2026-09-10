package me.lovehan1me.logic.platform

// Android：实现仍留在 :app（依赖 HCacheManager/WorkManager worker），
// 启动时由 :app 注册 provider（见 HanimeApplication.onCreate）。
@Volatile private var videoCacheStoreProvider: (() -> VideoCacheStore)? = null
@Volatile private var downloadWorkControllerProvider: (() -> DownloadWorkController)? = null

fun setVideoCacheStoreProvider(provider: () -> VideoCacheStore) {
    videoCacheStoreProvider = provider
}

fun setDownloadWorkControllerProvider(provider: () -> DownloadWorkController) {
    downloadWorkControllerProvider = provider
}

internal actual fun videoCacheStore(): VideoCacheStore =
    requireNotNull(videoCacheStoreProvider) { "VideoCacheStore provider 未注册" }()

internal actual fun downloadWorkController(): DownloadWorkController =
    requireNotNull(downloadWorkControllerProvider) { "DownloadWorkController provider 未注册" }()
