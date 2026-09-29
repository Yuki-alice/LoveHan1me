package lovehan1me.core.platform

import lovehan1me.core.util.decodeFromStringByBase64
import lovehan1me.data.network.createThirdPartyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 第三方站点档：带 DNS 覆盖与用户代理，不经 ECH 网关（理由见 `createThirdPartyClient`）。
 * 此前是裸 client，配了代理的用户更新检查仍走直连。
 */
private val updateClient by lazy { createThirdPartyClient() }

// P6d-4F：原 :app AppUpdateChecker.requestUpdateJson 照搬
actual suspend fun performUpdateJsonRequest(): String? = withContext(Dispatchers.IO) {
    val ENCODED_UPDATE_URL = "aHR0cHM6Ly9obm0tMTI1ODY2NDI3Ni5jb3MuYXAtc2hhbmdoYWkubXlxY2xvdWQuY29tL3VwZGF0ZS5qc29u"
    val ENCODED_UPDATE_REFERER = "aG5tdmlld2VydXAuY29t"
    val request = Request.Builder()
        .url(ENCODED_UPDATE_URL.decodeFromStringByBase64())
        .header("Referer", ENCODED_UPDATE_REFERER.decodeFromStringByBase64())
        .get()
        .build()
    updateClient.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "Update check failed with HTTP ${response.code}" }
        response.body.string()
    }
}
