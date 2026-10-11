package lovehan1me.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B4-3a 网关参数透传的生效值守卫。
 *
 * 全部经 `SettingsRepository` 热读：InMemorySettingStore 注入不同存档，
 * 断言钳制 / 空白归一 / 脏数据丢弃。坏数据必须导向"网关默认值"，
 * 绝不能导向"一个能跑但语义错的值"（如明文 DoH）。
 */
class EchGateArgsTest {

    private class InMemorySettingsStore(initial: AppSettings) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun install(timeoutSec: Int = 15, dohUrl: String = "", echB64: String = "") {
        SettingsRepository.install(
            InMemorySettingsStore(
                AppSettings(
                    echGateTimeoutSec = timeoutSec,
                    echGateDohUrl = dohUrl,
                    echGateEchB64 = echB64,
                ),
            ),
        )
    }

    /** 用例间隔离：自定义存档只在本类有效，不漏给别的测试文件（见 `SettingsRepositoryReinstallTest` 惯例）。 */
    @AfterTest
    fun tearDown() {
        SettingsRepository.install(InMemorySettingsStore(AppSettings()))
    }

    @Test
    fun `默认走网关默认值`() {
        install()
        assertEquals(15, echGateTimeoutSec())
        assertNull(echGateDohUrl(), "空 DoH = 网关默认，不传参")
        assertNull(echGateEchB64(), "空 b64 = 网关自取，不传参")
    }

    @Test
    fun `超时钳到范围`() {
        install(timeoutSec = 3)
        assertEquals(5, echGateTimeoutSec(), "过小钳到下限，否则慢流被误杀")
        install(timeoutSec = 300)
        assertEquals(120, echGateTimeoutSec(), "过大钳到上限")
        install(timeoutSec = 20)
        assertEquals(20, echGateTimeoutSec())
    }

    @Test
    fun `非 https 的 DoH 直接丢弃`() {
        install(dohUrl = "http://dns.alidns.com/resolve")
        assertNull(echGateDohUrl(), "明文 DoH 比默认值更糟，不能将就用")
        install(dohUrl = "https://dns.alidns.com/resolve")
        assertEquals("https://dns.alidns.com/resolve", echGateDohUrl())
    }

    @Test
    fun `空白 DoH 与 b64 一律归一为空`() {
        install(dohUrl = "   ", echB64 = "  ")
        assertNull(echGateDohUrl())
        assertNull(echGateEchB64())
    }

    @Test
    fun `b64 只验字母表`() {
        assertTrue(isPlausibleEchB64("AEX+DQBBfAAgACDS/HJeLkFCWgJwi/tLzjbiwNyTmO0HLFRZOOBxb+jZOwAEAAEAAQASY2A=="))
        assertFalse(isPlausibleEchB64(""), "空串不算合法输入（调用方先判空）")
        assertFalse(isPlausibleEchB64("https://dns.alidns.com/resolve"), "贴错 URL 必须拦下")
        assertFalse(isPlausibleEchB64("AEX，DQBB"), "中文标点必须拦下")
    }

    @Test
    fun `三项互相独立`() {
        install(timeoutSec = 20, dohUrl = "https://dns.alidns.com/resolve", echB64 = "QUJD")
        assertEquals(20, echGateTimeoutSec())
        assertEquals("https://dns.alidns.com/resolve", echGateDohUrl())
        assertEquals("QUJD", echGateEchB64())
    }
}
