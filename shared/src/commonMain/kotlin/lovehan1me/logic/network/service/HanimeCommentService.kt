package lovehan1me.logic.network.service

import lovehan1me.HANIME_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.encodeURLPathPart

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/09/19 019 17:44
 *
 * P3：Retrofit interface → Ktor 实现，方法/参数名与 @Field/@Query 键名保持原样。
 */
class HanimeCommentService(
    private val client: HttpClient,
    private val baseUrl: String = HANIME_BASE_URL,
) {

    suspend fun getComments(
        type: String, // 類似 "video", "preview"
        code: String,
    ): HttpResponse = client.get(baseUrl + "loadComment") {
        url {
            parameters.append("type", type)
            parameters.append("id", code)
        }
    }

    suspend fun getCommentReply(
        commentId: String,
    ): HttpResponse = client.get(baseUrl + "loadReplies") {
        url { parameters.append("id", commentId) }
    }

    suspend fun postComment(
        csrfToken: String?,
        currentUserId: String,
        type: String, // 類似 "video", "preview"
        targetUserId: String,
        text: String,
        count: Int = 1, // 感觉没什么用，仅前端用
        isPolitical: Int = 0, // 感觉没什么用，仅前端用
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("comment-user-id", currentUserId)
            append("comment-type", type)
            append("comment-foreign-id", targetUserId)
            append("comment-text", text)
            append("comment-count", count.toString())
            append("comment-is-political", isPolitical.toString())
        }
        return client.submitForm(baseUrl + "createComment", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun postCommentReply(
        csrfToken: String?,
        replyCommentId: String,
        text: String,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("reply-comment-id", replyCommentId)
            append("reply-comment-text", text)
        }
        return client.submitForm(baseUrl + "replyComment", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun likeComment(
        csrfToken: String?,
        foreignType: String,
        foreignId: String?,
        isPositive: Int, // 你選擇的是讚還是踩，1是讚，0是踩
        likeUserId: String?,
        commentLikesCount: Int,
        commentLikesSum: Int,
        likeCommentStatus: Int, // 你之前有沒有點過讚，1是0否
        unlikeCommentStatus: Int, // 你之前有沒有點過踩，1是0否
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("foreign_type", foreignType)
            if (foreignId != null) append("foreign_id", foreignId)
            append("is_positive", isPositive.toString())
            if (likeUserId != null) append("comment-like-user-id", likeUserId)
            append("comment-likes-count", commentLikesCount.toString())
            append("comment-likes-sum", commentLikesSum.toString())
            append("like-comment-status", likeCommentStatus.toString())
            append("unlike-comment-status", unlikeCommentStatus.toString())
        }
        return client.submitForm(baseUrl + "commentLike", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }

    suspend fun submitReport(
        userId: String?,
        csrfToken: String?,
        redirectUrl: String,
        reportableId: String?,
        reportableType: String?,
        reason: String,
        csrfToken_1: String? = csrfToken,
    ): HttpResponse {
        val uid = requireNotNull(userId) { "userId must not be null" }
        val form = Parameters.build {
            if (csrfToken != null) append("_token", csrfToken)
            append("redirect-url", redirectUrl)
            if (reportableId != null) append("reportable-id", reportableId)
            if (reportableType != null) append("reportable-type", reportableType)
            append("reason", reason)
        }
        return client.submitForm(baseUrl + "user/" + uid.encodeURLPathPart() + "/report", form) {
            if (csrfToken_1 != null) header("X-CSRF-TOKEN", csrfToken_1)
        }
    }
}
