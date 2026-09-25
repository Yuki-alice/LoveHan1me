package lovehan1me.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import lovehan1me.data.network.HanimeImageHeaders

// iOS 真实现（Gate4-平台能力补齐）：Darwin 引擎 + 通用图片管线（ECH 改写，见帮助函数）。
// 预览模式走默认构造（与 jvm 侧同分支）。
@Composable
actual fun rememberHanimeImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val isInspectionMode = LocalInspectionMode.current
    return remember(context, isInspectionMode) {
        if (isInspectionMode) {
            ImageLoader.Builder(context).build()
        } else {
            val client = HttpClient(Darwin) { install(HanimeImageHeaders) }
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient = client)) }
                .build()
        }
    }
}
