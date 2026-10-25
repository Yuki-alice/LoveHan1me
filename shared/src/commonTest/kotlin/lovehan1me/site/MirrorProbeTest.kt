package lovehan1me.site

import kotlinx.coroutines.runBlocking
import lovehan1me.data.network.egress.AttemptOutcome
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressReporter
import lovehan1me.data.network.egress.MirrorRegistry
import lovehan1me.data.network.egress.RouteId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F10/B3-3 镜像选优。
 *
 * 探测函数注入，因此全部离线断言 —— 不发真实请求、不真的切站。
 */
class MirrorProbeTest {

    private val now = 1_000_000L
    private val a = "hanime1.me"
    private val b = "hanime1.com"
    private val c = "hanimeone.me"

    /** 上报会喂进全局事件流/注册表；不清会殃及字母序后位用例的 `.single()` 断言。 */
    @kotlin.test.AfterTest
    fun tearDown() {
        lovehan1me.data.network.egress.EgressEvents.clear()
        lovehan1me.data.network.egress.RouteRegistry.reset()
    }

    private fun melt(host: String) {
        repeat(3) {
            EgressReporter.report(DomainClass.Hanime, RouteId.Gate, AttemptOutcome.Blocked, 10L, now, mirror = host)
        }
    }

    @Test
    fun `并发探测收集各镜像延迟`() = runBlocking {
        val result = MirrorProbe.probe(listOf(a, b)) { host -> if (host == a) 30L else 80L }
        assertEquals(mapOf(a to 30L, b to 80L), result)
    }

    @Test
    fun `不可达的镜像被剔除且不连累其他`() = runBlocking {
        val result = MirrorProbe.probe(listOf(a, b, c)) { host ->
            when (host) {
                a -> null
                b -> 50L
                else -> 70L
            }
        }
        assertEquals(mapOf(b to 50L, c to 70L), result, "个别失败不该让整体拿不到答案")
    }

    @Test
    fun `有探测数据时取延迟最低的`() {
        MirrorRegistry.reset()
        assertEquals(b, MirrorProbe.bestOf(listOf(a, b, c), mapOf(a to 90L, b to 40L, c to 70L), now))
    }

    @Test
    fun `熔断的镜像不参与择优`() {
        MirrorRegistry.reset()
        melt(a)
        assertEquals(
            b,
            MirrorProbe.bestOf(listOf(a, b), mapOf(a to 10L, b to 60L), now),
            "a 这一刻更快但正在冷却 —— 选它等于刚恢复就再打一次",
        )
    }

    @Test
    fun `全熔断时仍给出一个答案`() {
        MirrorRegistry.reset()
        melt(a)
        melt(b)
        assertEquals(
            a,
            MirrorProbe.bestOf(listOf(a, b), mapOf(a to 10L, b to 20L), now),
            "返回 null 会让调用方无从下手；最坏的那个也好过没有",
        )
    }

    @Test
    fun `没探测数据时回退档案排序`() {
        MirrorRegistry.reset()
        repeat(3) {
            EgressReporter.report(DomainClass.Hanime, RouteId.Gate, AttemptOutcome.Success, 30L, now, mirror = b)
        }
        assertEquals(
            b,
            MirrorProbe.bestOf(listOf(a, b), emptyMap(), now),
            "探测全失败时档案仍要给得出依据 —— 这正是 B3-2 的意义",
        )
    }

    @Test
    fun `候选为空返回 null`() {
        assertNull(MirrorProbe.bestOf(emptyList(), emptyMap(), now))
    }
}
