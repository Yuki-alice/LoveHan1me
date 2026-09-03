package io.github.daisukikaffuchino.han1meviewer.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.datastore.DataStoreManager
import io.github.daisukikaffuchino.utils.LogUtil
import kotlinx.coroutines.runBlocking

/**
 * P3a 验证切片入口：
 *  - 先初始化 DataStore 并把它装进 SettingsRepository（等价 :app HanimeApplication 的
 *    DataStoreManager.initialize + SettingsRepository.install 两步；漏装会 UninitializedPropertyAccessException）。
 *    拦截器链里的 HProxySelector/HDns 等都会在第一次网络请求时读它，顺序必须在此之前；
 *  - Coil 桌面需注册单例 ImageLoader 并挂上 ktor3 网络取图器（否则图片不会加载）；
 *  - 受限网络下可用环境变量 HAN1ME_P3A_PROXY=host:port 给 JVM 设代理（否则直连），仅验证脚手架用。
 *
 * 注：shared 的 App()（Compose 模板）保留未删，仅本入口不再使用。
 */
fun main() = application {
    System.getenv("HAN1ME_P3A_PROXY")?.takeIf { it.isNotBlank() }?.let { hp ->
        val idx = hp.lastIndexOf(':')
        if (idx > 0) {
            val host = hp.substring(0, idx)
            val port = hp.substring(idx + 1)
            System.setProperty("http.proxyHost", host)
            System.setProperty("http.proxyPort", port)
            System.setProperty("https.proxyHost", host)
            System.setProperty("https.proxyPort", port)
            LogUtil.d("P3a", "main: JVM proxy=$host:$port (from HAN1ME_P3A_PROXY)")
        }
    }

    // 只初始化一次；任何网络/设置读取之前完成
    runBlocking {
        LogUtil.d("P3a", "main: initializing DataStoreManager...")
        DataStoreManager.initialize()
        SettingsRepository.install(DataStoreManager)
        LogUtil.d("P3a", "main: DataStore+SettingsRepository ready, baseUrl=${SettingsRepository.baseUrl}")
    }
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    LogUtil.d("P3a", "main: Coil singleton ImageLoader(ktor3) registered")
    Window(
        onCloseRequest = ::exitApplication,
        title = "Han1meViewer KMP 骨架",
        state = rememberWindowState(size = DpSize(480.dp, 420.dp)),
    ) {
        // P6c-D：骨架屏（真站回归用 P3aVerificationScreen，临时切换处保留）
        DesktopScaffold()
        // P3aVerificationScreen()
    }
}
