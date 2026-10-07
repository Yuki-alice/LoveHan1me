package lovehan1me.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.AttemptOutcome
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressEvents
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.ForceMode
import lovehan1me.data.network.egress.NoRouteException
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.data.network.interceptor.EchGateInterceptor
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 网关拦截器改写/回退回归（纯内存，不碰真实网络）。
 *
 * [RecordingChain] 按目标扮两个角色：回环地址扮网关（断言改写与头），
 * 其它扮源站。`EchGate.status` 是进程全局，用完即还原。
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
        SettingsRepository.install(GateTestStore(AppSettings()))
        // 前置条件一律**显式建立**，不要依赖"别的用例没改过"——三处都是进程全局状态：
        //  - useEchGate 现在参与判定（EgressPlanner），而 EchGateRuntimeTest.resetGlobals()
        //    会把它写成 false 且不还原，于是本类在它之后跑时网关会被整体跳过；
        //  - 熔断健康度同理，且冷却期是 5 分钟；
        //  - 调度器注册表与事件流（Phase 2 新增）：上一个用例熔断的域会把下个用例的网关摘掉。
        runBlocking { SettingsRepository.update { it.copy(useEchGate = true) } }
        RouteRegistry.reset()
        EgressEvents.clear()
    }

    private class RecordingChain(
        private var req: Request,
        private val canceled: Boolean = false,
        private val handler: (Request) -> Response,
    ) : Interceptor.Chain {
        val seen = mutableListOf<Request>()
        override fun request(): Request = req
        override fun proceed(request: Request): Response {
            seen += request
            return handler(request)
        }

        // 真 Call（只借它的取消态，不执行）：canceled=true 时先 cancel，
        // 执行器据此判定"调用方主动取消"，不喂熔断器。
        private val backingCall: Call by lazy {
            OkHttpClient().newCall(req).also { if (canceled) it.cancel() }
        }
        override fun call(): Call = backingCall
        override fun connection(): Connection = throw UnsupportedOperationException()
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

    /** 配 Direct 档：排除宿主机系统代理的干扰，"无代理"必须显式建立（见 403 用例注释）。 */
    private fun withNoProxy(block: () -> Unit) {
        install()
        runBlocking {
            SettingsRepository.update { it.copy(proxyType = ProxyType.Direct) }
        }
        try {
            block()
        } finally {
            runBlocking {
                SettingsRepository.update { it.copy(proxyType = ProxyType.System) }
            }
        }
    }

    @Test
    fun `网关开着但没跑起来且无代理时诚实失败`() = withNoProxy {
        EchGate.publish(EchGateStatus.Idle)
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 200, "ORIGIN") }
            // 受限域上直连已知撞 RST：不排就是不浪费时间，直接诚实失败。
            val failure = assertFailsWith<NoRouteException> { EchGateInterceptor().intercept(chain) }
            assertTrue(failure.reason.contains("未运行") || failure.message!!.contains("未运行"))
            assertEquals(0, chain.seen.size, "表空时一次也不该出去撞墙")
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `网关开关关闭时直连旧语义`() {
        install()
        runBlocking { SettingsRepository.update { it.copy(useEchGate = false) } }
        EchGate.publish(EchGateStatus.Idle)
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 200, "ORIGIN") }
            // 关开关等于声明"我的直连可用"：加速项缺席不连累正常请求（海外用户活在这里）。
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals(200, resp.code)
            assertEquals("ORIGIN", resp.body.string())
            assertEquals(1, chain.seen.size)
            assertEquals("hanime1.me", chain.seen.first().url.host)
        } finally {
            EchGate.publish(EchGateStatus.Idle)
            runBlocking { SettingsRepository.update { it.copy(useEchGate = true) } }
        }
    }

    @Test
    fun `改写携带目标与Host`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
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
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `Cookie按原域名注入`() {
        install()
        runBlocking {
            SettingsRepository.update { it.copy(loginCookie = "session=abc") }
        }
        EchGate.publish(EchGateStatus.Running(18080))
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
            EchGate.publish(EchGateStatus.Idle)
            runBlocking { SettingsRepository.update { it.copy(loginCookie = "") } }
        }
    }

    @Test
    fun `图片链不把站点登录态发给图床`() {
        install()
        runBlocking {
            SettingsRepository.update { it.copy(loginCookie = "session=abc") }
        }
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            var cookie: String? = null
            val chain = RecordingChain(
                // 图床是第三方：会话凭据发过去没有用途，只有泄漏风险。
                Request.Builder().url("https://vdownload.hembed.com/image/a.jpg").build(),
            ) { req ->
                cookie = req.header("Cookie")
                textResponse(req, 200, "IMG")
            }
            EchGateInterceptor(attachSiteCookies = false).intercept(chain)
            assertNull(cookie, "图片链必须关掉站点 Cookie 注入，实际=$cookie")
        } finally {
            EchGate.publish(EchGateStatus.Idle)
            runBlocking { SettingsRepository.update { it.copy(loginCookie = "") } }
        }
    }

    @Test
    fun `网关502先重试网关成功则不用回退`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
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
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `网关502两次且无代理时诚实失败`() = withNoProxy {
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") textResponse(req, 502, "echgate: boom")
                else textResponse(req, 200, "ORIGIN")
            }
            // 受限域、无代理：网关两次都挂不再撞直连（旧行为在此等直连 RST）。
            assertFailsWith<NoRouteException> { EchGateInterceptor().intercept(chain) }
            assertEquals(2, chain.seen.size, "只撞网关（初次 + 单次重试），不碰直连")
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `网关502两次且有代理时走代理`() = withHttpProxy {
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") textResponse(req, 502, "echgate: boom")
                else textResponse(req, 200, "ORIGIN")
            }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals("ORIGIN", resp.body.string())
            // 网关两次 + 代理一次；代理走的是原域名。
            assertEquals(3, chain.seen.size)
            assertEquals("hanime1.me", chain.seen.last().url.host)
            assertNull(chain.seen.last().header(EchGatePolicy.TARGET_HEADER))
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `源站502不回退`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
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
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `网关异常且无代理时诚实失败`() = withNoProxy {
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") throw IOException("connection refused")
                else textResponse(req, 200, "ORIGIN")
            }
            assertFailsWith<NoRouteException> { EchGateInterceptor().intercept(chain) }
            assertEquals(1, chain.seen.size, "网关挂了就停，不拿直连再撞一次")
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `网关异常且有代理时走代理`() = withHttpProxy {
        EchGate.publish(EchGateStatus.Running(18080))
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
            EchGate.publish(EchGateStatus.Idle)
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
        EchGate.publish(EchGateStatus.Running(18080))
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
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `代理路径可用时把网关判为出口被封并熔断`() = withHttpProxy {
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") textResponse(req, 403, "you have been blocked")
                else textResponse(req, 200, "ORIGIN")
            }
            EchGateInterceptor().intercept(chain)
            // 代理能通、网关不能 ⇒ 网关出口被封，阻断类失败一次即熔断本域。
            assertTrue(
                RouteRegistry.healthOf(DomainClass.Hanime).isOpen(RouteId.Gate, currentEpochMillis()),
                "代理路径可用却仍留着网关，下个请求还要再撞一遍",
            )
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `无可用代理时403不重试也不误熔断`() {
        install()
        // 必须显式用 Direct：默认档是 System，而 System 是否"有可用代理"取决于**宿主机**的
        // 系统代理设置 —— 留着默认档，这条用例在配了系统代理的机器上会按设计真的去重试一次，
        // 变成"本机有代理就失败"的伪 flake。
        runBlocking { SettingsRepository.update { it.copy(proxyType = ProxyType.Direct) } }
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 403, "you have been blocked") }
            val resp = EchGateInterceptor().intercept(chain)
            // 没有第二条路可退，原样交出去让 NetworkRepo 按原语义（IP 被封 / CF）处理。
            assertEquals(403, resp.code)
            assertEquals(1, chain.seen.size, "没有代理就不该白重试一次")
            assertFalse(
                RouteRegistry.healthOf(DomainClass.Hanime).isOpen(RouteId.Gate, currentEpochMillis()),
                "没试过代理路径，判不了网关的责",
            )
        } finally {
            EchGate.publish(EchGateStatus.Idle)
            runBlocking { SettingsRepository.update { it.copy(proxyType = ProxyType.System) } }
        }
    }

    @Test
    fun `代理路径同样403时不算网关的锅`() = withHttpProxy {
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 403, "you have been blocked") }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals(403, resp.code)
            assertEquals(2, chain.seen.size, "试用期里值得试一次代理")
            assertFalse(
                RouteRegistry.healthOf(DomainClass.Hanime).isOpen(RouteId.Gate, currentEpochMillis()),
                "两边都 403 ⇒ 不是网关的锅，误熔断会让网关在整个冷却期里形同虚设",
            )
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    // ── Phase 2 新增：用途 tag、上报、强制模式 ──

    @Test
    fun `请求tag可覆盖链默认用途`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder()
                    .url("https://hanime1.me/x.jpg")
                    .tag(EgressPurpose::class.java, EgressPurpose.Image)
                    .build(),
            ) { req -> textResponse(req, 200, "IMG") }
            // 链默认是 Api，tag 把这次标成 Image：预算应走图片档。
            EchGateInterceptor().intercept(chain)
            val event = EgressEvents.recent().last()
            assertEquals(DomainClass.Hanime, event.domain)
            assertEquals(RouteId.Gate, event.route)
            assertEquals(30_000L, event.budgetMs)
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `成功上报喂注册表`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 200, "OK") }
            EchGateInterceptor().intercept(chain)
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
    fun `强制直连经设置生效`() {
        install()
        runBlocking {
            SettingsRepository.update { it.copy(egressForceMode = ForceMode.ForceDirect.name) }
        }
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req -> textResponse(req, 200, "ORIGIN") }
            // 网关明明在跑，强制直连就该走直连：一次、原域名、无网关头。
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals("ORIGIN", resp.body.string())
            assertEquals(1, chain.seen.size)
            assertEquals("hanime1.me", chain.seen.first().url.host)
            assertNull(chain.seen.first().header(EchGatePolicy.TARGET_HEADER))
        } finally {
            EchGate.publish(EchGateStatus.Idle)
            runBlocking { SettingsRepository.update { it.copy(egressForceMode = ForceMode.Auto.name) } }
        }
    }

    // ── 2026-10-04 崩溃回归：取消风暴误熔断 + NoRoute 逃逸 ──

    @Test
    fun `调用方取消不喂熔断器`() {
        install()
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
                canceled = true,
            ) { throw IOException("Canceled") }
            // 取消原样抛，不记账、不熔断。
            val failure = assertFailsWith<IOException> { EchGateInterceptor().intercept(chain) }
            assertEquals("Canceled", failure.message)
            assertEquals(
                0,
                RouteRegistry.healthOf(DomainClass.Hanime).single(RouteId.Gate).consecutiveFailures,
                "取消不是路的问题，记一次都能在风暴里攒出误熔断",
            )
            assertTrue(
                EgressEvents.recent().none { it.domain == DomainClass.Hanime },
                "取消不应产生诊断事件，否则三态会误报切换中",
            )
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `NoRoute走IOException进OkHttp回调而非未捕获`() {
        // 崩溃现场：RealCall$AsyncCall（Coil 图片链）只把 IOException 交给 callback，
        // 非 IO 异常从裸分发线程逃逸 → exit 10。本断言钉住这条递送契约。
        val failure: Exception = NoRouteException(DomainClass.CdnMedia, "test")
        assertTrue(failure is java.io.IOException)
    }

    // ── 3.1 多步让位 × 3.2 路由折叠 ──
    // 折叠后计划至多两步 [Gate, Default]，Default 通常就在末位、无处可让；唯一还能
    // 让位的形态是 Default 被粘滞锁定置顶成 [Default, Gate]。下面两条钉住这个形态：
    // 幂等方法失败必须让位到 Gate（否则 Gate 是死步，F5），非幂等禁止让位。

    /** 预置粘滞锁定：Default 连续 3 次成功 ⇒ 排表时它会置顶到 Gate 之前。 */
    private fun lockDefaultFirst() {
        repeat(3) {
            RouteRegistry.update(DomainClass.Hanime) {
                it.onResult(RouteId.Default, AttemptOutcome.Success, 50L, currentEpochMillis())
            }
        }
    }

    @Test
    fun `默认出口锁定在首位时失败让位网关`() = withHttpProxy {
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            lockDefaultFirst()
            var hits = 0
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                hits++
                // 首位 Default 挂了，应让位给排在它后面的 Gate（改写到回环）。
                if (req.url.host != "127.0.0.1") throw SocketException("egress down")
                textResponse(req, 200, "GATE")
            }
            val resp = EchGateInterceptor().intercept(chain)
            assertEquals("GATE", resp.body.string())
            assertEquals(2, chain.seen.size, "Default 失败后必须让位 Gate，否则 [Default, Gate] 的 Gate 是死步")
            assertEquals(2, hits)
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `非幂等方法在默认出口失败时不让位`() = withHttpProxy {
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            lockDefaultFirst()
            var hits = 0
            val chain = RecordingChain(
                Request.Builder()
                    .url("https://hanime1.me/api")
                    .post("x".toRequestBody("text/plain".toMediaType()))
                    .build(),
            ) { req ->
                hits++
                throw SocketException("egress down")
            }
            // POST 换路重发有双提交风险：即便身后还有 Gate 也不让位。
            val failure = assertFailsWith<IOException> { EchGateInterceptor().intercept(chain) }
            assertFalse(failure is NoRouteException)
            assertEquals(1, chain.seen.size, "非幂等方法禁止让位")
            assertEquals(1, hits)
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `网关502两次后默认出口也失败时诚实失败`() = withHttpProxy {
        EchGate.publish(EchGateStatus.Running(18080))
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { req ->
                if (req.url.host == "127.0.0.1") textResponse(req, 502, "echgate: boom")
                else throw SocketException("egress refused")
            }
            // 网关参与过的计划全败：按“无可用出口”诚实失败，不再交外层 Retry 反复撞。
            assertFailsWith<NoRouteException> { EchGateInterceptor().intercept(chain) }
            assertEquals(3, chain.seen.size, "网关两次（初次 + 单次重试）+ 默认出口一次，全败才诚实失败")
        } finally {
            EchGate.publish(EchGateStatus.Idle)
        }
    }

    @Test
    fun `纯默认出口计划失败时抛原异常而非NoRoute`() {
        install()
        runBlocking {
            SettingsRepository.update { it.copy(useEchGate = false, proxyType = ProxyType.Direct) }
        }
        EchGate.publish(EchGateStatus.Idle)
        try {
            val chain = RecordingChain(
                Request.Builder().url("https://hanime1.me/").build(),
            ) { throw SocketException("rst") }
            // 关网关 + 直连：单步纯默认出口计划（无网关步），异常要让外层 Retry 按幂等规则处理。
            val failure = assertFailsWith<IOException> { EchGateInterceptor().intercept(chain) }
            assertFalse(failure is NoRouteException, "纯默认出口计划不该被翻成 NoRoute，Retry 语义要保住")
            assertEquals(1, chain.seen.size)
        } finally {
            EchGate.publish(EchGateStatus.Idle)
            runBlocking {
                SettingsRepository.update { it.copy(useEchGate = true, proxyType = ProxyType.System) }
            }
        }
    }
}
