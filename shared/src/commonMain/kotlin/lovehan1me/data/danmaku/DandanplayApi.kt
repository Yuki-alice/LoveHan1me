package lovehan1me.data.danmaku

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.Sha256
import kotlin.io.encoding.Base64

/**
 * 弹弹play 应用凭据。默认来自构建期注入的 [lovehan1me.core.domain.model.DanmakuBuildCredentials]
 * （值在 gitignore 掉的 `local.properties` 里，源码与 git 历史始终只有空串），
 * 用户也可在设置页填自己申请的一对来覆盖。
 */
data class DandanCredentials(
    val appId: String,
    val appSecret: String,
)

/**
 * 弹弹play v2 API 客户端（只读：番剧搜索 → 剧集搜索 → 弹幕拉取）。
 *
 * - 鉴权：`X-AppId / X-Timestamp / X-Signature`，签名 = Base64(SHA256 原始摘要)。
 *   [credentials] 为 null 时**不发**这三个头 —— 那是走用户自建代理的形状，
 *   代理由自己的凭据代签。
 * - 本文件只做 HTTP + JSON，不做匹配决策（见 [DandanplayMatcher]），
 *   不做渲染（见 `feature/danmaku`）。三层解耦。
 */
class DandanplayApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL,
    private val credentials: DandanCredentials? = null,
) {
    companion object {
        const val BASE_URL = "https://api.dandanplay.net"
        const val API_V2 = "/api/v2"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth(apiPath: String) {
        header("Accept", "application/json")
        val creds = credentials ?: return
        val timestamp = currentEpochMillis() / 1000L
        header("X-AppId", creds.appId)
        header("X-Timestamp", timestamp.toString())
        header(
            "X-Signature",
            dandanplaySignature(creds.appId, timestamp, apiPath, creds.appSecret),
        )
    }

    /** 番剧搜索：`GET /search/anime?keyword=`。 */
    suspend fun searchAnime(keyword: String): List<DandanAnime> {
        if (keyword.isBlank()) return emptyList()
        val path = "$API_V2/search/anime"
        val body = client.get(baseUrl + path) {
            applyAuth(path)
            url.parameters.append("keyword", keyword)
        }.bodyAsText()
        val response = json.decodeFromString<DandanSearchResponse>(body)
        response.throwOnError("searchAnime")
        return response.animes
    }

    /**
     * 剧集搜索：`GET /search/episodes?anime=&episode=`。
     *
     * 返回**按番剧分组**：官方把 `episodes` 嵌在每个命中的 anime 下面，
     * 一个关键字可能命中多部同名不同季的番剧，摊平成一维就说不清某集属于谁了。
     */
    suspend fun searchEpisodes(
        animeTitle: String,
        episode: String? = null,
    ): List<DandanSubjectEpisodes> {
        val path = "$API_V2/search/episodes"
        val body = client.get(baseUrl + path) {
            applyAuth(path)
            url.parameters.append("anime", animeTitle)
            if (!episode.isNullOrBlank()) url.parameters.append("episode", episode)
        }.bodyAsText()
        val response = json.decodeFromString<DandanEpisodeSearchResponse>(body)
        response.throwOnError("searchEpisodes")
        return response.animes
    }

    /** 弹幕拉取：`GET /comment/{episodeId}?chConvert=0&withRelated=true`。 */
    suspend fun getComments(episodeId: Long): List<DandanComment> {
        val path = "$API_V2/comment/$episodeId"
        val body = client.get(baseUrl + path) {
            applyAuth(path)
            url.parameters.append("chConvert", "0")
            url.parameters.append("withRelated", "true")
        }.bodyAsText()
        val response = json.decodeFromString<DandanCommentResponse>(body)
        response.throwOnError("getComments")
        return response.comments
    }
}

private fun DandanApiError.throwOnError(call: String) {
    if (!success || errorCode != 0) {
        throw DandanplayException(
            "dandanplay $call 失败：errorCode=$errorCode message=${errorMessage.ifBlank { "unknown" }}",
            errorCode,
        )
    }
}

class DandanplayException(message: String, val errorCode: Int = -1) : Exception(message)

/**
 * 弹弹play 签名（顶层纯函数，不依赖 HttpClient，单测直调）。
 *
 * 官方算法：`base64(sha256(AppId + Timestamp + Path + AppSecret))` —— Base64 吃的是
 * **32 字节原始摘要**，不是那串 64 字符的十六进制文本。两者都是合法 Base64，
 * 服务端只会回 `Invalid Signature`，所以这条由 `DanmakuDataTest` 用固定向量钉死。
 * 另外 Path 只到 `?` 之前、不含域名，时间戳单位为秒。
 */
fun dandanplaySignature(appId: String, timestampSeconds: Long, apiPath: String, appSecret: String): String =
    Base64.encode(Sha256.digest((appId + timestampSeconds.toString() + apiPath + appSecret).encodeToByteArray()))

internal interface DandanApiError {
    val success: Boolean
    val errorCode: Int
    val errorMessage: String
}

@Serializable
data class DandanAnime(
    @SerialName("animeId") val animeId: Int = 0,
    @SerialName("animeTitle") val animeTitle: String = "",
    @SerialName("episodeCount") val episodeCount: Int = 0,
    @SerialName("startDate") val startDate: String = "",
)

@Serializable
private data class DandanSearchResponse(
    @SerialName("errorCode") override val errorCode: Int = 0,
    @SerialName("success") override val success: Boolean = true,
    @SerialName("errorMessage") override val errorMessage: String = "",
    @SerialName("animes") val animes: List<DandanAnime> = emptyList(),
) : DandanApiError

@Serializable
data class DandanEpisode(
    @SerialName("episodeId") val episodeId: Long = 0L,
    @SerialName("episodeTitle") val episodeTitle: String = "",
)

/** `/search/episodes` 的一个命中项：一部番剧 + 它自己的剧集。 */
@Serializable
data class DandanSubjectEpisodes(
    @SerialName("animeId") val animeId: Int = 0,
    @SerialName("animeTitle") val animeTitle: String = "",
    @SerialName("episodes") val episodes: List<DandanEpisode> = emptyList(),
)

/** 形状由 `DanmakuDataTest` 用真实回包钉死（ episodes 嵌在 animes 下面 ）。 */
@Serializable
internal data class DandanEpisodeSearchResponse(
    @SerialName("errorCode") override val errorCode: Int = 0,
    @SerialName("success") override val success: Boolean = true,
    @SerialName("errorMessage") override val errorMessage: String = "",
    @SerialName("animes") val animes: List<DandanSubjectEpisodes> = emptyList(),
) : DandanApiError

/**
 * 弹弹play 评论 `p` 字段：`"播放时间(秒),模式,颜色(十进制),发送者"`，
 * 模式 1=滚动 4=底部 5=顶部（其余按滚动处理）。
 */
@Serializable
data class DandanComment(
    @SerialName("cid") val cid: Long = 0L,
    @SerialName("p") val p: String = "",
    @SerialName("m") val m: String = "",
) {
    fun toItem(): DanmakuItem? {
        if (m.isBlank()) return null
        val parts = p.split(",")
        val timeSeconds = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        if (timeSeconds < 0) return null
        val mode = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val color = parts.getOrNull(2)?.toIntOrNull()?.let { 0xFF000000.toInt() or it }
            ?: 0xFFFFFFFF.toInt()
        val location = when (mode) {
            4 -> DanmakuLocation.BOTTOM
            5 -> DanmakuLocation.TOP
            else -> DanmakuLocation.SCROLL
        }
        return DanmakuItem(
            id = cid,
            playTimeMillis = (timeSeconds * 1000).toLong(),
            text = m,
            color = color,
            location = location,
            isSelf = false,
        )
    }
}

@Serializable
private data class DandanCommentResponse(
    @SerialName("errorCode") override val errorCode: Int = 0,
    @SerialName("success") override val success: Boolean = true,
    @SerialName("errorMessage") override val errorMessage: String = "",
    @SerialName("count") val count: Int = 0,
    @SerialName("comments") val comments: List<DandanComment> = emptyList(),
) : DandanApiError
