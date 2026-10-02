package lovehan1me.data.network

import com.sun.net.httpserver.HttpServer
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.GateHealth
import lovehan1me.data.network.egress.GateHealthHolder
import lovehan1me.data.network.egress.onNetworkChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class NetworkChangeTestStore : SettingsStore {
    // useEchGate=false：本用例只需出口复位路径，避免请求链在测试里触发网关自愈拉起。
    private val state = MutableStateFlow(AppSettings(useEchGate = false))
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/**
 * 网络变化复位（[onNetworkChanged]）的回归。
 *
 * 钉住两件用户能感知的事：
 * 1. 熔断中的网关在切网后必须立刻重新参与 —— 否则"换到能用的网络"仍要被最长
 *    5 分钟的冷却期挡着，体感是"换了网还是打不开"；
 * 2. 旧网络上的空闲连接必须被摘掉 —— 否则切网后首批请求会先撞它们（黑洞时
 *    要等满读/呼叫超时）再重试。
 *
 * `SettingsRepository` 是全 JVM 共用的单例，这里只在未安装时安装一个假 store
 * （与 EgressChainTest 同一手法）。
 */
class NetworkChangeReactionsTest {

    private fun ensureStoreInstalled() {
        runCatching { SettingsRepository.install(NetworkChangeTestStore()) }
    }

    @AfterTest
    fun tearDown() {
        // 健康度是进程全局的：留着打开态会把同一次运行里其它网关用例整场拖死。
        GateHealthHolder.reset()
    }

    @Test
    fun `切网后熔断的网关立刻重新参与`() {
        ensureStoreInstalled()
        val now = currentEpochMillis()
        GateHealthHolder.recordFailure(now, blocking = true)
        assertTrue(GateHealthHolder.current.isOpen(now), "前置条件：阻断类失败应一次即熔断")

        onNetworkChanged()

        assertEquals(
            GateHealth.CLOSED,
            GateHealthHolder.current,
            "切网后旧的熔断结论不该继续挡着网关（用户会以为'换了网还是打不开'）",
        )
    }

    @Test
    fun `切网后摘掉空闲连接`() {
        ensureStoreInstalled()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            val client = ServiceCreator.hClient
            client.newCall(
                Request.Builder().url("http://127.0.0.1:${server.address.port}/").build(),
            ).execute().use { response ->
                assertEquals(200, response.code)
                response.body.string()
            }
            assertTrue(
                client.connectionPool.connectionCount() >= 1,
                "前置条件：请求完成后应当有一条空闲连接在池里",
            )

            onNetworkChanged()

            assertEquals(
                0,
                client.connectionPool.connectionCount(),
                "旧网络上的空闲连接应当被摘掉，否则切网后首批请求先撞它们再重试",
            )
        } finally {
            server.stop(0)
        }
    }
}