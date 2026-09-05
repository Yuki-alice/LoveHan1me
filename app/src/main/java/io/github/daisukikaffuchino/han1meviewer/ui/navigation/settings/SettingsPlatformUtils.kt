@file:Suppress("DEPRECATION")

package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import android.app.Activity
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.action_app_open_by_default_settings_not_support
import io.github.daisukikaffuchino.utils.SonnerToast
import org.jetbrains.compose.resources.getString

// P6d-4：自 :app SettingsRouteUtils.kt 拆分的 Android 专属部分（系统服务/Intent 跳转）。
// 平台无关函数已下沉 shared commonMain 同包 SettingsRouteUtils.kt。

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
