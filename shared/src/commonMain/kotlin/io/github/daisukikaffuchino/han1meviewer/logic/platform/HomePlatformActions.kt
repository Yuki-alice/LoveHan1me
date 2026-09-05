package io.github.daisukikaffuchino.han1meviewer.logic.platform

import io.github.daisukikaffuchino.han1meviewer.logic.model.AppLanguage

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
