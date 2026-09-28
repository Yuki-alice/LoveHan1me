package lovehan1me.data.network.egress

import kotlin.concurrent.Volatile

/**
 * 网关健康度（熔断器）。**纯状态机**，不带时钟也不碰任何全局 —— 时间由调用方传入，
 * 于是"连续三次失败才熔断""阻断一次即熔断""冷却后重新放行"全都能离线断言。
 *
 * ## 为什么需要它
 * 网关此前没有"能不能用"的判断：每次请求都先撞一遍它。网关被阻断时，用户的系统代理
 * 整条路被绕过（网关改写后出口 = 本机直连），回退判据又只覆盖传输层异常 ——
 * 于是"原本能访问的变得不能访问"。
 *
 * ## 两条失败口径
 * - **阻断类**（403 / `you have been blocked` / `Just a moment`）：说明网关的**出口被封**，
 *   再试只是继续消耗用户的耐心，因此**一次即熔断**。
 * - **其余**（连接被重置、网关自己的 502）：可能只是抖动，累计到
 *   [FAILURE_THRESHOLD] 次才熔断。
 *
 * ## 半开
 * 冷却 [COOLDOWN_MS] 内不放行；到期后 [isOpen] 返回 false，但 `opened` 仍为 true ——
 * 于是下一次失败会立刻重新打开（无需再攒够阈值），而下一次成功会彻底闭合。
 */
data class GateHealth(
    val consecutiveFailures: Int = 0,
    val opened: Boolean = false,
    val openedAtMs: Long = 0L,
) {

    fun onSuccess(): GateHealth = CLOSED

    fun onFailure(nowMs: Long, blocking: Boolean): GateHealth {
        val failures = consecutiveFailures + 1
        return if (blocking || failures >= FAILURE_THRESHOLD) {
            GateHealth(consecutiveFailures = failures, opened = true, openedAtMs = nowMs)
        } else {
            GateHealth(consecutiveFailures = failures, opened = false, openedAtMs = 0L)
        }
    }

    /** 当前是否处于熔断（不放行网关）。冷却到期后返回 false = 半开。 */
    fun isOpen(nowMs: Long): Boolean = opened && (nowMs - openedAtMs) < COOLDOWN_MS

    /** 给设置页/日志用的一句话状态；null = 正常。 */
    fun describe(nowMs: Long): String? = when {
        isOpen(nowMs) -> "网关已熔断（连续 $consecutiveFailures 次失败），暂由代理/直连接管"
        opened -> "网关半开重试中（上次失败：连续 $consecutiveFailures 次）"
        consecutiveFailures > 0 -> "网关近期失败 $consecutiveFailures 次"
        else -> null
    }

    companion object {
        /** 非阻断类失败累计到此数才熔断。 */
        const val FAILURE_THRESHOLD = 3

        /** 熔断冷却时长；到期后半开。 */
        const val COOLDOWN_MS = 5 * 60 * 1000L

        val CLOSED = GateHealth()
    }
}

/**
 * 全局持有的那一份健康度。
 *
 * 读写都发生在网络线程上，用 `@Volatile` 保证跨线程可见即可，不需要锁 ——
 * 与 [lovehan1me.data.network.EchGate] 的端口同一处理方式。
 */
object GateHealthHolder {

    @Volatile
    var current: GateHealth = GateHealth.CLOSED
        private set

    fun recordSuccess() {
        current = current.onSuccess()
    }

    /**
     * @param blocking 该次失败是否属于"网关出口被封"（403 / 阻断页）。
     *   判为阻断时一次即熔断。
     */
    fun recordFailure(nowMs: Long, blocking: Boolean) {
        current = current.onFailure(nowMs, blocking)
    }

    /** 用户手动重开关卡/改网络设置后调用：把健康度归零，给网关一次干净的机会。 */
    fun reset() {
        current = GateHealth.CLOSED
    }
}
