package lovehan1me.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * P0-4：CSRF 按站隔离回归。
 *
 * 钉住：切站 stash/restore 来回不丢；本站命中优先、未知站回退全局；
 * host 归一化（scheme/路径/端口/大小写）一致。
 */
class CsrfTokenProviderTest {

    @Test
    fun `切站来回_token各归各站`() {
        CsrfTokenProvider.csrfToken = null
        CsrfTokenProvider.clearFor("https://hanime1.me/")
        CsrfTokenProvider.clearFor("https://javchu.com/")

        CsrfTokenProvider.setTokenFor("https://hanime1.me/", "A")
        assertEquals("A", CsrfTokenProvider.csrfToken)

        // 切到 B 站：手头 A 存槽，B 无槽 → null 等页面重抓。
        CsrfTokenProvider.stashForSwitch("https://hanime1.me/", "https://javchu.com/")
        assertNull(CsrfTokenProvider.csrfToken)

        CsrfTokenProvider.setTokenFor("https://javchu.com/", "B")
        // 切回 A 站：B 存槽，A 装回。
        CsrfTokenProvider.stashForSwitch("https://javchu.com/", "https://hanime1.me/")
        assertEquals("A", CsrfTokenProvider.csrfToken)

        // 清理，避免污染同 JVM 后续用例。
        CsrfTokenProvider.clearFor("https://hanime1.me/")
        CsrfTokenProvider.clearFor("https://javchu.com/")
        CsrfTokenProvider.csrfToken = null
    }

    @Test
    fun `tokenFor_本站优先_未知回退全局`() {
        CsrfTokenProvider.csrfToken = null
        CsrfTokenProvider.clearFor("hanime1.me")
        CsrfTokenProvider.setTokenFor("hanime1.me", "SITE")
        CsrfTokenProvider.csrfToken = "GLOBAL"

        // 本站槽命中（即使全局已被覆盖为 GLOBAL，槽里仍是 SITE）。
        assertEquals("SITE", CsrfTokenProvider.tokenFor("https://hanime1.me/watch?v=1"))
        // 未知站回退全局。
        assertEquals("GLOBAL", CsrfTokenProvider.tokenFor("https://unknown.example/"))

        CsrfTokenProvider.clearFor("hanime1.me")
        CsrfTokenProvider.csrfToken = null
    }

    @Test
    fun `host归一化_端口路径大小写一致`() {
        // 端口/路径/大小写剥离，但子域保留（www 与裸域是不同 host，不混）。
        assertEquals(
            CsrfTokenProvider.normalizeHost("https://WWW.Hanime1.ME:443/watch?v=1"),
            CsrfTokenProvider.normalizeHost("http://www.hanime1.me/other"),
        )
        assertEquals(
            CsrfTokenProvider.normalizeHost("https://hanime1.me/"),
            CsrfTokenProvider.normalizeHost("hanime1.me"),
        )
    }
}
