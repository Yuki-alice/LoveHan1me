package lovehan1me.data.network

import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.util.LogUtil
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 桌面侧的网关起服器：拉起各 OS 的 Go 二进制（`echgate/build.sh` 产物）。
 *
 * ## 为什么是独立进程
 * ECH 依赖 Go 的 `crypto/tls`，Kotlin/JVM 侧没有对等能力；网关以独立 exe 常驻
 * `127.0.0.1`，App 侧只做 URL 改写（见 [EchGatePolicy]）。进程崩了也不拖垮主程序
 * —— Android 的进程内模型没有这条退路，那是它的取舍（见 [EchGateRuntime]）。
 *
 * ## 与骨架的分工
 * 本类不再管"什么时候该拉起"（那是 [EchGateRuntime] 的事），只负责：
 * 挑产物 → 解包 → 选端口 → 探测上游 IP → spawn → 监听到就绪行。
 * 状态仍然由本类 publish —— 就绪行来自子进程 stdout，骨架猜不到。
 *
 * ## 平台产物
 * 产物由 `echgate/build.sh` 交叉编译进 `desktopApp/src/main/resources/`：
 * `echgate-windows-amd64.exe`（另保留旧名 `echgate.exe` 兼容）、
 * `echgate-darwin-arm64`、`echgate-darwin-amd64`、`echgate-linux-amd64`。
 * 无对应产物的平台（Windows-arm64、Linux-arm64 等）直接跳过，功能等同关闭。
 */
object DesktopEchGateStarter : EchGateStarter {

    private const val TAG = "EchGate"

    @Volatile
    private var process: Process? = null

    override fun start(): Boolean {
        if (EchGate.port > 0 || process != null) return true
        val artifact = currentArtifact() ?: legacyArtifact() ?: run {
            EchGate.publish(EchGateStatus.Failed("当前平台无网关产物"))
            LogUtil.w(TAG, "当前平台无 ECH 网关产物，直连降级（代理/内置 hosts 兜底）")
            return false
        }
        return startBlocking(artifact)
    }

    /**
     * 启动的阻塞部分（骨架的后台线程）：解包 → 选端口 → 探测上游 IP → spawn → 监听。
     * 任何一步失败先落 [EchGateStatus.Failed] 再返回 false。
     *
     * 返回 false 不等于"状态已落失败"：探测期间被 [stop] 打断时状态已被落成
     * [EchGateStatus.Stopped]，这里不能再覆盖它（骨架也只在自己的兜底里复查这一条）。
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

        // 上游 IP 种子（与 Android 起服器共用一个入口，见 echGateSeedIps）：
        // 网关自己解析会撞上被污染的 DoH；这里给"探测过能建连"的内置 IP 打底。
        val ips = echGateSeedIps()

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
     * 据此在下次请求时重新拉起。**主动 [EchGateRuntime.stop] 不在此列**：它已先把状态
     * 落成 [EchGateStatus.Stopped] 并清掉进程引用，所有权检查会把这条路径让过去。
     */
    private fun monitor(proc: Process, port: Int) {
        thread(start = true, isDaemon = true, name = "echgate-monitor") {
            runCatching {
                proc.inputStream.bufferedReader().useLines { lines ->
                    var ready = false
                    for (line in lines) {
                        if (!ready && EchGateContract.isReadyLine(line)) {
                            ready = true
                            // 就绪行可能是在 stop() 之后才从管道里被读出来的（已缓冲）：
                            // 无条件 publish 会把 Stopped 覆盖成 Running，而进程侧的收尾
                            // 拿不到所有权（owned=false）也就不会改回来 —— 留下一份永久的假就绪。
                            if (EchGateRuntime.publishRunningIfStillStarting(port)) {
                                LogUtil.i(TAG, "ECH 网关就绪：$line")
                            } else {
                                LogUtil.i(TAG, "ECH 网关已就绪但期间已被停止，忽略：$line")
                            }
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
                val owned = synchronized(this@DesktopEchGateStarter) {
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

    @Synchronized
    override fun stop() {
        val proc = process ?: return
        process = null
        runCatching { proc.destroy() }
        runCatching { proc.waitFor(3, TimeUnit.SECONDS) }
        if (proc.isAlive) runCatching { proc.destroyForcibly() }
        LogUtil.i(TAG, "ECH 网关已停止")
    }

    /** 当前平台的网关产物；null = 无产物（未覆盖的架构）。 */
    internal fun currentArtifact(): Artifact? {
        val artifact = artifactNameFor(
            System.getProperty("os.name", ""),
            System.getProperty("os.arch", ""),
        ) ?: return null
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
        DesktopEchGateStarter::class.java.getResourceAsStream(path) != null

    /**
     * 把 jar 里的网关解包到 `~/.lovehan1me/runtime/echgate-<sha256前8>/`。
     *
     * 按内容哈希建目录：换了网关版本就换目录，不会顶掉正在运行的那一份
     * （Windows 上覆盖一个正在执行的 exe 会失败）。
     */
    private fun extractExecutable(resource: String, exeName: String): Path {
        val bytes = DesktopEchGateStarter::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
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
