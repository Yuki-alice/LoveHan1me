package lovehan1me.app.web

import java.io.File
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

    // ── clearance 判定：本轮由"cf_ 前缀"收紧为"精确 cf_clearance" ──────

    @Test
    fun `精确命中 cf_clearance`() {
        val found = CloudflareCdp.findClearanceCookie(
            listOf(CloudflareCdp.CdpCookie("cf_clearance", "v", ".hanime1.me")),
            "hanime1.me",
        )
        assertEquals("cf_clearance", found?.name)
    }

    @Test
    fun `挑战进行中的 cf_ 系 cookie 不算通过`() {
        // cf_bm / cf_chl_* 会在挑战**尚未通过**时就下发。旧实现按 cf_ 前缀判定，
        // 于是"假通过"→ 写回一个没用的 cookie → 重试仍 403 → 反复弹窗。
        assertNull(
            CloudflareCdp.findClearanceCookie(
                listOf(
                    CloudflareCdp.CdpCookie("cf_bm", "x", ".hanime1.me"),
                    CloudflareCdp.CdpCookie("cf_chl_2", "y", ".hanime1.me"),
                ),
                "hanime1.me",
            )
        )
    }

    @Test
    fun `域不匹配的 clearance 不采用`() {
        // cf_clearance 是 hostOnly 语义，兄弟站的 clearance 对本站无效
        assertNull(
            CloudflareCdp.findClearanceCookie(
                listOf(CloudflareCdp.CdpCookie("cf_clearance", "v", ".javchu.com")),
                "hanime1.me",
            )
        )
    }

    @Test
    fun `父域与子域的域匹配`() {
        // .hanime1.me 的 clearance 对 www.hanime1.me 有效（RFC 6265）
        assertEquals(
            "cf_clearance",
            CloudflareCdp.findClearanceCookie(
                listOf(CloudflareCdp.CdpCookie("cf_clearance", "v", ".hanime1.me")),
                "www.hanime1.me",
            )?.name,
        )
        // 空域一律拒绝：宁可继续等，也不写一个来路不明的 cookie
        assertNull(
            CloudflareCdp.findClearanceCookie(
                listOf(CloudflareCdp.CdpCookie("cf_clearance", "v", "")),
                "hanime1.me",
            )
        )
    }

    @Test
    fun `值为空或含分隔符控制字符一律拒绝`() {
        val host = "hanime1.me"
        fun cookie(value: String) = listOf(CloudflareCdp.CdpCookie("cf_clearance", value, ".hanime1.me"))
        assertNull(CloudflareCdp.findClearanceCookie(cookie(""), host))
        // 它要被拼进 Cookie 请求头，含 ; 会破坏整个请求
        assertNull(CloudflareCdp.findClearanceCookie(cookie("a;b"), host))
        assertNull(CloudflareCdp.findClearanceCookie(cookie("a\nb"), host))
    }

    // ── 启动参数：可见窗口 + UA 一致（两条都是被实测推翻/定位过的设计）──

    @Test
    fun `启动参数不得含 headless 与自动化 flags`() {
        val args = CloudflareCdp.buildArgs("chrome.exe", 9333, File("/tmp/cf"), proxyArg = null)
        // 实测：无头 47 秒仍拿不到 cf_clearance，可见窗口能拿到（见类 KDoc）
        assertFalse(args.any { it.startsWith("--headless") }, "无头过不了 CF，别再传回去")
        assertFalse(args.contains("--no-sandbox"))
        assertFalse(args.contains("--disable-gpu"))
        assertFalse(args.contains("--enable-unsafe-swiftshader"))
        assertFalse(args.contains("--disable-dev-shm-usage"))
        // 可见窗口
        assertTrue(args.contains("--new-window"))
    }

    @Test
    fun `启动参数不得覆盖浏览器 UA`() {
        val args = CloudflareCdp.buildArgs("chrome.exe", 9333, File("/tmp/cf"), proxyArg = null)
        // 实测（同 profile、同代理、同一 Chrome 153）：把浏览器强制成常量里的 Chrome/149
        // → 60 秒仍卡在"请稍候…"；不覆盖 UA → 10 秒内直接进入真实站点。
        // 伪造 UA 与 Sec-CH-UA 客户端提示不一致，本身就是最明显的机器人特征。
        // UA 一致要靠"读真实 UA、让 HTTP 层跟随"（currentHttpUserAgent），不是靠伪造。
        assertFalse(
            args.any { it.startsWith("--user-agent") },
            "不要给浏览器塞 --user-agent：CF 会一直挑战",
        )
    }

    @Test
    fun `能从 Runtime_evaluate 回包取出真实 UA`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/153.0.0.0 Safari/537.36"
        val frame = """{"id":9,"result":{"result":{"type":"string","value":"$ua"}}}"""
        assertEquals(ua, CloudflareCdp.extractUserAgentFrame(frame, 9))
        // id 对不上的旧回包 / 事件帧 / 坏帧 / 非字符串结果一律 null
        // （调用方据此沿用原 UA，不阻断求解）
        assertNull(CloudflareCdp.extractUserAgentFrame(frame, 8))
        assertNull(
            CloudflareCdp.extractUserAgentFrame(
                """{"method":"Runtime.executionContextCreated","params":{}}""",
                9,
            ),
        )
        assertNull(CloudflareCdp.extractUserAgentFrame("not json", 9))
        assertNull(
            CloudflareCdp.extractUserAgentFrame("""{"id":9,"result":{"result":{"type":"undefined"}}}""", 9),
        )
    }

    // ── "挑战是不是真的过了"（本轮新增：只看 cookie 会抢早收割）──────

    @Test
    fun `挑战页标题一律判为未通过`() {
        assertTrue(CloudflareCdp.isChallengeTitle("Just a moment..."))
        assertTrue(CloudflareCdp.isChallengeTitle("请稍候…"))
        assertTrue(CloudflareCdp.isChallengeTitle("Attention Required! | Cloudflare"))
        // 空标题保守视为"还在挑战"：宁可多等一会，也不要抢早收割一个无效 clearance
        // （实测：CF 在挑战**中途**就下发 cf_clearance，抢早拿到的那枚拿去请求仍然 403）
        assertTrue(CloudflareCdp.isChallengeTitle(null))
        assertTrue(CloudflareCdp.isChallengeTitle("   "))
    }

    @Test
    fun `站点真实标题判为已通过`() {
        assertFalse(CloudflareCdp.isChallengeTitle("Hanime1.me - H動漫/裏番/線上看"))
        assertFalse(CloudflareCdp.isChallengeTitle("hanime1.me"))
    }

    @Test
    fun `代理参数按配置透传`() {
        val arg = "--proxy-server=http://127.0.0.1:7897"
        val withProxy = CloudflareCdp.buildArgs("chrome.exe", 9333, File("/tmp/cf"), proxyArg = arg)
        assertTrue(withProxy.contains(arg))
        // Direct/System 不传参（Chrome 默认走系统代理）
        val direct = CloudflareCdp.buildArgs("chrome.exe", 9333, File("/tmp/cf"), proxyArg = null)
        assertFalse(direct.any { it.startsWith("--proxy-server") })
    }

    @Test
    fun `user-data-dir 指向传入的 profile`() {
        val dir = File(System.getProperty("user.home"), ".lovehan1me/cf-browser-profile")
        val args = CloudflareCdp.buildArgs("chrome.exe", 9333, dir, proxyArg = null)
        assertTrue(args.contains("--user-data-dir=${dir.absolutePath}"))
    }

    @Test
    fun `json-new 回包取调试地址`() {
        val body = """{"description":"","devtoolsFrontendUrl":"/devtools/inspector.html","id":"X","title":"","type":"page","url":"about:blank","webSocketDebuggerUrl":"ws://127.0.0.1:9/ABC"}"""
        assertEquals("ws://127.0.0.1:9/ABC", CloudflareCdp.extractTargetUrl(body))
        assertNull(CloudflareCdp.extractTargetUrl("{}"))
    }
}
