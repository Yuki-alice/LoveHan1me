package lovehan1me.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.GateHealthHolder
import lovehan1me.data.network.interceptor.EchGateInterceptor
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 网关拦截器改写/回退回归（纯内存，不碰真实网络）。
 *
 * [RecordingChain] 按目标扮两个角色：回环地址扮网关（断言改写与头），
 * 其它扮源站。`EchGate.port` 是进程全局，用完即还原。
 */
class EchGateInterceptorTest {

    private class GateTestStore(initial: AppSettings) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun install() {
        runCatching { SettingsRepository.install(GateTestStore(AppSettings())) }
        // 前置条件一律**显式建立**，不要依赖"别的用例没改过"——两处都是进程全局状态：
        //  - useEchGate 现在参与判定（EgressPlanner），而 EchGateProcessTest.resetGlobals()
        //    会把它写成 false 且不还原，于是本类在它之后跑时网关会被整体跳过；
        //  - 熔断健康度同理，且冷却期是 5 分钟。
        runBlocking { SettingsRepository.update { it.copy(useEchGate = true) } }
        GateHealthHolder.reset()
    }

    private class RecordingChain(
        private var req: Request,
        private val handler: (Request) -> Response,
    ) : Interceptor.Chain {
        val seen = mutableListOf<Request>()
        override fun request(): Request = req
        override fun proceed(request: Request): Response {
            seen += request
            return handler(request)
        }

        override fun connection(): Connection = throw UnsupportedOperationException()
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 10_000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 10_000
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 10_000
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private fun textResponse(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("text/plain".toMediaType()))
            .build()

    @Test
    fun `网关关闭直接放行`() {
        install()
        EchGate.port = -1
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 200, "ORIGIN") }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals(200, resp.code)
            assertEquals("ORIGIN", resp.body.string())
            assertEquals(1, chain.seen.size)
            assertEquals("hanime1.me", chain.seen.first().url.host)
        } finally {
            EchGate.port = -1
        }
    }

    @Test
    fun `改写携带目标与Host`() {
        install()
        EchGate.port = 18080
        try {
            var gateTarget: String? = null
            var gateHost: String? = null
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/search?q=1").build(),
            ) { req ->
                gateTarget = req.header(EchGatePolicy.TARGET_HEADER)
                gateHost = req.header("Host")
                textResponse(req, 200, "GATE")
            }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals("GATE", resp.body.string())
            assertEquals(1, chain.seen.size)
            val gateReq = chain.seen.first()
            assertEquals("127.0.0.1", gateReq.url.host)
            assertEquals(18080, gateReq.url.port)
            assertEquals("/search?q=1", gateReq.url.encodedPath + "?" + gateReq.url.encodedQuery)
            assertEquals("hanime1.me", gateTarget)
            assertEquals("hanime1.me", gateHost)
        } finally {
            EchGate.port = -1
        }
    }

    @Test
    fun `Cookie按原域名注入`() {
        install()
        runBlocking {
            SettingsRepository.update { it.copy(loginCookie = "session=abc") }
        }
        EchGate.port = 18080
        try {
            var cookie: String? = null
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                cookie = req.header("Cookie")
                textResponse(req, 200, "GATE")
            }
            EchGateInterceptor().intercept(chain)
            assertTrue(cookie?.contains("session=abc") == true, "Cookie 应按原域名注入，实际=$cookie")
        } finally {
            EchGate.port = -1
            runBlocking { SettingsRepository.update { it.copy(loginCookie = "") } }
        }
    }

    @Test
    fun `网关502先重试网关成功则不用回退`() {
        install()
        EchGate.port = 18080
        try {
            var gateHits = 0
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") {
                    gateHits++
                    if (gateHits == 1) textResponse(req, 502, "echgate: boom")
                    else {
                        // 第二次应带重试标记。
                        assertEquals("1", req.header("X-Ech-Retry"))
                        textResponse(req, 200, "GATE-RETRY")
                    }
                } else textResponse(req, 200, "ORIGIN")
            }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals("GATE-RETRY", resp.body.string())
            assertEquals(2, gateHits)
            assertEquals(2, chain.seen.size)
        } finally {
            EchGate.port = -1
        }
    }

    @Test
    fun `网关502两次才回退直连`() {
        install()
        EchGate.port = 18080
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") textResponse(req, 502, "echgate: boom")
                else textResponse(req, 200, "ORIGIN")
            }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals("ORIGIN", resp.body.string())
            // 网关两次 + 直连一次；最后一次是原样重试：目标回到原域名。
            assertEquals(3, chain.seen.size)
            assertEquals("hanime1.me", chain.seen.last().url.host)
            assertNull(chain.seen.last().header(EchGatePolicy.TARGET_HEADER))
        } finally {
            EchGate.port = -1
        }
    }

    @Test
    fun `源站502不回退`() {
        install()
        EchGate.port = 18080
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") textResponse(req, 502, "<html>bad gateway</html>")
                else textResponse(req, 200, "ORIGIN")
            }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals(502, resp.code)
            assertEquals(1, chain.seen.size)
        } finally {
            EchGate.port = -1
        }
    }

    @Test
    fun `网关异常回退直连`() {
        install()
        EchGate.port = 18080
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") throw IOException("connection refused")
                else textResponse(req, 200, "ORIGIN")
            }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals("ORIGIN", resp.body.string())
            assertEquals(2, chain.seen.size)
        } finally {
            EchGate.port = -1
        }
    }

    // ── 本轮修复的核心：网关"连上了但被阻断"时必须有第二条路 ──

    /** 配一个手填 HTTP 代理。有可用代理 ⇒ 网关进入试用期，失败即让位。 */
    private fun withHttpProxy(block: () -> Unit) {
        install()
        runBlocking {
            SettingsRepository.update {
                it.copy(proxyType = ProxyType.Http, proxyIp = "203.0.113.7", proxyPort = 7890)
            }
        }
        try {
            block()
        } finally {
            runBlocking {
                SettingsRepository.update { it.copy(proxyType = ProxyType.System, proxyIp = "", proxyPort = -1) }
            }
        }
    }

    @Test
    fun `网关403且处于试用期时经代理重试`() = withHttpProxy {
        EchGate.port = 18080
        try {
            var gateHits = 0
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") {
                    gateHits++
                    textResponse(req, 403, "you have been blocked")
                } else {
                    textResponse(req, 200, "ORIGIN")
                }
            }
            val resp = EchGateInterceptor().intercept(chain)
            // 用户看到的是能打开，而不是"IP 被封"——这正是故障的正面修复。
            assertEquals(200, resp.code)
            assertEquals("ORIGIN", resp.body.string())
            assertEquals(1, gateHits, "网关只该被撞一次，不能反复打扰")
            assertEquals(2, chain.seen.size)
            assertEquals("hanime1.me", chain.seen.last().url.host)
        } finally {
            EchGate.port = -1
        }
    }

    @Test
    fun `代理路径可用时把网关判为出口被封并熔断`() = withHttpProxy {
        EchGate.port = 18080
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") textResponse(req, 403, "you have been blocked")
                else textResponse(req, 200, "ORIGIN")
            }
            EchGateInterceptor().intercept(chain)
            // 代理能通、网关不能 ⇒ 网关出口被封，阻断类失败一次即熔断。
            assertTrue(GateHealthHolder.current.opened, "代理路径可用却仍留着网关，下个请求还要再撞一遍")
        } finally {
            EchGate.port = -1
        }
    }

    @Test
    fun `无可用代理时403不重试也不误熔断`() {
        install()
        // 必须显式用 Direct：默认档是 System，而 System 是否"有可用代理"取决于**宿主机**的
        // 系统代理设置 —— 留着默认档，这条用例在配了系统代理的机器上会按设计真的去重试一次，
        // 变成"本机有代理就失败"的伪 flake。
        runBlocking { SettingsRepository.update { it.copy(proxyType = ProxyType.Direct) } }
        EchGate.port = 18080
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 403, "you have been blocked") }
            val resp = EchGateInterceptor().intercept(chain)
            // 没有第二条路可退，原样交出去让 NetworkRepo 按原语义（IP 被封 / CF）处理。
            assertEquals(403, resp.code)
            assertEquals(1, chain.seen.size, "没有代理就不该白重试一次")
            assertFalse(GateHealthHolder.current.opened, "没试过代理路径，判不了网关的责")
        } finally {
            EchGate.port = -1
            runBlocking { SettingsRepository.update { it.copy(proxyType = ProxyType.System) } }
        }
    }

    @Test
    fun `代理路径同样403时不算网关的锅`() = withHttpProxy {
        EchGate.port = 18080
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 403, "you have been blocked") }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals(403, resp.code)
            assertEquals(2, chain.seen.size, "试用期里值得试一次代理")
            assertFalse(
                GateHealthHolder.current.opened,
                "两边都 403 ⇒ 不是网关的锅，误熔断会让网关在整个冷却期里形同虚设",
            )
        } finally {
            EchGate.port = -1
        }
    }
}
