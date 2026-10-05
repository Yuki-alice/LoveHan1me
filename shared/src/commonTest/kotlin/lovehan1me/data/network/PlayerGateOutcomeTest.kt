package lovehan1me.data.network

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressEvents
import lovehan1me.data.network.egress.RouteHealth
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.feature.player.PlayerNetworkConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 播放网关结局上报（F9 / 阶段 4.2）。
 *
 * 关键回归：**视频域能独立熔断**。此前播放 outcomes 不上报，视频 CDN 与图床常不同域
 * （`vdownload.hembed.com → *.rsc.cdn77.org`），该域的 RouteHealth 永远拿不到网关样本，
 * 网关被墙也不会让位 —— 账本是"编"出来的。
 *
 * 走真实 [defaultPlayerNetworkConfig]（三端各自的 actual），而不是手造假配置，
 * 这样守卫的是"接线"而不只是"分类函数"。
 */
class PlayerGateOutcomeTest {

    /** 视频 CDN 域名：非 hanime、非 getchu，按 Video 用途归 CdnMedia。 */
    private val videoCdn = "https://vdownload.hembed.com/video/seed.mp4"

    @AfterTest
    fun tearDown() {
        RouteRegistry.reset()
        EgressEvents.clear()
    }

    private fun config(): PlayerNetworkConfig = defaultPlayerNetworkConfig()

    @Test
    fun `视频域网关连续失败攒够阈值即熔断`() {
        val config = config()
        repeat(RouteHealth.FAILURE_THRESHOLD) { i ->
            config.onGateLoadOutcome(videoCdn, ok = false, reason = "attempt-$i")
        }
        val now = currentEpochMillis()
        assertTrue(
            RouteRegistry.healthOf(DomainClass.CdnMedia).isOpen(RouteId.Gate, now),
            "CdnMedia 的网关出口应熔断（视频域有自己的健康数据了）",
        )
        // 独立性：视频域自己熔，不连累浏览域（按域记账的意义所在）。
        assertFalse(RouteRegistry.healthOf(DomainClass.Hanime).isOpen(RouteId.Gate, now))
    }

    @Test
    fun `阈值前不熔断`() {
        val config = config()
        repeat(RouteHealth.FAILURE_THRESHOLD - 1) { config.onGateLoadOutcome(videoCdn, ok = false) }
        assertFalse(RouteRegistry.healthOf(DomainClass.CdnMedia).isOpen(RouteId.Gate, currentEpochMillis()))
    }

    @Test
    fun `一条成功喂的是Gate路由且不熔断`() {
        config().onGateLoadOutcome(videoCdn, ok = true)
        val single = RouteRegistry.healthOf(DomainClass.CdnMedia).single(RouteId.Gate)
        assertEquals(1, single.consecutiveSuccesses)
        assertFalse(single.opened)
    }
}