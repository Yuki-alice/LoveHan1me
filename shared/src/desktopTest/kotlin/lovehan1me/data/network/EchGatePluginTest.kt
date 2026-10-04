package lovehan1me.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressEvents
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.NoRouteException
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.RouteRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Darwin 插件（调度执行器）回归（MockEngine，不碰真实网络）。
 *
 * 与 `EchGateInterceptorTest` 同构：MockEngine 按 host 扮两个角色
 * （回环扮网关、其它扮源站），`EchGate.status` 与注册表用完即还原。
 * 断言与 OkHttp 侧一一对应 —— 两端行为分叉时这里先红。
 */
class EchGatePluginTest {

    private class PluginTestStore(initial: AppSettings) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun install() {
        runCatching { SettingsRepository.install(PluginTestStore(AppSettings())) }
        runBlocking { SettingsRepository.update { it.copy(useEchGate = true) } }
        RouteRegistry.reset()
        EgressEvents.clear()
    }

    /** 无代理必须显式建立：宿主机系统代理在 Darwin 侧不可见，但单测跑在 JVM 上。 */
    private fun withDirectProxy(block: suspend () -> Unit) {
        install()
        runBlocking { SettingsRepository.update { it.copy(proxyType = ProxyType.Direct) } }
        try {
            runBlocking { block() }
        } finally {
            runBlocking { SettingsRepository.update { it.copy(proxyType = ProxyType.System) } }
        }
    }

    private fun withHttpProxy(block: suspend () -> Unit) {
        install()
        runBlocking {
            SettingsRepository.update {
                it.copy(proxyType = ProxyType.Http, proxyIp = "203.0.113.7", proxyPort = 7890)
            }
        }
        try {
            runBlocking { block() }
        } finally {
            runBlocking {
                SettingsRepository.update { it.copy(proxyType = ProxyType.System, proxyIp = "", proxyPort = -1) }
            }
        }
    }

    @Test
    fun `网关改写携带目标与Cookie`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            var gateTarget: String? = null
            var gateCookie: String? = null
            val testClient = HttpClient(MockEngine { request ->
                if (request.url.host == EchGatePolicy.GATE_HOST) {
                    gateTarget = request.headers[EchGatePolicy.TARGET_HEADER]
                    gateCookie = request.headers["Cookie"]
                    assertEquals(18080, request.url.port)
                    assertEquals("/search", request.url.encodedPath)
                    respond("GATE", HttpStatusCode.OK)
                } else {
                    respond("ORIGIN", HttpStatusCode.OK)
                }
            }) {
                install(EchGateClientPlugin) {
                    cookieHeaderProvider = { "session=abc" }
                }
            }
            val body = runBlocking { testClient.get("https://hanime1.me/search?q=1").bodyAsText() }
            assertEquals("GATE", body)
            assertEquals("hanime1.me", gateTarget)
            assertTrue(gateCookie?.contains("session=abc") == true, "Cookie 应按原域名注入，实际=$gateCookie")
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `网关没跑时诚实失败`() {
        withDirectProxy {
            EchGate.publish(EchGateStatus.Idle)
            try {
                val testClient = HttpClient(MockEngine { respond("ORIGIN", HttpStatusCode.OK) }) {
                    install(EchGateClientPlugin) {
                        cookieHeaderProvider = { "session=abc" }
                    }
                }
                assertFailsWith<NoRouteException> { testClient.get("https://hanime1.me/") }
            } finally {
                EchGate.publish(EchGateStatus.Idle)
            }
        }
    }

    @Test
    fun `网关502两次且有代理时走代理`() {
        withHttpProxy {
            EchGate.publish(EchGateStatus.Running(18080))
            try {
                var gateHits = 0
                val testClient = HttpClient(MockEngine { request ->
                    if (request.url.host == EchGatePolicy.GATE_HOST) {
                        gateHits++
                        respond("boom", HttpStatusCode.BadGateway)
                    } else {
                        respond("ORIGIN", HttpStatusCode.OK)
                    }
                }) {
                    install(EchGateClientPlugin) {
                        cookieHeaderProvider = { "session=abc" }
                    }
                }
                val body = testClient.get("https://hanime1.me/").bodyAsText()
                assertEquals("ORIGIN", body)
                assertEquals(2, gateHits, "网关只该被撞两次（初次 + 单次重试），随后让位代理")
            } finally {
                EchGate.publish(EchGateStatus.Idle)
            }
        }
    }

    @Test
    fun `成功上报喂注册表`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val testClient = HttpClient(MockEngine { request ->
                if (request.url.host == EchGatePolicy.GATE_HOST) respond("GATE", HttpStatusCode.OK)
                else respond("ORIGIN", HttpStatusCode.OK)
            }) {
                install(EchGateClientPlugin) {
                    cookieHeaderProvider = { "session=abc" }
                }
            }
            runBlocking { testClient.get("https://hanime1.me/").bodyAsText() }
            assertEquals(
                1,
                RouteRegistry.healthOf(DomainClass.Hanime).single(RouteId.Gate).consecutiveSuccesses,
                "执行器必须上报，否则调度器永远没有择优数据",
            )
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `请求attribute可覆盖默认用途`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val testClient = HttpClient(MockEngine { request ->
                if (request.url.host == EchGatePolicy.GATE_HOST) respond("GATE", HttpStatusCode.OK)
                else respond("ORIGIN", HttpStatusCode.OK)
            }) {
                install(EchGateClientPlugin) {
                    cookieHeaderProvider = { "session=abc" }
                }
            }
            runBlocking {
                testClient.get("https://hanime1.me/x.jpg") {
                    attributes.put(EgressPurposeAttributeKey, EgressPurpose.Image)
                }.bodyAsText()
            }
            val event = EgressEvents.recent().last()
            assertEquals(RouteId.Gate, event.route)
            assertEquals(30_000L, event.budgetMs)
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `无Cookie提供方时不附加Cookie头`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            var gateCookie: String? = "unset"
            val testClient = HttpClient(MockEngine { request ->
                if (request.url.host == EchGatePolicy.GATE_HOST) {
                    gateCookie = request.headers["Cookie"]
                    respond("GATE", HttpStatusCode.OK)
                } else {
                    respond("ORIGIN", HttpStatusCode.OK)
                }
            }) {
                install(EchGateClientPlugin) {
                    cookieHeaderProvider = null
                }
            }
            runBlocking { testClient.get("https://hanime1.me/").bodyAsText() }
            assertNull(gateCookie)
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }
}
