package lovehan1me.data.network.interceptor

import android.content.Context
import lovehan1me.data.network.CloudflareVerifier
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * P3：自 :app 下沉到 shared androidMain（仅 Android 安装，desktop 的
 * [lovehan1me.data.network.createCloudflareInterceptor] 返回 null）。
 *
 * 原实现对 :app CloudflareVerificationCoordinator 的直接调用改为经 [CloudflareVerifier] 回调注入；
 * OkHttp 拦截器是同步调用，这里用 runBlocking 桥接 suspend 的 verify。
 */
class CloudflareInterceptor(
    private val context: Context,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 403 && response.header("cf-mitigated") == "challenge") {
            response.close()
            val verified = runBlocking {
                CloudflareVerifier.verify(
                    context = context,
                    url = request.url.toString(),
                )
            }
            if (!verified) {
                throw IOException("Cloudflare verification was cancelled, failed, or timed out")
            }
            return chain.proceed(request)
        }
        return response
    }
}
