package lovehan1me.data.network

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.domain.model.cfCookieFor
import lovehan1me.core.domain.model.cfCookieKeyFor
import lovehan1me.data.NetworkRepo
import lovehan1me.data.SettingsRepository
import lovehan1me.data.datastore.DataStoreManager
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CF clearance 一致性闭环的回归（离线，可跑）。
 *
 * 覆盖的是"浏览器验证成功了、应用还是 403"这条链上的四个断点，每个都对应用户实际看到的现象：
 * 1. **按域取用**（[cfCookieFor]）：四个可切域名 + 用户镜像，单行存储会互相顶掉；
 * 2. **作废删对键**（[SettingsRepository.clearCloudFlareCookie]）：403 时把命中的那条（可能是父域）丢掉；
 * 3. **写入即广播**（[SettingsRepository.setCloudFlareCookie] → [CloudflareChallenges.awaitPassed]）：
 *    验证成功后原来失败的请求自己续跑，而不是等用户退回再进；
 * 4. **请求侧只认持久化那份**（[HCookieJar.loadForRequest]）：内存里再留一把死钥匙就会绕过失效逻辑。
 *
 * `SettingsRepository` 是 install-once 的单例，同一 JVM 里多个测试类共用那份内存 store，
 * 所以每条用例都用自己的域名（`*.test`）取隔离，断言也只查"有没有那枚"而不查整表。
 */
private class InMemorySettingsStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private fun installStore() {
    runCatching { SettingsRepository.install(InMemorySettingsStore()) }
}

class CfClearanceLookupTest {

    private val store = mapOf(
        "hanime1.me" to "cf_clearance=parent",
        "www.hanime1.me" to "cf_clearance=exact",
        "hanimeone.me" to "cf_clearance=other-zone",
    )

    @Test
    fun `精确域优先于父域`() {
        assertEquals("cf_clearance=exact", store.cfCookieFor("www.hanime1.me"))
    }

    @Test
    fun `子域没有记录时回落父域`() {
        assertEquals("cf_clearance=parent", mapOf("hanime1.me" to "cf_clearance=parent").cfCookieFor("video.hanime1.me"))
    }

    @Test
    fun `跨zone不串用`() {
        assertNull(store.cfCookieFor("hanime4.me"))
    }

    @Test
    fun `兄弟zone拿不到主站的凭据`() {
        assertEquals("cf_clearance=other-zone", store.cfCookieFor("www.hanimeone.me"))
        assertEquals("cf_clearance=exact", store.cfCookieFor("www.hanime1.me"))
    }

    @Test
    fun `空值记录等于没有记录`() {
        assertNull(mapOf("hanime1.me" to "").cfCookieFor("hanime1.me"))
        assertNull(mapOf("hanime1.me" to "").cfCookieFor("www.hanime1.me"))
    }

    @Test
    fun `不沿用到TLD一档`() {
        // 键为 "me" 的记录一旦生效，任何 .me 站都能互相冒充 clearance。
        assertNull(mapOf("me" to "tld-wide").cfCookieFor("hanime1.me"))
        // 单段 host（本地镜像）只走精确匹配。
        assertEquals("x", mapOf("localhost" to "x").cfCookieFor("localhost"))
    }

    @Test
    fun `请求域大小写不敏感`() {
        // 键统一小写是写侧不变量（见 CfClearanceStoreTest 与迁移用例），
        // 读侧就不再逐请求复制一张小写表。
        val mixed = mapOf("www.hanime1.me" to "cf_clearance=up")
        assertEquals("cf_clearance=up", mixed.cfCookieFor("WWW.HANIME1.ME"))
        assertEquals("www.hanime1.me", mixed.cfCookieKeyFor("WWW.Hanime1.ME"))
    }

    @Test
    fun `命中的是键而不是值_作废时才删得对`() {
        assertEquals("hanime1.me", store.cfCookieKeyFor("video.hanime1.me"))
        assertEquals("www.hanime1.me", store.cfCookieKeyFor("www.hanime1.me"))
        assertNull(store.cfCookieKeyFor("hanime4.me"))
    }
}

class CfClearanceStoreTest {

    @Test
    fun `一个域的验证结果不会顶掉另一个域`() = runBlocking {
        installStore()
        SettingsRepository.setCloudFlareCookie("a1.test", "cf_clearance=keep-me")
        SettingsRepository.setCloudFlareCookie("a2.test", "cf_clearance=new-one")

        assertEquals("cf_clearance=keep-me", SettingsRepository.cfCookieFor("a1.test"))
        assertEquals("cf_clearance=new-one", SettingsRepository.cfCookieFor("a2.test"))
    }

    @Test
    fun `写入会把域键小写化`() = runBlocking {
        installStore()
        SettingsRepository.setCloudFlareCookie("B2.Test", "cf_clearance=x")
        assertEquals("cf_clearance=x", SettingsRepository.cfCookieFor("b2.test"))
    }

    @Test
    fun `作废删掉的是实际命中的父域记录`() = runBlocking {
        installStore()
        SettingsRepository.setCloudFlareCookie("c1.test", "cf_clearance=parent")
        // 请求打在 www.c1.test，用的是父域那条；403 作废时必须把它丢掉，
        // 否则"清了但没清"，下一次继续拿死钥匙裸奔。
        SettingsRepository.clearCloudFlareCookie("www.c1.test")
        assertNull(SettingsRepository.cfCookieFor("www.c1.test"))
        assertNull(SettingsRepository.cfCookieFor("c1.test"))
    }

    @Test
    fun `作废不误伤别的域`() = runBlocking {
        installStore()
        SettingsRepository.setCloudFlareCookie("d1.test", "cf_clearance=keep")
        SettingsRepository.setCloudFlareCookie("d2.test", "cf_clearance=dead")
        SettingsRepository.clearCloudFlareCookie("d2.test")
        assertEquals("cf_clearance=keep", SettingsRepository.cfCookieFor("d1.test"))
    }

    @Test
    fun `退出登录清空全部域`() = runBlocking {
        installStore()
        SettingsRepository.setCloudFlareCookie("e1.test", "cf_clearance=1")
        SettingsRepository.setCloudFlareCookie("e2.test", "cf_clearance=2")
        SettingsRepository.clearAllCloudFlareCookies()
        assertNull(SettingsRepository.cfCookieFor("e1.test"))
        assertNull(SettingsRepository.cfCookieFor("e2.test"))
    }

    @Test
    fun `诊断指纹不含cookie值`() = runBlocking {
        installStore()
        val sentinel = "cf_clearance=SECRET-VALUE-f1.test"
        SettingsRepository.setCloudFlareCookie("f1.test", sentinel)

        val withClearance = NetworkRepo.cfFailureFingerprint("https://f1.test/search?k=1", 403)
        assertTrue(withClearance.contains("host=f1.test"), withClearance)
        assertTrue(withClearance.contains("hasClearance=true"), withClearance)
        assertFalse(withClearance.contains("SECRET-VALUE"), withClearance)

        SettingsRepository.clearCloudFlareCookie("f1.test")
        assertTrue(
            NetworkRepo.cfFailureFingerprint("https://f1.test/", 403).contains("hasClearance=false"),
            "作废后指纹要如实报缺凭据",
        )
    }
}

class CfPassSignalTest {

    @Test
    fun `写入clearance会叫醒等这个域的请求`() = runBlocking {
        installStore()
        val waiting = async { CloudflareChallenges.awaitPassed("www.g1.test", 5_000) }
        // 通过信号不留档：不先让等待方订阅上，这一发就直接错过。
        yield()
        SettingsRepository.setCloudFlareCookie("g1.test", "cf_clearance=ok")
        assertTrue(waiting.await(), "父域验证通过也要能叫醒子域上挂着的请求")
    }

    @Test
    fun `别的域通过不算数`() = runBlocking {
        installStore()
        val waiting = async { CloudflareChallenges.awaitPassed("h1.test", 300) }
        yield()
        SettingsRepository.setCloudFlareCookie("h2.test", "cf_clearance=other")
        assertFalse(waiting.await(), "跨 zone 的通过信号不能让请求拿错钥匙重试")
    }

    @Test
    fun `没人验证就一直等到超时`() = runBlocking {
        installStore()
        assertFalse(CloudflareChallenges.awaitPassed("i1.test", 120))
    }

    @Test
    fun `用户放弃验证会立刻叫醒等待中的请求`() = runBlocking {
        installStore()
        val waiting = async { CloudflareChallenges.awaitPassed("j1.test", 5_000) }
        yield()
        CloudflareChallenges.abandoned("j1.test")
        assertFalse(waiting.await(), "验证页都关了还挂着转圈，用户只会以为应用卡死了")
    }

    @Test
    fun `hostOf只取主机名并去端口和小写`() {
        assertEquals("www.hanime1.me", CloudflareChallenges.hostOf("https://www.hanime1.me:443/video/1?a=2"))
        assertEquals("hanime1.me", CloudflareChallenges.hostOf("https://hanime1.me/"))
        assertEquals("hanime1.me", CloudflareChallenges.hostOf("https://HANIME1.ME/search"))
        assertTrue(CloudflareChallenges.hostOf("").isBlank(), "解不出域时交给调用方跳过，别拿整串 URL 当键")
    }
}

class CfClearanceMigrationTest {

    @Test
    fun `老单行clearance升到按域表`() {
        val migrated = DataStoreManager.decodeCfCookies(null, "cf_clearance=legacy", "JAVCHU.TEST")
        assertEquals(mapOf("javchu.test" to "cf_clearance=legacy"), migrated)
    }

    @Test
    fun `新表已存在时不再回捞旧键`() {
        // 用户清掉验证后旧键若还能被捞回来，等于复活一把已经作废的钥匙。
        assertEquals(emptyMap(), DataStoreManager.decodeCfCookies("{}", "cf_clearance=legacy", "javchu.test"))
    }

    @Test
    fun `旧键缺host时不迁移`() {
        assertEquals(emptyMap(), DataStoreManager.decodeCfCookies(null, "cf_clearance=legacy", null))
        assertEquals(emptyMap(), DataStoreManager.decodeCfCookies(null, "", "javchu.test"))
    }

    @Test
    fun `整表往返并保持小写键`() {
        val raw = DataStoreManager.encodeCfCookies(mapOf("WWW.K1.TEST" to "cf_clearance=round"))
        assertEquals(mapOf("www.k1.test" to "cf_clearance=round"), DataStoreManager.decodeCfCookies(raw, null, null))
    }

    @Test
    fun `表被写坏只当没有凭据_不拖垮设置`() {
        assertEquals(emptyMap(), DataStoreManager.decodeCfCookies("{not-json", "cf_clearance=x", "k2.test"))
    }
}

class ClearanceRequestTest {

    private fun cookie(host: String, name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).domain(host).path("/").build()

    @Test
    fun `请求只带持久化的clearance_内存那份被忽略`() = runBlocking {
        installStore()
        val url = "https://m1.test/search".toHttpUrl()
        SettingsRepository.setCloudFlareCookie("m1.test", "cf_clearance=from-store")
        HCookieJar.cookieMap[url.host] = mutableListOf(
            cookie(url.host, CF_CLEARANCE_NAME, "from-memory"),
            cookie(url.host, "user_lang", "zh_tw"),
        )

        val sent = HCookieJar().loadForRequest(url).filter { it.name == CF_CLEARANCE_NAME }
        assertEquals(listOf("from-store"), sent.map { it.value })
        assertEquals(1, sent.size, "内存与持久化两份 clearance 同时上请求，等于让死钥匙参与投票")
    }

    @Test
    fun `非clearance的内存cookie照常带上`() = runBlocking {
        installStore()
        val url = "https://m2.test/search".toHttpUrl()
        HCookieJar.cookieMap[url.host] = mutableListOf(cookie(url.host, "user_lang", "zh_tw"))
        assertTrue(
            HCookieJar().loadForRequest(url).any { it.name == "user_lang" },
            "站点自己的会话 cookie 不该被 clearance 那套逻辑顺手清掉",
        )
    }

    @Test
    fun `父域凭据会自动带在子域请求上`() = runBlocking {
        installStore()
        val url = "https://www.m3.test".toHttpUrl()
        SettingsRepository.setCloudFlareCookie("m3.test", "cf_clearance=parent")
        val sent = HCookieJar().loadForRequest(url).filter { it.name == CF_CLEARANCE_NAME }
        assertEquals(listOf("parent"), sent.map { it.value })
    }

    @Test
    fun `回包写cookie是合并而不是覆盖`() = runBlocking {
        installStore()
        val url = "https://m4.test/search".toHttpUrl()
        HCookieJar.cookieMap[url.host] = mutableListOf(cookie(url.host, "keep_me", "1"))
        HCookieJar().saveFromResponse(url, listOf(cookie(url.host, "from_response", "2")))

        val names = HCookieJar.cookieMap[url.host].orEmpty().map { it.name }
        assertTrue("keep_me" in names, "覆盖式写入会把上一轮的会话 cookie 抹掉：$names")
        assertTrue("from_response" in names, names.toString())
    }

    @Test
    fun `回包里的同名cookie以响应值为准`() = runBlocking {
        installStore()
        val url = "https://m5.test/search".toHttpUrl()
        HCookieJar.cookieMap[url.host] = mutableListOf(cookie(url.host, "session", "old"))
        HCookieJar().saveFromResponse(url, listOf(cookie(url.host, "session", "new")))

        val values = HCookieJar.cookieMap[url.host].orEmpty().filter { it.name == "session" }.map { it.value }
        assertEquals(listOf("new"), values, "同名留两份会让请求随机带上新旧会话")
    }
}
