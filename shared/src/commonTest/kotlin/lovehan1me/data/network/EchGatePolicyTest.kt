package lovehan1me.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ECH 改写策略回归（纯逻辑，离线可跑）。
 *
 * 三端（OkHttp 拦截器 / Ktor 插件 / mpv·Exo·AVPlayer 改写）共用同一判定，
 * 这里钉住：https 改写、非 https 放行、字面量与回环放行、网关未运行全放行、
 * path/query 原样保留、非法 URL 不抛。
 */
class EchGatePolicyTest {

    @Test
    fun `https 站点改写到本地网关并携带原域名`() {
        val rewrite = EchGatePolicy.rewrite("https://hanime1.me/search?q=abc", 8080)
        assertNotNull(rewrite)
        assertEquals("http://127.0.0.1:8080/search?q=abc", rewrite.url)
        assertEquals("hanime1.me", rewrite.targetHost)
    }

    @Test
    fun `根路径`() {
        val rewrite = EchGatePolicy.rewrite("https://hanime1.me", 8080)
        assertNotNull(rewrite)
        // Ktor URLBuilder 对空 path 不补斜杠（与带斜杠语义等价，网关只看 path）。
        assertEquals("http://127.0.0.1:8080", rewrite.url)
    }

    @Test
    fun `网关未运行全放行`() {
        assertNull(EchGatePolicy.rewrite("https://hanime1.me/", -1))
        assertNull(EchGatePolicy.rewrite("https://hanime1.me/", 0))
    }

    @Test
    fun `http 不进网关`() {
        assertNull(EchGatePolicy.rewrite("http://hanime1.me/", 8080))
    }

    @Test
    fun `回环与字面量放行`() {
        assertNull(EchGatePolicy.rewrite("http://127.0.0.1:8080/x", 8080))
        assertNull(EchGatePolicy.rewrite("https://127.0.0.1/x", 8080))
        assertNull(EchGatePolicy.rewrite("https://localhost/x", 8080))
        assertNull(EchGatePolicy.rewrite("https://192.168.1.10/x", 8080))
        assertNull(EchGatePolicy.rewrite("https://10.0.0.5:8443/x", 8080))
    }

    @Test
    fun `视频 CDN 域名同样改写_由网关按 CNAME 策略出站`() {
        val rewrite = EchGatePolicy.rewrite("https://vdownload.hembed.com/f/abc.mp4", 8080)
        assertNotNull(rewrite)
        assertTrue(rewrite.url.startsWith("http://127.0.0.1:8080/"))
        assertEquals("vdownload.hembed.com", rewrite.targetHost)
    }

    @Test
    fun `非法 URL 放行不抛`() {
        assertNull(EchGatePolicy.rewrite("::::", 8080))
        assertNull(EchGatePolicy.rewrite("", 8080))
    }

    @Test
    fun `bypass 判定`() {
        assertTrue(EchGatePolicy.isBypassHost(""))
        assertTrue(EchGatePolicy.isBypassHost("127.0.0.1"))
        assertTrue(EchGatePolicy.isBypassHost("localhost"))
        assertTrue(EchGatePolicy.isBypassHost("::1"))
        assertTrue(EchGatePolicy.isBypassHost("192.168.0.1"))
        assertEquals(false, EchGatePolicy.isBypassHost("hanime1.me"))
        assertEquals(false, EchGatePolicy.isBypassHost("www.hanime1.me"))
    }
}
