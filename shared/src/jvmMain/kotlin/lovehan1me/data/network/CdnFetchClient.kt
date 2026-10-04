package lovehan1me.data.network

import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.interceptor.EchGateInterceptor
import lovehan1me.data.network.interceptor.RetryInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
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
): OkHttpClient {
    val base = CDN_CLIENTS.getOrPut(connectTimeoutSeconds) { buildCdnFetchClient(connectTimeoutSeconds) }
    if (extraInterceptors.isEmpty()) return base
    // 派生实例共享连接池与分发器：每次调用都从 Builder 起一个新 client，等于每个
    // 调用点各开一个连接池，首页几十张封面各自握一次 TLS，复用完全落空。
    // 差异只有追加的拦截器，用 newBuilder() 表达正好。
    return base.newBuilder().apply { extraInterceptors.forEach { addInterceptor(it) } }.build()
}

/** 抓第三方站点（更新检查、自建服务）的客户端工厂。 */
internal fun createThirdPartyClient(connectTimeoutSeconds: Long = 15L): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor())
        .dns(HanimeDns.SHARED)
        // 尊重用户手填代理：此前这里是裸 client，配了代理的用户更新检查仍走直连，
        // 与站内请求不是同一条出口，失败时看不出是网络问题还是接口问题。
        .proxySelector(HanimeProxySelector.SHARED)
        // 刻意**不加** EchGateInterceptor：网关只对 --cf-hosts 点名的域名走 CF IP + ECH，
        // 第三方域名进去只是普通转发，多一跳且拿不到任何收益；而这些站点本来就不在
        // 阻断名单上，直连才是正确出口。
        .build()

private val CDN_CLIENTS = ConcurrentHashMap<Long, OkHttpClient>()

/** 网络变化时摘掉 CDN 链的空闲连接（见 `ServiceCreator.evictConnectionPools`）。 */
internal fun evictCdnConnectionPools() {
    CDN_CLIENTS.values.forEach { it.connectionPool.evictAll() }
}

private fun buildCdnFetchClient(connectTimeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
    // 图片是小二进制：读停滞 15s / 整呼叫 30s 还没完就是死了（与 Image 预算 30s 同口径）。
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    // 重试放最外层：要重跑整条链（含网关改写），不是只重放最内层的网络调用。
    .addInterceptor(RetryInterceptor())
    .dns(HanimeDns.SHARED)
    .proxySelector(HanimeProxySelector.SHARED)
    // 不注入站点 Cookie：图床是第三方，登录态发过去只有泄漏风险，没有用途。
    .addInterceptor(EchGateInterceptor(attachSiteCookies = false, defaultPurpose = EgressPurpose.Image))
    .build()
