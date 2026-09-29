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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

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
 * 网关启动后要先经 DoH 取 ECH 公钥配置，才打印就绪行并真正可用。
 * 这条线不能阻塞 UI，所以监听 stdout 放在守护线程里，拿到端口才把 [EchGate] 落成
 * [EchGateStatus.Running]。在此之前端口仍是 -1，请求照旧直连
 * ——**不会有一段"改了一半"的坏状态**。
 *
 * ## 平台产物
 * 产物由 `echgate/build.sh` 交叉编译进 `desktopApp/src/main/resources/`：
 * `echgate-windows-amd64.exe`（另保留旧名 `echgate.exe` 兼容）、
 * `echgate-darwin-arm64`、`echgate-darwin-amd64`、`echgate-linux-amd64`。
 * 无对应产物的平台（Android / iOS / Windows-arm64 等）直接跳过，功能等同关闭。
 *
 * ## 跨进程口令
 * 就绪行、错误页前缀、诊断行关键词、CLI 参数名统一由 [EchGateContract] 持有，
 * 本文件不再自带副本。
 */
object EchGateProcess {

    private const val TAG = "EchGate"

    /**
     * 冷启动时首页请求与网关就绪的竞态窗口（秒级：ECH 配置走磁盘缓存即毫秒级，
     * 首次 DoH 约 1–3s）。拦截器在改写判定前调 [awaitReadyIfStarting] 做有界等待，
     * 等不到就按原逻辑走直连/代理兜底——等几秒好过首屏直接失败让用户点重试。
     */
    const val STARTUP_GRACE_MS = 8_000L

    /** 自愈拉起的节流：进程死亡后最多每 30s 重试一次，避免每个请求都 spawn。 */
    private const val HEAL_THROTTLE_MS = 30_000L

    /**
     * 状态变更的唤醒信号。等待者在这里等"离开 [EchGateStatus.Starting]"，
     * 由 [EchGate.publish] 的通知钩子叫醒 —— 不是定时轮询。
     */
    private val readyLock = ReentrantLock()
    private val readyChanged = readyLock.newCondition()

    init {
        // commonMain 里没有跨平台的 wait/notify，把"叫醒等待者"接回本对象的信号上。
        EchGate.onStatusChanged = { readyLock.withLock { readyChanged.signalAll() } }
    }

    @Volatile
    private var lastHealAttemptMs: Long = 0L

    /**
     * SingleFlight 闸门：同批请求里只放**一个**进来等就绪，其余立刻返回 false 走兜底。
     *
     * 此前每个请求各自阻塞最多 [STARTUP_GRACE_MS]：首页 API + 几十张封面同时到达时，
     * 几十个 OkHttp 分发线程一起卡在就绪信号上，用户看到的是"首屏全白 8 秒"。
     * 而探明网关是否就绪只需要一个请求 —— 其余应当立刻走兜底出口，能出的先出。
     * 网关一旦就绪，后续请求走 `port > 0` 那条提前返回，等待彻底消失。
     */
    private val awaitingReady = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var process: Process? = null

    /** 网关已在运行（或已发起启动）则返回 true。 */
    @Synchronized
    fun start(): Boolean {
        if (EchGate.port > 0 || process != null || EchGate.starting) return true
        val artifact = currentArtifact() ?: legacyArtifact() ?: run {
            EchGate.publish(EchGateStatus.Failed("当前平台无网关产物"))
            LogUtil.w(TAG, "当前平台无 ECH 网关产物，直连降级（代理/内置 hosts 兜底）")
            return false
        }
        // 解包/探测/spawn 全放后台：全站并集探测最坏数秒，绝不能 block 调用线程
        // （桌面 LaunchedEffect 即 EDT）。
        EchGate.publish(EchGateStatus.Starting)
        thread(start = true, isDaemon = true, name = "echgate-start") {
            runCatching { startBlocking(artifact) }.onFailure {
                LogUtil.e(TAG, "启动 ECH 网关异常", it)
                EchGate.publish(EchGateStatus.Failed("网关启动失败"))
            }
        }
        return true
    }

    /**
     * 启动的阻塞部分（后台线程）：解包 → 选端口 → 探测上游 IP → spawn → 监听。
     * 任何一步失败先落 [EchGateStatus.Failed] 再返回 false。
     *
     * 返回 false 不等于"状态已落失败"：探测期间被 [stop] 打断时状态已被 stop 落成
     * [EchGateStatus.Stopped]，这里不能再覆盖它。
     */
    private fun startBlocking(artifact: Artifact): Boolean {
        val exe = runCatching { extractExecutable(artifact.resource, artifact.exeName) }
            .onFailure {
                EchGate.publish(EchGateStatus.Failed("解包网关失败"))
                LogUtil.w(TAG, "解包 ECH 网关失败：${it.message}")
            }
            .getOrNull() ?: return false

        val port = runCatching { ServerSocket(0).use { it.localPort } }
            .onFailure {
                EchGate.publish(EchGateStatus.Failed("无可用本地端口"))
                LogUtil.w(TAG, "ECH 网关无可用本地端口：${it.message}")
            }
            .getOrNull() ?: return false

        // 上游 IP 复用自动档探测出来的结果：网关自己解析会撞上被污染的系统 DNS。
        // 取**全站并集**而非仅首站：IP 封锁常只封一批边缘 IP，姊妹站的真实边缘
        // IP 可能恰好可达（javchu.com 实测）。网关侧探测还会并入各域 DoH 结果，
        // 这里只是种子，越多越好。
        val ips = HanimeConstants.HANIME_HOSTNAME.flatMap { host ->
            runCatching { HanimeDns.SHARED.preferredIps(host) }.getOrNull().orEmpty()
        }.distinct().ifEmpty {
            HanimeConstants.HANIME_HOSTNAME.flatMap { host ->
                runCatching { HanimeDns.SHARED.getCDNList(host) }.getOrNull().orEmpty()
            }.distinct()
        }

        val proc = runCatching {
            ProcessBuilder(
                exe.toString(),
                EchGateContract.FLAG_LISTEN, "127.0.0.1:$port",
                EchGateContract.FLAG_IP_LIST, ips.joinToString(","),
                // 只有这些域名才配用 CF IP + ECH；其余域名（视频 CDN、图床）由网关
                // 自己按 CNAME / 普通 TLS 走——ECH 握手到 CF 边缘时外层 SNI 是
                // cloudflare-ech.com，CF 会照常接受，不点名就会把非 CF 站点误判为可用。
                EchGateContract.FLAG_CF_HOSTS, HanimeConstants.HANIME_HOSTNAME.joinToString(","),
                // ECH 公钥配置缓存：没有它，网关要先等一次 DoH 往返才 listen（数秒），
                // 而首页那批图片在启动瞬间就开始加载，会全部撞上"网关还没就绪"。
                EchGateContract.FLAG_CACHE_DIR, runtimeDir().toString(),
                // stderr 并进 stdout：单独留着不读会把管道撑满，网关自己卡死。
            ).redirectErrorStream(true).start()
        }.onFailure {
            EchGate.publish(EchGateStatus.Failed("网关进程启动失败"))
            LogUtil.e(TAG, "启动 ECH 网关失败", it)
        }.getOrNull() ?: return false

        synchronized(this) {
            // stop() 可能在探测期间被调用（关开关）：已停就地清理刚 spawn 的进程。
            if (EchGate.status !is EchGateStatus.Starting) {
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
     * 找到就绪行就关管道的写法，会让网关下一次打日志时收到 SIGPIPE
     * 直接死亡（实测：首个请求的 plan 日志即杀死网关，客户端看到 EOF）。
     * Go 侧 main.go 已忽略 SIGPIPE 做纵深防御，但正确做法仍是这里不关。
     *
     * 进程退出（输出流 EOF）时把状态落成 [EchGateStatus.Exited]，拦截器的自愈逻辑
     * 据此在下次请求时重新拉起。**主动 [stop] 不在此列**：它已先把状态落成
     * [EchGateStatus.Stopped] 并清掉进程引用，所有权检查会把这条路径让过去。
     */
    private fun monitor(proc: Process, port: Int) {
        thread(start = true, isDaemon = true, name = "echgate-monitor") {
            runCatching {
                proc.inputStream.bufferedReader().useLines { lines ->
                    var ready = false
                    for (line in lines) {
                        if (!ready && EchGateContract.isReadyLine(line)) {
                            ready = true
                            EchGate.publish(EchGateStatus.Running(port))
                            LogUtil.i(TAG, "ECH 网关就绪：$line")
                        } else if (EchGateContract.isDiagnosticLine(line)) {
                            // ⚠️ 网关的"为什么失败"全在这几行里，而项目默认只留 INFO ——
                            // 以前这里一律用 d 级打，等于把排障依据全丢掉：客户端只看到
                            // `[EchGate]网关异常，回退直连 …`，却不知道网关侧是探测超时、
                            // 拨号被拒还是 CNAME 走了错路（2026-09-25 因此绕了很久）。
                            LogUtil.i(TAG, "echgate: $line")
                        } else {
                            LogUtil.d(TAG, "echgate: $line")
                        }
                    }
                }
                val owned = synchronized(this@EchGateProcess) {
                    if (process === proc) {
                        process = null
                        true
                    } else {
                        false
                    }
                }
                EchGate.publish(EchGate.status.onProcessOutputEnded(owned))
                if (owned) {
                    LogUtil.w(TAG, "ECH 网关输出结束（进程已退出，port=$port）")
                } else {
                    LogUtil.d(TAG, "ECH 网关输出结束（已不归本监视器管，忽略，port=$port）")
                }
            }.onFailure {
                // 监视线程自己坏了：进程可能还活着，只是失去了"检测退出"的手段。
                // 已经就绪的不改判 —— 把它标成失败会让一条能用的出口被停用。
                if (EchGate.status !is EchGateStatus.Running) {
                    EchGate.publish(EchGateStatus.Failed("网关监听异常"))
                }
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
     * @return 网关可用（调用返回时 [EchGate.port] > 0）。
     */
    fun awaitReadyIfStarting(timeoutMs: Long = STARTUP_GRACE_MS): Boolean {
        if (EchGate.port > 0) return true
        // 只认 Starting（stop/就绪/失败都会离开它）：从未启动时不等，
        // 请求零延迟走兜底。
        val want = runCatching { SettingsRepository.useEchGate }.getOrDefault(false)
        if (!want) return false
        if (!EchGate.starting) {
            // 自愈：开关开着但网关不在（进程意外死亡 / 从未拉起成功），
            // 节流重试拉起一次。start() 的阻塞部分在后台线程，无死锁。
            val now = System.currentTimeMillis()
            if (now - lastHealAttemptMs > HEAL_THROTTLE_MS) {
                lastHealAttemptMs = now
                LogUtil.d(TAG, "网关不在运行，尝试自愈拉起")
                runCatching { start() }
            }
            if (!EchGate.starting) return false
        }
        if (!awaitingReady.compareAndSet(false, true)) return false
        try {
            LogUtil.d(TAG, "网关拉起中，首个请求等待就绪（≤${timeoutMs}ms），其余走兜底")
            awaitLeavingStarting(timeoutMs)
        } finally {
            awaitingReady.set(false)
        }
        return EchGate.port > 0
    }

    /**
     * 守卫等待：等"离开 [EchGateStatus.Starting]"或预算耗尽。
     *
     * 条件在锁内复查，[EchGate.publish] 也经同一把锁发信号 —— 于是"改状态"与
     * "进等待"之间不存在丢唤醒的窗口，不需要按固定间隔醒来空转。
     */
    private fun awaitLeavingStarting(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        readyLock.withLock {
            while (EchGate.starting && EchGate.port <= 0) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return
                try {
                    readyChanged.await(remaining, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        // 先落 Stopped：探测线程的 startBlocking 据此丢弃刚 spawn 的进程
        // （process 尚未赋值时直接 return 会漏清，导致关了开关网关照起），
        // 监视器的 EOF 收尾也据此不把它误报成"网关进程已退出"。
        EchGate.publish(EchGateStatus.Stopped)
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
