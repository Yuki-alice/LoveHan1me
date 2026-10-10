package lovehan1me.data.network

import lovehan1me.data.network.resolveMediaProxyUrl
import lovehan1me.core.constant.HanimeConstants.HANIME_HOSTNAME
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.ProxyMode
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.ForceMode
import lovehan1me.data.network.egress.ProxyState
import lovehan1me.data.network.egress.RouteRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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

/**
 * 确保有一个可写的 store。
 *
 * **必须用 [SettingsRepository.installIfAbsent] 而不是 `install`**：本助手在外层
 * （[withProxy]）与内层（[withRunningGate]）都会被调到。若每次调用都 `install` 一个全新的
 * 出厂默认 store，内层那次会把外层刚设好的代理清掉 —— `MediaProxyPreferenceWiringTest`
 * 随即看到"没有可用代理"而误判要改写到网关。用 installIfAbsent 才是真正的"确保存在"。
 */
private fun ensureStoreInstalled() {
    SettingsRepository.installIfAbsent(EgressTestStore())
}

private fun withProxy(
    type: ProxyType,
    ip: String = "",
    port: Int = -1,
    mode: ProxyMode = ProxyMode.Global,
    block: () -> Unit,
) {
    ensureStoreInstalled()
    runBlocking {
        SettingsRepository.update {
            it.copy(proxyType = type, proxyIp = ip, proxyPort = port, proxyMode = mode)
        }
    }
    try {
        block()
    } finally {
        runBlocking {
            SettingsRepository.update {
                it.copy(proxyType = ProxyType.System, proxyIp = "", proxyPort = -1, proxyMode = ProxyMode.Global)
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

    @Test
    fun `Rules 模式只让受限域走代理`() = withProxy(ProxyType.Http, "203.0.113.7", 7890, ProxyMode.Rules) {
        // 站点：按代理走
        assertEquals(Proxy.Type.HTTP, selector.select(uri).first().type())
        // 第三方 API（弹弹play）：直连，不经代理
        assertEquals(
            listOf(Proxy.NO_PROXY),
            selector.select(URI("https://api.dandanplay.net/api/v2/search/anime")),
        )
        // 未知 host（图床）：同样直连，省带宽
        assertEquals(
            listOf(Proxy.NO_PROXY),
            selector.select(URI("https://vdownload.hembed.com/x.mp4")),
        )
    }

    @Test
    fun `Rules 模式下 getchu 也走代理`() = withProxy(ProxyType.Http, "203.0.113.7", 7890, ProxyMode.Rules) {
        assertEquals(Proxy.Type.HTTP, selector.select(URI("https://www.getchu.com/")).first().type())
    }

    @Test
    fun `Direct 模式即便配了代理也全直连`() = withProxy(ProxyType.Http, "203.0.113.7", 7890, ProxyMode.Direct) {
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(uri))
    }

    @Test
    fun `Global 模式保持历史行为`() = withProxy(ProxyType.Http, "203.0.113.7", 7890, ProxyMode.Global) {
        // 默认档：第三方也照旧走代理（不是 Rules），保证老用户零变化
        assertEquals(Proxy.Type.HTTP, selector.select(URI("https://api.dandanplay.net/")).first().type())
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

/**
 * 播放层代理落点（[mediaProxyUrlFor]）的回归：**关掉 ECH 网关 + 用系统代理** 这个组合。
 *
 * 隧道与改写道同源（[lovehan1me.data.network.egress.EgressScheduler.tunnelUrl]），
 * 网关一关隧道即 null。旧实现里"系统代理"档只给隧道，于是 mpv 拿到 null 去裸直连，
 * 受限网下报 `tls: IO error -10054` / `mpv_error=-13` —— 2026-10-05 桌面视频打不开的根因。
 */
class MediaProxyFallbackTest {

    @Test
    fun `系统代理在网关关闭时仍把具体代理交给 mpv`() {
        assertEquals(
            "http://127.0.0.1:7897",
            mediaProxyUrlFor(
                proxy = ProxyState.SystemResolved,
                resolvedProxyUrl = "http://127.0.0.1:7897",
                // 网关关闭 → 隧道为 null；旧实现在这里把可用的系统代理整个丢掉。
                tunnelUrl = null,
            ),
        )
    }

    @Test
    fun `解析不出具体代理时退隧道兜底`() {
        // SOCKS：resolveMediaProxyUrl 返回 null，此时才轮到网关 CONNECT 隧道。
        assertEquals(
            "http://127.0.0.1:8080",
            mediaProxyUrlFor(
                proxy = ProxyState.Explicit("203.0.113.7", 7891, socks = true),
                resolvedProxyUrl = null,
                tunnelUrl = "http://127.0.0.1:8080",
            ),
        )
    }

    @Test
    fun `无代理时不编造出口`() {
        assertNull(
            mediaProxyUrlFor(
                proxy = ProxyState.None,
                resolvedProxyUrl = "http://127.0.0.1:7897",
                tunnelUrl = "http://127.0.0.1:8080",
            ),
        )
    }
}

/**
 * 媒体让位代理（2026-10-05 新增）。
 *
 * 实测依据：同一 1080p 直链，网关 CNAME 降级通道（`vdownload.hembed.com →
 * *.rsc.cdn77.org`）本机只有 ~23–33 KB/s，系统代理 `127.0.0.1:7897` 有 ~235 KB/s
 * （约 8 倍）。网关是绕 SNI 阻断的通道、不是带宽通道，视频走它只会一直缓冲，
 * 故只要解析得出**具体 HTTP 代理**，媒体就不该被网关改写。
 */
class PreferProxyOverGateForMediaTest {

    @Test
    fun `解析出具体代理时媒体让位代理`() {
        assertTrue(
            preferProxyOverGateForMedia(
                force = ForceMode.Auto,
                proxy = ProxyState.SystemResolved,
                resolvedProxyUrl = "http://127.0.0.1:7897",
            ),
        )
    }

    @Test
    fun `SOCKS 解析不出地址时仍走网关`() {
        // ffmpeg 的 http_proxy 只认 HTTP 代理：SOCKS 时让位等于让 mpv 裸直连，比网关更糟。
        assertFalse(
            preferProxyOverGateForMedia(
                force = ForceMode.Auto,
                proxy = ProxyState.Explicit("203.0.113.7", 7891, socks = true),
                resolvedProxyUrl = null,
            ),
        )
    }

    @Test
    fun `无代理时不编排让位`() {
        assertFalse(
            preferProxyOverGateForMedia(
                force = ForceMode.Auto,
                proxy = ProxyState.None,
                resolvedProxyUrl = "http://127.0.0.1:7897",
            ),
        )
    }

    @Test
    fun `强制网关不让位`() {
        assertFalse(
            preferProxyOverGateForMedia(
                force = ForceMode.ForceGate,
                proxy = ProxyState.SystemResolved,
                resolvedProxyUrl = "http://127.0.0.1:7897",
            ),
        )
    }
}

/**
 * 串线级守卫：让位判定真的接在 `defaultPlayerNetworkConfig()` 上。
 *
 * [PreferProxyOverGateForMediaTest] 只钉纯判据，这里钉**两半都要到位**：
 * 有代理时 `rewriteForGate` 必须放行、`proxyUrlFor` 必须把代理交出去 ——
 * 少任何一半，mpv 都会退回裸直连（或慢网关），正是"一直缓冲"的成因。
 */
class MediaProxyPreferenceWiringTest {

    private val mediaUri = "https://vdownload.hembed.com/408492-1080p.mp4?secure=abc,123"

    @Test
    fun `有手填 HTTP 代理时媒体不改写网关且拿到代理`() =
        withProxy(ProxyType.Http, "203.0.113.7", 7890) {
            withRunningGate {
                val config = defaultPlayerNetworkConfig()
                assertNull(config.rewriteForGate(mediaUri), "有可用代理时媒体不该再被改写到网关")
                assertEquals("http://203.0.113.7:7890", config.proxyUrlFor(mediaUri))
            }
        }

    @Test
    fun `无代理时媒体仍走网关改写`() = withProxy(ProxyType.Direct) {
        withRunningGate {
            val config = defaultPlayerNetworkConfig()
            val rewrite = config.rewriteForGate(mediaUri)
            assertTrue(
                rewrite?.first?.startsWith("http://127.0.0.1:2602/") == true,
                "无代理时媒体应仍被改写到网关，实际=${rewrite?.first}",
            )
            assertEquals("vdownload.hembed.com", rewrite?.second?.get(EchGatePolicy.TARGET_HEADER))
            // 网关已改写 → 播放层不得再塞 http-proxy（ffmpeg 没有 bypass，会给回环请求也套代理）。
            assertNull(config.proxyUrlFor(mediaUri))
        }
    }

    /**
     * 起网关 + 清健康度 + 显式打开网关开关。
     *
     * 两处都是全 JVM 共享状态，别的用例会写坏且不还原，不显式建立前置条件本类就会
     * 因测试顺序随机变红（单独跑则必绿）：
     * - `useEchGate`：[EchGateRuntimeTest] / [EchGateInterceptorTest] / [NetworkChangeReactionsTest]
     *   会把它写成 false（同 [EchGateLiveTest] 的处理）；
     * - 熔断：[RouteRegistry] 里的健康度，别的用例可能已把视频域熔掉。
     */
    private fun withRunningGate(block: () -> Unit) {
        ensureStoreInstalled()
        val previousStatus = EchGate.status
        val previousUseEchGate = runCatching { SettingsRepository.useEchGate }.getOrDefault(true)
        RouteRegistry.reset()
        runCatching { runBlocking { SettingsRepository.update { it.copy(useEchGate = true) } } }
        EchGate.publish(EchGateStatus.Running(2602))
        try {
            block()
        } finally {
            EchGate.publish(previousStatus)
            runCatching {
                runBlocking { SettingsRepository.update { it.copy(useEchGate = previousUseEchGate) } }
            }
        }
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
    fun `改完代理设置即刻影响下载客户端（客户端不重建）`() {
        ensureStoreInstalled()
        val client = ServiceCreator.downloadClient
        withProxy(ProxyType.Http, "203.0.113.7", 7890) {
            assertSame(
                client,
                ServiceCreator.downloadClient,
                "下载客户端是稳定单例，不应因改设置而重建",
            )
            val proxies = client.proxySelector.select(URI("https://hanime1.me/"))
            assertEquals(
                Proxy.Type.HTTP,
                proxies.first().type(),
                "改了代理却仍走旧出口 —— 选择器没有每请求读实时配置",
            )
        }
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
