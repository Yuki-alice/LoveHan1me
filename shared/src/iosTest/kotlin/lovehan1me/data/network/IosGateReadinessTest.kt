package lovehan1me.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * iOS 冷启动网关就绪等待（网络优化 1.3）：有界等待 / 单飞 / 关键前置三语义。
 * 状态迁移全部走 `EchGate.publish`（与 Swift 壳同入口），离线可断言。
 */
class IosGateReadinessTest {

    private fun install(useEchGate: Boolean) {
        SettingsRepository.install(object : SettingsStore {
            private val state = MutableStateFlow(AppSettings(useEchGate = useEchGate))
            override val settings: MutableStateFlow<AppSettings> = state
            override suspend fun update(transform: (AppSettings) -> AppSettings) {
                state.value = transform(state.value)
            }
        })
    }

    @AfterTest
    fun resetGate() {
        EchGate.publish(EchGateStatus.Idle)
    }

    @Test
    fun 已就绪立即返回() = runBlocking {
        EchGate.publish(EchGateStatus.Running(12345))
        assertTrue(IosGateReadiness.awaitReadyIfStarting(timeoutMs = 100))
    }

    @Test
    fun 开关关着零等待() = runBlocking {
        install(useEchGate = false)
        EchGate.publish(EchGateStatus.Starting)
        val began = kotlin.time.TimeSource.Monotonic.markNow()
        assertFalse(IosGateReadiness.awaitReadyIfStarting(timeoutMs = 2_000))
        assertTrue(began.elapsedNow() < kotlin.time.Duration.parse("1s"), "开关关着必须零等待")
    }

    @Test
    fun 拉起中等到就绪() = runBlocking {
        install(useEchGate = true)
        EchGate.publish(EchGateStatus.Starting)
        withTimeout(5_000) {
            launch {
                delay(120)
                EchGate.publish(EchGateStatus.Running(1111))
            }
            assertTrue(IosGateReadiness.awaitReadyIfStarting(timeoutMs = 3_000))
        }
        assertEquals(1111, EchGate.port)
    }

    @Test
    fun 一直不就绪等满预算后放行() = runBlocking {
        install(useEchGate = true)
        EchGate.publish(EchGateStatus.Starting)
        val began = kotlin.time.TimeSource.Monotonic.markNow()
        assertFalse(IosGateReadiness.awaitReadyIfStarting(timeoutMs = 300))
        assertTrue(
            began.elapsedNow() >= kotlin.time.Duration.parse("250ms"),
            "预算内不就绪必须等满预算（放行走兜底）",
        )
    }

    @Test
    fun 失败落点也离开Starting_等待者不被卡满() = runBlocking {
        install(useEchGate = true)
        EchGate.publish(EchGateStatus.Starting)
        withTimeout(5_000) {
            launch {
                delay(100)
                EchGate.publish(EchGateStatus.Stopped)  // Swift 失败路径的落点
            }
            assertFalse(IosGateReadiness.awaitReadyIfStarting(timeoutMs = 3_000))
        }
    }
}
