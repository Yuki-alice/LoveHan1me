package lovehan1me.data.network

import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.interceptor.GetchuInterceptor
import lovehan1me.data.network.interceptor.SpeedLimitInterceptor
import lovehan1me.data.network.interceptor.UrlLoggingInterceptor
import lovehan1me.data.network.interceptor.UserAgentInterceptor
import lovehan1me.core.util.unsafeLazy
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
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

    private val dns = HanimeDns()

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
     *
     * 三个客户端一起重建：下载客户端漏在外的话，用户在设置页改完代理，
     * 页面能刷新了、图片能加载了，**下载仍拿旧代理**跑到下次冷启动才生效。
     */
    fun rebuildOkHttpClient() {
        hClient = buildHClient()
        downloadClient = buildDownloadClient()
        getchuClient = buildGetchuClient()
    }

    private fun buildGetchuClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(UrlLoggingInterceptor())
            .addInterceptor(GetchuInterceptor())
            .cookieJar(CookieJar.NO_COOKIES)
            .proxySelector(HanimeProxySelector())
            .dns(dns)
            .build()
    }

    private fun buildDownloadClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(downloadSpeedLimitInterceptor)
            // 与浏览同一出口：站点直连被重置时，"能看不能下"就是代理没跟过来
            .proxySelector(HanimeProxySelector())
            .dns(dns)
            .build()
    }

    /**
     * Build OkHttpClient
     *
     * A-2：补 read/call 超时（此前只有 connect 15s）。API 侧全是 HTML 小响应，
     * 读停滞 30s / 整呼叫 60s 还没完就是死了——fast-fail 报错，不要转圈到分钟级。
     * 下载与 getchu 客户端不动：前者长连接传文件不能掐，后者是别的业务。
     */
    private fun buildHClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .cache(cache)
            .cookieJar(HCookieJar())
            .proxySelector(HanimeProxySelector())
            .dns(dns)
        createCloudflareInterceptor()?.let { builder.addInterceptor(it) }
        return builder.build()
    }

}
