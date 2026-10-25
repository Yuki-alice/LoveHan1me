package lovehan1me.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * iOS 应用内代理字典（网络优化 1.2）：四模式 + 例外表恒含回环。
 * 纯函数离线断言（活计划 1.2 验收第 1 条）；真机上"Direct 空字典能否真正
 * 关闭系统代理 / SOCKS 兼容性"两项手工验收见活计划（未验）。
 */
class ProxyDictionaryTest {

    @Test
    fun 直连档给空字典() {
        assertEquals(emptyMap(), proxyDictionaryFor(HProxyTypes.TYPE_DIRECT, "", -1))
    }

    @Test
    fun 跟随系统档返回null_不设代理键() {
        assertNull(proxyDictionaryFor(HProxyTypes.TYPE_SYSTEM, "1.2.3.4", 8080))
    }

    @Test
    fun HTTP档含HTTP与HTTPS两组键() {
        val dict = proxyDictionaryFor(HProxyTypes.TYPE_HTTP, "192.168.1.10", 8888)!!
        assertEquals(1, dict["kCFNetworkProxiesHTTPEnable"])
        assertEquals("192.168.1.10", dict["kCFNetworkProxiesHTTPProxy"])
        assertEquals(8888, dict["kCFNetworkProxiesHTTPPort"])
        assertEquals(1, dict["kCFNetworkProxiesHTTPSEnable"])
        assertEquals("192.168.1.10", dict["kCFNetworkProxiesHTTPSProxy"])
        assertEquals(8888, dict["kCFNetworkProxiesHTTPSPort"])
    }

    @Test
    fun SOCKS档含SOCKS组键() {
        val dict = proxyDictionaryFor(HProxyTypes.TYPE_SOCKS, "192.168.1.10", 1080)!!
        assertEquals(1, dict["kCFNetworkProxiesSOCKSEnable"])
        assertEquals("192.168.1.10", dict["kCFNetworkProxiesSOCKSProxy"])
        assertEquals(1080, dict["kCFNetworkProxiesSOCKSPort"])
    }

    @Test
    fun 例外表恒含网关回环() {
        for (type in intArrayOf(HProxyTypes.TYPE_HTTP, HProxyTypes.TYPE_SOCKS)) {
            val dict = proxyDictionaryFor(type, "1.2.3.4", 8080)!!
            val exceptions = dict[KEY_PROXY_EXCEPTIONS_LIST] as? List<*>
            assertTrue(
                exceptions != null && "127.0.0.1" in exceptions && "localhost" in exceptions,
                "type=$type 的例外表必须含回环地址，网关改写流量不得进用户代理",
            )
        }
    }

    @Test
    fun 配置不完整不产出代理键() {
        assertNull(proxyDictionaryFor(HProxyTypes.TYPE_HTTP, "", 8888), "空地址不得注入")
        assertNull(proxyDictionaryFor(HProxyTypes.TYPE_HTTP, "1.2.3.4", 0), "端口越界不得注入")
        assertNull(proxyDictionaryFor(HProxyTypes.TYPE_SOCKS, "1.2.3.4", 70000), "端口越界不得注入")
        assertNull(proxyDictionaryFor(-1, "1.2.3.4", 8080), "未知类型不得注入")
    }
}
