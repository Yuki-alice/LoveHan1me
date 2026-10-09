package lovehan1me.data.network

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * HCookieJar 存储有界性回归（2026-10-09 全站子评论 400 的根因）。
 *
 * 事故：`saveFromResponse` 对 DataStore 的 loginCookie 做无条件 append，
 * 去重只管本响应自带的同键 —— 每个响应攒一份完整登录 Cookie，
 * 发出头线性增长直到 nginx 报 "Request Header Or Cookie Too Large"。
 * 全无网络，纯内存断言：有界性是数据结构性质，不需要真实服务端。
 */
class HCookieJarBoundsTest {

    private val url = "https://hanime1.me/loadReplies?id=1".toHttpUrl()
    private val jar = HCookieJar()

    init {
        // load/save 两条路都读 SettingsRepository（videoLanguage/loginCookie/cf），
        // 不装 store 就是初始化噪声（同 CommentParserTest 的做法）。
        SettingsRepository.install(BoundTestStore())
    }

    @BeforeTest
    fun clearJar() {
        HCookieJar.cookieMap.clear()
    }

    @AfterTest
    fun clearJarAfter() {
        HCookieJar.cookieMap.clear()
    }

    @Test
    fun `空响应反复存_内存表不增长`() {
        repeat(5) { jar.saveFromResponse(url, emptyList()) }
        val names = HCookieJar.cookieMap[url.host].orEmpty().map { it.name }
        // loginCookie 两键 + 恒插的 user_lang：5 次存后仍是 3 条，不是 15 条。
        assertEquals(3, names.size, "内存表在增长：$names")
        assertEquals(names.size, names.toSet().size, "内存表内有重名：$names")
    }

    @Test
    fun `发出头同键只发一份`() {
        repeat(3) { jar.saveFromResponse(url, emptyList()) }
        val sent = jar.loadForRequest(url)
        val keys = sent.map { Triple(it.name, it.domain, it.path) }
        assertEquals(keys.size, keys.toSet().size, "发出头有重复键")
    }

    @Test
    fun `响应带来的新值优先于DataStore旧值`() {
        jar.saveFromResponse(url, emptyList())
        val fresh = Cookie.Builder().domain(url.host).path("/")
            .name("hanime1_session").value("NEW").build()
        jar.saveFromResponse(url, listOf(fresh))
        val sent = jar.loadForRequest(url)
        assertEquals(
            "NEW",
            sent.first { it.name == "hanime1_session" }.value,
            "服务端旋转后的值必须优先，否则等于拿旧 session 撞登录态",
        )
    }

    @Test
    fun `过期持久cookie不进表`() {
        val dead = Cookie.Builder().domain(url.host).path("/")
            .name("stale_pref").value("x")
            .expiresAt(System.currentTimeMillis() - 1_000L)
            .build()
        assertTrue(dead.persistent)
        jar.saveFromResponse(url, listOf(dead))
        val names = HCookieJar.cookieMap[url.host].orEmpty().map { it.name }
        assertTrue("stale_pref" !in names, "过期 cookie 不该占名额：$names")
    }

    // 与 CommentParserTest 内的同名类区分：嵌套 + 后缀。
    private class BoundTestStore : SettingsStore {
        private val state = MutableStateFlow(
            AppSettings(loginCookie = "hanime1_session=AAA; XSRF-TOKEN=BBB"),
        )
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }
}
