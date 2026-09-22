package lovehan1me.data.network

import lovehan1me.feature.player.resolveMediaProxyUrl
import lovehan1me.core.constant.HanimeConstants.HANIME_HOSTNAME
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * 出口链路（代理 / DNS）的回归（离线，可跑）。
 *
 * 对应用户那句"浏览器能打开、应用死活进不去"：浏览器走系统代理，而应用里
 * 任何一条**没接上同一个出口判定**的路都会直连撞墙。这里盯住三处：
 * 1. [HanimeProxySelector]：四种代理模式各自的选路结果（HTTP 层与 mpv 共用它）；
 * 2. [resolveMediaProxyUrl]：播放器拿不拿得到那个 HTTP 代理（SOCKS 明确拿不到）；
 * 3. [HanimeDns]：内置 / 自定义 IP 档是否按设置生效。
 *
 * `SettingsRepository` 是全 JVM 共用的单例，代理设置用 [withProxy] 用完即还原，
 * 否则残留的假代理会把同一次运行里的 live 用例一起拖死。
 */
private class EgressTestStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private fun ensureStoreInstalled() {
    runCatching { SettingsRepository.install(EgressTestStore()) }
}

private fun withProxy(type: ProxyType, ip: String = "", port: Int = -1, block: () -> Unit) {
    ensureStoreInstalled()
    runBlocking {
        SettingsRepository.update { it.copy(proxyType = type, proxyIp = ip, proxyPort = port) }
    }
    try {
        block()
    } finally {
        runBlocking {
            SettingsRepository.update {
                it.copy(proxyType = ProxyType.System, proxyIp = "", proxyPort = -1)
            }
        }
    }
}

private fun useBuiltInHosts(enabled: Boolean, customIps: String = "", block: () -> Unit) {
    ensureStoreInstalled()
    runBlocking {
        SettingsRepository.update { it.copy(useBuiltInHosts = enabled, customHostsData = customIps) }
    }
    try {
        block()
    } finally {
        // 还原：这套设置是全 JVM 共用的，留着会把同一次运行里的 live 用例钉在内置 IP 上。
        runBlocking {
            SettingsRepository.update { it.copy(useBuiltInHosts = false, customHostsData = "") }
        }
    }
}

class HanimeProxySelectorTest {

    private val uri = URI("https://hanime1.me/")

    // 选择器在构造时就读设置，所以必须等 store 装好之后再用（懒加载而不是字段初始化）。
    private val selector by lazy { HanimeProxySelector() }

    @Test
    fun `直连档不编造代理`() = withProxy(ProxyType.Direct) {
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(uri))
    }

    @Test
    fun `手填 HTTP 代理走指定地址`() = withProxy(ProxyType.Http, "203.0.113.7", 7890) {
        val proxies = selector.select(uri)
        assertEquals(1, proxies.size)
        assertEquals(Proxy.Type.HTTP, proxies.first().type())
        val address = proxies.first().address() as InetSocketAddress
        assertEquals("203.0.113.7", address.hostString)
        assertEquals(7890, address.port)
    }

    @Test
    fun `手填 SOCKS 代理走 SOCKS 通道`() = withProxy(ProxyType.Socks, "203.0.113.7", 7891) {
        assertEquals(Proxy.Type.SOCKS, selector.select(uri).first().type())
    }

    @Test
    fun `代理端口没填时不把请求交出去`() = withProxy(ProxyType.Http, "203.0.113.7", -1) {
        // 填了 IP 忘了端口：退回默认选择器，而不是拿一个 port=-1 的半截配置去连。
        assertTrue(
            selector.select(uri).none { (it.address() as? InetSocketAddress)?.hostString == "203.0.113.7" },
        )
    }
}

class MediaProxyUrlTest {

    @Test
    fun `HTTP 代理会透给 mpv`() = withProxy(ProxyType.Http, "203.0.113.7", 7890) {
        assertEquals("http://203.0.113.7:7890", resolveMediaProxyUrl())
    }

    @Test
    fun `SOCKS 代理不塞给 mpv`() = withProxy(ProxyType.Socks, "203.0.113.7", 7891) {
        // ffmpeg 的 http_proxy 只认 HTTP；塞 socks5:// 只会让流更打不开。
        assertEquals(null, resolveMediaProxyUrl())
    }

    @Test
    fun `直连档播放器不设代理`() = withProxy(ProxyType.Direct) {
        assertEquals(null, resolveMediaProxyUrl())
    }
}

class DownloadClientEgressTest {

    @Test
    fun `下载客户端与浏览走同一个出口判定`() {
        ensureStoreInstalled()
        assertTrue(
            ServiceCreator.downloadClient.proxySelector is HanimeProxySelector,
            "下载客户端没接代理选择器时，用户看到的就是网页能开、视频下不动",
        )
    }

    @Test
    fun `改完代理设置后下载客户端跟着重建`() {
        ensureStoreInstalled()
        val before = ServiceCreator.downloadClient
        ServiceCreator.rebuildOkHttpClient()
        assertNotSame(
            before,
            ServiceCreator.downloadClient,
            "重建漏了下载客户端，改完代理要等下次冷启动才影响下载",
        )
    }
}

class HanimeDnsTest {

    @Test
    fun `内置 IP 档按 Hanime 站点生效`() = useBuiltInHosts(true) {
        val host = HANIME_HOSTNAME.first()
        val addresses = HanimeDns().lookup(host).map { it.hostAddress }
        assertTrue(addresses.isNotEmpty(), "$host 应拿到内置 IP")
        assertTrue(addresses.all { it.isNotBlank() })
    }

    @Test
    fun `自定义 IP 覆盖内置 IP`() = useBuiltInHosts(true, "203.0.113.11, 203.0.113.12") {
        val addresses = HanimeDns().lookup(HANIME_HOSTNAME.first()).map { it.hostAddress }
        assertEquals(listOf("203.0.113.11", "203.0.113.12"), addresses)
    }

    @Test
    fun `自定义 IP 填坏时回落内置而不是返回空表`() = useBuiltInHosts(true, " , ") {
        assertTrue(HanimeDns().lookup(HANIME_HOSTNAME.first()).isNotEmpty(), "空表会让 OkHttp 直接判死")
    }

    @Test
    fun `getchu 用自己的固定 IP_不被 Hanime 内置表串走`() = useBuiltInHosts(true) {
        assertEquals(
            listOf("210.155.150.166", "210.155.150.145"),
            HanimeDns().lookup("www.getchu.com").map { it.hostAddress },
        )
    }
}

/**
 * [HanimeDns.preferredIps] 的回归：桌面 CF 验证浏览器靠它决定要不要用
 * `--host-resolver-rules` 把 host 钉住。钉错比不钉更糟——会把验证页送到另一个出口。
 *
 * 这里刻意**只测不触发探测的分支**（两档都关 / 非站点域名 / 手动档），
 * 自动档要连真实网络，交给 live 用例，不混进离线单测。
 */
class PreferredIpsTest {

    private fun withBuiltInModes(
        force: Boolean,
        auto: Boolean,
        customIps: String = "",
        block: () -> Unit,
    ) {
        ensureStoreInstalled()
        runBlocking {
            SettingsRepository.update {
                it.copy(
                    useBuiltInHosts = force,
                    autoBuiltInHosts = auto,
                    customHostsData = customIps,
                )
            }
        }
        try {
            block()
        } finally {
            // 还原成出厂默认：强制档关、自动档开（自动档默认是开的，留着 false 会拖累别的用例）
            runBlocking {
                SettingsRepository.update {
                    it.copy(useBuiltInHosts = false, autoBuiltInHosts = true, customHostsData = "")
                }
            }
        }
    }

    @Test
    fun `两档都关时不给出内置 IP`() = withBuiltInModes(force = false, auto = false) {
        // 不能拿系统解析结果冒充"内置 IP"——那正是被污染的假地址，钉上去验证页就废了。
        assertTrue(HanimeDns().preferredIps(HANIME_HOSTNAME.first()).isEmpty())
    }

    @Test
    fun `非站点域名不参与内置 IP`() = withBuiltInModes(force = true, auto = true) {
        assertTrue(HanimeDns().preferredIps("example.com").isEmpty())
    }

    @Test
    fun `手动档给出自定义 IP_供验证浏览器对齐出口`() =
        withBuiltInModes(force = true, auto = true, customIps = "203.0.113.11") {
            assertEquals(listOf("203.0.113.11"), HanimeDns().preferredIps(HANIME_HOSTNAME.first()))
        }
}
