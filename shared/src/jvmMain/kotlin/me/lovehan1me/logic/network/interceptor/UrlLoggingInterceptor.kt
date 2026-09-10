package me.lovehan1me.logic.network.interceptor

import me.lovehan1me.utils.LogUtil
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class UrlLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
        LogUtil.i("NetworkRequest",decodedUrl)
        return chain.proceed(request)
    }
}
