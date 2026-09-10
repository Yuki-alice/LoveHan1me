package me.lovehan1me.logic.platform

import me.lovehan1me.utils.decodeFromStringByBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val updateClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

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
