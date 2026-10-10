package lovehan1me.data.network

import kotlin.concurrent.Volatile

/**
 * 本地 ECH 网关（`echgate`）的**生命周期状态**。纯状态机：转换都是纯函数，
 * 不带时钟也不碰全局，于是"主动停不会被记成失败"这类规则能离线断言。
 *
 * 六个状态覆盖进程外网关的全部可观察处境；此前它们由三个互不相干的变量
 * （`port`、`starting`、`lastError`）拼出来，于是"用户主动关"与"进程意外死亡"
 * 在类型上无法区分，设置页只能把两者都报成失败。
 */
sealed interface EchGateStatus {

    /** 从未启动过（含"当前平台无产物"之外的初始态）。 */
    data object Idle : EchGateStatus

    /** 已 spawn、还没打印就绪行。请求此时不等它，直接走兜底。 */
    data object Starting : EchGateStatus

    /** 正在监听 [port]。 */
    data class Running(override val port: Int) : EchGateStatus

    /** 用户主动停掉（关开关 / 应用退出）。 */
    data object Stopped : EchGateStatus

    /** 启动或监听失败，[reason] 是给用户看的原因。 */
    data class Failed(val reason: String) : EchGateStatus

    /** 进程意外退出。 */
    data object Exited : EchGateStatus

    /**
     * 进程输出结束（stdout EOF）时的落点。
     *
     * @param ownedByCurrentMonitor 输出结束的这个进程是否**仍归本监视器管**。
     *   主动停止（门面的 stop）会先把状态落成 [Stopped]、再回收运行时并清掉引用，
     *   于是这里拿到的 `owned` 为 false —— 预期内的停止不会变成 [Exited]，
     *   设置页也就不再报"网关失败：网关进程已退出"。
     */
    fun onProcessOutputEnded(ownedByCurrentMonitor: Boolean = true): EchGateStatus = when {
        !ownedByCurrentMonitor -> this
        this is Stopped -> this
        else -> Exited
    }

    /** 监听端口；不在运行都是 -1（改写层据此零改动放行）。 */
    val port: Int get() = (this as? Running)?.port ?: -1

    val starting: Boolean get() = this is Starting

    /** 给设置页看的一句话原因；null = 没有可报的失败。 */
    val lastError: String? get() = when (this) {
        is Failed -> reason
        Exited -> "网关进程已退出"
        else -> null
    }

    /**
     * 处于"用户可一键重试"的失败态（B1-7）。
     *
     * 只有 [Failed] / [Exited] 是"还值得再拉一次"的态：前者是启动/监听失败（多半是端口被占、
     * 产物缺失等可恢复问题），后者是进程意外退出（自愈逻辑节流后仍可手动催一次）。
     * [Stopped] 是用户主动关（重试没有意义），[Starting] / [Running] 更不必说。
     */
    val canRetry: Boolean get() = this is Failed || this is Exited
}

/**
 * 本地 ECH 网关（`echgate`）的**运行状态**，也是这条进程外边界上状态的唯一真相源。
 *
 * 网关是一个**独立进程/运行时**，监听 `127.0.0.1:<port>`，把站点域名的流量用 ECH
 * （Encrypted Client Hello）加密 ClientHello 送出去。
 *
 * ## 为什么需要它
 * 直连时 TLS 握手的 SNI 是明文的，DPI 看到 `hanime1.me` 就重置连接。实测
 * （2026-09-22）：TCP 能握手（1.05s），TLS 阶段必被 RST；忽略证书校验也一样
 * ⇒ 不是证书问题。ECH 把真 SNI 塞进加密信封，外层只暴露 Cloudflare 的公共名。
 *
 * ## 平台运行时
 * - 桌面：jvm 侧的 `DesktopEchGateStarter` 拉起各 OS 的 Go 二进制
 *   （`echgate/build.sh` 产物）；
 * - Android：`AndroidEchGateStarter`（`:app` 壳）驱动 gomobile 产物
 *   （`echgate/build-android.sh` → `app/libs/Echgate.aar`）进程内起服，
 *   与 iOS 共用同一份 `gate.Start`；
 * - iOS：同上，Swift 壳（`EchGateBootstrap.swift`）起服后经 [EchGatePortReporter] 回填端口。
 *
 * 三端的消费方（拦截器、设置页、站点切换）只依赖门面，不引用任何具体运行时实现。
 * 没启动时 [port] 为 -1，所有改写层自动放行直连，
 * **行为与接入前完全一致**——这是刻意的设计，网关挂了不该连累正常请求。
 * 此时现有机制（代理选择器 / 内置 hosts / DoH）即兜底。
 */
object EchGate {

    /**
     * 当前状态。由各平台的进程管理者经 [publish] 写入。
     * 读写都发生在网络线程/启动流程上，`@Volatile` 保证跨线程可见即可。
     */
    @Volatile
    var status: EchGateStatus = EchGateStatus.Idle
        private set

    /**
     * 状态变更后的平台通知钩子。commonMain 里没有跨平台的 wait/notify，
     * 于是把"叫醒等待者"这件事交给平台侧：jvm 侧由门面（`EchGateRuntime`）接到
     * 就绪信号上，没有运行时的平台不设。空实现只是退回轮询，不影响正确性。
     */
    internal var onStatusChanged: (() -> Unit)? = null

    /** 状态唯一的变更入口：改完再通知等待者，顺序不能反。 */
    fun publish(newStatus: EchGateStatus) {
        status = newStatus
        onStatusChanged?.invoke()
    }

    /** 网关监听端口；`-1` = 未运行。 */
    val port: Int get() = status.port

    /** 网关已 spawn 但还没就绪。 */
    val starting: Boolean get() = status.starting

    /** 最近一次失败原因；null = 未失败过或已成功。 */
    val lastError: String? get() = status.lastError
}

/**
 * 按设置确保网关在运行（热切换/备份恢复后调用）。
 *
 * - JVM：`useEchGate` 开着就经门面 `EchGateRuntime.start()`（已在运行则 no-op；
 *   运行时意外死亡后借此复活）；
 * - iOS：运行时由 Swift 壳（`EchGateBootstrap`）**无条件**起服、端口经
 *   `EchGatePortReporter` 回填，Kotlin 侧没有"拉起"这回事，故这里是 no-op ——
 *   不是"iOS 没有网关"。
 */
expect fun ensureEchGateway()
