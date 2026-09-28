package lovehan1me.data.network.interceptor

import lovehan1me.core.util.LogUtil
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlin.random.Random

/**
 * 传输层重试：只对幂等方法、只对连接类异常。
 *
 * ## 为什么需要
 * 站点在 DPI 环境下会**间歇性** RST：同一个请求第一次被重置、重发一次就好。
 * 现网现象是"封面首刷失败，滑回来再滑过去又有了"。此前这种抖动只能靠用户
 * 手动重滑，HTTP 层没有任何自愈。
 *
 * ## 与已有的两处重试为什么不会打架
 * - [EchGateInterceptor] 的重试针对**状态码**（网关回 502 且 body 是网关自己的错误页）；
 * - `NetworkRepo.ioRequest` 的续跑针对 **CF 挑战**（403 + "Just a moment"）。
 *
 * 本类**只在 `chain.proceed` 抛异常时**重试，永不看状态码 —— 三者互不重叠，
 * 也不存在"一层重试把另一层的结论吃掉"。
 *
 * ## 边界（刻意不做的事）
 * - **非幂等方法一律不重试**：POST 重发有双提交风险（与网关侧同一条理由）。
 * - **`UnknownHostException` 不重试**：域名类失败由 `HanimeDns` 的四段降级树负责，
 *   在 socket 层再重试只是白等一轮，且解决不了污染。
 * - **有总预算**：`maxAttempts` 与 `maxRetryBudgetMs` 同时约束。只限次数不够——
 *   连接被黑洞时单次就要等满 connectTimeout，三次叠加是分钟级的转圈。
 *   超过预算立即把异常抛出去，让上层按原语义报错。
 *
 * `sleep` / `jitterMs` 可注入：单测据此在不真的等待的前提下验证次数与退避曲线。
 */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val baseBackoffMs: Long = 300L,
    private val maxRetryBudgetMs: Long = 8_000L,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val jitterMs: () -> Long = { Random.nextLong(0L, 250L) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" && request.method != "HEAD") return chain.proceed(request)

        val startedAtNs = System.nanoTime()
        var attempt = 1
        while (true) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000
                if (attempt >= maxAttempts ||
                    !e.isRetryableOnIdempotent() ||
                    elapsedMs >= maxRetryBudgetMs
                ) {
                    throw e
                }
                // 括号不能省：infix 的 `shl` 优先级低于 `+`，写成 `a shl (b) + c` 会被解析成
                // `a shl ((b) + c)`。
                val delayMs = (baseBackoffMs shl (attempt - 1)) + jitterMs()
                LogUtil.w(
                    TAG,
                    "重试 ${request.method} ${request.url.host}（第 $attempt 次失败，已用 ${elapsedMs}ms，" +
                        "退避 ${delayMs}ms）：${e::class.simpleName}: ${e.message}",
                )
                sleep(delayMs)
                attempt++
            }
        }
    }

    /**
     * 连接类异常才值得重发：握手被重置、连接被拒、读写超时。
     *
     * `SSLException` 在列：DPI 的 RST 打在 TLS 握手阶段时，抛出来的正是它
     * （项目里"TCP 能握手、TLS 阶段必被 RST"那条实测就是这种表现）。
     * 代价是真的证书错误会多试两次——每次退避 300ms/600ms，可接受。
     */
    private fun IOException.isRetryableOnIdempotent(): Boolean = when (this) {
        is SocketTimeoutException, is SocketException, is SSLException -> true
        else -> false
    }

    private companion object {
        const val TAG = "Retry"
    }
}
