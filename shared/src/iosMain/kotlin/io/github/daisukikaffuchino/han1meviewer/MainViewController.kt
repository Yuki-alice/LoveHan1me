package io.github.daisukikaffuchino.han1meviewer

import androidx.compose.ui.window.ComposeUIViewController
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.datastore.DataStoreManager
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController

/**
 * M1/M2 iOS 入口：Xcode 工程把返回的 UIViewController 设为 rootViewController。
 *
 * 启动链与桌面 Main.kt 对齐（DataStore 初始化 + Settings 装配）。
 * M2 起使用须知/来源确认门控进共享 App，此处不再自动置位。
 * `DataStoreManager` 的作用域是 `Dispatchers.Default`，此处主线程
 * `runBlocking` 不会与 `Dispatchers.Main` 死锁；try/catch 保证初始化失败也不炸启动屏
 *（首页走 loading + 重试）。
 *
 * 图片加载无需注册单例：`rememberHanimeImageLoader` 的 iosMain actual 自带默认构造。
 */
fun MainViewController(): UIViewController {
    runCatching {
        runBlocking {
            DataStoreManager.initialize()
            SettingsRepository.install(DataStoreManager)
        }
    }
    return ComposeUIViewController { App() }
}
