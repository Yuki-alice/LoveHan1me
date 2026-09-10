package me.lovehan1me.logic.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import me.lovehan1me.logic.model.DOWNLOAD_SPEED_BYTES

class SpeedLimitInterceptor(var maxSpeed: Long) : Interceptor {

    companion object {
        const val NO_LIMIT_INDEX = 0

        @JvmField
        val SPEED_BYTES = DOWNLOAD_SPEED_BYTES
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        return response.newBuilder()
            .body(SpeedLimitResponseBody(body, maxSpeed))
            .build()
    }
}
