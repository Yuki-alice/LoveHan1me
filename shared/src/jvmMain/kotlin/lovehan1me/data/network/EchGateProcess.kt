package lovehan1me.data.network

import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 本地 ECH 网关进程的拉起与回收。
 *
 * ## 为什么是独立进程
 * ECH 依赖 Go 的 `crypto/tls`，Kotlin/JVM 侧没有对等能力；网关以独立 exe 常驻
 * `127.0.0.1`，App 侧只做 URL 改写（见 [EchGatePolicy]）。进程崩了也不拖垮主程序。
 *
 * ## 为什么在 jvmMain 而不是 desktopMain
 * 设置页（`jvmMain`）要能开关它，依赖方向不允许反向引用。放在 jvmMain 后，
 * Android 侧因为包里没有网关产物，[start] 会自己判掉并跳过——与不支持的平台同一条路径。
 * （Android 的长期形态是 gomobile 进程内起服，见 `echgate/gate` 包；exe 模型只用于桌面。）
 *
 * ## 就绪是异步的
 * 网关启动后要先经 DoH 取 ECH 公钥配置，才打印 `LISTENING` 并真正可用。
 * 这条线不能阻塞 UI，所以监听 stdout 放在守护线程里，拿到端口才写 [EchGate.port]。
 * 在此之前 [EchGate.port] 仍是 -1，请求照旧直连——**不会有一段"改了一半"的坏状态**。
 *
 * ## 平台产物
 * 产物由 `echgate/build.sh` 交叉编译进 `desktopApp/src/main/resources/`：
 * `echgate-windows-amd64.exe`（另保留旧名 `echgate.exe` 兼容）、
 * `echgate-darwin-arm64`、`echgate-darwin-amd64`、`echgate-linux-amd64`。
 * 无对应产物的平台（Android / iOS / Windows-arm64 等）直接跳过，功能等同关闭。
 */
object EchGateProcess {

    private const val TAG = "EchGate"
    private const val READY_PREFIX = "LISTENING"

    /**
     * 冷启动时首页请求与网关就绪的竞态窗口（秒级：ECH 配置走磁盘缓存即毫秒级，
     * 首次 DoH 约 1–3s）。拦截器在改写判定前调 [awaitReadyIfStarting] 做有界等待，
     * 等不到就按原逻辑走直连/代理兜底——等几秒好过首屏直接失败让用户点重试。
     */
    const val STARTUP_GRACE_MS = 8_000L

    /** 自愈拉起的节流：进程死亡后最多每 30s 重试一次，避免每个请求都 spawn。 */
    private const val HEAL_THROTTLE_MS = 30_000L

    @Volatile
    private var lastHealAttemptMs: Long = 0L

    @Volatile
    private var process: Process? = null

    /**
     * 网关拉起中（已 spawn、LISTENING 未到）。拦截器只在拉起中才等待；
     * 从未启动/启动失败/已停止时不等，请求立即走兜底。
     */
    @Volatile
    var starting: Boolean = false
        internal set

    /**
     * 最近一次启动失败的原因（null = 未失败过或已成功）。设置页据此展示，
     * 用户不再靠猜（"开了 ECH 还是失败"时先看这里）。
     */
    @Volatile
    var lastError: String? = null
        private set

    /** 网关已在运行（或已发起启动）则返回 true。 */
    @Synchronized
    fun start(): Boolean {
        if (EchGate.port > 0 || process != null || starting) return true
        val artifact = currentArtifact() ?: legacyArtifact() ?: run {
            lastError = "当前平台无网关产物"
            LogUtil.w(TAG, "当前平台无 ECH 网关产物，直连降级（代理/内置 hosts 兜底）")
            return false
        }
        // 解包/探测/spawn 全放后台：全站并集探测最坏数秒，绝不能 block 调用线程
        // （桌面 LaunchedEffect 即 EDT）。
        starting = true
        lastError = null
        thread(start = true, isDaemon = true, name = "echgate-start") {
            val ok = runCatching { startBlocking(artifact) }.getOrNull() ?: false
            if (!ok) starting = false
        }
        return true
    }

    /**
     * 启动的阻塞部分（后台线程）：解包 → 选端口 → 探测上游 IP → spawn → 监听。
     * 任何一步失败记 [lastError] 并返回 false（调用方清 [starting]）。
     */
    private fun startBlocking(artifact: Artifact): Boolean {
        val exe = runCatching { extractExecutable(artifact.resource, artifact.exeName) }
            .onFailure {
                lastError = "解包网关失败"
                LogUtil.w(TAG, "解包 ECH 网关失败：${it.message}")
            }
            .getOrNull() ?: return false

        val port = runCatching { ServerSocket(0).use { it.localPort } }
            .onFailure {
                lastError = "无可用本地端口"
                LogUtil.w(TAG, "ECH 网关无可用本地端口：${it.message}")
            }
            .getOrNull() ?: return false

        // 上游 IP 复用自动档探测出来的结果：网关自己解析会撞上被污染的系统 DNS。
        // 取**全站并集**而非仅首站：IP 封锁常只封一批边缘 IP，姊妹站的真实边缘
        // IP 可能恰好可达（javchu.com 实测）。网关侧探测还会并入各域 DoH 结果，
        // 这里只是种子，越多越好。
        val ips = HanimeConstants.HANIME_HOSTNAME.flatMap { host ->
            runCatching { HanimeDns().preferredIps(host) }.getOrNull().orEmpty()
        }.distinct().ifEmpty {
            HanimeConstants.HANIME_HOSTNAME.flatMap { host ->
                runCatching { HanimeDns().getCDNList(host) }.getOrNull().orEmpty()
            }.distinct()
        }

        val proc = runCatching {
            ProcessBuilder(
                exe.toString(),
                "--listen", "127.0.0.1:$port",
                "--ip-list", ips.joinToString(","),
                // 只有这些域名才配用 CF IP + ECH；其余域名（视频 CDN、图床）由网关
                // 自己按 CNAME / 普通 TLS 走——ECH 握手到 CF 边缘时外层 SNI 是
                // cloudflare-ech.com，CF 会照常接受，不点名就会把非 CF 站点误判为可用。
                "--cf-hosts", HanimeConstants.HANIME_HOSTNAME.joinToString(","),
                // ECH 公钥配置缓存：没有它，网关要先等一次 DoH 往返才 listen（数秒），
                // 而首页那批图片在启动瞬间就开始加载，会全部撞上"网关还没就绪"。
                "--cache-dir", runtimeDir().toString(),
                // stderr 并进 stdout：单独留着不读会把管道撑满，网关自己卡死。
            ).redirectErrorStream(true).start()
        }.onFailure {
            lastError = "网关进程启动失败"
            LogUtil.e(TAG, "启动 ECH 网关失败", it)
        }.getOrNull() ?: return false

        synchronized(this) {
            // stop() 可能在探测期间被调用（关开关）：已停就地清理刚 spawn 的进程。
            if (!starting) {
                runCatching { proc.destroyForcibly() }
                return false
            }
            process = proc
        }
        registerShutdownHookOnce()
        LogUtil.i(TAG, "ECH 网关进程已启动（${artifact.resource}），等待就绪（ip-list=$ips)")

        monitor(proc, port)
        return true
    }

    /**
     * 输出监听（独立守护线程，为进程整个生命周期排空 stdout）。
     *
     * 找到 LISTENING 就关管道的写法，会让网关下一次打日志时收到 SIGPIPE
     * 直接死亡（实测：首个请求的 plan 日志即杀死网关，客户端看到 EOF）。
     * Go 侧 main.go 已忽略 SIGPIPE 做纵深防御，但正确做法仍是这里不关。
     *
     * 进程退出（输出流 EOF）时清掉全部运行态 `[process]/[starting]/[EchGate.port]`，
     * 拦截器的自愈逻辑据此在下次请求时重新拉起。
     */
    /**
     * 网关的决策/失败行 —— 这些必须可见，见 [monitor] 里的说明。
     * 匹配的是 Go 侧 `log.Printf` 的内容（不含 Kotlin 侧加的 `echgate: ` 前缀）。
     */
    private fun isDiagnosticLine(line: String): Boolean =
        line.contains("upstream error") ||
            line.contains("plan ") ||
            line.contains("CONNECT ") ||
            line.contains("拨号失败") ||
            line.contains("解析无结果") ||
            line.contains("ECH 试不通") ||
            line.contains("被阻断，改用 CNAME") ||
            line.contains("候选 ")

    private fun monitor(proc: Process, port: Int) {
        thread(start = true, isDaemon = true, name = "echgate-monitor") {
            runCatching {
                proc.inputStream.bufferedReader().useLines { lines ->
                    var ready = false
                    for (line in lines) {
                        if (!ready && line.startsWith(READY_PREFIX)) {
                            ready = true
                            EchGate.port = port
                            starting = false
                            LogUtil.i(TAG, "ECH 网关就绪：$line")
                        } else if (isDiagnosticLine(line)) {
                            // ⚠️ 网关的"为什么失败"全在这几行里，而项目默认只留 INFO ——
                            // 以前这里一律用 d 级打，等于把排障依据全丢掉：客户端只看到
                            // `[EchGate]网关异常，回退直连 …`，却不知道网关侧是探测超时、
                            // 拨号被拒还是 CNAME 走了错路（2026-09-25 因此绕了很久）。
                            LogUtil.i(TAG, "echgate: $line")
                        } else {
                            LogUtil.d(TAG, "echgate: $line")
                        }
                    }
                    synchronized(this@EchGateProcess) {
                        if (process === proc) process = null
                    }
                    starting = false
                    if (EchGate.port == port) EchGate.port = -1
                    lastError = "网关进程已退出"
                    LogUtil.w(TAG, "ECH 网关输出结束（进程已退出，port=$port）")
                }
            }.onFailure {
                starting = false
                lastError = "网关监听异常"
                LogUtil.w(TAG, "ECH 网关监听异常：${it.message}")
            }
        }
    }

    /**
     * 网关被要求启动但尚未就绪时，有界等待它（供拦截器在改写判定前调用）。
     *
     * 只等"拉起中"：开关没开 / 产物缺失导致从未启动 / 已停止时立即返回 false，
     * 请求零延迟走兜底。等待发生在 OkHttp 分发线程上——`start()` 内部不依赖
     * OkHttp（探测走裸 socket，ECH 解析在网关进程内），故无死锁。
     *
     * @return 网关可用（调用返回时 `EchGate.port > 0`）。
     */
    fun awaitReadyIfStarting(timeoutMs: Long = STARTUP_GRACE_MS): Boolean {
        if (EchGate.port > 0) return true
        // 只认 starting 标记（stop/就绪/异常都会清它）：从未启动时不等，
        // 请求零延迟走兜底。
        val want = runCatching { SettingsRepository.useEchGate }.getOrDefault(false)
        if (!want) return false
        if (!starting) {
            // 自愈：开关开着但网关不在（进程意外死亡 / 从未拉起成功），
            // 节流重试拉起一次。start() 的阻塞部分在后台线程，无死锁。
            val now = System.currentTimeMillis()
            if (now - lastHealAttemptMs > HEAL_THROTTLE_MS) {
                lastHealAttemptMs = now
                LogUtil.d(TAG, "网关不在运行，尝试自愈拉起")
                runCatching { start() }
            }
            if (!starting) return false
        }
        LogUtil.d(TAG, "网关拉起中，请求等待就绪（≤${timeoutMs}ms）")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (EchGate.port > 0 || !starting) break
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return EchGate.port > 0
    }

    @Synchronized
    fun stop() {
        // 先清 starting：探测线程的 startBlocking 据此丢弃刚 spawn 的进程
        // （process 尚未赋值时直接 return 会漏清，导致关了开关网关照起）。
        starting = false
        EchGate.port = -1
        val proc = process ?: return
        process = null
        runCatching { proc.destroy() }
        runCatching { proc.waitFor(3, TimeUnit.SECONDS) }
        if (proc.isAlive) runCatching { proc.destroyForcibly() }
        LogUtil.i(TAG, "ECH 网关已停止")
    }

    /** 当前平台的网关产物；null = 无产物（Android/iOS/未覆盖的架构）。 */
    internal fun currentArtifact(): Artifact? {
        val artifact = artifactNameFor(
            System.getProperty("os.name", ""),
            System.getProperty("os.arch", ""),
        ) ?: return null
        // Android 的 os.name 同样是 Linux：包里没有产物即不支持，靠资源存在性判定。
        return artifact.takeIf { hasResource(it.resource) }
    }

    /** 纯映射（OS/架构 → 产物名），不碰资源与文件系统，可单测。 */
    internal fun artifactNameFor(osName: String, osArch: String): Artifact? {
        val arch = when {
            osArch.contains("aarch64", ignoreCase = true) || osArch.contains("arm64", ignoreCase = true) -> "arm64"
            osArch.contains("64", ignoreCase = true) -> "amd64"
            else -> return null
        }
        return when {
            osName.startsWith("Windows", ignoreCase = true) ->
                if (arch == "amd64") Artifact("/echgate-windows-amd64.exe", "echgate.exe")
                else null
            osName.startsWith("Mac", ignoreCase = true) ->
                Artifact("/echgate-darwin-$arch", "echgate")
            osName.contains("Linux", ignoreCase = true) || osName.contains("nix", ignoreCase = true) ->
                Artifact("/echgate-linux-$arch", "echgate")
            else -> null
        }
    }

    /**
     * 旧名单产物兼容：历史包曾带 `/echgate.exe`（Windows）。
     * 新产物优先，缺失才回落——调用方在 [currentArtifact] 之后试它。
     */
    internal fun legacyArtifact(): Artifact? =
        Artifact("/echgate.exe", "echgate.exe").takeIf { hasResource(it.resource) }

    /** 网关产物描述（资源路径 + 解包文件名）。公开以便测试与调用方日志。 */
    data class Artifact(val resource: String, val exeName: String)

    private fun hasResource(path: String): Boolean =
        EchGateProcess::class.java.getResourceAsStream(path) != null

    /**
     * 把 jar 里的网关解包到 `~/.lovehan1me/runtime/echgate-<sha256前8>/`。
     *
     * 按内容哈希建目录：换了网关版本就换目录，不会顶掉正在运行的那一份
     * （Windows 上覆盖一个正在执行的 exe 会失败）。
     */
    private fun extractExecutable(resource: String, exeName: String): Path {
        val bytes = EchGateProcess::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
            ?: error("应用包中缺少 ECH 网关组件（$resource）")

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .take(8)
            .joinToString("") { "%02x".format(it) }

        val dir = runtimeDir().resolve("echgate-$hash")
        val exe = dir.resolve(exeName)
        if (Files.isRegularFile(exe)) return exe

        Files.createDirectories(dir)
        Files.copy(bytes.inputStream(), exe, StandardCopyOption.REPLACE_EXISTING)
        exe.toFile().setExecutable(true)
        return exe
    }

    /**
     * 应用退出时把网关一起带走。
     *
     * 不加这个钩子，网关会**变成孤儿进程留在后台**——实测一次调试下来就攒了 3 个
     * （Kotlin 侧的 Process 句柄随 JVM 消失，但子进程不会自动终止）。
     * 钩子只注册一次，重复 start/stop 不会叠加。
     */
    private fun registerShutdownHookOnce() {
        if (!shutdownHookRegistered.compareAndSet(false, true)) return
        runCatching {
            Runtime.getRuntime().addShutdownHook(Thread({ stop() }, "echgate-shutdown"))
        }
    }

    private val shutdownHookRegistered = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 网关产物与缓存的共用目录（`~/.lovehan1me/runtime`）。
     *
     * 放在数据目录旁边而不是临时目录：ECH 公钥缓存要跨启动复用，
     * 放 temp 会被系统清理掉，"秒级就绪"的优势就没了。
     */
    private fun runtimeDir(): Path =
        Path.of(System.getProperty("user.home"), ".lovehan1me", "runtime").also {
            runCatching { Files.createDirectories(it) }
        }
}

/** 按设置确保网关在运行（热切换后复活意外死亡的进程；已运行则 no-op）。 */
actual fun ensureEchGateway() {
    if (runCatching { SettingsRepository.useEchGate }.getOrDefault(false)) {
        EchGateProcess.start()
    }
}
