package lovehan1me.data.network.interceptor

import android.content.Context
import lovehan1me.data.network.CloudflareVerificationCoordinator
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * P3：自 :app 下沉到 shared androidMain（仅 Android 安装，desktop 的
 * [lovehan1me.data.network.createCloudflareInterceptor] 返回 null）。
 *
 * M2：原先经 `CloudflareVerifier` 的 `launcher` 钩子回调 `:app` 的协调器，
 * 现在**协调器与验证页都在 shared/androidMain**，直接调用即可 —— 钩子已删除。
 *
 * 也因此**不再需要 `runBlocking`**：协调器的 `verify` 本就是阻塞式的
 * （它的语义是"这个请求必须等用户在 UI 上给出验证结论"，与协程取消无关），
 * 之前那层 `suspend` 包装只是为了跨模块回调，属于多余的桥接。
 */
class CloudflareInterceptor(
    private val context: Context,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 403 && response.header("cf-mitigated") == "challenge") {
            response.close()
            val verified = CloudflareVerificationCoordinator.verify(
                context = context,
                url = request.url.toString(),
            )
            if (!verified) {
                throw IOException("Cloudflare verification was cancelled, failed, or timed out")
            }
            return chain.proceed(request)
        }
        return response
    }
}
