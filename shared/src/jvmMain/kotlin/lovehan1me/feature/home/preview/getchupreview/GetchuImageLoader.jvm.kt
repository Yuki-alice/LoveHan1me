package lovehan1me.feature.home.preview.getchupreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import lovehan1me.core.constant.DESKTOP_USER_AGENT
import lovehan1me.data.network.createCdnFetchClient
import okhttp3.Interceptor

// P6d-2：jvmMain 真实现（:app 原 rememberGetchuImageLoader + createGetchuImageLoader 照搬）。
// HanimeDns/HanimeProxySelector/拦截器链全在 jvmMain 可用；LocalInspectionMode 分支保留。

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
    // 出口（DNS / 代理 / 网关 / 重试）走共用工厂；getchu 的域名特化头作为额外拦截器
    // 追加在网关改写之后，顺序与收进工厂前逐字一致。
    val imageClient = createCdnFetchClient(extraInterceptors = listOf(getchuBrandHeaderInterceptor))
    return ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
        }
        .build()
}

/**
 * getchu 的 `/brandnew/` 路径要补 UA/Referer/Cookie（与 `ServiceCreator` 的
 * `GetchuInterceptor` 同语义；这里是图片加载，走的是另一条 client）。
 */
private val getchuBrandHeaderInterceptor = Interceptor { chain ->
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
