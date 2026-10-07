package lovehan1me.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.service.HanimeBaseService
import kotlin.test.Test
import kotlin.test.assertEquals

private class BaseUrlTestStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/**
 * 站点地址必须**每次请求实时解析**，不能是构造期快照。
 *
 * 反例是改造前的行为：service 构造时把 baseUrl 存成 `val`，切站后同一个实例仍打旧站，
 * 只能靠重建 service 兜底。这里用同一个实例连打两次请求，断言第二次打到新站 ——
 * 一旦有人把 baseUrl 改回构造期快照，本用例立刻变红。
 */
class HanimeServiceBaseUrlTest {

    @Test
    fun `同一个 service 实例切站后请求打到新站`() = runBlocking {
        SettingsRepository.install(BaseUrlTestStore())
        // 别的用例可能先装过 store；统一以"当前值"为基准，用完还原，避免污染后续用例。
        val originalDomain = SettingsRepository.current.domainName
        val originalMirror = SettingsRepository.current.useCustomMirrorSite

        try {
            val engine = MockEngine { respond("", HttpStatusCode.OK) }
            val service = HanimeBaseService(HttpClient(engine))

            SettingsRepository.update {
                it.copy(domainName = "https://hanime1.me/", useCustomMirrorSite = false)
            }
            service.getHanimeSearchResult()
            assertEquals(
                "hanime1.me",
                engine.requestHistory.last().url.host,
                "首次请求没打到当前站点",
            )

            SettingsRepository.update { it.copy(domainName = "https://javchu.com/") }
            service.getHanimeSearchResult()
            assertEquals(
                "javchu.com",
                engine.requestHistory.last().url.host,
                "切站后同一个 service 仍打旧站 —— baseUrl 又被构造期快照了",
            )
        } finally {
            SettingsRepository.update {
                it.copy(domainName = originalDomain, useCustomMirrorSite = originalMirror)
            }
        }
    }
}
