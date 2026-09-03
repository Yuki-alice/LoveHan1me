package io.github.daisukikaffuchino.han1meviewer.logic.network

import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.network.interceptor.GetchuInterceptor
import io.github.daisukikaffuchino.han1meviewer.logic.network.interceptor.SpeedLimitInterceptor
import io.github.daisukikaffuchino.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import io.github.daisukikaffuchino.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import io.github.daisukikaffuchino.utils.unsafeLazy
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 22:35
 *
 * P3：自 :app 下沉 jvmMain（android + desktop 共享）。
 * - Retrofit 的 create / createGetchu 已删除（Retrofit 一并移除），
 *   服务实例改由 commonMain 的 Ktor expect 工厂（createHanimeHttpClient 等）承接。
 * - 缓存目录走 [httpCacheDirectory]；Cloudflare 拦截器走 [createCloudflareInterceptor]
 *   （仅 Android 真装），null 则不 addInterceptor，保持原拦截器顺序不变。
 */
object ServiceCreator {

    private val cache = Cache(
        directory = httpCacheDirectory(),
        maxSize = 10 * 1024 * 1024
    )

    private val downloadSpeedLimitInterceptor by unsafeLazy {
        SpeedLimitInterceptor(maxSpeed = SettingsRepository.downloadSpeedLimit)
    }

    private val dns = HDns()

    /**
     * OkHttpClient
     */
    var hClient: OkHttpClient = buildHClient()
        private set

    var downloadClient: OkHttpClient = buildDownloadClient()
        private set

    var getchuClient: OkHttpClient = buildGetchuClient()
        private set

    /**
     * Rebuild OkHttpClient
     */
    fun rebuildOkHttpClient() {
        hClient = buildHClient()
        getchuClient = buildGetchuClient()
    }

    private fun buildGetchuClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(UrlLoggingInterceptor())
            .addInterceptor(GetchuInterceptor())
            .cookieJar(CookieJar.NO_COOKIES)
            .proxySelector(HProxySelector())
            .dns(dns)
            .build()
    }

    private fun buildDownloadClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(downloadSpeedLimitInterceptor)
            .dns(dns)
            .build()
    }

    /**
     * Build OkHttpClient
     */
    private fun buildHClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .cache(cache)
            .cookieJar(HCookieJar())
            .proxySelector(HProxySelector())
            .dns(dns)
        createCloudflareInterceptor()?.let { builder.addInterceptor(it) }
        return builder.build()
    }

}
