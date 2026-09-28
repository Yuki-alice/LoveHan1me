package lovehan1me.data.network

import lovehan1me.data.network.interceptor.EchGateInterceptor
import lovehan1me.data.network.interceptor.RetryInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 抓 CDN 二进制（封面、站点图片、getchu 图片）用的客户端工厂。
 *
 * ## 为什么要有它
 * 「这些 client 必须与浏览/下载同一条出口」这条规矩，此前是靠人肉往每个
 * `OkHttpClient.Builder()` 里复制 `.dns()` / `.proxySelector()` / `.addInterceptor(EchGateInterceptor())`
 * 维持的。它已经在 `CoverImageFetcher` 上失效过一次：那里只配了 DNS，
 * 于是开着 ECH 网关或手填代理时，下载任务的封面会走裸直连。
 *
 * 复制粘贴的规矩拦不住人；函数 + `CdnFetchClientTest` 才拦得住。收成一份之后，
 * 三个调用点不可能再各缺一块。
 *
 * 覆盖的是「取 CDN 上的二进制」这一类，不覆盖 API/HTML（那是 `ServiceCreator.hClient`
 * 的职责：它还要 cookie、CF 验证、磁盘缓存）与下载（`ServiceCreator.downloadClient`：
 * 它有限速拦截器与 HTTP/1.1 钉死）。**超时档位本就该不同，所以参数化，不做统一。**
 *
 * @param connectTimeoutSeconds 连接超时。封面（下载流程的附属品）用 5s 快速失败，
 *   图片用 15s —— 与各自引入本工厂前的取值一致。
 * @param extraInterceptors 追加在网关改写**之后**的拦截器（如 getchu 的域名特化头）。
 */
internal fun createCdnFetchClient(
    connectTimeoutSeconds: Long = 15L,
    extraInterceptors: List<Interceptor> = emptyList(),
): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
    // 重试放最外层：要重跑整条链（含网关改写），不是只重放最内层的网络调用。
    .addInterceptor(RetryInterceptor())
    .dns(HanimeDns())
    .proxySelector(HanimeProxySelector())
    .addInterceptor(EchGateInterceptor())
    .apply { extraInterceptors.forEach { addInterceptor(it) } }
    .build()
