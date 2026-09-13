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
import kotlin.coroutines.coroutineContext

/**
 * 阶段一⑩：桌面 CF 验证——CDP 驱动本机 Chrome/Edge（替代 KCEF）。
 *
 * 背景：KCEF 首次需下载约 200MB 的 CEF 运行时，国内网络常失败 =
 * 桌面端打不开应用。真浏览器（本机已装的 Chrome/Edge，走 CDP）与 KCEF
 * 是同一类解法（Managed Challenge/Turnstile 在真浏览器里自动过），
 * 但**零下载、零 JNI/CEF 原生崩溃面**。
 *
 * ## 为什么是"可见窗口"，而不是无头（2026-09-13 实测推翻原设计）
 * 本机、经系统代理、同一台机器：
 * - **无头**（`--headless=new` + `--no-sandbox/--disable-gpu/--enable-unsafe-swiftshader`）
 *   访问挑战页，**47 秒后仍停在"请稍候…"**，profile 里始终没有 `cf_clearance`；
 * - 换**可见窗口**（同一 UA、同一代理、同样全新 profile）→ **拿到了 `cf_clearance`**。
 *
 * 结论：无头 + 那组自动化 flag 本身就是 CF 重点打量的指纹，Managed Challenge
 * 不会放行。故本类**不传 `--headless`**，也不再传任何自动化专用 flag ——
 * 用一个"与普通用户手动打开浏览器没有区别"的窗口去解挑战。
 *
 * ## 流程
 * 1. [findBrowser] 探活本机 Chrome/Edge 可执行文件；
 * 2. **持久 profile**（`~/.lovehan1me/cf-browser-profile`）起一个**可见**窗口：
 *    Chrome 136+ 禁止对默认 profile 开远程调试，所以必须独立 profile；
 *    但"每次全新"等于每次都被当新设备 → 持久化后重复访问常直接放行；
 * 3. 经 CDP 建页并导航到挑战 URL，轮询 `Network.getAllCookies`，
 *    命中 **`cf_clearance`（精确名 + 域匹配 + 值合法）** 即视为通过；
 * 4. 经 [persistSolvedCookies] 写回 [HCookieJar.cookieMap] + DataStore，
 *    **写回成功才算完成**（写不进去还报成功，用户只会再撞一次 403）。
 *
 * ## 两条绑定约束（`cf_clearance` 绑定 UA 与出口 IP）
 * - **UA**：浏览器 `--user-agent` 用 [DESKTOP_USER_AGENT]；桌面端 HTTP 层经
 *   `currentHttpUserAgent()` 发的也是同一个常量。**两头必须一致** ——
 *   此前桌面 HTTP 层发的是移动 UA（`USER_AGENT`），与浏览器不一致，
 *   导致收割回来的 clearance 永久失效，属独立于 headless 的结构性错误（已修）。
 * - **出口 IP**：代理按 [SettingsRepository] 的 proxy 配置透传 `--proxy-server`；
 *   System/Direct 不传参（Chrome 默认走系统代理）。
 *
 * ## 判定为什么收紧到 `cf_clearance`
 * 旧实现是"名字以 `cf_` 开头即通过"，而 `cf_bm` / `cf_chl_*` 这类 cookie
 * **挑战进行中就会下发** → 假通过 → 写回一个没用的 cookie → 重试仍 403 →
 * 反复弹窗。[findClearanceCookie] 因此要求精确名 + 域匹配 + 值合法。
 *
 * 失败一律以 [SolveResult.Failed]/[SolveResult.Timeout]/[SolveResult.NoBrowser]
 * 返回，由窗口切手动兜底面板；**进程**在 finally 里清理，profile 目录保留。
 */
object CloudflareCdp {

    private const val TAG = "CloudflareCdp"
    private val cdpJson = Json { ignoreUnknownKeys = true }

    sealed interface SolveResult {
        /** 已拿到可用的 clearance（只可能有一个）。写法回由调用方决定。 */
        data class Solved(val clearance: CdpCookie) : SolveResult
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
     * 用一个**可见**浏览器窗口解挑战（见类 KDoc：无头实测过不了）。
     *
     * @param onStage UI 阶段文案回调（"正在启动…"/"等待通过…"）。
     * @param proxyArg 代理参数（`--proxy-server=...`）；默认按用户代理设置解析，
     *        可注入——真实网络验证见 `CloudflareCdpLiveTest`
     * @return Solved / NoBrowser / Failed / Timeout（调用方切手动兜底）。
     */
    suspend fun solve(
        url: String,
        timeoutMs: Long = SOLVE_TIMEOUT_MS,
        onStage: (String) -> Unit = {},
        proxyArg: String? = proxyFlag(),
    ): SolveResult = withContext(Dispatchers.IO) {
        val browser = findBrowser() ?: return@withContext SolveResult.NoBrowser
        onStage("starting")
        val port = freePort() ?: return@withContext SolveResult.Failed("无可用本地端口")
        val profileDir = runCatching { challengeProfileDirectory() }.getOrNull()
            ?: return@withContext SolveResult.Failed("无法创建浏览器 profile 目录")

        val args = buildArgs(browser, port, profileDir, proxyArg)
        val process = runCatching {
            ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull() ?: return@withContext SolveResult.Failed("浏览器启动失败")
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
            // 只杀进程；**profile 目录保留**（暖 profile 能让下次少被挑战，见类 KDoc）
            runCatching { process.destroy() }
            runCatching {
                delay(500)
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    /**
     * CF 验证浏览器专用的**持久** profile 目录。
     *
     * 两个约束共同决定了这个形态：
     * 1. Chrome 136+ **禁止对默认 profile 开 `--remote-debugging-port`** →
     *    不能借用户自己的浏览器 profile，必须用独立目录；
     * 2. 但"每次新建空 profile" = 每次都像一台新设备 → 每次都被挑战。
     *    持久化后，上次通过验证沉淀下来的 clearance 与浏览历史会让重复访问
     *    常常直接放行（这也是它放在用户主目录下的原因）。
     */
    internal fun challengeProfileDirectory(): File {
        val dir = File(System.getProperty("user.home"), ".lovehan1me/cf-browser-profile")
        if (!dir.exists() && !dir.mkdirs()) {
            error("无法创建 CF 浏览器 profile 目录：$" + "{dir.absolutePath}")
        }
        return dir
    }

    /**
     * 可见窗口的启动参数。
     *
     * ⚠️ **不要加回 `--headless` 与那组自动化 flag**（`--no-sandbox` /
     * `--disable-gpu` / `--enable-unsafe-swiftshader` / `--disable-dev-shm-usage`）：
     * 它们是为"无头也能跑"而加的，但实测无头**根本拿不到 clearance**（见类 KDoc），
     * 而且这套组合本身就是 CF 重点打量的指纹。`CloudflareCdpTest` 有回归测试钉住。
     *
     * @param proxyArg 由 [proxyFlag] 解析或调用方注入（测试用）
     */
    internal fun buildArgs(
        browser: String,
        port: Int,
        profileDir: File,
        proxyArg: String? = proxyFlag(),
    ): List<String> {
        val args = mutableListOf(
            browser,
            // 必须与 HTTP 层同一个 UA：cf_clearance 绑定 (UA, 出口 IP)，见类 KDoc
            "--user-agent=$DESKTOP_USER_AGENT",
            "--remote-debugging-port=$port",
            // Chrome 111+ 起 WS 调试连接默认拒绝（无 Origin 即 500），自动化必须显式放行；
            // 只绑 127.0.0.1 临时端口 + 用完即杀进程，对外无暴露面。
            "--remote-allow-origins=*",
            "--no-first-run",
            "--no-default-browser-check",
            // 可见窗口：用户看得见、能自己点（Turnstile 有时需要人工交互），
            // 也让整个会话看起来就是"一次普通浏览"
            "--new-window",
            "--window-size=1080,760",
            "--user-data-dir=${profileDir.absolutePath}",
        )
        proxyArg?.let { args += it }
        args += "about:blank"
        return args
    }

    /**
     * 代理透传（与 JVM 侧 HanimeProxySelector 同语义）：
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
            // SettingsRepository.proxyType 是 id（Int），对齐 HanimeProxySelector 的用法
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
        // 判定锚定挑战 URL 的 host：clearance 是 hostOnly 语义，别把别的站的收进来
        val challengeHost = runCatching { URI(challengeUrl).host?.lowercase() }.getOrNull()
            ?: return SolveResult.Failed("验证地址无效")
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
                val clearance = findClearanceCookie(cookies, challengeHost)
                if (clearance != null) {
                    LogUtil.d(TAG, "拿到 cf_clearance（domain=${clearance.domain}）")
                    return SolveResult.Solved(clearance)
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

    /**
     * 从回包里找出**真正的** clearance cookie（单测入口）。
     *
     * 旧实现是"名字以 `cf_` 开头就算通过"，而 `cf_bm` / `cf_chl_*` 这类
     * **挑战进行中就会下发**的 cookie 也满足它 → 假通过 → 写回一个没用的 cookie
     * → 重试仍 403 → 反复弹窗。这里收紧到三条：
     * 1. 名字**精确**等于 [CLEARANCE_COOKIE_NAME]；
     * 2. 域对目标 host 有效（RFC 6265：`D == host` 或 `host` 以 `.` + D 结尾）；
     * 3. 值非空，且**不含 `;` 与控制字符** —— 它会被拼进 Cookie 请求头，
     *    含分隔符会破坏整个请求（与 legacy WebView2 助手同一道校验）。
     *
     * @return 命中的 cookie；没有则 null（调用方继续轮询）
     */
    internal fun findClearanceCookie(cookies: List<CdpCookie>, host: String): CdpCookie? {
        val target = host.trim().trimStart('.').lowercase()
        if (target.isEmpty()) return null
        return cookies.firstOrNull { cookie ->
            cookie.name == CLEARANCE_COOKIE_NAME &&
                cookie.value.isNotBlank() &&
                cookie.value.none { it == ';' || it.isISOControl() } &&
                cookie.domain.isCookieDomainAllowedFor(target)
        }
    }

    /**
     * RFC 6265 域匹配：`.hanime1.me` 的 cookie 对 `www.hanime1.me` 有效，
     * 对兄弟站 `hanimeone.me` **无效**。空域一律拒绝（宁可继续等，也不写错的 cookie）。
     */
    private fun String.isCookieDomainAllowedFor(targetHost: String): Boolean {
        val domain = trim().trimStart('.').trimEnd('.').lowercase()
        if (domain.isEmpty()) return false
        return domain == targetHost || targetHost.endsWith("." + domain)
    }

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
     * 把 clearance 写回内存 + DataStore（后续请求自动携带）。
     * `HCookieJar.loadForRequest` 在 host 匹配时叠加它。
     *
     * **只写 `cf_clearance` 一个**：浏览器 profile 里其它 cookie（`cf_bm`、
     * 站点自己的会话 cookie 等）跟"应用能不能请求"无关，掺进来只会让
     * "写回了什么"变得不可解释。
     *
     * @return 是否写回成功。**调用方只有拿到 true 才能把验证标记为完成** ——
     *         写不进去还报成功，用户只会再撞一次 403，然后怀疑"验证根本没用"。
     */
    suspend fun persistSolvedCookies(host: String, clearance: CdpCookie): Boolean {
        val cookie = runCatching {
            okhttp3.Cookie.Builder()
                .name(clearance.name)
                .value(clearance.value)
                .domain(host)
                .path("/")
                .build()
        }.getOrNull() ?: return false

        HCookieJar.cookieMap[host] = mutableListOf(cookie)
        return runCatching {
            SettingsRepository.setCloudFlareCookie(
                value = "${clearance.name}=${clearance.value}",
                host = host,
            )
            true
        }.getOrDefault(false)
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

    /**
     * clearance cookie 的**精确**名字。判据只认它 —— `cf_bm` / `cf_chl_*` 会在
     * 挑战进行中就下发，按前缀判定会"假通过"（见 [findClearanceCookie]）。
     */
    internal const val CLEARANCE_COOKIE_NAME = "cf_clearance"

    private const val SOLVE_TIMEOUT_MS = 120_000L
    private const val POLL_INTERVAL_MS = 2_500L
    private const val POLL_READ_TIMEOUT_MS = 10_000L
    private const val DEBUGGER_WAIT_MS = 25_000L
}
