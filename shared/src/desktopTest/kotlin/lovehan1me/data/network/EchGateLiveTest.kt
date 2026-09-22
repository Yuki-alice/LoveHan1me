package lovehan1me.data.network

import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.interceptor.EchGateInterceptor
import lovehan1me.data.network.interceptor.UserAgentInterceptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ECH 网关的**端到端**验证（需要真实网络 + `echgate.exe` 产物，属 live 用例）。
 *
 * ## 它证明什么
 * 直连时 TLS 的明文 SNI 会被 DPI 重置，所以：
 * - **不开网关** → 请求必然失败（这是对照，证明后面的成功不是"网络本来就通"）；
 * - **开网关** → 站点拿到 200（ECH），视频 CDN 也能建立连接（CNAME 真名降级）。
 *
 * 对照组不可省：只跑正例，无法排除"本机其实没被阻断"。
 *
 * 网关进程由测试自己拉起（不用 `EchGateProcess`——那要求 exe 在测试 classpath 里，
 * 而它实际躺在 `desktopApp` 的 resources 中）。
 */
class EchGateLiveTest {

    /** exe 可能在两处：桌面应用资源（打包用）或网关源码目录（开发用）。 */
    private fun findExe(): File? = listOf(
        "../desktopApp/src/main/resources/echgate.exe",
        "desktopApp/src/main/resources/echgate.exe",
        "../echgate/echgate.exe",
    ).map(::File).firstOrNull { it.isFile }

    private fun installStore() {
        runCatching {
            SettingsRepository.install(object : SettingsStore {
                private val state = MutableStateFlow(AppSettings())
                override val settings: StateFlow<AppSettings> = state
                override suspend fun update(transform: (AppSettings) -> AppSettings) {
                    state.value = transform(state.value)
                }
            })
        }
    }

    private fun clientWithGate(port: Int): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor)
        .addInterceptor(EchGateInterceptor())
        .build()

    /**
     * 拉起网关并等它就绪，返回进程与端口。调用方负责关掉。
     *
     * `--cf-hosts` 必须带：不带的话，ECH 握手到 CF 边缘时外层 SNI 是
     * `cloudflare-ech.com`、CF 照常接受，于是**非 CF 站点也会被误判成可用**，
     * 结果是视频 CDN 被塞进 CF 通道、慢到不可用（实测踩过）。
     */
    private fun startGate(exe: File): Pair<Process, Int> {
        val port = ServerSocket(0).use { it.localPort }
        // 上游 IP 走生产同一条自动档路径：既验证探测，也验证网关确实用了这些 IP。
        val ips = runCatching { HanimeDns().preferredIps("hanime1.me") }.getOrNull().orEmpty()
        println("LIVE ip-list = $ips")

        val proc = ProcessBuilder(
            exe.absolutePath,
            "--listen", "127.0.0.1:$port",
            "--ip-list", ips.joinToString(","),
            "--cf-hosts", "hanime1.me,hanime1.com,hanimeone.me,javchu.com",
        ).redirectErrorStream(true).start()

        val ready = proc.inputStream.bufferedReader().useLines { lines ->
            lines.firstOrNull { it.startsWith("LISTENING") }
        }
        assertNotNull(ready, "网关未打印 LISTENING（产物或网络有问题，看 stdout 日志）")
        EchGate.port = port
        return proc to port
    }

    private fun stopGate(proc: Process) {
        EchGate.port = -1
        runCatching { proc.destroy() }
        runCatching { proc.waitFor(3, TimeUnit.SECONDS) }
        if (proc.isAlive) runCatching { proc.destroyForcibly() }
    }

    /**
     * 对照组：不经网关直连，请求必须失败。
     *
     * 只断言"失败"而不断言具体异常：DNS 污染超时、TLS 被 RST 都会走到这里，
     * 而这两种正是我们要覆盖的现实——环境变了也不该让这个用例变假绿。
     */
    @Test
    fun `不经网关直连站点必然失败`() {
        installStore()
        EchGate.port = -1

        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .build()

        val outcome = runCatching {
            client.newCall(Request.Builder().url(SITE_URL).build()).execute().use { it.code }
        }
        println("LIVE no-gate outcome = $outcome")
        assertTrue(outcome.isFailure, "直连竟然成功了？那这个对照就失去意义（可能本机没被阻断）")
    }

    /** 正例：拉起网关后，站点必须拿到 200（走 ECH）。 */
    @Test
    fun `经ECH网关直连站点拿到200`() {
        installStore()
        val exe = findExe()
        if (exe == null) {
            println("LIVE SKIP: 未找到 echgate.exe，跳过（不把缺产物当成失败）")
            return
        }

        val (proc, port) = startGate(exe)
        try {
            val code = clientWithGate(port).newCall(Request.Builder().url(SITE_URL).build())
                .execute().use { resp ->
                    println("LIVE site code=${resp.code} server=${resp.header("Server")}")
                    resp.code
                }
            assertEquals(200, code, "经 ECH 网关应拿到 200（403 = CF 风控，异常 = 没穿透）")
        } finally {
            stopGate(proc)
        }
    }

    /**
     * CNAME 真名降级：视频 CDN 是**另一条赛道** ——
     * `vdownload.hembed.com` 不在 Cloudflare 后（没有 `ech=`），自己也同样被 SNI 阻断，
     * 只能改用它的 CNAME 真名出站。
     *
     * 这里只断言**链路建立**（网关不返回 502），不断言状态码：
     * 该路径本来就需要 `secure` 签名 token，403/404 都属正常；
     * 要证的是"连得上"，而不是"这个 URL 有内容"。
     * 完整视频下载（带真实 `secure` 直链）属手工验证，不入 CI。
     */
    @Test
    fun `视频CDN经网关可建立连接`() {
        installStore()
        val exe = findExe()
        if (exe == null) {
            println("LIVE SKIP: 未找到 echgate.exe，跳过")
            return
        }

        val (proc, port) = startGate(exe)
        try {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/")
                .header("X-Ech-Target", "vdownload.hembed.com")
                .build()
            val code = clientWithGate(port).newCall(request).execute().use { resp ->
                println("LIVE video-cdn code=${resp.code} server=${resp.header("Server")}")
                resp.code
            }
            // 502 = 网关没能连上上游（策略失败）；其余状态码都说明连接建立了。
            assertTrue(
                code != 502,
                "视频 CDN 经网关应当能建立连接，502 说明 CNAME 降级没生效",
            )
        } finally {
            stopGate(proc)
        }
    }

    private companion object {
        const val SITE_URL = "https://hanime1.me/"
    }
}
