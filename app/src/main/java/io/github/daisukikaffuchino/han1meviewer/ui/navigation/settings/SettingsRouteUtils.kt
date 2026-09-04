@file:Suppress("DEPRECATION")

package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import android.app.Activity
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.text.parseAsHtml
import io.github.daisukikaffuchino.han1meviewer.HanimeConstants.HANIME_HOSTNAME
import io.github.daisukikaffuchino.han1meviewer.HanimeConstants.HANIME_URL
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.action_app_open_by_default_settings_not_support
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.cache_usage_summary
import io.github.daisukikaffuchino.han1meviewer.ui.player.PlayerDefaults
import io.github.daisukikaffuchino.utils.formatBytesPerSecond
import io.github.daisukikaffuchino.utils.formatFileSize
import io.github.daisukikaffuchino.utils.SonnerToast
import io.github.daisukikaffuchino.utils.toastText
import org.jetbrains.compose.resources.getString

// P6d-3-C2：context.getString→边界预解析。调用方（remember{} 内 plain builder）
// 无法调 @Composable/suspend，故字符串在 composable 层 stringResource 解析后以 String 传入。

internal fun buildDomainOptions(defaultLabel: String, alternativeLabel: String): List<Pair<String, String>> = listOf(
    "${HANIME_HOSTNAME[0]} ($defaultLabel)" to HANIME_URL[0],
    "${HANIME_HOSTNAME[1]} ($alternativeLabel)" to HANIME_URL[1],
    "${HANIME_HOSTNAME[2]} ($alternativeLabel)" to HANIME_URL[2],
    "${HANIME_HOSTNAME[3]} (av)" to HANIME_URL[3],
)

internal suspend fun generateClearCacheSummary(size: Long): CharSequence {
    return getString(Res.string.cache_usage_summary, size.formatFileSize()).parseAsHtml()
}

internal fun toPrettySensitivityString(
    @IntRange(from = 1, to = 7) value: Int,
    levelNames: List<String>,
    currentTemplate: String,
): String {
    val pretty = levelNames.getOrNull(value - 1) ?: error("Invalid sensitivity value: $value")
    // 模板 "Current Sensitivity: %s"，commonMain 外可用 replace（:app 侧）
    return currentTemplate.replace("%s", pretty)
}

internal fun toPrettyCountdownRemindString(
    @IntRange(from = 5, to = 30) value: Int,
    remindTemplate: String,
    defaultLabel: String,
): String {
    return buildString {
        // 模板 "Will remind %d seconds before countdown"
        append(remindTemplate.replace("%d", value.toString()))
        if (value == PlayerDefaults.DEFAULT_COUNTDOWN_SECONDS) {
            append(" ($defaultLabel)")
        }
    }
}

internal fun Long.toDownloadSpeedPrettyString(noLimitText: String): String {
    return if (this == 0L) {
        noLimitText
    } else {
        formatBytesPerSecond()
    }
}

internal fun toDownloadCountLimitPrettyString(noLimitText: String, value: Int): String {
    return if (value == 0) noLimitText else value.toString()
}

internal fun isDeviceSecureCompat(context: Context): Boolean {
    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    return km.isDeviceSecure
}

internal fun isPipPermissionGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
        Process.myUid(),
        context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

internal fun openPipPermissionSettings(context: Context) {
    val intent = Intent(
        "android.settings.PICTURE_IN_PICTURE_SETTINGS",
        "package:${context.packageName}".toUri()
    )
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

@RequiresApi(Build.VERSION_CODES.S)
internal suspend fun openApplyDeepLinksSettings(context: Context, activity: Activity) {
    try {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
            addCategory(Intent.CATEGORY_DEFAULT)
            data = "package:${context.packageName}".toUri()
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        activity.startActivity(intent)
    } catch (e: Exception) {
        SonnerToast.warning(getString(Res.string.action_app_open_by_default_settings_not_support))
        e.printStackTrace()
    }
}
