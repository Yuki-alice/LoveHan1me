package lovehan1me.data.network.egress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B4-2/F14 每域出口覆盖（§6.2「显式覆盖默认」）：
 * 每域覆盖 > 全局 `egressForceMode`；坏数据静默丢弃并回落到全局。
 */
class ForceModeOverrideTest {

    private class InMemorySettingsStore(initial: AppSettings) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun install(global: String, perDomain: Map<String, String> = emptyMap()) {
        SettingsRepository.install(
            InMemorySettingsStore(AppSettings(egressForceMode = global, perDomainForceMode = perDomain)),
        )
    }

    @Test
    fun `无覆盖时跟随全局`() {
        install("ForceGate")
        assertEquals(ForceMode.ForceGate, currentForceMode(DomainClass.Hanime))
        assertEquals(ForceMode.ForceGate, currentForceMode(DomainClass.ThirdParty))
    }

    @Test
    fun `每域覆盖优先于全局`() {
        install("ForceGate", mapOf("ThirdParty" to "ForceProxy"))
        assertEquals(ForceMode.ForceGate, currentForceMode(DomainClass.Hanime), "未覆盖的域仍跟随全局")
        assertEquals(ForceMode.ForceProxy, currentForceMode(DomainClass.ThirdParty), "被覆盖的域取每域值")
    }

    @Test
    fun `未知值被丢弃并回落全局`() {
        // 脏备份 / 手改 DataStore 可能塞进不在枚举里的名字：不能让它把该域钉成 Auto。
        install("ForceDirect", mapOf("Hanime" to "Nonsense", "Getchu" to "ForceProxy"))
        assertEquals(ForceMode.ForceDirect, currentForceMode(DomainClass.Hanime), "坏值应回落全局")
        assertEquals(ForceMode.ForceProxy, currentForceMode(DomainClass.Getchu), "好值照常生效")
    }

    @Test
    fun `未知键被忽略`() {
        install("ForceDirect", mapOf("NotADomain" to "ForceProxy"))
        assertEquals(ForceMode.ForceDirect, currentForceMode(DomainClass.Hanime))
        assertEquals(ForceMode.ForceDirect, currentForceMode(DomainClass.ThirdParty))
    }
}
