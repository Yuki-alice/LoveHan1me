package lovehan1me.data.network.egress

import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.domain.model.ProxyMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F8 规则可见的判据测试。
 *
 * 判据放在 commonMain 是为了三端共用；这里守的是"设置页显示的那句话与调度器的实际行为同源"。
 */
class ProxyRulesTest {

    @Test
    fun `Global 档是全部流量且不逐域列举`() {
        val summary = proxyRuleSummary(ProxyMode.Global, ForceMode.Auto, "hanime1.me")
        assertEquals(ProxyRuleScope.All, summary.scope)
        assertTrue(summary.hosts.isEmpty(), "「全部流量」的语义下逐域列举没有意义")
    }

    @Test
    fun `Rules 档列出内置受限域与当前站点`() {
        val summary = proxyRuleSummary(ProxyMode.Rules, ForceMode.Auto, "my.mirror.example")
        assertEquals(ProxyRuleScope.RestrictedOnly, summary.scope)
        HanimeConstants.HANIME_HOSTNAME.forEach { host ->
            assertTrue(summary.hosts.contains(host), "受限域清单缺 $host")
        }
        assertTrue(summary.hosts.contains(GETCHU_HOST))
        assertTrue(
            summary.hosts.contains("my.mirror.example"),
            "自定义镜像不在内置表里，但必须与站点同出口 —— 漏了它，镜像站就走不了代理",
        )
    }

    @Test
    fun `Rules 档下站点已在内置表时不重复`() {
        val known = HanimeConstants.HANIME_HOSTNAME.first()
        val summary = proxyRuleSummary(ProxyMode.Rules, ForceMode.Auto, known)
        assertEquals(summary.hosts.distinct().size, summary.hosts.size)
    }

    @Test
    fun `强制直连与强制网关盖住任何代理档`() {
        ProxyMode.entries.forEach { mode ->
            assertEquals(
                ForceMode.ForceDirect,
                proxyRuleSummary(mode, ForceMode.ForceDirect, null).shadowedBy,
                "强制直连下 $mode 无从生效，必须标出来而不是静默失效",
            )
            assertEquals(
                ForceMode.ForceGate,
                proxyRuleSummary(mode, ForceMode.ForceGate, null).shadowedBy,
                "强制走网关时代理不参与，必须标出来",
            )
        }
    }

    @Test
    fun `强制走代理不盖任何档`() {
        // 与 ForceDirect / ForceGate 不同：强制代理下代理**是**参与出站的，
        // 代理档位没有被架空。它与 Rules 叠加后的实际行为取决于调度器对"第三方拿不到代理"的
        // 回退 —— 那一层尚未厘清（§6.2），在定论之前宁可不标，也不给一句不准确的"暂不生效"。
        ProxyMode.entries.forEach { mode ->
            assertNull(proxyRuleSummary(mode, ForceMode.ForceProxy, null).shadowedBy)
        }
    }

    @Test
    fun `自动档不盖住任何设置`() {
        ProxyMode.entries.forEach { mode ->
            assertNull(proxyRuleSummary(mode, ForceMode.Auto, null).shadowedBy)
        }
    }

    @Test
    fun `解析规则去注释空行与重复`() {
        val rules = parseProxyRuleHosts(
            """
            # 这是一条注释
            example.com

            EXAMPLE.COM
              a.example.com
            """.trimIndent(),
        )
        assertEquals(listOf("example.com", "a.example.com"), rules)
    }

    @Test
    fun `粘贴完整 URL 也能用`() {
        // 用户从地址栏复制一整条 URL 粘进来是最典型的动作，不该要求他手工剥出域名。
        assertEquals(listOf("example.com"), parseProxyRuleHosts("https://example.com:8443/path?q=1"))
    }

    @Test
    fun `通配前缀归一化成同一条`() {
        assertEquals(listOf("example.com"), parseProxyRuleHosts("*.example.com"))
        assertEquals(listOf("example.com"), parseProxyRuleHosts(".example.com"))
    }

    @Test
    fun `后缀匹配连带任意层级子域`() {
        val rules = listOf("example.com")
        assertTrue(matchesProxyRule("example.com", rules))
        assertTrue(matchesProxyRule("a.example.com", rules))
        assertTrue(matchesProxyRule("a.b.example.com", rules))
    }

    @Test
    fun `看起来像的域名不误判`() {
        // 用 contains 或者裸 endsWith(rule) 写都会命中 —— 这正是本函数存在的理由。
        assertFalse(matchesProxyRule("notexample.com", listOf("example.com")))
        assertFalse(matchesProxyRule("myexample.com", listOf("example.com")))
    }

    @Test
    fun `空规则与空 host 都不命中`() {
        assertFalse(matchesProxyRule("example.com", emptyList()))
        assertFalse(matchesProxyRule("", listOf("example.com")))
        assertFalse(matchesProxyRule("   ", listOf("example.com")))
    }

    @Test
    fun `用户追加的规则进得了命中清单`() {
        val summary = proxyRuleSummary(ProxyMode.Rules, ForceMode.Auto, null, listOf("my.cdn.example"))
        assertTrue(summary.hosts.contains("my.cdn.example"))
        // Global 档不列举 —— 那档下所有流量都走代理，列清单没有意义
        assertTrue(proxyRuleSummary(ProxyMode.Global, ForceMode.Auto, null, listOf("x.com")).hosts.isEmpty())
    }
}
