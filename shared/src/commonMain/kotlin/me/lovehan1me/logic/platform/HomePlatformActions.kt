package me.lovehan1me.logic.platform

import me.lovehan1me.logic.model.AppLanguage

/**
 * P6d-4：HomeSettingsRoute 的平台面收敛（原散落于 LocalContext.cacheDir /
 * AppCompatDelegate / HanimeApplication.switchLauncher / BuildConfig）。
 */
/** 缓存目录递归字节数（原 utils.folderSize：目录递归 listFiles 求和），无目录返回 0 */
expect suspend fun getCacheDirSize(): Long

/** 清空缓存目录（原 cacheDir.deleteRecursively()），返回是否成功 */
expect suspend fun clearCacheDir(): Boolean

/** 切换应用语言（原 AppLanguageManager.setAppLanguage：Android 走 AppCompatDelegate） */
expect fun applyAppLanguage(language: AppLanguage)

/** 切换桌面启动器图标 alias（原 HanimeApplication.switchLauncher）；桌面/iOS 无此概念，no-op */
expect fun switchLauncherIcon(alias: String)

/** 版本展示串 "name(code)"（原 BuildConfig.VERSION_NAME(VERSION_CODE)） */
expect fun appVersionDisplay(): String

/** Android S+ 才有「应用默认打开」设置页（桌面/iOS 无此概念，false 隐藏入口） */
expect fun supportsPerAppLinks(): Boolean

/** 备份导出/导入：按平台语义打开 uri 对应流（Android=contentResolver，桌面=File），失败/null 由调用方静默跳过 */
expect suspend fun openBackupSink(uri: String): okio.Sink?
expect suspend fun openBackupSource(uri: String): okio.Source?

/** 备份文本读写（local/online lists 的 json） */
expect suspend fun writeBackupText(uri: String, content: String): Boolean
expect suspend fun readBackupText(uri: String): String?

/** 截屏保护立即生效（Android=FLAG_SECURE，桌面/iOS no-op） */
expect fun applySecureMode(enabled: Boolean)

/** 重建当前 Activity（语言/主题切换后，Android 语义） */
expect fun recreateActivity()

/** 原始版本号（BackupData 头信息用，区别于展示串 appVersionDisplay） */
expect fun appVersionNameRaw(): String
expect fun appVersionCodeRaw(): Int

/** 跳转系统「应用默认打开」设置页（Android S+，桌面/iOS no-op） */
expect fun openPerAppLinksSettings()

/** PiP 权限是否已授予（Android AppOps 检查；桌面/iOS 无 PiP，true 使开关走普通路径） */
expect fun isPipPermissionGranted(): Boolean

/** 跳转系统 PiP 设置页（桌面/iOS no-op） */
expect fun openPipPermissionSettings()

/** 设备是否已设置锁屏凭据（应用锁前置检查；桌面/iOS 无系统锁屏概念） */
expect fun isDeviceSecure(): Boolean

/** 拉取远端更新 JSON（Android=OkHttp；桌面/iOS 返回 null 走本地缓存降级） */
expect suspend fun performUpdateJsonRequest(): String?

/** 账号登出（cookie/登录态清理；HomePageViewModel 会话过期等跨平台调用点） */
expect suspend fun performAccountLogout()
