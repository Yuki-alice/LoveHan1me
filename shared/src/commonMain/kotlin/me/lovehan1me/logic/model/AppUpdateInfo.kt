package me.lovehan1me.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val updateDescription: String,
    val forceUpdate: Boolean,
)


data class AppUpdateCheckResult(
    val updateInfo: AppUpdateInfo? = null,
    val error: String? = null,
)
