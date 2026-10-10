package lovehan1me.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B4-1 / F13：hosts 文本解析与网络配置往返。
 *
 * 解析是**纯函数**，把 `/etc/hosts` 的三种容易踩的写法（注释、IPv6、空行）钉死；
 * 导入/导出路径用内存 store 走一遍，确认"hosts 文本→IP 列表"与"配置 JSON→整份应用"
 * 两条分支各归其位，且脏输入不改设置。
 */
class NetworkConfigManagerTest {

    private class FakeStore(initial: AppSettings = AppSettings()) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun install(initial: AppSettings = AppSettings()) {
        SettingsRepository.install(FakeStore(initial))
    }

    @AfterTest
    fun 复位为默认设置() {
        SettingsRepository.install(FakeStore(AppSettings()))
    }

    // ---------- hosts 文本解析（纯函数） ----------

    @Test
    fun 整行注释与行内注释被丢弃() {
        val text = """
            # 这是整行注释
            1.2.3.4 hanime1.me   # 行内注释
            5.6.7.8 www.hanime1.me
        """.trimIndent()
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), parseHostsText(text))
    }

    @Test
    fun 空行与空白行被跳过() {
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), parseHostsText("\n1.2.3.4\n   \n\t\n5.6.7.8\n"))
    }

    @Test
    fun IPV6各种写法被接受() {
        val text = """
            ::1 localhost
            2001:db8::1 example.com
            [::1] bracketed
            fe80::1%eth0 zone
            2001:0db8:0000:0000:0000:0000:0000:0001 full
        """.trimIndent()
        assertEquals(
            listOf("::1", "2001:db8::1", "fe80::1", "2001:0db8:0000:0000:0000:0000:0000:0001"),
            parseHostsText(text),
        )
    }

    @Test
    fun 去重保序() {
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), parseHostsText("1.1.1.1\n8.8.8.8\n1.1.1.1\n"))
    }

    @Test
    fun 兼容旧逗号格式() {
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), parseHostsText("1.2.3.4, 5.6.7.8"))
    }

    @Test
    fun 丢弃非法IP与主机名() {
        assertEquals(listOf("1.2.3.4"), parseHostsText("999.1.1.1 bad\nnot-an-ip host\n1.2.3.4 ok"))
    }

    @Test
    fun 处理CRLF换行() {
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), parseHostsText("1.2.3.4\r\n5.6.7.8\r\n"))
    }

    // ---------- 配置 JSON 往返 ----------

    @Test
    fun 配置序列化后可原样解析() {
        install(AppSettings(domainName = "https://a.me/", useDoH = true, proxyPort = 1080))
        val parsed = NetworkConfigManager.parseConfigText(NetworkConfigManager.buildConfigText())
        assertEquals("https://a.me/", parsed?.domainName)
        assertTrue(parsed?.useDoH == true)
        assertEquals(1080, parsed?.proxyPort)
    }

    @Test
    fun 无version键的JSON不被当作配置() {
        // ignoreUnknownKeys 下 `{"unknown":1}` 能被解码成全默认值 —— 必须被 version 门挡住，
        // 否则导入别的应用的 JSON 会清空用户设置。
        assertNull(NetworkConfigManager.parseConfigText("not json at all"))
        assertNull(NetworkConfigManager.parseConfigText("{\"unknown\":1}"))
    }

    // ---------- 导入分支 ----------

    @Test
    fun 导入hosts文本只写IP列表() = runBlocking {
        install(AppSettings(customHostsData = ""))
        val outcome = NetworkConfigManager.importText("# my hosts\n1.2.3.4 a.me\n5.6.7.8 b.me\n")
        assertEquals(NetworkConfigManager.ImportOutcome.HostsImported(2), outcome)
        assertEquals("1.2.3.4,5.6.7.8", SettingsRepository.customHostsData)
    }

    @Test
    fun 导入配置JSON整份应用() = runBlocking {
        install(AppSettings(domainName = "https://orig.me/"))
        val config = NetworkConfigManager.NetworkConfig(
            domainName = "https://new.me/",
            useDoH = true,
            customHostsData = "9.9.9.9",
            proxyPort = 8080,
        )
        val outcome = NetworkConfigManager.importText(NetworkConfigManager.buildConfigText(config))
        assertEquals(NetworkConfigManager.ImportOutcome.ConfigApplied, outcome)
        assertEquals("https://new.me/", SettingsRepository.current.domainName)
        assertTrue(SettingsRepository.current.useDoH)
        assertEquals("9.9.9.9", SettingsRepository.current.customHostsData)
        assertEquals(8080, SettingsRepository.current.proxyPort)
    }

    @Test
    fun 脏文本不改设置() = runBlocking {
        install(AppSettings(customHostsData = "1.1.1.1"))
        val outcome = NetworkConfigManager.importText("hello world\n# nothing here\n")
        assertEquals(NetworkConfigManager.ImportOutcome.Invalid, outcome)
        assertEquals("1.1.1.1", SettingsRepository.customHostsData)
    }
}