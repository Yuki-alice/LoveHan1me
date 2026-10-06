package lovehan1me.data.network

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.platform.playerPlatformCapabilities
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressEvents
import lovehan1me.data.network.egress.RouteHealth
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.data.network.egress.SingleRouteHealth
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
 *
 * ## 端差异：按能力表分叉，而不是跳过
 * iOS 播放**不经网关**（`PlayerWiring.ios.kt` 的 `rewriteForGate` 恒 null，阶段 3.2 的结论），
 * 所以它的 `onGateLoadOutcome` 刻意保持接口默认的空实现 —— 没有"网关链路结局"可报，
 * 报一条等于往该域健康里灌假样本。本用例据 [playerPlatformCapabilities] 的 `gateRewrite` 分叉：
 * 接了网关的端守"熔断该长出来"，没接的端反向守"一个字都不许写进健康度"。三端都跑真断言，
 * 且哪端把 `gateRewrite` 翻成 `true` 却忘了覆写上报，这里立刻转红（与 `PlayerCapabilityMatrixTest` 同口径）。
 */
class PlayerGateOutcomeTest {

    /** 视频 CDN 域名：非 hanime、非 getchu，按 Video 用途归 CdnMedia。 */
    private val videoCdn = "https://vdownload.hembed.com/video/seed.mp4"

    /** 本平台播放是否真的过网关 —— 决定结局上报该不该落进健康度。 */
    private val gateWired: Boolean = playerPlatformCapabilities().gateRewrite

    @AfterTest
    fun tearDown() {
        RouteRegistry.reset()
        EgressEvents.clear()
    }

    private fun config(): PlayerNetworkConfig = defaultPlayerNetworkConfig()

    /** 未接网关的端的反向守卫：CdnMedia 上不许留下任何 Gate 健康样本。 */
    private fun assertNoGateSampleWritten() {
        val health = RouteRegistry.healthOf(DomainClass.CdnMedia)
        assertEquals(
            SingleRouteHealth(),
            health.single(RouteId.Gate),
            "该端播放不经网关，上报却写进了 Gate 健康度（等于凭空造样本）",
        )
        assertFalse(health.isOpen(RouteId.Gate, currentEpochMillis()))
    }

    @Test
    fun `视频域网关连续失败攒够阈值即熔断`() {
        val config = config()
        repeat(RouteHealth.FAILURE_THRESHOLD) { i ->
            config.onGateLoadOutcome(videoCdn, ok = false, reason = "attempt-$i")
        }
        if (!gateWired) {
            assertNoGateSampleWritten()
            return
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
        if (!gateWired) {
            // 不接网关的端没有"阈值前"这回事：走反向守卫，别让这条退化成空断言。
            assertNoGateSampleWritten()
            return
        }
        assertFalse(RouteRegistry.healthOf(DomainClass.CdnMedia).isOpen(RouteId.Gate, currentEpochMillis()))
    }

    @Test
    fun `一条成功喂的是Gate路由且不熔断`() {
        config().onGateLoadOutcome(videoCdn, ok = true)
        if (!gateWired) {
            assertNoGateSampleWritten()
            return
        }
        val single = RouteRegistry.healthOf(DomainClass.CdnMedia).single(RouteId.Gate)
        assertEquals(1, single.consecutiveSuccesses)
        assertFalse(single.opened)
    }
}