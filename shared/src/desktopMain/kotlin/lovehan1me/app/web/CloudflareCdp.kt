package lovehan1me.app.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lovehan1me.core.constant.DESKTOP_USER_AGENT
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.CF_CLEARANCE_NAME
import lovehan1me.data.network.CloudflareChallenges
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.HanimeDns
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
 * 4. 经 [persistSolvedCookies] 按域写回 DataStore（clearance 与浏览器 UA 成对落盘），
 *    **写回成功才算完成**（写不进去还报成功，用户只会再撞一次 403）。
 *
 * ## 两条绑定约束（`cf_clearance` 绑定 UA 与出口 IP）
 * - **UA：绝不改浏览器 UA，改成"读它、让 HTTP 层跟随"。**
 *   2026-09-13 实测（同 profile、同代理、同一 Chrome 153）：
 *   不覆盖 UA 时 **10 秒内直接进入真实站点**；把浏览器强制成
 *   `--user-agent=…Chrome/149…` 后 **60 秒仍卡在"请稍候…"**。
 *   原因：CF 会比对 UA 字符串与 `Sec-CH-UA` 客户端提示，**伪造 UA 本身就是自曝**。
 *   而 `cf_clearance` 又确实绑定 UA，所以正确做法是用 [readUserAgent]
 *   （`Runtime.evaluate` 读 `navigator.userAgent`）采集**真实** UA，
 *   再由桌面 HTTP 层（`currentHttpUserAgent()`）发同一个字符串。
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
        /**
         * 已拿到可用的 clearance（只可能有一个）。写法回由调用方决定。
         *
         * [browserUserAgent] 是浏览器自报的真实 UA，**非空**：`cf_clearance` 绑定 UA，
         * 拿不到它就写不回 HTTP 层，那样的 clearance 是一把注定无效的钥匙 ——
         * 用户照样验完一遍再撞 403。所以读不到 UA 直接算失败，不进门就别假装通过。
         */
        data class Solved(
            val clearance: CdpCookie,
            val browserUserAgent: String,
        ) : SolveResult
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

        val args = buildArgs(
            browser,
            port,
            profileDir,
            proxyArg,
            hostResolverRules(CloudflareChallenges.hostOf(url)),
        )
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
    /**
     * 把验证浏览器钉到与 App **同一个边缘 IP**。
     *
     * ## 为什么需要（2026-09-21 本机实测，不是推测）
     * 系统 DNS 对三个 Hanime 域名全部返回**不可达**的假 IP：
     * `hanime1.me`→108.160.173.207、`www.hanime1.me`→211.104.160.39、
     * `hanimeone.me`→157.240.10.36（Facebook 段，典型污染特征）——
     * 这三个 IP 的 **443 握手全部超时**。
     *
     * 后果不是"慢"，是**验证页根本打不开**：CDP 浏览器走系统 DNS ⇒ 连不上 CF ⇒
     * 拿不到真的挑战页 ⇒ 用户看到的就是"验证弹窗跟拼运气一样 / 验证过了还是 403"。
     * 而 App 开了内置 hosts 之后走的是真 CF IP（实测 172.64.229.154 等 3 个可达，
     * 170–230ms）——两边根本不在同一个网络世界里，`cf_clearance` 自然绑不上。
     *
     * Chrome/Edge 的 `--host-resolver-rules` 可以把指定 host 钉到给定 IP，
     * 于是**不引入任何代理**就能让两边同出口。
     *
     * ## 只在"应用自己也会走内置 IP"时下发
     * 判定交给 [HanimeDns.preferredIps]：手动内置档返回整张表，自动档返回探测过
     * 能建连的那些，两者都关（或 host 不属于站点族）则返回空表 ⇒ 不钉。
     * 不能图省事用 `getCDNList`——它在没走内置 IP 时会**回退到系统解析结果**，
     * 拿那个去钉等于把验证页送到系统 DNS 的假 IP 上，正是我们要躲开的那个坑。
     *
     * 手填 HTTP/Socks 代理时浏览器把解析交给代理，本规则自然不生效，
     * 此时出口一致性由 [proxyFlag] 保证——两者各管一段，不冲突。
     */
    internal fun hostResolverRules(host: String?): String? {
        if (host.isNullOrBlank()) return null
        // 取列表首位：App 侧 OkHttp 也是按这个顺序尝试，两边才会落在同一个 IP 上。
        val ip = runCatching { HanimeDns().preferredIps(host) }.getOrNull()
            ?.firstOrNull { it.isNotBlank() } ?: return null
        return "--host-resolver-rules=MAP $host $ip"
    }

    internal fun buildArgs(
        browser: String,
        port: Int,
        profileDir: File,
        proxyArg: String? = proxyFlag(),
        hostResolverArg: String? = null,
    ): List<String> {
        val args = mutableListOf(
            browser,
            // ⚠️ **刻意不传 --user-agent**：实测把真实 Chrome 153 强制成常量里的
            // Chrome/149 会让 CF 一直卡在挑战页 —— 伪造 UA 与 Sec-CH-UA 不一致，
            // 本身就是最明显的机器人特征。真实 UA 由 [readUserAgent] 采集，
            // HTTP 层再来对齐（见类 KDoc）。
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
        hostResolverArg?.let { args += it }
        args += "about:blank"
        return args
    }

    /**
     * 代理透传。
     *
     * ## ECH 网关优先 —— 否则 cf_clearance 会一直对不上
     * 网关在跑时，App 的 HTTP 层**全部经网关出站**（URL 被改写到 `127.0.0.1`），
     * 出口 = 本机直连。而用户设置是 System/Direct 时，本函数原本**不传参** →
     * Chrome 走**系统代理** → 出口 = 代理 IP。两边出口不同，clearance 绑的是
     * 代理 IP、请求却用本机 IP，表现就是"**验证过了仍然要验证**"（`cf_clearance`
     * 绑定 UA + 出口 IP，见 `SettingsRepository.clearCloudFlareCookie` 的注释）。
     *
     * 所以网关在跑时一律把验证窗也指到网关：Chrome 走网关的 **CONNECT 隧道**
     * （`echgate/gate/gate.go` 的 `handleConnect` 只做 DoH 解析 + 裸 TCP，
     * 不需要 `X-Ech-Target`，标准 `--proxy-server` 就能用），两边同出口。
     *
     * ## 其余情况（网关没跑）
     * 与 JVM 侧 [HanimeProxySelector] 同语义：Http/Socks 且配了 ip:port →
     * `--proxy-server`；System/Direct 不传参（Chrome 默认走系统代理，与 JVM 一致）。
     */
    internal fun proxyFlag(): String? {
        // ECH 网关优先：认证窗必须与 App 同出口，详见上面的 KDoc。
        val gatePort = runCatching { EchGate.port }.getOrDefault(0)
        if (gatePort > 0) {
            return "--proxy-server=http://${EchGatePolicy.GATE_HOST}:$gatePort"
        }
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
            // 浏览器自报 UA：必须在**不改 UA**的前提下拿到它（见类 KDoc）。
            // 放在导航前读：about:blank 上 navigator.userAgent 已可用，不受页面加载影响。
            val browserUserAgent = run {
                val uaId = send(
                    "Runtime.evaluate",
                    """{"expression":"navigator.userAgent","returnByValue":true}""",
                )
                readUntil(socket) { extractUserAgentFrame(it, uaId) }
            }
            if (browserUserAgent.isNullOrBlank()) {
                // 以前这里只 warn 一句就继续验：验完落盘一把没人能对上的 clearance，
                // 用户白等两分钟再撞一次 403。读不到 UA 就是通道有问题，直接判失败。
                LogUtil.w(TAG, "未能读到浏览器 UA，判为验证失败")
                return SolveResult.Failed("未能读取浏览器 UA（调试通道异常），请重试")
            }
            LogUtil.d(TAG, "浏览器真实 UA = $browserUserAgent")
            send("Page.navigate", """{"url":${jsonString(challengeUrl)}}""")
            // 轮询收割：发一个 poll 包、收一个回包交替进行
            val endAt = System.currentTimeMillis() + SOLVE_TIMEOUT_MS
            while (coroutineContext.isActive && System.currentTimeMillis() < endAt) {
                val pollId = send("Network.getAllCookies")
                val cookies = readCookiesUntil(socket, pollId) ?: continue
                val clearance = findClearanceCookie(cookies, challengeHost)
                if (clearance != null) {
                    // ⚠️ 光有 cookie 不算通过：CF 在挑战流程**中途**就会下发 cf_clearance，
                    // 那一刻的它拿去请求仍然 403（实测：应用抢早收割 → 重试仍 403 → 再弹窗，循环）。
                    // 真正的判据是**页面已经离开挑战页**。
                    val title = readPageTitle(socket, ::send)
                    if (!isChallengeTitle(title)) {
                        LogUtil.d(TAG, "已取得 clearance 且页面离开挑战（title=$title）")
                        return SolveResult.Solved(clearance, browserUserAgent)
                    }
                    LogUtil.d(TAG, "已有 clearance 但仍在挑战页（title=$title），继续等")
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
        val result = extractResultObject(text, pollId) ?: return null
        val cookies = result["cookies"] ?: return emptyList()
        // 用显式 serializer：Json 的成员重载是 (deserializer, element)，reified 扩展在这里不占优
        return runCatching {
            cdpJson.decodeFromJsonElement(ListSerializer(CdpCookie.serializer()), cookies)
        }.getOrNull()
    }

    /**
     * WS 文本帧按 id 取 `Runtime.evaluate` 的**字符串**返回值（单测入口）。
     * 读浏览器 UA、读 `document.title` 都走它 —— 两者回包形状相同。
     *
     * 回包形状：`{"id":n,"result":{"result":{"type":"string","value":"Mozilla/5.0 …"}}}`。
     * 拿不到（id 不匹配 / 非字符串 / 坏帧）返回 null —— 调用方据此沿用原 UA，不阻断求解。
     */
    internal fun extractUserAgentFrame(text: String, pollId: Int): String? {
        val result = extractResultObject(text, pollId) ?: return null
        return result["result"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }

    /** 读当前页面标题（`Runtime.evaluate` 取 `document.title`）；读不到返回 null。 */
    private fun readPageTitle(socket: CdpWebSocket, send: (String, String?) -> Int): String? {
        val id = send(
            "Runtime.evaluate",
            """{"expression":"document.title","returnByValue":true}""",
        )
        return readUntil(socket) { extractUserAgentFrame(it, id) }
    }

    /**
     * 页面是否仍停在 CF 挑战页（按标题判）。
     *
     * 为什么用标题：三端/多语言下挑战页标题是固定的几种写法
     * （英文 `Just a moment...`、简中 `请稍候…`、拦截页 `Attention Required!`），
     * 而站点真实页面标题是站点自己的。**空标题视为"还在挑战"**（保守：宁可多等一会）。
     *
     * 反例（本方法修的就是它）：只看 `cf_clearance` 存在就返回通过 ——
     * CF 在挑战**中途**就会下发它，抢早收割的 cookie 拿去请求仍然是 403。
     */
    internal fun isChallengeTitle(title: String?): Boolean {
        val value = title?.trim().orEmpty()
        if (value.isEmpty()) return true
        return CHALLENGE_TITLE_MARKERS.any { value.contains(it, ignoreCase = true) }
    }

    /** 按 id 取出回包的 result 对象（事件帧、旧回包、坏帧一律 null）。 */
    private fun extractResultObject(text: String, id: Int): JsonObject? {
        val msg = runCatching { cdpJson.decodeFromString<CdpMessage>(text) }.getOrNull()
        return if (msg?.id == id) msg.result else null
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
     * 1. 名字**精确**等于 [CF_CLEARANCE_NAME]；
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
            cookie.name == CF_CLEARANCE_NAME &&
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

    /** 读 WS 帧直到拿到指定 id 的 getAllCookies 回包（其它帧跳过）。 */
    private fun readCookiesUntil(socket: CdpWebSocket, pollId: Int): List<CdpCookie>? =
        readUntil(socket) { extractCookiesFrame(it, pollId) }

    /**
     * 读 WS 帧直到 [extract] 从某一帧里解析出结果（其它帧跳过；单测不覆盖，走真机）。
     *
     * 收帧与解析解耦：getAllCookies 与 Runtime.evaluate 共用同一个循环，
     * 差别只在 [extract]。
     */
    private fun <T> readUntil(socket: CdpWebSocket, extract: (String) -> T?): T? {
        val deadline = System.currentTimeMillis() + POLL_READ_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            // pollText 内部按剩余时间阻塞收一帧；超时/关闭返回 null
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(0)
            if (remaining == 0) return null
            val text = socket.pollText(remaining) ?: continue
            extract(text)?.let { return it }
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

    // ── 产物写回 ────────────────────────────────────────

    /**
     * 把 clearance **按域**写进 DataStore（[lovehan1me.data.SettingsRepository.setCloudFlareCookie]），
     * 请求侧由 `HCookieJar.loadForRequest` 取该域可用的那一条（精确域 → 父域回落）。
     *
     * 不再往 [lovehan1me.data.network.HCookieJar.cookieMap] 里塞：内存那份会绕过失效逻辑，
     * 403 作废了持久层、内存里还留着死钥匙，正是"验完还是 403"的成因之一。
     *
     * **只写 `cf_clearance` 一个**：浏览器 profile 里其它 cookie（`cf_bm`、
     * 站点自己的会话 cookie 等）跟"应用能不能请求"无关，掺进来只会让
     * "写回了什么"变得不可解释。
     *
     * @return 是否写回成功。**调用方只有拿到 true 才能把验证标记为完成** ——
     *         写不进去还报成功，用户只会再撞一次 403，然后怀疑"验证根本没用"。
     */
    suspend fun persistSolvedCookies(
        host: String,
        clearance: CdpCookie,
        browserUserAgent: String,
    ): Boolean {
        LogUtil.d(
            TAG,
            "persist host=$host clearanceLen=${clearance.value.length} " +
                "uaHead=${browserUserAgent.take(48)} proxyType=${SettingsRepository.proxyType}"
        )
        return runCatching {
            // 顺序有讲究：UA 先落盘。写 clearance 会叫醒等在那儿的请求（见
            // CloudflareChallenges.passed），那些请求当场就要用配对的 UA 出去。
            SettingsRepository.setDesktopBrowserUserAgent(browserUserAgent)
            SettingsRepository.setCloudFlareCookie(
                host = host,
                value = "${clearance.name}=${clearance.value}",
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
        /**
         * CDP 回包的 `result`。形状随方法而异 —— `Network.getAllCookies` 给 `cookies`，
         * `Runtime.evaluate` 给 `result.value` —— 故留成原始 JSON，按需在下面解。
         */
        val result: JsonObject? = null,
    )

    @Serializable
    private data class CdpTarget(
        val type: String = "",
        val webSocketDebuggerUrl: String = "",
    )

    /**
     * 挑战页标题的固定写法（英文 / 简中 / CF 拦截页）。
     * 标题命中它们 = 还在挑战中，不能算通过（见 [isChallengeTitle]）。
     */
    private val CHALLENGE_TITLE_MARKERS = listOf(
        "just a moment",
        "请稍候",
        "attention required",
        "checking your browser",
    )

    private const val SOLVE_TIMEOUT_MS = 120_000L
    private const val POLL_INTERVAL_MS = 2_500L
    private const val POLL_READ_TIMEOUT_MS = 10_000L
    private const val DEBUGGER_WAIT_MS = 25_000L
}
