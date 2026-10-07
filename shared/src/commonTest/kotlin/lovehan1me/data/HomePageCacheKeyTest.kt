package lovehan1me.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 缓存键一致性守卫（纯决策，可离线断言）。
 *
 * 钉两条不变式：① 同一输入 → 同一 key（换域名/换用户必须 miss，不串台）；
 * ② 前缀隔离（首页与发现页互不可见）。空输入回退到 `default`/`anon`，
 * 与旧内联逻辑逐字一致。
 */
class HomePageCacheKeyTest {

    @Test
    fun `同输入同key_换域名换用户必变`() {
        val base = cacheKeyFor("home", "https://hanime1.me", "u1")
        assertEquals(base, cacheKeyFor("home", "https://hanime1.me", "u1"), "同一输入必须稳定")

        assertNotEquals(base, cacheKeyFor("home", "https://v2.hanime1.me", "u1"), "换域名必须 miss")
        assertNotEquals(base, cacheKeyFor("home", "https://hanime1.me", "u2"), "换用户必须 miss")
    }

    @Test
    fun `前缀隔离_路径截断_空值回退`() {
        val home = cacheKeyFor("home", "https://hanime1.me", "u1")
        val discover = cacheKeyFor("discover", "https://hanime1.me", "u1")
        assertNotEquals(home, discover, "首页与发现页 key 必须隔离")
        assertTrue(home.startsWith("home_") && discover.startsWith("discover_"))

        // 带路径的域名只取 host。
        assertEquals(home, cacheKeyFor("home", "https://hanime1.me/some/path", "u1"))

        // 空输入回退（与旧逻辑一致：SettingsRepository 未初始化时的退化值）。
        assertEquals("home_default_anon", cacheKeyFor("home", "", ""))
    }
}
