package lovehan1me.data.network.interceptor

import lovehan1me.core.constant.USER_AGENT
import okhttp3.Interceptor
import okhttp3.Response

object UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().addHeader(
            "User-Agent", USER_AGENT
        ).build()
        return chain.proceed(request)
    }
}