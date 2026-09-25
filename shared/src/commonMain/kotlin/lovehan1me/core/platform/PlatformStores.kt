package lovehan1me.core.platform

/**
 * 平台工厂（P6a-F）。
 * Android 实现依赖 :app 的 HanimeCacheManager/WorkManager worker，无法进 shared androidMain，
 * 采用「provider 注册」：:app 启动时调 [setVideoCacheStoreProvider]/[setDownloadWorkControllerProvider]；
 * desktop/iOS actual 直连自家引擎（Gate4-4 起均为真实现：DB 行 + 文件存在性双判定）。
 */
internal expect fun videoCacheStore(): VideoCacheStore
internal expect fun downloadWorkController(): DownloadWorkController
