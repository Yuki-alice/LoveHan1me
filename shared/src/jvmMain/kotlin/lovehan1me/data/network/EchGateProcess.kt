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
 * 本地 ECH 网关进程的拉起与回收。
 *
 * ## 为什么是独立进程
 * ECH 依赖 Go 的 `crypto/tls`，Kotlin/JVM 侧没有对等能力；网关以独立 exe 常驻
 * `127.0.0.1`，App 侧只做 URL 改写（见 [EchGateInterceptor]）。进程崩了也不拖垮主程序。
 *
 * ## 为什么在 jvmMain 而不是 desktopMain
 * 设置页（`jvmMain`）要能开关它，依赖方向不允许反向引用。放在 jvmMain 后，
 * Android 侧因为包里没有网关产物，[start] 会自己判掉并跳过——与不支持的平台同一条路径。
 *
 * ## 就绪是异步的
 * 网关启动后要先经 DoH 取 ECH 公钥配置，才打印 `LISTENING` 并真正可用。
 * 这条线不能阻塞 UI，所以监听 stdout 放在守护线程里，拿到端口才写 [EchGate.port]。
 * 在此之前 [EchGate.port] 仍是 -1，请求照旧直连——**不会有一段"改了一半"的坏状态**。
 *
 * ## 平台
 * 目前只有 Windows 有可用产物（macOS/Linux 需各自交叉编译并签名，尚未产出）。
 * 非 Windows 或产物缺失时直接跳过，功能等同关闭。
 */
object EchGateProcess {

    private const val TAG = "EchGate"
    private const val RESOURCE = "/echgate.exe"
    private const val EXE_NAME = "echgate.exe"
    private const val READY_PREFIX = "LISTENING"

    @Volatile
    private var process: Process? = null

    /** 网关已在运行（或已发起启动）则返回 true。 */
    @Synchronized
    fun start(): Boolean {
        if (EchGate.port > 0 || process != null) return true
        if (!isWindows()) {
            LogUtil.w(TAG, "非 Windows 平台，未集成 ECH 网关产物，跳过")
            return false
        }

        val exe = runCatching { extractExecutable() }
            .onFailure { LogUtil.w(TAG, "解包 ECH 网关失败：${it.message}") }
            .getOrNull() ?: return false

        val port = runCatching { ServerSocket(0).use { it.localPort } }
            .getOrNull() ?: return false

        // 上游 IP 复用自动档探测出来的结果：网关自己解析会撞上被污染的系统 DNS。
        val ips = runCatching { HanimeDns().preferredIps(HanimeConstants.HANIME_HOSTNAME.first()) }
            .getOrNull()
            .orEmpty()
            .ifEmpty {
                runCatching { HanimeDns().getCDNList(HanimeConstants.HANIME_HOSTNAME.first()) }
                    .getOrNull()
                    .orEmpty()
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
        }.onFailure { LogUtil.e(TAG, "启动 ECH 网关失败", it) }
            .getOrNull() ?: return false

        process = proc
        registerShutdownHookOnce()
        LogUtil.i(TAG, "ECH 网关进程已启动，等待就绪（ip-list=$ips）")

        thread(start = true, isDaemon = true, name = "echgate-monitor") {
            val ready = runCatching {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.firstOrNull { it.startsWith(READY_PREFIX) }
                }
            }.getOrNull()
            if (ready != null) {
                EchGate.port = port
                LogUtil.i(TAG, "ECH 网关就绪：$ready")
            } else {
                LogUtil.w(TAG, "ECH 网关未就绪（进程已退出？）")
            }
        }
        return true
    }

    @Synchronized
    fun stop() {
        val proc = process ?: return
        process = null
        EchGate.port = -1
        runCatching { proc.destroy() }
        runCatching { proc.waitFor(3, TimeUnit.SECONDS) }
        if (proc.isAlive) runCatching { proc.destroyForcibly() }
        LogUtil.i(TAG, "ECH 网关已停止")
    }

    /**
     * 把 jar 里的网关解包到 `~/.lovehan1me/runtime/echgate-<sha256前8>/`。
     *
     * 按内容哈希建目录：换了网关版本就换目录，不会顶掉正在运行的那一份
     * （Windows 上覆盖一个正在执行的 exe 会失败）。
     */
    private fun extractExecutable(): Path {
        val bytes = EchGateProcess::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }
            ?: error("应用包中缺少 ECH 网关组件（$RESOURCE）")

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .take(8)
            .joinToString("") { "%02x".format(it) }

        val dir = runtimeDir().resolve("echgate-$hash")
        val exe = dir.resolve(EXE_NAME)
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

    private fun isWindows(): Boolean =
        System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
}
