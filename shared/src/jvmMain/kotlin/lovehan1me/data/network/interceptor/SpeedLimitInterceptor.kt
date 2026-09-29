package lovehan1me.data.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import lovehan1me.core.domain.model.DOWNLOAD_SPEED_BYTES
import lovehan1me.data.SettingsRepository

class SpeedLimitInterceptor : Interceptor {

    companion object {
        const val NO_LIMIT_INDEX = 0

        @JvmField
        val SPEED_BYTES = DOWNLOAD_SPEED_BYTES
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        // 档位每请求读取：客户端是稳定单例，在构造期抓死 maxSpeed 会让改完限速
        // 直到下次冷启动才生效。
        return response.newBuilder()
            .body(SpeedLimitResponseBody(body, SettingsRepository.downloadSpeedLimit))
            .build()
    }
}
