package lovehan1me.data.network.service

import lovehan1me.core.constant.HANIME_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.encodeURLPathPart

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 22:10
 *
 * P3：Retrofit interface → Ktor 实现（包名/方法名/参数名不变，
 * 返回类型 Response<ResponseBody> → HttpResponse）。baseUrl 默认取
 * HANIME_BASE_URL（与旧 Retrofit 在 create 时快照一致：本实例随 HanimeNetwork.rebuildNetwork 重建）。
 */
class HanimeBaseService(
    private val client: HttpClient,
    private val baseUrl: String = HANIME_BASE_URL,
) {

    suspend fun getHomePage(url: String): HttpResponse = client.get(url)

    suspend fun getHanimeSearchResult(
        page: Int = 1,
        query: String? = null,
        genre: String? = null,
        sort: String? = null,
        broad: String? = null,
        date: String? = null,
        duration: String? = null,
        tags: Set<String> = emptySet(),
        brands: Set<String> = emptySet(),
    ): HttpResponse = client.get(baseUrl + "search") {
        url {
            parameters.append("page", page.toString())
            if (query != null) parameters.append("query", query)
            if (genre != null) parameters.append("genre", genre)
            if (sort != null) parameters.append("sort", sort)
            if (broad != null) parameters.append("broad", broad)
            if (date != null) parameters.append("date", date)
            if (duration != null) parameters.append("duration", duration)
            tags.forEach { parameters.append("tags[]", it) }
            brands.forEach { parameters.append("brands[]", it) }
        }
    }

    suspend fun getHanimeVideo(
        videoCode: String,
    ): HttpResponse = client.get(baseUrl + "watch") {
        url { parameters.append("v", videoCode) }
    }

    suspend fun getHanimePreview(
        date: String, // 类似 202206. 202012
    ): HttpResponse = client.get(baseUrl + "previews/" + date.encodeURLPathPart())

    suspend fun login(
        csrfToken: String?,
        email: String,
        password: String,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("email", email)
            append("password", password)
        }
        return client.submitForm(baseUrl + "login", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun getLoginPage(): HttpResponse = client.get(baseUrl + "login")

    suspend fun getMySubscriptions(
        page: Int,
    ): HttpResponse = client.get(baseUrl + "subscriptions") {
        url { parameters.append("page", page.toString()) }
    }

}
