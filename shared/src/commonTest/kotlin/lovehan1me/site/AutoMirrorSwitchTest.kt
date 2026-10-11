package lovehan1me.site

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.AutoMirrorGuard
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.MirrorRegistry
import lovehan1me.data.network.egress.hostOfUrl
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B3-3b 自动镜像切换（档 A「失效才切」）。
 *
 * 探测与切换均注入，因此全部离线断言 —— 不发真实请求、不真的切站。
 * 关注点：当前域可达时绝不换站、guard 两档、不跨站点类别、无可达候选时不盲切。
 */
class AutoMirrorSwitchTest {

    private val animeA = HanimeConstants.HANIME_URL[0] // https://hanime1.me/
    private val animeB = HanimeConstants.HANIME_URL[1] // https://hanime1.com/
    private val animeC = HanimeConstants.HANIME_URL[2] // https://hanimeone.me/
    private val av = HanimeConstants.AV_URL            // https://javchu.com/

    private class InMemorySettingsStore(initial: AppSettings) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun install(
        domainName: String,
        guard: AutoMirrorGuard = AutoMirrorGuard.RequireClearance,
        cfCookies: Map<String, String> = emptyMap(),
        useCustomMirrorSite: Boolean = false,
    ) {
        val settings = AppSettings(
            domainName = domainName,
            autoMirrorGuard = guard,
            cfCookies = cfCookies,
            useCustomMirrorSite = useCustomMirrorSite,
        )
        SettingsRepository.install(InMemorySettingsStore(settings))
    }

    @AfterTest
    fun tearDown() = MirrorRegistry.reset()

    /** 记录切换动作（默认不真正切站，避免测试触发网络/网关副作用）。 */
    private class Recorder {
        val targets = mutableListOf<String>()
        val switchTo: suspend (String) -> Unit = { targets += it }
    }

    @Test
    fun `当前域可达时不动站点`() = runBlocking {
        install(animeA)
        val rec = Recorder()
        val result = AutoMirrorSwitch.run(
            // 当前域探测成功；其余候选"更快"也不该换 —— 档 A 的核心。
            probeOne = { host -> if (host == hostOfUrl(animeA)) 60L else 10L },
            switchTo = rec.switchTo,
        )
        assertEquals(AutoMirrorSwitch.Result.Kept, result)
        assertTrue(rec.targets.isEmpty(), "当前域可达却换了站")
    }

    @Test
    fun `guard 为 RequireClearance 且目标无存档时被拦下`() = runBlocking {
        install(animeA) // 默认 RequireClearance，cfCookies 为空
        val rec = Recorder()
        val result = AutoMirrorSwitch.run(
            probeOne = { host -> if (host == hostOfUrl(animeA)) null else 40L },
            switchTo = rec.switchTo,
        )
        assertTrue(result is AutoMirrorSwitch.Result.Skipped, "应被 guard 拦下，实际=$result")
        assertTrue(rec.targets.isEmpty(), "被拦下却仍切了站")
    }

    @Test
    fun `guard 为 Always 时无条件切换`() = runBlocking {
        install(animeA, guard = AutoMirrorGuard.Always)
        val rec = Recorder()
        val result = AutoMirrorSwitch.run(
            probeOne = { host -> if (host == hostOfUrl(animeA)) null else 40L },
            switchTo = rec.switchTo,
        )
        assertTrue(result is AutoMirrorSwitch.Result.Switched, "应切换，实际=$result")
        assertEquals(listOf(animeB), rec.targets, "探测延迟相同时应取候选表靠前者")
    }

    @Test
    fun `目标域已有 clearance 存档时允许切换`() = runBlocking {
        install(animeA, cfCookies = mapOf(hostOfUrl(animeB)!! to "cf_clearance=x"))
        val rec = Recorder()
        val result = AutoMirrorSwitch.run(
            probeOne = { host -> if (host == hostOfUrl(animeA)) null else 40L },
            switchTo = rec.switchTo,
        )
        assertTrue(result is AutoMirrorSwitch.Result.Switched, "目标已有 clearance 应放行，实际=$result")
        assertEquals(listOf(animeB), rec.targets)
    }

    @Test
    fun `AV 站不与番剧站互切`() = runBlocking {
        install(av)
        val rec = Recorder()
        val result = AutoMirrorSwitch.run(
            probeOne = { null }, // 当前域不可达
            switchTo = rec.switchTo,
        )
        assertEquals(AutoMirrorSwitch.Result.NoCandidate, result, "AV 站只有一个，没有同类镜像可切")
        assertTrue(rec.targets.isEmpty())
    }

    @Test
    fun `开了自定义镜像就不自动切`() = runBlocking {
        install(animeA, useCustomMirrorSite = true)
        val rec = Recorder()
        val result = AutoMirrorSwitch.run(
            probeOne = { if (it == hostOfUrl(animeA)) null else 40L },
            switchTo = rec.switchTo,
        )
        assertEquals(AutoMirrorSwitch.Result.NoCandidate, result)
        assertTrue(rec.targets.isEmpty(), "自定义入口是用户的显式选择，不该被自动切换覆盖")
    }

    @Test
    fun `所有候选都不可达时不盲切`() = runBlocking {
        install(animeA, guard = AutoMirrorGuard.Always)
        val rec = Recorder()
        val result = AutoMirrorSwitch.run(
            probeOne = { null }, // 当前域与候选全不可达
            switchTo = rec.switchTo,
        )
        assertEquals(AutoMirrorSwitch.Result.NoCandidate, result)
        assertTrue(rec.targets.isEmpty(), "没有探通任何候选却切了站")
    }

    @Test
    fun `当前域不可达时切到延迟最低的可用候选`() = runBlocking {
        install(animeA, guard = AutoMirrorGuard.Always)
        val rec = Recorder()
        val result = AutoMirrorSwitch.run(
            probeOne = { host ->
                when (host) {
                    hostOfUrl(animeA) -> null
                    hostOfUrl(animeB) -> 90L
                    else -> 30L // animeC 最快
                }
            },
            switchTo = rec.switchTo,
        )
        assertTrue(result is AutoMirrorSwitch.Result.Switched)
        assertEquals(listOf(animeC), rec.targets, "应切到延迟最低的候选")
    }
}
