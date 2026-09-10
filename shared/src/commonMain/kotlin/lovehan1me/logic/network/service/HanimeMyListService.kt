package lovehan1me.logic.network.service

import lovehan1me.HANIME_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.encodeURLPathPart

/**
 * MyList 是指 喜欢的影片 + 稍后再看
 *
 * Playlist 是指 自定义的播放列表
 *
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2023/08/26 026 16:30
 *
 * P3：Retrofit interface → Ktor 实现。
 */
class HanimeMyListService(
    private val client: HttpClient,
    private val baseUrl: String = HANIME_BASE_URL,
) {

    suspend fun getMyListItems(
        userId: String,
        listType: String,
        page: Int,
    ): HttpResponse = client.get(baseUrl + "user/" + userId.encodeURLPathPart() + "/" + listType.encodeURLPathPart()) {
        url { parameters.append("page", page.toString()) }
    }

    suspend fun getOnlineWatchHistories(
        userId: String,
        sort: String,
        page: Int,
    ): HttpResponse = client.get(baseUrl + "user/" + userId.encodeURLPathPart() + "/histories") {
        url {
            parameters.append("sort", sort)
            parameters.append("page", page.toString())
        }
    }

    suspend fun getUserAccountPage(
        userId: String,
    ): HttpResponse = client.get(baseUrl + "user/" + userId.encodeURLPathPart() + "/edit")

    suspend fun updateUserAccountProfile(
        userId: String,
        csrfToken: String?,
        method: String = "patch",
        type: String = "profile",
        name: String,
        email: String,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("_method", method)
            append("type", type)
            append("name", name)
            append("email", email)
        }
        return client.submitForm(baseUrl + "user/" + userId.encodeURLPathPart(), form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun updateUserAccountPassword(
        userId: String,
        csrfToken: String?,
        method: String = "patch",
        type: String = "password",
        oldPassword: String,
        newPassword: String,
        newPasswordConfirm: String,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("_method", method)
            append("type", type)
            append("password_old", oldPassword)
            append("password_new", newPassword)
            append("password_new_confirm", newPasswordConfirm)
        }
        return client.submitForm(baseUrl + "user/" + userId.encodeURLPathPart(), form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    /**
     * 头像上传：原 Retrofit @Multipart @Part("_token"/"_method"/"type") + @Part photo
     * 改写为 Ktor multipart；[photo] 为图片字节，[photoName] 为原始文件名（Content-Disposition filename）。
     */
    suspend fun updateUserAccountAvatar(
        userId: String,
        csrfToken: String,
        method: String,
        type: String,
        photo: ByteArray,
        photoName: String,
    ): HttpResponse {
        val parts = formData {
            append("_token", csrfToken, Headers.build {
                append(HttpHeaders.ContentType, "text/plain")
                append(HttpHeaders.ContentDisposition, "form-data; name=\"_token\"")
            })
            append("_method", method, Headers.build {
                append(HttpHeaders.ContentType, "text/plain")
                append(HttpHeaders.ContentDisposition, "form-data; name=\"_method\"")
            })
            append("type", type, Headers.build {
                append(HttpHeaders.ContentType, "text/plain")
                append(HttpHeaders.ContentDisposition, "form-data; name=\"type\"")
            })
            append("photo", photo, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "form-data; name=\"photo\"; filename=\"$photoName\"")
            })
        }
        return client.submitFormWithBinaryData(baseUrl + "user/" + userId.encodeURLPathPart(), parts)
    }

    suspend fun deleteOnlineWatchHistory(
        videoCode: String,
        tab: String = "histories",
        csrfToken: String?,
    ): HttpResponse {
        val form = Parameters.build {
            append("tab", tab)
        }
        return client.request(baseUrl + "user/tab-item/" + videoCode.encodeURLPathPart()) {
            method = HttpMethod.Delete
            setBody(FormDataContent(form))
            if (csrfToken != null) header("X-CSRF-TOKEN", csrfToken)
        }
    }

    suspend fun getMyPlayListItems(
        listCode: String,
        page: Int,
    ): HttpResponse = client.get(baseUrl + "playlist") {
        url {
            parameters.append("list", listCode)
            parameters.append("page", page.toString())
        }
    }

    suspend fun deleteMyListItems(
        listType: String,
        videoCode: String,
        count: Int = 1, // 隨便傳一個就行
        csrfToken: String?,
    ): HttpResponse {
        val form = Parameters.build {
            append("playlist_id", listType)
            append("video_id", videoCode)
            append("count", count.toString())
        }
        return client.submitForm(baseUrl + "deletePlayitem", form) {
            if (csrfToken != null) header("X-CSRF-TOKEN", csrfToken)
        }
    }

    suspend fun addToMyFavVideo(
        videoCode: String,
        likeStatus: String,
        csrfToken: String?,
        userId: String?,
        isPositive: Int = 1,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            append("like-foreign-id", videoCode)
            append("like-status", likeStatus)
            if (csrfToken != null) append("_token", csrfToken)
            if (userId != null) append("like-user-id", userId)
            append("like-is-positive", isPositive.toString())
        }
        return client.submitForm(baseUrl + "like", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun rateVideo(
        videoCode: String,
        isPositive: Int,
        likeStatus: String,
        unlikeStatus: String,
        likesCount: Int,
        unlikesCount: Int,
        csrfToken: String?,
        userId: String?,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            append("like-foreign-id", videoCode)
            append("like-is-positive", isPositive.toString())
            append("like-status", likeStatus)
            append("unlike-status", unlikeStatus)
            append("likes-count", likesCount.toString())
            append("unlikes-count", unlikesCount.toString())
            if (csrfToken != null) append("_token", csrfToken)
            if (userId != null) append("like-user-id", userId)
        }
        return client.submitForm(baseUrl + "like", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun getPlaylists(
        userId: String,
        page: Int,
    ): HttpResponse = client.get(baseUrl + "user/" + userId.encodeURLPathPart() + "/playlists") {
        url { parameters.append("page", page.toString()) }
    }

    suspend fun createPlaylist(
        csrfToken: String?,
        videoCode: String,
        title: String,
        description: String,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("create-playlist-video-id", videoCode)
            append("playlist-title", title)
            append("playlist-description", description)
        }
        return client.submitForm(baseUrl + "createPlaylist", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun addToMyList(
        csrfToken: String?,
        listCode: String,
        videoCode: String,
        isChecked: Boolean,
        userId: String = "",
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("input_id", listCode)
            append("video_id", videoCode)
            append("is_checked", isChecked.toString())
            append("user_id", userId)
        }
        return client.submitForm(baseUrl + "save", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun modifyPlaylist(
        listCode: String,
        title: String,
        description: String,
        delete: String?, // 删除 "on"，不删除 null
        csrfToken: String?,
        method: String? = "PUT",
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            append("playlist-title", title)
            append("playlist-description", description)
            if (delete != null) append("playlist-delete", delete)
            if (csrfToken != null) append("_token", csrfToken)
            if (method != null) append("_method", method)
        }
        return client.submitForm(baseUrl + "playlist/" + listCode.encodeURLPathPart(), form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }
}
