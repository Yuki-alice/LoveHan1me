package lovehan1me.data.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P0-3：CF 等待唤醒的域匹配回归。
 *
 * 与 `AppSettings.cfCookieKeyFor` 同一规则：精确命中 + 子域后缀命中父域，
 * 单段父域（TLD）永不命中。否则"cookie 能用但没人叫醒"或"一条 me 叫醒整棵树"。
 */
class CloudflareOutcomeMatchTest {

    @Test
    fun `精确命中`() {
        assertTrue(CloudflareChallenges.outcomeMatchesHost("hanime1.me", "hanime1.me"))
    }

    @Test
    fun `子域以后缀命中父域`() {
        assertTrue(CloudflareChallenges.outcomeMatchesHost("www.hanime1.me", "hanime1.me"))
    }

    @Test
    fun `大小写不敏感`() {
        assertTrue(CloudflareChallenges.outcomeMatchesHost("WWW.Hanime1.ME", "hanime1.me"))
    }

    @Test
    fun `单段TLD永不命中`() {
        assertFalse(CloudflareChallenges.outcomeMatchesHost("a.me", "me"))
        assertFalse(CloudflareChallenges.outcomeMatchesHost("hanime1.me", "me"))
    }

    @Test
    fun `不同站不串`() {
        assertFalse(CloudflareChallenges.outcomeMatchesHost("hanime1.me", "hanimeone.me"))
        assertFalse(CloudflareChallenges.outcomeMatchesHost("evilhanime1.me", "hanime1.me"))
    }
}
