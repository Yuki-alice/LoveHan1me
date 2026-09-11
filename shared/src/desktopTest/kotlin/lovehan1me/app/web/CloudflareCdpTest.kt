package lovehan1me.app.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 阶段一⑩ CDP 验证器的纯逻辑单测（不碰真实浏览器/网络）。
 *
 * 真浏览器端到端（Edge headless 起 CDP）需人工跑，见 `CdpEdgeLiveTest` 注释
 * （scratch，不进仓库）。
 */
class CloudflareCdpTest {

    // ── 浏览器探活 ────────────────────────────────────

    @Test
    fun `mac 按固定路径命中 Chrome`() {
        val found = CloudflareCdp.findBrowser(
            os = "Mac OS X",
            pathExists = { it == "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" },
            which = { null },
            env = { null },
        )
        assertEquals("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", found)
    }

    @Test
    fun `Windows 先走 where 再走固定路径`() {
        val found = CloudflareCdp.findBrowser(
            os = "Windows 11",
            pathExists = { it == "C:\\Edge\\msedge.exe" },
            which = { name -> if (name == "msedge.exe") "C:\\Edge\\msedge.exe" else null },
            env = { null },
        )
        assertEquals("C:\\Edge\\msedge.exe", found)
    }

    @Test
    fun `Linux 走 which`() {
        val found = CloudflareCdp.findBrowser(
            os = "Linux",
            pathExists = { it == "/usr/bin/chromium" },
            which = { name -> if (name == "chromium") "/usr/bin/chromium" else null },
            env = { null },
        )
        assertEquals("/usr/bin/chromium", found)
    }

    @Test
    fun `什么都没装返回 null（窗口直接切手动）`() {
        val found = CloudflareCdp.findBrowser(
            os = "Mac OS X",
            pathExists = { false },
            which = { null },
            env = { null },
        )
        assertNull(found)
    }

    // ── CDP 帧解析 ────────────────────────────────────

    @Test
    fun `getAllCookies 回包按 id 命中`() {
        val frame = """{"id":7,"result":{"cookies":[{"name":"cf_clearance","value":"abc","domain":"x.me"},{"name":"uid","value":"1","domain":"x.me"}]}}"""
        val cookies = CloudflareCdp.extractCookiesFrame(frame, 7)
        assertEquals(2, cookies?.size)
        assertEquals("cf_clearance", cookies?.first()?.name)
    }

    @Test
    fun `事件帧与旧回包被丢弃`() {
        // 无 id 的事件帧
        assertNull(CloudflareCdp.extractCookiesFrame("""{"method":"Page.loadEventFired","params":{}}""", 7))
        // id 对不上的旧回包
        assertNull(CloudflareCdp.extractCookiesFrame("""{"id":6,"result":{"cookies":[]}}""", 7))
        // 坏帧不抛
        assertNull(CloudflareCdp.extractCookiesFrame("not json", 7))
    }

    @Test
    fun `cf_ 判定（与旧实现同语义）`() {
        assertTrue(
            CloudflareCdp.hasClearanceCookies(
                listOf(CloudflareCdp.CdpCookie("cf_clearance", "v", "x.me"))
            )
        )
        assertFalse(
            CloudflareCdp.hasClearanceCookies(
                listOf(CloudflareCdp.CdpCookie("uid", "1", "x.me"))
            )
        )
        assertFalse(CloudflareCdp.hasClearanceCookies(emptyList()))
    }

    @Test
    fun `json-new 回包取调试地址`() {
        val body = """{"description":"","devtoolsFrontendUrl":"/devtools/inspector.html","id":"X","title":"","type":"page","url":"about:blank","webSocketDebuggerUrl":"ws://127.0.0.1:9/ABC"}"""
        assertEquals("ws://127.0.0.1:9/ABC", CloudflareCdp.extractTargetUrl(body))
        assertNull(CloudflareCdp.extractTargetUrl("{}"))
    }
}
