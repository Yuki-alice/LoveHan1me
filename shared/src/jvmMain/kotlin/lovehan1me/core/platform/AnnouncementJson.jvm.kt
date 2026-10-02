package lovehan1me.core.platform

import lovehan1me.core.util.decodeFromStringByBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 公告 JSON 与更新 JSON 同桶（COS）同出口要求，故共用 [remoteJsonClient]。
 *
 * 失败语义与 [performUpdateJsonRequest] 一致：**抛异常**而不是返回 null ——
 * 「没拿到」和「拿到了但内容为空」必须能区分，否则仓储会把一次网络故障
 * 当成「服务端撤下了所有公告」而清空缓存。降级由调用侧 `runCatching` 负责。
 */
actual suspend fun performAnnouncementJsonRequest(): String? = withContext(Dispatchers.IO) {
    val ENCODED_ANNOUNCEMENT_URL =
        "aHR0cHM6Ly9obm0tMTI1ODY2NDI3Ni5jb3MuYXAtc2hhbmdoYWkubXlxY2xvdWQuY29tL2Fubm91bmNlbWVudC5qc29u"
    val ENCODED_UPDATE_REFERER = "aG5tdmlld2VydXAuY29t"
    val request = Request.Builder()
        .url(ENCODED_ANNOUNCEMENT_URL.decodeFromStringByBase64())
        .header("Referer", ENCODED_UPDATE_REFERER.decodeFromStringByBase64())
        .get()
        .build()
    remoteJsonClient.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "Announcement fetch failed with HTTP ${response.code}" }
        response.body.string()
    }
}
