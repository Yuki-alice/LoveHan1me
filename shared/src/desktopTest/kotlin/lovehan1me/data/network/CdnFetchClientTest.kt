package lovehan1me.data.network

import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.interceptor.EchGateInterceptor
import lovehan1me.data.network.interceptor.RetryInterceptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class CdnFetchTestStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/**
 * `createCdnFetchClient` 的回归：钉住「CDN 抓取与浏览/下载同一条出口」这条规矩。
 *
 * 这条规矩此前靠人肉往每个客户端里复制配置，并已在 `CoverImageFetcher` 上失效过一次
 * —— 那里只配了 DNS，于是开着 ECH 网关或手填代理时，下载任务的封面走裸直连拿不到。
 * 断言消息写成用户症状，是为了让后人改坏时一眼看懂代价。
 */
class CdnFetchClientTest {

    private fun client(
        connectTimeoutSeconds: Long = 15L,
        extra: List<Interceptor> = emptyList(),
    ) = runCatching { SettingsRepository.install(CdnFetchTestStore()) }.let {
        createCdnFetchClient(connectTimeoutSeconds = connectTimeoutSeconds, extraInterceptors = extra)
    }

    @Test
    fun `出口三件套齐全`() {
        val client = client()
        assertTrue(
            client.proxySelector is HanimeProxySelector,
            "缺代理选择器 —— 手填 HTTP/SOCKS 代理时图片与封面会走裸直连",
        )
        assertTrue(
            client.dns is HanimeDns,
            "缺 DNS 覆盖 —— 站点域名在本机常被污染，走系统解析拿不到图",
        )
        assertEquals(
            1,
            client.interceptors.count { it is EchGateInterceptor },
            "缺 ECH 网关改写（或有重复）—— 开着网关时封面/图片走裸直连，视频却正常，最难排查的一类分叉",
        )
    }

    @Test
    fun `重试挂在最外层`() {
        val client = client()
        assertEquals(
            1,
            client.interceptors.count { it is RetryInterceptor },
            "缺重试 —— DPI 间歇性 RST 时首刷必失败，只能靠用户手动重滑",
        )
        assertTrue(
            client.interceptors.first() is RetryInterceptor,
            "重试必须在最外层：它要重跑整条链（含网关改写），放在内层就只重放网络调用",
        )
    }

    @Test
    fun `额外拦截器追加在网关改写之后`() {
        val marker = Interceptor { chain -> chain.proceed(chain.request()) }
        val interceptors = client(extra = listOf(marker)).interceptors
        // getchu 的域名特化头靠这个位置关系生效：先改写 URL，再按原 host 补头。
        assertTrue(
            interceptors.indexOfFirst { it is EchGateInterceptor } < interceptors.indexOf(marker),
            "额外拦截器跑到网关改写前面了，域名特化头会贴到改写后的 127.0.0.1 上",
        )
    }

    @Test
    fun `超时档位随调用方给出的预算走`() {
        // 封面是下载流程的附属品，超时要短（快速失败），不能与图片加载共用 15s。
        assertEquals(5_000, client(connectTimeoutSeconds = 5).connectTimeoutMillis)
        assertEquals(15_000, client().connectTimeoutMillis)
    }
}
