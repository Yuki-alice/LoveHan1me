package lovehan1me.data.network.service

import lovehan1me.core.constant.HANIME_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters

/**
 * Retrofit interface → Ktor 实现。
 */
class HanimeSubscriptionService(
    private val client: HttpClient,
) {

    // 站点地址每次请求实时解析：不能在构造期快照，否则切站必须重建整个 service。
    private val baseUrl: String get() = HANIME_BASE_URL

    suspend fun subscribeArtist(
        csrfToken: String?,
        userId: String,
        artistId: String,
        // 如果当前未订阅会发送空字符串，否则发1
        status: String,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("subscribe-user-id", userId)
            append("subscribe-artist-id", artistId)
            append("subscribe-status", status)
        }
        return client.submitForm(baseUrl + "subscribe", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }
}
