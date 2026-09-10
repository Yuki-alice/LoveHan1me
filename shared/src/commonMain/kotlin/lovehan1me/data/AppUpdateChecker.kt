package lovehan1me.data

import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import lovehan1me.Res
import lovehan1me.core.domain.model.Announcement
import lovehan1me.core.domain.model.AppUpdateInfo
import lovehan1me.update_announcement_title
import lovehan1me.core.util.decodeFromStringByBase64
import kotlinx.coroutines.withContext
import lovehan1me.core.platform.performUpdateJsonRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString


data class AppUpdateCheckResult(
    val updateInfo: AppUpdateInfo? = null,
    val announcement: Announcement? = null,
)

sealed interface AppUpdateState {
    data object Checking : AppUpdateState
    data object NoUpdate : AppUpdateState
    data class Available(val info: AppUpdateInfo) : AppUpdateState
}

@Serializable
private data class AppUpdatePayload(
    val versionName: String? = null,
    val versionCode: Int = 0,
    val downloadUrl: String? = null,
    val updateDescription: String = "",
    val forceUpdate: Boolean = false,
    val isShowAnnouncement: Boolean = false,
    val announcement: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    private const val ENCODED_UPDATE_URL =
        "aHR0cHM6Ly9obm0tMTI1ODY2NDI3Ni5jb3MuYXAtc2hhbmdoYWkubXlxY2xvdWQuY29tL3VwZGF0ZS5qc29u"
    private const val ENCODED_UPDATE_REFERER = "aG5tdmlld2VydXAuY29t"
    private const val CURRENT_VERSION_CODE = 260805

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    suspend fun checkForUpdate(): AppUpdateCheckResult {
        val cachedJson = SettingsRepository.current.cachedUpdateJson

        val responseJson = runCatching { requestUpdateJson() }
            .onFailure { LogUtil.e(TAG, "Failed to check for updates", it) }
            .getOrNull()

        if (responseJson != null) SettingsRepository.setCachedUpdateJson(responseJson)

        val jsonToUse = responseJson ?: cachedJson
        if (responseJson == null) {
            jsonToUse?.let { LogUtil.d(TAG, "Using stale update JSON: $it") }
        }
        return jsonToUse.toUpdateCheckResult()
    }

    suspend fun ignoreUpdate(versionCode: Int) = SettingsRepository.setIgnoredVersionCode(versionCode)

    // P6d-4F：网络请求经 expect 下沉（jvmMain=OkHttp 实现，桌面/iOS 返回 null 走缓存降级）
    private suspend fun requestUpdateJson(): String? = performUpdateJsonRequest()

    private suspend fun String?.toUpdateCheckResult(): AppUpdateCheckResult {
        if (this.isNullOrBlank()) return AppUpdateCheckResult()
        return runCatching {
            val payload = jsonParser.decodeFromString<AppUpdatePayload>(this)
            AppUpdateCheckResult(
                updateInfo = payload.toAvailableUpdateOrNull(),
                announcement = payload.toAnnouncementOrNull(),
            )
        }.onFailure {
            LogUtil.e(TAG, "Invalid update JSON", it)
        }.getOrDefault(AppUpdateCheckResult())
    }

    private fun AppUpdatePayload.toAvailableUpdateOrNull(): AppUpdateInfo? {
        val versionName = versionName?.trim().orEmpty()
        val downloadUrl = downloadUrl?.trim().orEmpty()
        if (versionName.isBlank() || versionCode <= 0 || downloadUrl.isBlank()) return null
        if (runCatching { io.ktor.http.Url(downloadUrl) }.isFailure) {
            LogUtil.e(TAG, "downloadUrl is invalid")
            return null
        }

        val currentVersionCode = CURRENT_VERSION_CODE
        val ignoredVersionCode = SettingsRepository.current.ignoredVersionCode
        return AppUpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            downloadUrl = downloadUrl,
            updateDescription = updateDescription,
            forceUpdate = forceUpdate,
        ).takeIf {
            it.versionCode > currentVersionCode &&
                (it.forceUpdate || it.versionCode != ignoredVersionCode)
        }
    }

    private suspend fun AppUpdatePayload.toAnnouncementOrNull(): Announcement? {
        val content = announcement.trim()
        if (!isShowAnnouncement || content.isBlank()) return null
        return Announcement(
            title = getString(Res.string.update_announcement_title),
            content = content,
            isActive = true,
        )
    }
}
