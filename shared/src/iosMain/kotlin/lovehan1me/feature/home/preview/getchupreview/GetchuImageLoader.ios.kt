package lovehan1me.feature.home.preview.getchupreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import lovehan1me.core.util.PlatformLock
import lovehan1me.core.util.withLock
import lovehan1me.data.network.GetchuBrandHeaders
import lovehan1me.data.network.installEchGate

// iOS 真实现（Gate4-平台能力补齐）：Darwin 引擎 + getchu 域名特化头
//（UA/Referer/Cookie，与 jvm OkHttp 拦截器逐字一致）+ ECH 改写。
// 预览模式走默认构造（与 jvm 侧同分支）。
//
// A2：**进程级单例化**（与 jvm 侧同构）。此前每次调用新建一个 `HttpClient(Darwin)`，
// 现在随单例只建一次；inspection 分支不进缓存。

private val getchuLoaderLock = PlatformLock()

private var getchuSingletonLoader: ImageLoader? = null

/** 取进程级单例；首次调用时构造（含 `HttpClient(Darwin)`）。见 jvm 侧同名函数 KDoc。 */
internal fun getchuImageLoaderOrNull(
    context: PlatformContext,
    inspection: Boolean,
): ImageLoader? {
    if (inspection) return null
    return getchuLoaderLock.withLock {
        getchuSingletonLoader ?: run {
            val client = HttpClient(Darwin) {
                installEchGate(withCookies = false)
                install(GetchuBrandHeaders)
            }
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient = client)) }
                .build()
        }.also { getchuSingletonLoader = it }
    }
}

/** 复位进程级单例。**仅供测试**（生产代码不调用）。 */
internal fun resetGetchuImageLoaderForTest() {
    getchuLoaderLock.withLock { getchuSingletonLoader = null }
}

@Composable
actual fun rememberGetchuImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val isInspectionMode = LocalInspectionMode.current
    return remember(context, isInspectionMode) {
        getchuImageLoaderOrNull(context, inspection = isInspectionMode)
            ?: ImageLoader.Builder(context).build()
    }
}
