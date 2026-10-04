package lovehan1me.data.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证通过判定的唯一口径回归（纯函数，零 flake）。
 *
 * 钉住"精确 `cf_clearance`"：`cf_bm` 之类同前缀饼干不算通过，
 * 否则 iOS 验证窗会提前关闭、请求带着错钥匙再撞一次 403。
 */
class CloudflareChallengesTest {

    @Test
    fun `精确clearance才算通过`() {
        assertTrue(CloudflareChallenges.hasClearance(setOf("cf_clearance", "user_lang")))
    }

    @Test
    fun `同前缀的cf_bm不算通过`() {
        assertFalse(CloudflareChallenges.hasClearance(setOf("cf_bm", "user_lang")))
    }

    @Test
    fun `空集合不算通过`() {
        assertFalse(CloudflareChallenges.hasClearance(emptySet()))
    }
}
