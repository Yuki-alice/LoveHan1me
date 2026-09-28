package lovehan1me.data.network

import lovehan1me.data.network.interceptor.RetryInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [RetryInterceptor] 的回归（离线，不真的等待）。
 *
 * 测的是**用户能感知的那条线**：站点在 DPI 下间歇性 RST 时，同一个幂等请求重发一次就该好，
 * 而不是让用户自己滑走再滑回来。这里钉住四件事：
 * 1. 幂等方法 + 连接类异常 ⇒ 重试，成功即返回；
 * 2. 非幂等方法 ⇒ **一次都不重发**（POST 重发有双提交风险）；
 * 3. 重试次数有上限（`maxAttempts`），不能变成死循环；
 * 4. 域名类失败（`UnknownHostException`）不重试 —— 那是 DNS 降级树的职责，
 *    在 socket 层重试只是白等一轮。
 *
 * 用真实 [OkHttpClient] + 一个会抛异常的内层拦截器构造场景，不实现 `Chain` 接口：
 * OkHttp 5 的 Chain 成员随小版本变动，自己实现等于把测试绑在上游重写上。
 */
class RetryInterceptorTest {

    /** 让 [sleep] 与抖动都可注入，退避曲线才是可断言的确定值。 */
    private class Harness(
        maxAttempts: Int = 3,
        failures: Int = 1,
        maxRetryBudgetMs: Long = 8_000L,
        failure: () -> IOException = { SocketException("Connection reset") },
    ) {
        val sleeps = mutableListOf<Long>()
        val attempts = AtomicInteger()

        private val flaky = Interceptor { chain ->
            if (attempts.incrementAndGet() <= failures) throw failure()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("ok".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val client: OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false) // 关掉 OkHttp 自己的重试，attempts 才只反映本类的行为
            .addInterceptor(
                RetryInterceptor(
                    maxAttempts = maxAttempts,
                    maxRetryBudgetMs = maxRetryBudgetMs,
                    sleep = { sleeps += it },
                    jitterMs = { 0L }, // 关抖动，退避值可断言
                ),
            )
            .addInterceptor(flaky)
            .build()
    }

    private fun get() = Request.Builder().url("https://hanime1.me/test").build()

    private fun post() = Request.Builder()
        .url("https://hanime1.me/test")
        .post("".toRequestBody("text/plain".toMediaType()))
        .build()

    @Test
    fun `幂等请求遇连接重置会重发一次并成功`() {
        val h = Harness(failures = 1)
        h.client.newCall(get()).execute().use { response ->
            assertEquals(200, response.code)
        }
        assertEquals(2, h.attempts.get(), "第一次被重置后应重发一次")
        assertEquals(listOf(300L), h.sleeps, "退避应是 baseBackoffMs，且只退避一次")
    }

    @Test
    fun `非幂等方法一次都不重发`() {
        val h = Harness(failures = 1)
        assertFailsWith<SocketException> { h.client.newCall(post()).execute() }
        assertEquals(1, h.attempts.get(), "POST 重发有双提交风险，必须一次都不重试")
        assertEquals(emptyList(), h.sleeps)
    }

    @Test
    fun `一直失败时停在次数上限并抛出原异常`() {
        val h = Harness(maxAttempts = 3, failures = Int.MAX_VALUE)
        assertFailsWith<SocketException> { h.client.newCall(get()).execute() }
        assertEquals(3, h.attempts.get(), "maxAttempts=3 表示最多 3 次尝试（1 次原始 + 2 次重试）")
        assertEquals(listOf(300L, 600L), h.sleeps, "退避应指数增长 300 → 600")
    }

    @Test
    fun `域名类失败不重试`() {
        val h = Harness(failures = Int.MAX_VALUE, failure = { UnknownHostException("hanime1.me") })
        assertFailsWith<UnknownHostException> { h.client.newCall(get()).execute() }
        assertEquals(1, h.attempts.get(), "域名失败交给 HanimeDns 的四段降级树，不在这里重试")
    }

    @Test
    fun `总预算耗尽后不再重试`() {
        // 预算 0：第一次失败时已用时长必然 >= 0，判定立刻成立，一次退避都不该做。
        // 只限次数不够 —— 连接被黑洞时单次要等满 connectTimeout，三次叠加就是分钟级转圈。
        val h = Harness(failures = Int.MAX_VALUE, maxRetryBudgetMs = 0L)
        assertFailsWith<SocketException> { h.client.newCall(get()).execute() }
        assertEquals(1, h.attempts.get(), "预算已耗尽，不该再发第二次")
        assertEquals(emptyList(), h.sleeps)
    }
}
