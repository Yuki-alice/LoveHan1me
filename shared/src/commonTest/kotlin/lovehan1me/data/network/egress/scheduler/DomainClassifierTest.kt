package lovehan1me.data.network.egress.scheduler

import lovehan1me.data.network.egress.DomainClass
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.classifyDomain
import lovehan1me.data.network.egress.isRestrictedHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 域分类器回归（纯函数，不碰全局单例、不联网，故零 flake）。
 *
 * 这是"按域独立"的地基：分错域 = 健康记错账、直连该跳没跳。
 */
class DomainClassifierTest {

    @Test
    fun `hanime四站全归Hanime`() {
        listOf("hanime1.me", "hanime1.com", "hanimeone.me", "javchu.com").forEach { host ->
            assertEquals(
                DomainClass.Hanime,
                classifyDomain("https://$host/search?q=abc", EgressPurpose.Api),
                host,
            )
        }
    }

    @Test
    fun `getchu归Getchu`() {
        assertEquals(
            DomainClass.Getchu,
            classifyDomain("https://www.getchu.com/soft.phtml?id=123", EgressPurpose.Api),
        )
    }

    @Test
    fun `媒体用途下未知host是图床`() {
        listOf(EgressPurpose.Image, EgressPurpose.Video, EgressPurpose.Download).forEach { purpose ->
            assertEquals(
                DomainClass.CdnMedia,
                classifyDomain("https://vdownload.hembed.com/x.mp4", purpose),
                "$purpose",
            )
        }
    }

    @Test
    fun `Api与Probe用途下未知host是第三方`() {
        listOf(EgressPurpose.Api, EgressPurpose.Probe).forEach { purpose ->
            assertEquals(
                DomainClass.ThirdParty,
                classifyDomain("https://api.dandanplay.net/api/v2/search/anime", purpose),
                "$purpose",
            )
        }
    }

    @Test
    fun `分类只看host不看scheme`() {
        assertEquals(
            DomainClass.Hanime,
            classifyDomain("http://hanime1.me/", EgressPurpose.Api),
            "http 该不该进网关是 EchGatePolicy 的事，分类器只认域",
        )
    }

    @Test
    fun `大小写与空串`() {
        assertEquals(
            DomainClass.Hanime,
            classifyDomain("https://HANIME1.ME/", EgressPurpose.Api),
        )
        assertEquals(DomainClass.ThirdParty, classifyDomain("", EgressPurpose.Api))
        assertEquals(DomainClass.ThirdParty, classifyDomain(":::", EgressPurpose.Api))
    }

    @Test
    fun `仅凭host判定受限域`() {
        listOf("hanime1.me", "hanime1.com", "hanimeone.me", "javchu.com", "www.getchu.com").forEach { host ->
            assertTrue(isRestrictedHost(host), host)
        }
        assertTrue(isRestrictedHost("HANIME1.ME"), "大小写不敏感")
        // 第三方（含未知 host）：一律不受限 → Rules 模式直连
        assertFalse(isRestrictedHost("api.dandanplay.net"))
        assertFalse(isRestrictedHost("vdownload.hembed.com"), "图床/媒体系不在已知表内，按第三方直连")
        assertFalse(isRestrictedHost(""))
    }
}
