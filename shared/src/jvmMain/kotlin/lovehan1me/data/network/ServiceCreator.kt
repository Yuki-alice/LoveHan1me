package lovehan1me.data.network

import lovehan1me.data.network.interceptor.EchGateInterceptor
import lovehan1me.data.network.interceptor.GetchuInterceptor
import lovehan1me.data.network.interceptor.RetryInterceptor
import lovehan1me.data.network.interceptor.SpeedLimitInterceptor
import lovehan1me.data.network.interceptor.UrlLoggingInterceptor
import lovehan1me.data.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.CopyOnWriteArrayList
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
 * - 三条链最外层都挂 [RetryInterceptor]：站点在 DPI 下会间歇性 RST，同一请求重发一次即好，
 *   此前只能靠用户手动重滑。
 */
object ServiceCreator {

    private val cache = Cache(
        directory = httpCacheDirectory(),
        maxSize = 10 * 1024 * 1024
    )

    private val downloadSpeedLimitInterceptor = SpeedLimitInterceptor()

    private val dns = HanimeDns.SHARED

    /**
     * 网关未运行时它自己放行直连，所以常驻拦截器列表是安全的
     * （见 [EchGateInterceptor] 的说明）。
     */
    private val echGateInterceptor = EchGateInterceptor()

    /**
     * 传输层重试（幂等方法 + 连接类异常），见 [RetryInterceptor]。
     *
     * 放在每条链的**最外层**：重试要重跑整条链（含网关改写与 UA 覆盖），
     * 而不是只重放最内层的网络调用。无状态，三个客户端共用一个实例。
     */
    private val retryInterceptor = RetryInterceptor()

    /**
     * 三个客户端只依赖不随设置变化的参数（超时 / 协议 / 缓存目录）。出口判定
     * （DNS、代理、网关）、UA、Cookie、限速全部在拦截器里每请求读取实时设置，
     * 所以它们是**稳定单例**：改任何网络设置都不需要重建。
     */
    val hClient: OkHttpClient = buildHClient()

    val downloadClient: OkHttpClient = buildDownloadClient()

    val getchuClient: OkHttpClient = buildGetchuClient()

    /**
     * 换网时需要摘池的**额外**客户端（登记制）。
     *
     * 为什么要有它：此前这份清单一字排开写死在 [evictConnectionPools] 里
     * （hClient / getchuClient / downloadClient + CDN 链），于是"新增一个自建 client"
     * 与"记得回来加一行"之间只靠人记 —— 桌面下载控制器那组独立池就这么漏了一整轮。
     * 改成登记制后：**谁建池谁登记**，复位入口不必认识任何一个具体客户端。
     *
     * 只给 `:shared` 内部与 desktopMain 用（androidMain/desktopMain 都能看见 jvmMain 的 internal）。
     */
    private val extraPoolEvictors = CopyOnWriteArrayList<() -> Unit>()

    internal fun registerConnectionPoolEvictor(evict: () -> Unit) {
        extraPoolEvictors += evict
    }

    /**
     * 摘掉旧网络上的空闲连接（网络变化时由 `platformOnNetworkChanged` 调用）。
     *
     * `evictAll()` 按 OkHttp 语义只摘空闲连接（`allocationCount == 0`），进行中的请求
     * 与下载不受影响——`NetworkChangeReactionsTest` 把这条假设固定住，防升级语义漂移。
     */
    fun evictConnectionPools() {
        hClient.connectionPool.evictAll()
        getchuClient.connectionPool.evictAll()
        downloadClient.connectionPool.evictAll()
        // 登记过的自建客户端（见 registerConnectionPoolEvictor）。单个登记项抛异常不该
        // 连累其余复位动作：这里是"尽力清干净"，不是事务。
        extraPoolEvictors.forEach { runCatching { it() } }
        // CDN 链（封面 / 图片）的池是另一组派生实例，见 CdnFetchClient。
        evictCdnConnectionPools()
    }

    private fun buildGetchuClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(retryInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .addInterceptor(GetchuInterceptor())
            // getchu 同样可能被 SNI 阻断：网关未运行时放行零开销，运行时走普通 TLS 策略。
            .addInterceptor(echGateInterceptor)
            .cookieJar(CookieJar.NO_COOKIES)
            .proxySelector(HanimeProxySelector.SHARED)
            .dns(dns)
            .build()
    }

    private fun buildDownloadClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor(retryInterceptor)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(downloadSpeedLimitInterceptor)
            // 视频直链同样被 SNI 阻断（CDN77 走网关 CNAME 策略）：下载必须与浏览同出口。
            // 失败时 EchGateInterceptor 自己回退直连，不会把下载卡死在网关上。
            .addInterceptor(echGateInterceptor)
            // 与浏览同一出口：站点直连被重置时，"能看不能下"就是代理没跟过来
            .proxySelector(HanimeProxySelector.SHARED)
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
            .addInterceptor(retryInterceptor)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            // 放在日志之后：日志记录的是改写前的真实 URL，排查时才有意义。
            .addInterceptor(echGateInterceptor)
            .cache(cache)
            .cookieJar(HCookieJar())
            .proxySelector(HanimeProxySelector.SHARED)
            .dns(dns)
        createCloudflareInterceptor()?.let { builder.addInterceptor(it) }
        return builder.build()
    }

}
