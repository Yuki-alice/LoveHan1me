package lovehan1me.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cloudflare 通过人机验证后签发的那枚 Cookie 名，三端共用一份。
 *
 * 验证流程靠它判定"这一轮过了"，HTTP 层靠它把 clearance 与其它 Cookie 分开处理
 * （clearance 只认持久化那一份，见 `HCookieJar.loadForRequest`）。两端各写一个字面量，
 * 任何一边打错都会静默失效，表现正是"验证过了还是 403"。
 */
const val CF_CLEARANCE_NAME = "cf_clearance"

/**
 * CF 人机验证挑战请求（跨平台触发总线）。
 *
 * 背景：此前只有 Android 经 OkHttp 拦截器→`CloudflareVerifier`→Activity 跳转
 * 打开验证页；桌面（CDP 无头浏览器弹窗）/iOS（WKWebView 直嵌）的槽位实现虽已就绪
 * （M5-5），但没有任何调用方把 `CloudflareRoute` 压栈，验证 UI 实际不可达——
 * 桌面/iOS 在 CF 挑战下登录与浏览直接去世（仅一个 toast）。
 *
 * 本总线是三端统一触发点：[NetworkRepo.throwRequestException] 判定
 * 403 + "Just a moment" 时发送（不改变原抛错语义）；共享 `App()` 收集后压栈
 * `CloudflareRoute`，各端既有验证 UI 原样复用。Android 拦截器链路不受影响：
 * 拦截器消费首次 403（WebView 单独走），只有其重试仍 403 到达此处，
 * 且收集侧按"栈上已有该域的 CF 页"去重，不会双开。
 *
 * [passed] 是反方向的那半条链路：验证成功后叫醒正在等的那个请求（见
 * `NetworkRepo.ioRequest`）。没有它，"验证成功"对用户而言等于什么都没发生，
 * 只能手动退回再进——这正是"弹窗跟拼运气一样"的体感来源。
 * [abandoned] 是它的对岸：用户不验了就叫醒请求照常报错，别让界面挂着转圈。
 */
data class CloudflareChallenge(
    val url: String,
    val host: String,
)

object CloudflareChallenges {

    // replay=1： composition 前已发出的挑战不丢失（首页请求可能早于收集器注册）；
    // 已消费的事件不会重放给现有收集器，关闭后的 CF 页不会幽灵重开。
    private val _requests = MutableSharedFlow<CloudflareChallenge>(replay = 1, extraBufferCapacity = 1)
    val requests: SharedFlow<CloudflareChallenge> = _requests.asSharedFlow()

    /**
     * 某个域的验证结局（[passed] / [abandoned]）。
     *
     * 为什么结局也要广播：等信号的请求最怕的不是失败，是**没人再管它**。
     * 用户把验证窗关掉之后，请求要一直挂到超时才报错，界面就定格在转圈上。
     */
    private sealed interface Outcome {
        val host: String

        data class Solved(override val host: String) : Outcome
        data class GivenUp(override val host: String) : Outcome
    }

    /** 验证结局。一次性事件、不留档：留档会让"上次过了"变成"这次不用验"。 */
    private val _outcomes = MutableSharedFlow<Outcome>(extraBufferCapacity = 16)

    /** URL → 主机名（小写、去端口）。全仓只有这一份，别处不要再拆字符串。 */
    fun hostOf(url: String): String = url
        .substringAfter("://", "")
        .substringBefore("/")
        .substringBefore(":")
        .lowercase()

    fun request(url: String) {
        val host = hostOf(url)
        if (host.isBlank()) return
        _requests.tryEmit(CloudflareChallenge(url, host))
    }

    /** 由 clearance 写入口调用（`SettingsRepository.setCloudFlareCookie`），三端共用一条通知。 */
    fun passed(host: String) {
        _outcomes.tryEmit(Outcome.Solved(host.lowercase()))
    }

    /**
     * 用户放弃这个域的验证（关掉验证窗 / 验证页出栈而没通过）。
     * 只叫醒等待方，不改任何凭据状态。
     */
    fun abandoned(host: String) {
        _outcomes.tryEmit(Outcome.GivenUp(host.lowercase()))
    }

    /**
     * 等 [host] 的验证结局，最多 [timeoutMs]；超时或被放弃都返回 false。
     *
     * 父域通过也算通过（`www.x` 认 `x` 的 clearance），判定规则与
     * `AppSettings.kt` 的 `cfCookieKeyFor` 保持一致，否则会出现"cookie 能用但没人叫醒"。
     */
    suspend fun awaitPassed(host: String, timeoutMs: Long): Boolean {
        val name = host.lowercase()
        return withTimeoutOrNull(timeoutMs) {
            _outcomes.first { it.host == name || name.endsWith(".${it.host}") } is Outcome.Solved
        } ?: false
    }
}
