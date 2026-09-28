package lovehan1me.data.network

import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertNotSame

private class RebuildTestStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/**
 * `HanimeNetwork.rebuildNetwork()` 的回归。
 *
 * service 的 Ktor 客户端在构造期经 `engine { preconfigured = <底层 client> }` 抓了一份引用。
 * 重建时漏掉哪一个，它就会一直用重建前的底层客户端 —— 连接池、超时、缓存、系统代理的委托
 * 目标全部冻结在重建前。曾经漏过 `subscriptionService`。
 *
 * 断言消息写成用户症状：漏了 subscriptionService 时，观感是"设置页改完代理，订阅页还是老出口"。
 * 只靠"重建函数里多写一行"是防不住的，所以这里逐个字段点名。
 */
class HanimeNetworkRebuildTest {

    private fun snapshot(): List<Pair<String, Any>> = listOf(
        "hanimeService" to HanimeNetwork.hanimeService,
        "getchuService" to HanimeNetwork.getchuService,
        "commentService" to HanimeNetwork.commentService,
        "myListService" to HanimeNetwork.myListService,
        "subscriptionService" to HanimeNetwork.subscriptionService,
    )

    @Test
    fun `重建后每个 service 都换成新实例`() {
        // 构造底层客户端会读代理设置：装一个内存 store，免得读到别的用例留下的状态。
        // 已经装过就保持原样（install 不可重复），此处不是本用例的前提。
        runCatching { SettingsRepository.install(RebuildTestStore()) }

        val before = snapshot()
        HanimeNetwork.rebuildNetwork()
        val after = snapshot()

        for (i in before.indices) {
            val (name, old) = before[i]
            assertNotSame(
                old,
                after[i].second,
                "$name 没换新 —— rebuildNetwork() 漏了它，改完代理设置后它仍拿旧出口跑到下次冷启动",
            )
        }
    }
}
