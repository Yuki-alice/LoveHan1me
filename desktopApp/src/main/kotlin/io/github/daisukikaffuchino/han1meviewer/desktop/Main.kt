package io.github.daisukikaffuchino.han1meviewer.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.PlatformScreens
import io.github.daisukikaffuchino.han1meviewer.ui.player.DesktopVideoPageHost
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.github.daisukikaffuchino.han1meviewer.App
import io.github.daisukikaffuchino.han1meviewer.ui.crash.installCrashHandler
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.datastore.DataStoreManager
import io.github.daisukikaffuchino.han1meviewer.ui.player.DesktopWindowHolder
import io.github.daisukikaffuchino.utils.LogUtil
import kotlinx.coroutines.runBlocking

/**
 * M1 桌面入口：
 *  - 先初始化 DataStore 并把它装进 SettingsRepository（等价 :app HanimeApplication 的
 *    DataStoreManager.initialize + SettingsRepository.install 两步；漏装会 UninitializedPropertyAccessException）。
 *    拦截器链里的 HProxySelector/HDns 等都会在第一次网络请求时读它，顺序必须在此之前；
 *  - Coil 桌面需注册单例 ImageLoader 并挂上 ktor3 网络取图器（否则图片不会加载）；
 *  - M2：使用须知/来源确认门控已进共享 App（与 Android 同语义），此处不再自动置位；
 *  - 受限网络下可用环境变量 HAN1ME_P3A_PROXY=host:port 给 JVM 设代理（否则直连）。
 */
fun main() {
    // M5-3：尽早注册未捕获异常处理器（落盘报告 + 退出，下次启动展示崩溃页）
    installCrashHandler()
    application {
    System.getenv("HAN1ME_P3A_PROXY")?.takeIf { it.isNotBlank() }?.let { hp ->
        val idx = hp.lastIndexOf(':')
        if (idx > 0) {
            val host = hp.substring(0, idx)
            val port = hp.substring(idx + 1)
            System.setProperty("http.proxyHost", host)
            System.setProperty("http.proxyPort", port)
            System.setProperty("https.proxyHost", host)
            System.setProperty("https.proxyPort", port)
            LogUtil.d("Desktop", "main: JVM proxy=$host:$port (from HAN1ME_P3A_PROXY)")
        }
    }

    // 只初始化一次；任何网络/设置读取之前完成
    runBlocking {
        LogUtil.d("Desktop", "main: initializing DataStoreManager...")
        DataStoreManager.initialize()
        SettingsRepository.install(DataStoreManager)
        LogUtil.d("Desktop", "main: DataStore+SettingsRepository ready, baseUrl=${SettingsRepository.baseUrl}")
    }
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    LogUtil.d("Desktop", "main: Coil singleton ImageLoader(ktor3) registered")
    Window(
        onCloseRequest = ::exitApplication,
        title = "Han1meViewer",
        state = rememberWindowState(size = DpSize(480.dp, 800.dp)),
    ) {
        // M3：注入 AWT 窗口，供 Skia 渲染面定位 SkiaLayer（LocalWindow 垫片）。
        DesktopWindowHolder.window = window
        App(
            onExit = ::exitApplication,
            // M5-2：注入桌面窗口宿主，播放器全屏走 AWT setFullScreenWindow
            platformScreens = PlatformScreens(
                videoPageHost = remember { DesktopVideoPageHost() },
            ),
        )
    }
}
}
