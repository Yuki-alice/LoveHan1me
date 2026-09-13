package lovehan1me.app.web

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `CloudflareCdp` 的**真实网络**验证（默认跳过，手工开启）。
 *
 * ## 为什么必须有它
 * "无头拿不到 clearance、可见窗口能拿到"这条结论、以及"整条链路真的能解出
 * `cf_clearance`"，都只能在**真浏览器 + 真站点 + 真出口**下验证 ——
 * 纯逻辑单测（`CloudflareCdpTest`）证明不了任何一条。
 *
 * ## 默认 skip
 * 不设环境变量时立即返回。原因：它会**在你桌面弹出一个浏览器窗口**（最长 2.5 分钟），
 * 并真的访问目标站点，需要一个可用出口。放进日常 `desktopTest` 会打扰所有人。
 *
 * ## 手工跑法（PowerShell）
 * ```
 * $env:HAN1ME_CF_LIVE_URL='https://hanime1.me/'
 * $env:HAN1ME_CF_LIVE_PROXY='--proxy-server=http://127.0.0.1:7897'
 * ./gradlew :shared:desktopTest --tests '*CloudflareCdpLiveTest*' --rerun-tasks
 * ```
 * `HAN1ME_CF_LIVE_PROXY` 可省略（直连或系统代理场景）；代理写法要与
 * `CloudflareCdp.proxyFlag()` 一致（`--proxy-server=http://host:port` 或 `socks5://`）。
 */
class CloudflareCdpLiveTest {

    @Test
    fun `可见窗口能真的解出 cf_clearance`() {
        val url = System.getenv("HAN1ME_CF_LIVE_URL")?.takeIf { it.isNotBlank() }
        if (url == null) {
            println("[skip] 未设 HAN1ME_CF_LIVE_URL —— 跳过真实网络验证（见类 KDoc 的跑法）")
            return
        }
        val proxy = System.getenv("HAN1ME_CF_LIVE_PROXY")?.takeIf { it.isNotBlank() }
        println("[live] url=$url proxy=$proxy")

        val result = runBlocking {
            CloudflareCdp.solve(
                url = url,
                timeoutMs = 150_000,
                onStage = { println("[stage] $it") },
                proxyArg = proxy,
            )
        }
        println("[live] result=$result")

        val solved = assertIs<CloudflareCdp.SolveResult.Solved>(
            result,
            "未能解出 clearance；窗口会给出失败原因，Timeout 表示 2 分钟内没通过",
        )
        assertEquals("cf_clearance", solved.clearance.name)
        assertTrue(solved.clearance.value.isNotBlank(), "clearance 值不应为空")
        // 真实 UA 采集是"不伪造 UA"方案的另一半：拿不到它，HTTP 层就仍会发旧 UA
        assertNotNull(
            solved.browserUserAgent,
            "未采集到浏览器真实 UA（HTTP 层将沿用常量，clearance 可能失效）",
        )
        assertTrue(solved.browserUserAgent.contains("Mozilla/"), "UA 形态异常")
    }
}
