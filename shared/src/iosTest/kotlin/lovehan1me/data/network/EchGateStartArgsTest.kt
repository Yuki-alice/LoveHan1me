package lovehan1me.data.network

import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B4-3b：iOS 起服桥（`EchGateStartArgs`）的生效值守卫。
 *
 * Swift 侧只认这四个 getter —— 桥的默认值必须与网关 CLI 默认值同源，
 * 否则"用户没填"在 iOS 与桌面是两种行为。跑在 `iosSimulatorArm64Test`
 *（本机 macOS 可跑；CI 无 macOS 时跳过）。
 */
class EchGateStartArgsTest {

    private class InMemorySettingsStore(initial: AppSettings) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    @AfterTest
    fun tearDown() {
        SettingsRepository.install(InMemorySettingsStore(AppSettings()))
    }

    @Test
    fun `默认与网关 CLI 默认同源`() {
        SettingsRepository.install(InMemorySettingsStore(AppSettings()))
        assertEquals("https://dns.alidns.com/resolve", EchGateStartArgs.dohUrl())
        assertEquals(15000L, EchGateStartArgs.timeoutMs())
        assertEquals("", EchGateStartArgs.echB64())
        assertEquals("", EchGateStartArgs.upstreamProxy())
    }

    @Test
    fun `用户覆盖直达起服形参`() {
        SettingsRepository.install(
            InMemorySettingsStore(
                AppSettings(
                    echGateTimeoutSec = 20,
                    echGateDohUrl = "https://dns.alidns.com/resolve",
                    echGateEchB64 = "QUJD",
                ),
            ),
        )
        assertEquals(20000L, EchGateStartArgs.timeoutMs())
        assertEquals("https://dns.alidns.com/resolve", EchGateStartArgs.dohUrl())
        assertEquals("QUJD", EchGateStartArgs.echB64())
    }
}
