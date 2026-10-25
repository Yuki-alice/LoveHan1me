package lovehan1me.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile

/**
 * iOS 冷启动网关就绪等待（网络优化 1.3）：镜像 jvm `EchGateRuntime.awaitReadyIfStarting`
 * 语义 —— 网关"拉起中"时，**首个**请求有界等待它就绪，其余请求零延迟走兜底；
 * 网关关闭 / 从未启动 / 已停止时不等。
 *
 * 背景：Swift 壳无条件异步起服（`EchGateBootstrap.startIfNeeded`），冷启动的
 * 首个首页请求常早于网关就绪 —— 此前没有"拉起中"这个态（`EchGatePortReporter`
 * 只有 Running/Stopped 两口），首个请求直接按"网关不在"走直连抢跑，ECH 白配。
 * 现在 Swift 起服前先 `markStarting()`，改写判定前经 [awaitReadyIfStarting] 等一把
 * （默认 5s 上界，`installEchGate` 注入 `awaitGateReady`）。
 *
 * **单飞**：并发首屏只有一个请求在等待（[awaiting] CAS），其余照旧走兜底 ——
 * 等待的收益（首屏也走 ECH）由那个等待者拿到，代价（延迟）不叠加。
 *
 * 唤醒：`EchGate.onStatusChanged` 钩子在 iOS 此前空闲，接到换出的
 * `CompletableDeferred` 上；"设 deferred → 复查 starting → await"的顺序
 * 保证改状态与进等待之间没有丢唤醒窗口。钩子安装失败时退化为靠超时兜底
 * （不影响正确性，只是等待者多等到上限）。
 */
internal object IosGateReadiness {

    private const val TAG = "EchGate"

    /** 冷启动就绪等待上限。初值 5s（网关 DoH 首查 1–3s 量级），以实测定档。 */
    private const val STARTUP_GRACE_MS = 5_000L

    private val awaiting = AtomicInt(0)

    /** 当前等待者的唤醒口；单飞 ⇒ 至多一个在途。 */
    @Volatile
    private var wake: CompletableDeferred<Unit>? = null

    private val hookInstalled = AtomicInt(0)

    private fun ensureHook() {
        if (hookInstalled.compareAndSet(0, 1)) {
            EchGate.onStatusChanged = { wake?.complete(Unit) }
        }
    }

    /**
     * 网关被要求启动但尚未就绪时，有界等待它（`installEchGate` 的 awaitGateReady 调用）。
     * @return 网关可用（调用返回时 [EchGate.port] > 0）。
     */
    suspend fun awaitReadyIfStarting(timeoutMs: Long = STARTUP_GRACE_MS): Boolean {
        if (EchGate.port > 0) return true
        val want = runCatching { SettingsRepository.useEchGate }.getOrDefault(false)
        if (!want) return false
        if (!EchGate.starting) return false
        if (!awaiting.compareAndSet(0, 1)) return false
        try {
            LogUtil.d(TAG, "网关拉起中，首个请求等待就绪（≤${timeoutMs}ms），其余走兜底")
            awaitLeavingStarting(timeoutMs)
        } finally {
            awaiting.value = 0
        }
        return EchGate.port > 0
    }

    /** 守卫等待：等"离开 Starting"或预算耗尽。双检防丢唤醒。 */
    private suspend fun awaitLeavingStarting(timeoutMs: Long) {
        ensureHook()
        try {
            withTimeoutOrNull(timeoutMs) {
                while (EchGate.starting) {
                    val w = CompletableDeferred<Unit>().also { wake = it }
                    if (!EchGate.starting) break
                    w.await()
                }
            }
        } catch (_: TimeoutCancellationException) {
            // 预算耗尽 = 就绪太慢，放行走兜底（与 jvm 同语义）。
        }
    }
}
