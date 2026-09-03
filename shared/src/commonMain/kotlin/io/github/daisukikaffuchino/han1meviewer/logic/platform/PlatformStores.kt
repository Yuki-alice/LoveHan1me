package io.github.daisukikaffuchino.han1meviewer.logic.platform

/**
 * 平台工厂（P6a-F）。
 * Android 实现依赖 :app 的 HCacheManager/WorkManager worker，无法进 shared androidMain，
 * 采用「provider 注册」：:app 启动时调 [setVideoCacheStoreProvider]/[setDownloadWorkControllerProvider]；
 * desktop/ios actual 为 no-op（TODO P5/P7）。
 */
internal expect fun videoCacheStore(): VideoCacheStore
internal expect fun downloadWorkController(): DownloadWorkController
