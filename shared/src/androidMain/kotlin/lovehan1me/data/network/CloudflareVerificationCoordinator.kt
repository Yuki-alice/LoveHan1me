package lovehan1me.data.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 打开 Cloudflare 验证页的 Intent 契约。
 *
 * M2 起这三个常量定义在 **shared**：由本文件的协调器（发起方）与
 * `:app` 的 `handleMainIntent`（接收方）共用，两边必须一致。
 */
const val ACTION_OPEN_CLOUDFLARE_VERIFICATION =
    "lovehan1me.action.OPEN_CLOUDFLARE_VERIFICATION"
const val EXTRA_CLOUDFLARE_URL = "cloudflare_url"
const val EXTRA_CLOUDFLARE_HOST = "cloudflare_host"

/**
 * 把同一 host 上同时发生的多个 CF 挑战合并成一次验证，并给每个等待中的请求一个确定结果。
 * **被取消的验证不得让原请求在没有 clearance cookie 的情况下重试。**
 *
 * ## M2 迁移说明
 * 原先本对象在 `:app`，靠 `Intent(context, MainActivity::class.java)` 显式指向壳的 Activity；
 * 而引用它的 `CloudflareRouteScreen` 要下沉 shared，shared 又不能反向依赖 `:app`
 * （否则是向上依赖）。因此改成**由平台壳注册 Activity 类**：
 *
 * ```
 * // :app HanimeApplication.onCreate：
 * CloudflareVerificationCoordinator.verificationActivityClass = MainActivity::class.java
 * ```
 *
 * 只让壳交出"哪个 Activity 负责验证"这一点信息 —— 这属于决策 #9 划定给壳的
 * Activity/系统能力范畴，而不是把页面实现留在壳里。
 *
 * 未注册时 [verify] 会**立即失败并唤醒等待者**（而不是挂满超时），
 * 因为"没人能处理验证"是确定性错误，让请求早点走失败路径更可诊断。
 */
object CloudflareVerificationCoordinator {

    private const val VERIFICATION_TIMEOUT_MINUTES = 5L

    /**
     * 负责承载验证页的 Activity。由平台壳在 `Application.onCreate` 注册（见类 KDoc）。
     */
    @Volatile
    var verificationActivityClass: Class<out Activity>? = null

    private class Verification {
        val completed = CountDownLatch(1)

        @Volatile
        var succeeded = false
    }

    private val lock = Any()
    private val verifications = mutableMapOf<String, Verification>()

    /**
     * 发起（或加入）一次针定 [url] 所属 host 的验证，**阻塞直到用户在 UI 上给出结果**。
     *
     * 调用方是网络拦截器的后台线程，所以这里用 `CountDownLatch` 而不是挂起 —— 语义是
     * "这个请求必须等验证结论"，与协程取消无关。
     */
    fun verify(context: Context, url: String): Boolean {
        val host = url.toUri().host?.lowercase() ?: return false
        var shouldLaunch = false
        val verification = synchronized(lock) {
            verifications[host] ?: Verification().also {
                verifications[host] = it
                shouldLaunch = true
            }
        }

        if (shouldLaunch) {
            val launched = runCatching {
                val activityClass = verificationActivityClass ?: return@runCatching false
                context.startActivity(
                    Intent(context, activityClass)
                        .setAction(ACTION_OPEN_CLOUDFLARE_VERIFICATION)
                        .putExtra(EXTRA_CLOUDFLARE_URL, url)
                        .putExtra(EXTRA_CLOUDFLARE_HOST, host)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                true
            }.getOrDefault(false)
            // 起不来就立刻结清，别让等待者挂满 5 分钟
            if (!launched) complete(host, succeeded = false)
        }

        val succeeded = verification.completed.await(
            VERIFICATION_TIMEOUT_MINUTES,
            TimeUnit.MINUTES,
        ) && verification.succeeded
        if (!succeeded) {
            synchronized(lock) {
                if (verifications[host] === verification) {
                    verifications.remove(host)
                }
            }
        }
        return succeeded
    }

    /** 由验证页在本页结束时调用（成功/取消/失败都要调用，否则等待者会挂到超时）。 */
    fun complete(host: String, succeeded: Boolean) {
        val verification = synchronized(lock) { verifications.remove(host.lowercase()) } ?: return
        verification.succeeded = succeeded
        verification.completed.countDown()
    }
}
