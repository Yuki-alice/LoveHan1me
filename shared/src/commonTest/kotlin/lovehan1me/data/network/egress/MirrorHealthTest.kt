package lovehan1me.data.network.egress

import lovehan1me.core.constant.HanimeConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F12/B3-2 镜像档案。
 *
 * 守的核心是三件事：**档案跨切站保留**、**账本天然有界**、**主链路不受影响**。
 */
class MirrorHealthTest {

    private val now = 1_000_000L
    private val mirrorA = HanimeConstants.HANIME_HOSTNAME[0]
    private val mirrorB = HanimeConstants.HANIME_HOSTNAME[1]

    /** 上报会喂进全局事件流/注册表；不清会殃及字母序后位用例的 `.single()` 断言。 */
    @kotlin.test.AfterTest
    fun tearDown() {
        EgressEvents.clear()
        RouteRegistry.reset()
    }

    /** 阻断类一次即熔（见 [RouteHealth.FAILURE_THRESHOLD] 上方的语义注释）。 */
    private fun meltGate(mirror: String?, times: Int = 3) {
        repeat(times) {
            EgressReporter.report(DomainClass.Hanime, RouteId.Gate, AttemptOutcome.Blocked, 100L, now, mirror = mirror)
        }
    }

    @Test
    fun `每个镜像各自记账互不干扰`() {
        MirrorRegistry.reset()
        meltGate(mirrorA)

        assertTrue(MirrorRegistry.healthOf(mirrorA).isOpen(RouteId.Gate, now), "本镜像确实该熔断")
        assertFalse(
            MirrorRegistry.healthOf(mirrorB).isOpen(RouteId.Gate, now),
            "另一个镜像的档案不该被连累 —— 否则选优时它们看起来一样坏",
        )
    }

    @Test
    fun `切站清域账但不动镜像档案`() {
        MirrorRegistry.reset()
        RouteRegistry.reset()
        meltGate(mirrorA)
        RouteRegistry.reset() // 切站走的就是这一条

        assertFalse(
            RouteRegistry.healthOf(DomainClass.Hanime).isOpen(RouteId.Gate, now),
            "域账切站即清（F22 的既有语义）",
        )
        assertTrue(
            MirrorRegistry.healthOf(mirrorA).isOpen(RouteId.Gate, now),
            "镜像档案必须跨切站保留 —— 清掉它就等于每次切换都瞎选，那正是本档案存在的反面",
        )
    }

    @Test
    fun `换网清空镜像档案`() {
        MirrorRegistry.reset()
        meltGate(mirrorA)
        MirrorRegistry.reset() // 换网
        assertFalse(
            MirrorRegistry.healthOf(mirrorA).isOpen(RouteId.Gate, now),
            "换网后延迟特征变了，旧档案不可比",
        )
    }

    @Test
    fun `镜像键只认站点域名且大小写不敏感`() {
        assertEquals(mirrorA, mirrorHostFor(mirrorA))
        assertEquals(mirrorA, mirrorHostFor(mirrorA.uppercase()))
        assertNull(mirrorHostFor(""))
        assertNull(mirrorHostFor(null))
    }

    @Test
    fun `图床 host 不进档案`() {
        // 账本必须天然有界：图床 / CDN 的 host 千变万化，逐个记账会把内存撑爆。
        // 本仓在 HCookieJar 上已经栽过一次（无界累积最终全站 400），同样的坑不踩第二遍。
        assertNull(mirrorHostFor("vdownload.hembed.com"))
        assertNull(mirrorHostFor("cdn-1234.example-cdn.net"))
        assertNull(mirrorHostFor("api.dandanplay.net"))
    }

    @Test
    fun `择优把熔断的镜像排到最后`() {
        MirrorRegistry.reset()
        meltGate(mirrorA)
        repeat(3) {
            EgressReporter.report(DomainClass.Hanime, RouteId.Gate, AttemptOutcome.Success, 50L, now, mirror = mirrorB)
        }

        val ranked = MirrorRegistry.rankMirrors(listOf(mirrorA, mirrorB), nowMs = now)
        assertEquals(mirrorB, ranked.first(), "熔断的那个必须排到后面")
        assertEquals(mirrorA, ranked.last())
    }

    @Test
    fun `主链路的按域记账不受镜像维度影响`() {
        RouteRegistry.reset()
        MirrorRegistry.reset()
        meltGate(mirrorA)

        // 既有调用点读的是按域的那份，行为必须与加镜像维度之前完全一致。
        assertTrue(RouteRegistry.healthOf(DomainClass.Hanime).isOpen(RouteId.Gate, now))
        assertFalse(RouteRegistry.healthOf(DomainClass.Getchu).isOpen(RouteId.Gate, now), "别域照样干净")
    }
}
