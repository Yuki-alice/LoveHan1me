package lovehan1me.core.util

import kotlin.concurrent.Volatile

/**
 * 全平台日志门面（P2b：从 :app 下沉到 KMP 共享层，包名与 API 面保持不变）。
 *
 * 原实现直接调用 android.util.Log，且 [enabled] 默认取 BuildConfig.DEBUG。
 * commonMain 拿不到 BuildConfig，所以：
 * - 真正的写日志动作交给各端 [platformLog]；
 * - [enabled] 默认 true，由 :app 在启动时按 BuildConfig.DEBUG 覆盖。
 */
object LogUtil {
    const val DEFAULT_TAG = "Han1meViewer"

    // 与 android.util.Log 的优先级常量保持一致，便于 androidMain 直接透传
    private const val VERBOSE = 2
    private const val DEBUG = 3
    private const val INFO = 4
    private const val WARN = 5
    private const val ERROR = 6

    @Volatile
    var enabled: Boolean = true

    fun v(message: String) = v(DEFAULT_TAG, message)

    fun v(tag: String, message: String) = log { platformLog(VERBOSE, tag, message, null) }

    fun d(message: String) = d(DEFAULT_TAG, message)

    fun d(tag: String, message: String) = log { platformLog(DEBUG, tag, message, null) }

    fun i(message: String) = i(DEFAULT_TAG, message)

    fun i(tag: String, message: String) = log { platformLog(INFO, tag, message, null) }

    fun w(message: String) = w(DEFAULT_TAG, message)

    fun w(message: String, throwable: Throwable?) = w(DEFAULT_TAG, message, throwable)

    fun w(tag: String, message: String) = log { platformLog(WARN, tag, message, null) }

    fun w(tag: String, message: String, throwable: Throwable?) = log {
        platformLog(WARN, tag, message, throwable)
    }

    fun e(message: String) = e(DEFAULT_TAG, message)

    fun e(message: String, throwable: Throwable?) = e(DEFAULT_TAG, message, throwable)

    fun e(tag: String, message: String) = log { platformLog(ERROR, tag, message, null) }

    fun e(tag: String, message: String, throwable: Throwable?) = log {
        platformLog(ERROR, tag, message, throwable)
    }

    private inline fun log(block: () -> Unit) {
        if (enabled) block()
    }
}

/**
 * 各端的实际输出通道：priority 取值与 android.util.Log 一致（2=V, 3=D, 4=I, 5=W, 6=E）。
 */
internal expect fun platformLog(priority: Int, tag: String, message: String, throwable: Throwable?)
