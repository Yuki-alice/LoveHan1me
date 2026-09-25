package lovehan1me.feature.home.preview.getchupreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import lovehan1me.data.network.GetchuBrandHeaders
import lovehan1me.data.network.HanimeImageHeaders

// iOS 真实现（Gate4-平台能力补齐）：Darwin 引擎 + getchu 域名特化头
//（UA/Referer/Cookie，与 jvm OkHttp 拦截器逐字一致）+ ECH 改写。
// 预览模式走默认构造（与 jvm 侧同分支）。
@Composable
actual fun rememberGetchuImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val isInspectionMode = LocalInspectionMode.current
    return remember(context, isInspectionMode) {
        if (isInspectionMode) {
            ImageLoader.Builder(context).build()
        } else {
            val client = HttpClient(Darwin) {
                install(HanimeImageHeaders)
                install(GetchuBrandHeaders)
            }
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient = client)) }
                .build()
        }
    }
}
