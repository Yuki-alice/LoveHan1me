package lovehan1me.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import lovehan1me.data.network.HDns
import lovehan1me.data.network.HProxySelector
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// P6d-3-B：jvmMain 真实现。构造照抄 getchu 版（OkHttp + HDns + 代理选择器），
// 只是去掉 getchu 域名特化头（通用加载器）；同时覆盖 Android 和桌面。
@Composable
actual fun rememberHanimeImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val isInspectionMode = LocalInspectionMode.current
    return remember(context, isInspectionMode) {
        if (isInspectionMode) {
            ImageLoader.Builder(context).build()
        } else {
            val imageClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .dns(HDns())
                .proxySelector(HProxySelector())
                .build()
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
                }
                .build()
        }
    }
}
