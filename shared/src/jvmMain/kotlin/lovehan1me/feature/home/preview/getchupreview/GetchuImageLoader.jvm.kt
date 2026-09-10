package lovehan1me.feature.home.preview.getchupreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import lovehan1me.DESKTOP_USER_AGENT
import lovehan1me.data.network.HDns
import lovehan1me.data.network.HProxySelector
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// P6d-2：jvmMain 真实现（:app 原 rememberGetchuImageLoader + createGetchuImageLoader 照搬）。
// HDns/HProxySelector/拦截器链全在 jvmMain 可用；LocalInspectionMode 分支保留。

@Composable
actual fun rememberGetchuImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val isInspectionMode = LocalInspectionMode.current
    return remember(context, isInspectionMode) {
        if (isInspectionMode) {
            ImageLoader.Builder(context).build()
        } else {
            createGetchuImageLoader(context)
        }
    }
}

fun createGetchuImageLoader(context: coil3.PlatformContext): ImageLoader {
    val imageClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .dns(HDns())
        .proxySelector(HProxySelector())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val builder = request.newBuilder()
            if (url.host == "www.getchu.com" && url.encodedPath.startsWith("/brandnew/")) {
                builder
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .header("Referer", "https://www.getchu.com/")
                    .header("Cookie", "getchu_adalt_flag=getchu.com; gc=gc")
            }
            chain.proceed(builder.build())
        }
        .build()
    return ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
        }
        .build()
}
