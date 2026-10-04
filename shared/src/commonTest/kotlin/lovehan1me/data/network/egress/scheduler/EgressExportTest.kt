package lovehan1me.data.network.egress.scheduler

import lovehan1me.data.network.egress.AttemptOutcome
import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressEvent
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.buildEgressExport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 诊断导出回归（纯函数，零 flake）。
 *
 * 钉住格式稳定：用户粘出来的文本必须能被开发者逐行对应到一次 attempt
 * （时间/域/路由/结局/耗时/预算缺一不可）。
 */
class EgressExportTest {

    @Test
    fun `空事件流只剩头行`() {
        assertEquals(
            "LoveHan1me egress diagnostics (0 events)\n",
            buildEgressExport(emptyList()),
        )
    }

    @Test
    fun `每行包含全部六个字段`() {
        val text = buildEgressExport(
            listOf(
                EgressEvent(
                    atMs = 1_700_000_000_000L,
                    domain = DomainClass.Hanime,
                    route = RouteId.Gate,
                    outcome = AttemptOutcome.Success,
                    rttMs = 120L,
                    budgetMs = 60_000L,
                ),
            ),
        )
        assertTrue(text.contains("1700000000000"), text)
        assertTrue(text.contains("Hanime"), text)
        assertTrue(text.contains("Gate"), text)
        assertTrue(text.contains("Success"), text)
        assertTrue(text.contains("rtt=120ms"), text)
        assertTrue(text.contains("budget=60000ms"), text)
    }
}
