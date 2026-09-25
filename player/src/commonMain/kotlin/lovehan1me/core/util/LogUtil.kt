package lovehan1me.core.util

import kotlin.concurrent.Volatile

/**
 * 全平台日志门面（P2b：从 :app 下沉到 KMP 共享层，包名与 API 面保持不变）。
 *
 * 原实现直接调用 android.util.Log，且 [enabled] 默认取 BuildConfig.DEBUG。
 * commonMain 拿不到 BuildConfig，所以：
 * - 真正的写日志动作交给各端 [platformLog]；
 * - [enabled] 默认 true，由 :app 在启动时按 BuildConfig.DEBUG 覆盖。
 *
 * ## 级别门槛（Gate3-P6 追加）
 * 只有 [enabled] 一个布尔是不够的：Android 侧它跟着 `BuildConfig.DEBUG` 走，
 * 而**桌面/iOS 从来没有赋值过**，于是恒为 true —— 所有 `d()`/`v()` 级别的细节日志
 * 在桌面全量倒进终端（网关逐条转发、cookie 内容、mpv debug…），把真正有用的
 * `i()`/`w()` 埋在里面看不见。故补一个级别门槛：
 * - [minPriority] 默认 `VERBOSE`（= 等价于改造前的行为，任何端不设就还是全放行）；
 * - **平台入口**按运行形态把它抬到合适的位置，例如桌面 `main()` 抬到 `INFO`，
 *   并用 `HAN1ME_LOG=debug|verbose` 放行（见 `desktopApp/Main.kt`）。
 *
 * ⚠️ 级别只影响"谁是噪音"，**不能**拿来当敏感信息的挡箭牌：cookie/令牌这类东西
 * 不该在**任何**级别被打印出来（见 `Cookies.kt` / `HCookieJar.kt` 的遮蔽写法）。
 */
object LogUtil {
    const val DEFAULT_TAG = "LoveHan1me"

    // 与 android.util.Log 的优先级常量保持一致，便于 androidMain 直接透传
    private const val VERBOSE = 2
    private const val DEBUG = 3
    private const val INFO = 4
    private const val WARN = 5
    private const val ERROR = 6

    /** 总开关（:app 按 `BuildConfig.DEBUG` 覆盖；桌面/iOS 保持 true）。 */
    @Volatile
    var enabled: Boolean = true

    /**
     * 最低输出级别：数值越大越严重（与 android.util.Log 同向）。
     * 默认 [VERBOSE] = 全放行，保持历史行为；平台入口按需抬高。
     */
    @Volatile
    var minPriority: Int = VERBOSE

    /**
     * 是否放行调试级日志 —— 供**外部子系统**对齐自己的 verbosity：
     * 例如桌面引擎把 mpv 的 `msg-level` 挂在它上面（见 `DesktopMpvPlaybackEngine`），
     * 免得 mpv 自己的 debug 输出绕过本门面直接灌进终端。
     */
    val verboseEnabled: Boolean get() = enabled && minPriority <= DEBUG

    /**
     * 平台入口用：按名字设定门槛。
     * `verbose` → 全放行；`debug` → 放行 d 及以上；其余（含 null / 未知值）→ 只留 i 及以上。
     * 名字取自环境变量 `HAN1ME_LOG`（各入口自行读，commonMain 无 getenv）。
     */
    fun setLevelByName(name: String?) {
        minPriority = when (name?.trim()?.lowercase()) {
            "verbose", "v" -> VERBOSE
            "debug", "d" -> DEBUG
            else -> INFO
        }
    }

    fun v(message: String) = v(DEFAULT_TAG, message)

    fun v(tag: String, message: String) = log(VERBOSE) { platformLog(VERBOSE, tag, message, null) }

    fun d(message: String) = d(DEFAULT_TAG, message)

    fun d(tag: String, message: String) = log(DEBUG) { platformLog(DEBUG, tag, message, null) }

    fun i(message: String) = i(DEFAULT_TAG, message)

    fun i(tag: String, message: String) = log(INFO) { platformLog(INFO, tag, message, null) }

    fun w(message: String) = w(DEFAULT_TAG, message)

    fun w(message: String, throwable: Throwable?) = w(DEFAULT_TAG, message, throwable)

    fun w(tag: String, message: String) = log(WARN) { platformLog(WARN, tag, message, null) }

    fun w(tag: String, message: String, throwable: Throwable?) = log(WARN) {
        platformLog(WARN, tag, message, throwable)
    }

    fun e(message: String) = e(DEFAULT_TAG, message)

    fun e(message: String, throwable: Throwable?) = e(DEFAULT_TAG, message, throwable)

    fun e(tag: String, message: String) = log(ERROR) { platformLog(ERROR, tag, message, null) }

    fun e(tag: String, message: String, throwable: Throwable?) = log(ERROR) {
        platformLog(ERROR, tag, message, throwable)
    }

    private inline fun log(priority: Int, block: () -> Unit) {
        if (enabled && priority >= minPriority) block()
    }
}

/**
 * 各端的实际输出通道：priority 取值与 android.util.Log 一致（2=V, 3=D, 4=I, 5=W, 6=E）。
 */
internal expect fun platformLog(priority: Int, tag: String, message: String, throwable: Throwable?)
