package lovehan1me.core.platform

/**
 * 平台工厂（P6a-F）。
 * Android 实现依赖 :app 的 HanimeCacheManager/WorkManager worker，无法进 shared androidMain，
 * 采用「provider 注册」：:app 启动时调 [setVideoCacheStoreProvider]/[setDownloadWorkControllerProvider]；
 * desktop/ios actual 按能力裁剪（VideoCacheStore 暂 no-op，Gate4-平台能力收尾）。
 */
internal expect fun videoCacheStore(): VideoCacheStore
internal expect fun downloadWorkController(): DownloadWorkController
