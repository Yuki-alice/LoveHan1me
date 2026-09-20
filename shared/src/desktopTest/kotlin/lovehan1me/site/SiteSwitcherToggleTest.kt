package lovehan1me.site

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository

/**
 * 切站按钮目标（[SiteSwitcher.resolveToggleTarget]）的往返回归。
 *
 * 只测纯的目标解析，不走 `switchTo`（后者重建网络/WebView cookie，需真机环境）。
 * 番剧→AV→回程必须闭环；回程目标丢失或指向 AV 时兜底回番剧主站，不能"点了没反应"。
 */
class SiteSwitcherToggleTest {

    private class InMemorySettingsStore : SettingsStore {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun useSite(domainName: String, selectedBaseUrl: String) {
        runCatching { SettingsRepository.install(InMemorySettingsStore()) }
        runBlocking {
            SettingsRepository.update { it.copy(domainName = domainName, selectedBaseUrl = selectedBaseUrl) }
        }
    }

    @Test
    fun `番剧站切向AV站`() {
        useSite(HanimeConstants.HANIME_URL[0], HanimeConstants.HANIME_URL[0])
        assertEquals(HanimeConstants.AV_URL, SiteSwitcher.resolveToggleTarget())
    }

    @Test
    fun `AV站回程到记住的番剧站`() {
        useSite(HanimeConstants.AV_URL, HanimeConstants.HANIME_URL[1])
        assertEquals(HanimeConstants.HANIME_URL[1], SiteSwitcher.resolveToggleTarget())
    }

    @Test
    fun `回程目标为空时兜底番剧主站`() {
        useSite(HanimeConstants.AV_URL, "")
        assertEquals(HanimeConstants.HANIME_URL[0], SiteSwitcher.resolveToggleTarget())
    }

    @Test
    fun `回程目标本身指向AV站时兜底番剧主站`() {
        useSite(HanimeConstants.AV_URL, HanimeConstants.AV_URL)
        assertEquals(HanimeConstants.HANIME_URL[0], SiteSwitcher.resolveToggleTarget())
    }
}
