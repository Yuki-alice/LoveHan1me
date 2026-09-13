package lovehan1me.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import lovehan1me.core.util.StartupTrace
import lovehan1me.data.network.HanimeDns
import lovehan1me.data.network.HanimeProxySelector
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// P6d-3-B：jvmMain 真实现。构造照抄 getchu 版（OkHttp + HanimeDns + 代理选择器），
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
                .dns(HanimeDns())
                .proxySelector(HanimeProxySelector())
                .build()
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
                }
                .build()
                // M5-2：图片管线真正就绪的时刻（Coil 是懒加载，这一步通常在首帧之后，属预期）。
                // 放在这里而不是各端入口：Android 没在 Application 里预建 ImageLoader，
                // 桌面虽有单例注册（coil-register），但真正用来取图的仍是这个。
                .also { StartupTrace.mark("coil") }
        }
    }
}
