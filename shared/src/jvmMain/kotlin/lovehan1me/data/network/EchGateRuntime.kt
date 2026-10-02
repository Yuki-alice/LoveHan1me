package lovehan1me.data.network

import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * 平台侧的网关"起服器"：只负责**真的把网关起起来 / 停下来**。
 *
 * [EchGateRuntime] 承担与"进程"无关的部分（拉起时机、单飞就绪等待、启动宽限预算、
 * 自愈节流、状态落点），两种运行时在这里插拔：桌面是独立 exe 子进程，
 * Android 是 gomobile 进程内起服。此前这两类逻辑缠在同一个对象里，
 * 于是"Android 没有产物"要靠"资源存在性探测"绕一圈才能表达。
 *
 * ## 谁负责 publish
 * 实现方自己经 [EchGate.publish] 落 [EchGateStatus.Running]：
 * "何时算就绪"两端本就不同（桌面看子进程的 `LISTENING` 行，Android 看
 * `Server.addr()`），骨架不该替它猜。失败时也应 publish 具体原因，
 * 再由骨架兜一次 [EchGateStatus.Failed]（只在该实现没留终态时）。
 */
interface EchGateStarter {

    /**
     * 阻塞式起服。**由骨架在后台线程调用**，可以放心阻塞
     * （首次 DoH 取 ECH 公钥配置约 1–3s）。
     *
     * @return true = 已成功起服并已 publish 就绪；false = 起不起来
     *   （平台不支持 / 产物缺失 / 起服失败，原因应已 publish）。
     */
    fun start(): Boolean

    /**
     * 停止并回收资源；幂等。
     *
     * 状态由骨架负责落（[EchGateRuntime.stop] 会**先** publish
     * [EchGateStatus.Stopped] 再调这里），实现方不要再 publish ——
     * 顺序反了，"用户主动关"会被记成"进程已退出"。
     */
    fun stop()
}

/**
 * 本地 ECH 网关的**运行时门面**：消费方（拦截器、设置页、站点切换）唯一该碰的东西。
 *
 * ## 为什么要有这一层
 * 此前设置页与拦截器直接引用桌面那个"进程管理器"对象（它同时管着"独立 exe 子进程"
 * 与"什么时候该拉起来"两件事）。Android 要接进程内运行时，就只能
 * 复制或改写它 —— 于是骨架分叉。这里把两件事分开：
 *
 * - **本对象（jvm 共享层）**：与"进程"无关的骨架，两种运行时共用一份；
 * - **[EchGateStarter] 实现**：平台各一份（桌面 exe / Android gomobile / iOS 由
 *   Swift 壳经 `EchGatePortReporter` 那类同型回填口对接 —— 它不实现本接口，
 *   iOS 的起服与生命周期由壳自己管）。
 *
 * ## 没装 starter 就等于"当前平台没有网关能力"
 * 与"包里没有产物"走同一条路径：落 [EchGateStatus.Failed] 并让所有请求直连，
 * **行为与网关存在前完全一致**。网关是加速项，挂了不该连累正常请求。
 */
object EchGateRuntime {

    private const val TAG = "EchGate"

    /**
     * 冷启动时首页请求与网关就绪的竞态窗口（秒级：ECH 配置走磁盘缓存即毫秒级，
     * 首次 DoH 约 1–3s）。拦截器在改写判定前调 [awaitReadyIfStarting] 做有界等待，
     * 等不到就按原逻辑走直连/代理兜底——等几秒好过首屏直接失败让用户点重试。
     */
    const val STARTUP_GRACE_MS = 8_000L

    /** 自愈拉起的节流：运行时不在时最多每 30s 重试一次，避免每个请求都拉起。 */
    private const val HEAL_THROTTLE_MS = 30_000L

    /**
     * 状态变更的唤醒信号。等待者在这里等"离开 [EchGateStatus.Starting]"，
     * 由 [EchGate.publish] 的通知钩子叫醒 —— 不是定时轮询。
     */
    private val readyLock = ReentrantLock()
    private val readyChanged = readyLock.newCondition()

    init {
        // commonMain 里没有跨平台的 wait/notify，把"叫醒等待者"接回本对象的信号上。
        // 骨架独占这条钩子：桌面/Android 的 starter 都不该再各自设一遍。
        EchGate.onStatusChanged = { readyLock.withLock { readyChanged.signalAll() } }
    }

    @Volatile
    private var starter: EchGateStarter? = null

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
    private val awaitingReady = AtomicBoolean(false)

    /**
     * 装配平台运行时。由应用壳在启动流程里调用一次
     * （桌面 `Main.kt`、Android `HanimeApplication.onCreate`；iOS 侧不经此处，
     * Swift 自己起服后经同型的回填口写端口）。
     *
     * 幂等：重复装配只替换实现。刻意不做成"只能装一次"——测试要在同 JVM 里
     * 换假 starter，限制一次只会逼出 `@BeforeClass` 那类脆弱写法。
     */
    fun install(newStarter: EchGateStarter) {
        starter = newStarter
    }

    /**
     * 卸下运行时，回到"当前平台没有网关能力"的初始态。
     *
     * 只给测试用：装配是进程全局状态，同 JVM 里跑多条用例必须能把它还原，
     * 否则上一条装的假运行时会被下一条读到（这族"整跑全红、单跑全过"的老家）。
     */
    internal fun uninstall() {
        starter = null
    }

    /** 网关已在运行（或已发起启动）则返回 true。 */
    fun start(): Boolean {
        if (EchGate.port > 0 || EchGate.starting) return true
        val target = starter ?: run {
            EchGate.publish(EchGateStatus.Failed("当前平台无网关产物"))
            LogUtil.w(TAG, "当前平台无 ECH 网关运行时，直连降级（代理/内置 hosts 兜底）")
            return false
        }
        // 起服全放后台：上手探路（桌面解包/探测，Android 首次 DoH）最坏数秒，
        // 绝不能 block 调用线程（桌面调用点就是 EDT）。
        EchGate.publish(EchGateStatus.Starting)
        thread(start = true, isDaemon = true, name = "echgate-start") {
            runCatching { target.start() }
                .onFailure {
                    LogUtil.e(TAG, "启动 ECH 网关异常", it)
                    leaveStartingOnFailure("网关启动失败")
                }
                .onSuccess { ok -> if (!ok) leaveStartingOnFailure("网关启动失败") }
        }
        return true
    }

    /**
     * 起服失败时兜一次失败态，但**只在实现没留下终态时**。
     *
     * 实现可能已经 publish 了具体原因（"当前平台无网关产物" / "网关进程启动失败"），
     * 也可能在拉起途中被 [stop] 打断 —— 那时状态已被落成 [EchGateStatus.Stopped]，
     * 再覆盖它就会把"用户主动关"记成失败，正是这一族缺陷的老家。
     */
    private fun leaveStartingOnFailure(reason: String) {
        if (EchGate.status is EchGateStatus.Starting) {
            EchGate.publish(EchGateStatus.Failed(reason))
        }
    }

    /** 按用户开关停止网关；主动停**不得**记成失败（见 [EchGateStatus.onProcessOutputEnded]）。 */
    fun stop() {
        // 先落 Stopped，且**与起服尾段同一把锁**：起服是长阻塞的，用户随时可能在
        // 这段窗口里关开关，两件事必须有一个全序（见 [publishRunningIfStillStarting]）。
        // 落完再让运行时回收资源 —— 顺序不能反，否则起服线程会看到"还在 Starting"
        // 而把刚起好的那份接管下来。
        readyLock.withLock { EchGate.publish(EchGateStatus.Stopped) }
        runCatching { starter?.stop() }
    }

    /**
     * **起服尾段的原子收尾**：只有状态仍是 [EchGateStatus.Starting] 时才落 [EchGateStatus.Running]。
     *
     * ## 为什么需要它
     * 起服是长阻塞的（桌面要探测上游 IP，Android 首次 DoH 最长 15 秒）。用户完全可能在这段
     * 窗口里关掉开关 —— 那时门面已落 [EchGateStatus.Stopped]，而起服器手里却攥着一个**已经起好**
     * 的运行时。**无条件 publish [EchGateStatus.Running] 会把"开关已关"覆盖成"正在运行"**，
     * 留下设置页与实际不一致的假就绪，且 Go 侧监听与 goroutine 会常驻到进程退出。
     * 桌面侧同一处也有这个漏口：监视器读到就绪行时无条件 publish，若这一行在 `stop()`
     * 之后才从管道里被读出，`onProcessOutputEnded(owned=false)` 会把 `Running` 原样留下。
     *
     * ## 为什么是一个入口而不是各处自己判
     * "什么时候算就绪"两端口径不同（桌面看子进程的 `LISTENING` 行，Android 看 `Server.addr()`），
     * 但"**只有当门面还在等的时候才算数**"这一条是共用的、且必须可离线断言 —— 于是收在这里，
     * 两个运行时都只能经它落就绪。
     *
     * 与 [stop] 共用同一把锁，"复查状态 → 落就绪"与"落 Stopped"之间不存在交错窗口。
     *
     * @return true = 已接管（状态已落 [EchGateStatus.Running]）；
     *   false = 期间已被停止，**调用方必须回收刚起好的运行时**，并把这次起服当作已取消。
     */
    fun publishRunningIfStillStarting(port: Int): Boolean = readyLock.withLock {
        if (EchGate.status !is EchGateStatus.Starting) return@withLock false
        EchGate.publish(EchGateStatus.Running(port))
        true
    }

    /**
     * 网关被要求启动但尚未就绪时，有界等待它（供拦截器在改写判定前调用）。
     *
     * 只等"拉起中"：开关没开 / 从未启动 / 已停止时立即返回 false，
     * 请求零延迟走兜底。等待发生在 OkHttp 分发线程上 —— 起服本身在别的线程、
     * 且不依赖 OkHttp，故无死锁。
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
            // 自愈：开关开着但网关不在（运行时意外死亡 / 从未拉起成功），
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
}

/** 按设置确保网关在运行（热切换/备份恢复后复活意外死亡的运行时；已运行则 no-op）。 */
actual fun ensureEchGateway() {
    if (runCatching { SettingsRepository.useEchGate }.getOrDefault(false)) {
        EchGateRuntime.start()
    }
}
