package lovehan1me.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 跨进程口令的回归：这些字符串是与网关进程之间**唯一**的约定，
 * 改动等于改协议，必须让它在测试里显式地过一遍。
 */
class EchGateContractTest {

    @Test
    fun `就绪行按前缀识别`() {
        assertTrue(EchGateContract.isReadyLine("LISTENING 127.0.0.1:34567"))
        assertTrue(EchGateContract.isReadyLine("LISTENING"))
    }

    @Test
    fun `非就绪行不误判`() {
        assertFalse(EchGateContract.isReadyLine("waiting for LISTENING"))
        assertFalse(EchGateContract.isReadyLine(""))
    }

    @Test
    fun `错误页按前缀识别`() {
        assertTrue(EchGateContract.isErrorPage("echgate: upstream error: dial tcp: i/o timeout"))
    }

    @Test
    fun `上游自己回的 502 不是网关错误页`() {
        assertFalse(EchGateContract.isErrorPage("<html><title>502 Bad Gateway</title></html>"))
        assertFalse(EchGateContract.isErrorPage(""))
    }

    @Test
    fun `网关的决策行都判为诊断行`() {
        val lines = listOf(
            "2026/09/28 10:00:00 upstream error: dial tcp 172.64.1.1:443: i/o timeout",
            "plan host=hanime1.me use=cname",
            "CONNECT vdownload.hembed.com:443",
            "拨号失败 172.64.1.1:443",
            "解析无结果: hanime1.me",
            "ECH 试不通，回退 CNAME",
            "被阻断，改用 CNAME",
            "候选 3 个，择优",
        )
        for (line in lines) {
            assertTrue(EchGateContract.isDiagnosticLine(line), "漏掉了决策行：$line")
        }
    }

    @Test
    fun `普通日志行不是诊断行`() {
        assertFalse(EchGateContract.isDiagnosticLine("gate started"))
        assertFalse(EchGateContract.isDiagnosticLine(""))
    }

    @Test
    fun `CLI 参数名与网关侧约定一致`() {
        assertEquals(
            listOf("--listen", "--ip-list", "--cf-hosts", "--cache-dir"),
            listOf(
                EchGateContract.FLAG_LISTEN,
                EchGateContract.FLAG_IP_LIST,
                EchGateContract.FLAG_CF_HOSTS,
                EchGateContract.FLAG_CACHE_DIR,
            ),
            "改这些值等于改跨进程协议：Go 侧 flag 名必须同步改，否则网关会把它们当成位置参数",
        )
    }
}
