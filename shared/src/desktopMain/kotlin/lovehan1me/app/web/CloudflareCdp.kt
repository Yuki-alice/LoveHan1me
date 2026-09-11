package lovehan1me.app.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lovehan1me.core.constant.DESKTOP_USER_AGENT
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.HCookieJar
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

/**
 * 阶段一⑩：桌面 CF 验证——CDP 驱动本机 Chrome/Edge（替代 KCEF）。
 *
 * 背景：KCEF 首次需下载约 200MB 的 CEF 运行时，国内网络常失败 =
 * 桌面端打不开应用。真浏览器（本机已装的 Chrome/Edge，走 CDP）与 KCEF
 * 是同一类解法（Managed Challenge/Turnstile 在真浏览器里自动过），
 * 但**零下载、零 JNI/CEF 原生崩溃面**。
 *
 * 流程（与旧 KCEF 窗同语义）：
 * 1. [findBrowser] 探活本机 Chrome/Edge 可执行文件；
 * 2. 临时 profile（`--user-data-dir` 指向新建空目录，避免与用户正在跑的
 *    浏览器抢锁）+ headless 起 `--remote-debugging-port`；
 * 3. 经 CDP 建页并导航到挑战 URL，轮询 `Network.getAllCookies`，
 *    出现 `cf_*` 即视为通过（与旧实现同判定）；
 * 4. 经 [persistSolvedCookies] 写回 [HCookieJar.cookieMap] + DataStore
 *    （后续请求自动携带，对齐旧语义与 iOS 端）。
 *
 * 绑定约束（M8-1b 教训）：`cf_clearance` 绑定 UA 与出口 IP——
 * CDP 浏览器必须与应用 HTTP 层一致：UA 强制 [DESKTOP_USER_AGENT]
 * （`--user-agent`），代理按 [SettingsRepository] 的 proxy 配置透传
 * `--proxy-server`（System/Direct 模式不传参，Chrome 默认走系统代理，
 * 与 JVM 侧行为一致）。
 *
 * 失败一律以 [SolveResult.Failed]/[SolveResult.Timeout]/[SolveResult.NoBrowser]
 * 返回，由窗口切手动兜底面板；进程与临时目录在 finally 里清理。
 */
object CloudflareCdp {

    private const val TAG = "CloudflareCdp"
    private val cdpJson = Json { ignoreUnknownKeys = true }

    sealed interface SolveResult {
        data class Solved(val cookieHeader: String, val cookies: List<CdpCookie>) : SolveResult
        data object NoBrowser : SolveResult
        data class Failed(val reason: String) : SolveResult
        data object Timeout : SolveResult
    }

    // ── 浏览器探活 ──────────────────────────────────────

    /**
     * 找本机 Chrome/Edge 可执行文件（Chrome 优先）。
     *
     * [pathExists]/[which] 可注入（单测不碰真实文件系统）。
     */
    fun findBrowser(
        os: String = System.getProperty("os.name", ""),
        pathExists: (String) -> Boolean = { File(it).exists() },
        which: (String) -> String? = ::whichBinary,
        env: (String) -> String? = System::getenv,
    ): String? {
        val candidates = mutableListOf<String>()
        when {
            os.startsWith("Windows", ignoreCase = true) -> {
                candidates += listOf("chrome.exe", "msedge.exe").mapNotNull(which)
                val programFiles = listOfNotNull(
                    env("PROGRAMFILES"),
                    env("PROGRAMFILES(X86)"),
                    env("LOCALAPPDATA"),
                )
                for (base in programFiles) {
                    candidates += "$base\\Google\\Chrome\\Application\\chrome.exe"
                    candidates += "$base\\Microsoft\\Edge\\Application\\msedge.exe"
                }
            }

            os.startsWith("Mac", ignoreCase = true) -> {
                candidates += listOf(
                    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                )
                val home = System.getProperty("user.home", "")
                candidates += listOf(
                    "$home/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                    "$home/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                )
                candidates += listOf("google-chrome", "microsoft-edge").mapNotNull(which)
            }

            else -> {
                // Linux 及其他：PATH 里找
                candidates += listOf(
                    "google-chrome",
                    "chromium",
                    "chromium-browser",
                    "microsoft-edge",
                ).mapNotNull(which)
            }
        }
        return candidates.firstOrNull { pathExists(it) }
    }

    private fun whichBinary(name: String): String? {
        return runCatching {
            val proc = ProcessBuilder(if (isWindows()) listOf("where", name) else listOf("which", name))
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

    // ── 求解 ────────────────────────────────────────────

    /**
     * 用无头浏览器解挑战。
     *
     * @param onStage UI 阶段文案回调（"正在启动…"/"等待通过…"）。
     * @return Solved / NoBrowser / Failed / Timeout（调用方切手动兜底）。
     */
    suspend fun solve(
        url: String,
        timeoutMs: Long = SOLVE_TIMEOUT_MS,
        onStage: (String) -> Unit = {},
    ): SolveResult = withContext(Dispatchers.IO) {
        val browser = findBrowser() ?: return@withContext SolveResult.NoBrowser
        onStage("starting")
        val port = freePort() ?: return@withContext SolveResult.Failed("无可用本地端口")
        val profileDir = runCatching {
            Files.createTempDirectory("han1me-cdp").toFile()
        }.getOrNull() ?: return@withContext SolveResult.Failed("无法创建临时目录")

        val args = buildArgs(browser, port, profileDir)
        val process = runCatching {
            ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull() ?: run {
            profileDir.deleteRecursively()
            return@withContext SolveResult.Failed("浏览器启动失败")
        }
        try {
            onStage("waiting")
            withTimeoutOrNull(timeoutMs) {
                solveWithProcess(url, port)
            } ?: SolveResult.Timeout
        } catch (e: Exception) {
            if (coroutineContext.isActive) {
                LogUtil.e(TAG, "solve failed", e)
                // reason 带异常类名：手动面板可读，排查不再抓瞎
                val detail = e.message?.takeIf { it.isNotBlank() }
                    ?: e::class.simpleName ?: "未知错误"
                SolveResult.Failed(detail)
            } else {
                SolveResult.Timeout
            }
        } finally {
            runCatching { process.destroy() }
            runCatching {
                delay(500)
                if (process.isAlive) process.destroyForcibly()
            }
            runCatching { profileDir.deleteRecursively() }
        }
    }

    private fun buildArgs(browser: String, port: Int, profileDir: File): List<String> {
        val args = mutableListOf(
            browser,
            "--headless=new",
            "--remote-debugging-port=$port",
            // Chrome 111+ 起 WS 调试连接默认拒绝（无 Origin 即 500），自动化必须显式放行；
            // 只绑 127.0.0.1 临时端口 + 用完即杀进程，对外无暴露面。
            "--remote-allow-origins=*",
            "--no-first-run",
            "--no-default-browser-check",
            // 无头 renderer 在某些环境（无显示服务/远端会话的 macOS）不稳定，
            // 会秒崩 target（Inspector.targetCrashed）；--no-sandbox 是自动化
            // 惯例解法。安全面：临时空白 profile、只访问 CF 验证页、跑完即杀，
            // 不装扩展不同步账号，可接受。
            "--no-sandbox",
            "--disable-gpu",
            // 无头且无显示服务时（如 CI/服务器），渲染器初始化失败会导致
            // target 秒崩（Inspector.targetCrashed）；SwiftShader 软渲染兜底。
            // 实测：缺此 flag 时本机 Edge headless 必现 targetCrashed。
            "--enable-unsafe-swiftshader",
            "--disable-dev-shm-usage",
            "--user-data-dir=${profileDir.absolutePath}",
            "--user-agent=$DESKTOP_USER_AGENT",
        )
        proxyFlag()?.let { args += it }
        args += "about:blank"
        return args
    }

    /**
     * 代理透传（与 JVM 侧 HProxySelector 同语义）：
     * Http/Socks 且配了 ip:port → `--proxy-server`；System/Direct 不传参
     * （Chrome 默认走系统代理，与 JVM 侧一致，cf_clearance 才不会因出口 IP
     * 不一致而失效）。
     */
    internal fun proxyFlag(): String? {
        // 防御：Settings 未就绪（单测/极早调用）时不带代理，不崩；
        // 生产路径 DataStore 早已初始化，走正常分支。
        return runCatching {
            val ip = SettingsRepository.proxyIp
            val port = SettingsRepository.proxyPort
            if (ip.isBlank() || port == -1) return@runCatching null
            // SettingsRepository.proxyType 是 id（Int），对齐 HProxySelector 的用法
            when (SettingsRepository.proxyType) {
                ProxyType.Http.id -> "--proxy-server=http://$ip:$port"
                ProxyType.Socks.id -> "--proxy-server=socks5://$ip:$port"
                else -> null
            }
        }.onFailure {
            LogUtil.w(TAG, "proxyFlag: settings unavailable, skip proxy passthrough")
        }.getOrNull()
    }

    private fun freePort(): Int? {
        return runCatching {
            ServerSocket(0).use { it.localPort }
        }.getOrNull()
    }

    private suspend fun solveWithProcess(challengeUrl: String, port: Int): SolveResult {
        // 等调试端口就绪（Chrome 冷启动数秒）
        val debuggerBase = waitForDebugger(port) ?: return SolveResult.Failed("浏览器调试端口无响应")
        // 新建干净 target（fresh profile 下本就只有一个 about:blank 页，取首个 page 亦可；
        // /json/new 确保确定性）
        val targetWs = createTarget(debuggerBase) ?: return SolveResult.Failed("无法创建验证页")
        // WS 会话走自研最小客户端（stdlib）：Ktor-OkHttp 的握手默认带压缩扩展，
        // 在无头 Edge 下建连后数秒必掉（裸 socket 同流程稳定存活），故不用它。
        val socket = runCatching { CdpWebSocket.connect(targetWs) }.getOrElse { e ->
            LogUtil.e(TAG, "ws connect failed", e)
            return SolveResult.Failed("浏览器调试通道连接失败")
        }
        try {
            var id = 0
            fun send(method: String, params: String? = null): Int {
                id += 1
                val payload = if (params == null) {
                    """{"id":$id,"method":"$method"}"""
                } else {
                    """{"id":$id,"method":"$method","params":$params}"""
                }
                socket.sendText(payload)
                return id
            }
            send("Page.enable")
            send("Network.enable")
            send("Page.navigate", """{"url":${jsonString(challengeUrl)}}""")
            // 轮询收割：发一个 poll 包、收一个回包交替进行
            val endAt = System.currentTimeMillis() + SOLVE_TIMEOUT_MS
            while (coroutineContext.isActive && System.currentTimeMillis() < endAt) {
                val pollId = send("Network.getAllCookies")
                val cookies = readCookiesUntil(socket, pollId) ?: continue
                if (hasClearanceCookies(cookies)) {
                    val header = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                    return SolveResult.Solved(header, cookies)
                }
                delay(POLL_INTERVAL_MS)
            }
            return SolveResult.Failed("验证循环意外结束")
        } finally {
            runCatching { socket.close() }
        }
    }

    /** WS 文本帧按 id 取 getAllCookies 回包（事件帧/旧回包返回 null；单测入口）。 */
    internal fun extractCookiesFrame(text: String, pollId: Int): List<CdpCookie>? {
        val msg = runCatching { cdpJson.decodeFromString<CdpMessage>(text) }.getOrNull()
        if (msg?.id == pollId) return msg.result?.cookies
        return null
    }

    /** /json/new 回包取调试地址（单测入口）。 */
    internal fun extractTargetUrl(body: String): String? {
        return runCatching { cdpJson.decodeFromString<CdpTarget>(body).webSocketDebuggerUrl }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** 回包里是否有 cf_ 系 cookie（与旧 KCEF 窗同判定；单测入口）。 */
    internal fun hasClearanceCookies(cookies: List<CdpCookie>): Boolean =
        cookies.any { it.name.startsWith("cf_") }

    /** 读 WS 帧直到拿到指定 id 的 getAllCookies 回包（其它帧跳过；单测不覆盖，走真机）。 */
    private fun readCookiesUntil(socket: CdpWebSocket, pollId: Int): List<CdpCookie>? {
        val deadline = System.currentTimeMillis() + POLL_READ_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            // pollText 内部按剩余时间阻塞收一帧；超时/关闭返回 null
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(0)
            if (remaining == 0) return null
            val text = socket.pollText(remaining) ?: continue
            extractCookiesFrame(text, pollId)?.let { return it }
        }
        return null
    }

    private suspend fun waitForDebugger(port: Int): String? {
        val deadline = System.currentTimeMillis() + DEBUGGER_WAIT_MS
        while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
            val body = runCatching {
                withContext(Dispatchers.IO) {
                    URI("http://127.0.0.1:$port/json/version").toURL().readText()
                }
            }.getOrNull()
            if (body != null && "webSocketDebuggerUrl" in body) {
                return "http://127.0.0.1:$port"
            }
            delay(500)
        }
        return null
    }

    private suspend fun createTarget(debuggerBase: String): String? {
        // /json/new 是 PUT（GET 会被拒绝）；fresh profile 下本就只有一个
        // about:blank 页，失败时回退取 /json/list 首个 page target。
        val created = runCatching {
            withContext(Dispatchers.IO) {
                val conn = URI("$debuggerBase/json/new?about:blank").toURL().openConnection()
                    as java.net.HttpURLConnection
                conn.requestMethod = "PUT"
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                extractTargetUrl(body)
            }
        }.getOrNull()
        if (created != null) return created
        return runCatching {
            withContext(Dispatchers.IO) {
                val body = URI("$debuggerBase/json/list").toURL().readText()
                cdpJson.decodeFromString<List<CdpTarget>>(body)
                    .firstOrNull { it.type == "page" }?.webSocketDebuggerUrl
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun jsonString(raw: String): String {
        return buildString {
            append('"')
            for (c in raw) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }
    }

    // ── 产物写回（与旧 KCEF 路径同语义） ──────────────────

    /**
     * 收割到的 cookie 写回内存 + DataStore（后续请求自动携带）。
     * 与旧 KCEF 实现块等价：`HCookieJar.loadForRequest` 在 host 匹配时叠加。
     */
    suspend fun persistSolvedCookies(host: String, cookies: List<CdpCookie>) {
        val okhttpCookies = cookies.mapNotNull { c ->
            runCatching {
                okhttp3.Cookie.Builder()
                    .name(c.name)
                    .value(c.value)
                    .domain(host)
                    .path("/")
                    .build()
            }.getOrNull()
        }
        HCookieJar.cookieMap[host] = okhttpCookies.toMutableList()
        val cookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        SettingsRepository.setCloudFlareCookie(cookieHeader, host)
    }

    @Serializable
    data class CdpCookie(
        val name: String = "",
        val value: String = "",
        val domain: String = "",
    )

    @Serializable
    private data class CdpMessage(
        val id: Int? = null,
        val result: CdpCookiesResult? = null,
    )

    @Serializable
    private data class CdpCookiesResult(
        val cookies: List<CdpCookie> = emptyList(),
    )

    @Serializable
    private data class CdpTarget(
        val type: String = "",
        val webSocketDebuggerUrl: String = "",
    )

    private const val SOLVE_TIMEOUT_MS = 120_000L
    private const val POLL_INTERVAL_MS = 2_500L
    private const val POLL_READ_TIMEOUT_MS = 10_000L
    private const val DEBUGGER_WAIT_MS = 25_000L
}
