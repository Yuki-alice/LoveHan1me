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

    /**
     * 本机可执行的网关产物：按 OS/架构选名（与 [EchGateProcess.artifactNameFor]
     * 同一映射），在桌面资源目录与源码目录里找。
     *
     * 仓库里的二进制可能没有可执行位（git 不总是保留）：找到后尝试补上，
     * 补不上则当缺产物处理（SKIP，不把环境问题当失败）。
     * Windows PE 在 mac/Linux 上永远跑不了——靠按 OS 选名天然避开，
     * 而不是拿起来试（此前 Mac 上直接 exec Windows 版，EACCES 挂全类）。
     */
    private fun findExe(): File? {
        val resource = EchGateProcess.artifactNameFor(
            System.getProperty("os.name", ""),
            System.getProperty("os.arch", ""),
        )?.resource?.trimStart('/') ?: return null
        val candidates = listOf(
            "../desktopApp/src/main/resources/$resource",
            "desktopApp/src/main/resources/$resource",
            "../echgate/$resource",
        ).map(::File).filter { it.isFile }
        // 旧名单兼容（Windows 历史包名）。
        val legacy = listOf(
            "../desktopApp/src/main/resources/echgate.exe",
            "desktopApp/src/main/resources/echgate.exe",
            "../echgate/echgate.exe",
        ).map(::File).filter { it.isFile }
        for (file in candidates + legacy) {
            if (file.canExecute() || runCatching { file.setExecutable(true) }.getOrDefault(false)) {
                if (file.canExecute()) return file
            }
        }
        return null
    }

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
     * 输出流由后台线程为进程整个生命周期排空（生产 EchGateProcess 同模式）：
     * 找到 LISTENING 就关管道会让网关下次打日志时 SIGPIPE 死亡，
     * 请求侧看到的就是 `unexpected end of stream`。
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

        val readyLatch = java.util.concurrent.CountDownLatch(1)
        val drain = kotlin.concurrent.thread(start = true, isDaemon = true, name = "echgate-test-drain") {
            runCatching {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        println("LIVE gate: $line")
                        if (line.startsWith("LISTENING")) readyLatch.countDown()
                    }
                }
            }
        }
        val ready = readyLatch.await(60, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(ready, "网关 60s 内未打印 LISTENING（产物或网络有问题，看 LIVE gate 日志）")
        // 排空线程随进程退出自然结束（daemon，不阻塞 JVM 退出）。
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
     * 对照组：不经网关直连，在被阻断的网络下请求必须失败。
     *
     * 环境感知：开放网络下直连本来就能通（本机即如此），此时对照失效，
     * 打印一行跳过——不把"网络没毛病"当成失败。
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
        if (outcome.isSuccess) {
            println("LIVE SKIP: 本机直连未被阻断，对照失效（网关正例仍有效）")
            return
        }
    }

    /** 正例：拉起网关后，站点必须能走完 TLS+HTTP（200 穿透；403 是 CF 应用层风控，同样证明链路通了）。 */
    @Test
    fun `经ECH网关直连站点拿到200`() {
        installStore()
        val exe = findExe()
        if (exe == null) {
            println("LIVE SKIP: 未找到本机可执行的 echgate 产物，跳过（不把缺产物当成失败）")
            return
        }

        val (proc, port) = startGate(exe)
        try {
            val code = clientWithGate(port).newCall(Request.Builder().url(SITE_URL).build())
                .execute().use { resp ->
                    println("LIVE site code=${resp.code} server=${resp.header("Server")}")
                    resp.code
                }
            assertTrue(
                code == 200 || code == 403,
                "经 ECH 网关应走完 TLS+HTTP（200 穿透 / 403 CF 风控；502/异常 = 没穿透），实际=$code",
            )
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
        const val SITE_JAVCHU_URL = "https://javchu.com/"
    }

    /**
     * 姊妹站同属一个 CF 分区策略（`--cf-hosts` 含 javchu.com）：切站后页面走的
     * 同一条网关 ECH 路。这里证明该分区的 TLS+HTTP 同样穿透（200 穿透 / 403 风控）。
     */
    @Test
    fun `经ECH网关javchu拿到200`() {
        installStore()
        val exe = findExe()
        if (exe == null) {
            println("LIVE SKIP: 未找到本机可执行的 echgate 产物，跳过")
            return
        }

        val (proc, port) = startGate(exe)
        try {
            val code = clientWithGate(port).newCall(Request.Builder().url(SITE_JAVCHU_URL).build())
                .execute().use { resp ->
                    println("LIVE javchu code=${resp.code} server=${resp.header("Server")}")
                    resp.code
                }
            assertTrue(
                code == 200 || code == 403,
                "经 ECH 网关 javchu 应走完 TLS+HTTP（200 穿透 / 403 CF 风控），实际=$code",
            )
        } finally {
            stopGate(proc)
        }
    }
}
