package lovehan1me.ui.player

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import lovehan1me.R
import lovehan1me.Res
import lovehan1me.logic.SettingsRepository
import lovehan1me.reason_for_download_notification
import lovehan1me.ui.activity.MainActivity
import lovehan1me.ui.bridge.VideoPageHost
import lovehan1me.core.util.SonnerToast
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * M3：Android 侧 [VideoPageHost] 实现 + 组合记忆入口。
 *
 * 原 `:app` `VideoRouteHostScreen` 内的窗口操作代码（PiP RemoteAction、常亮锁、
 * requestedOrientation、systemBars、亮度读写、host 注册、通知权限申请）原样归位，
 * 仅 `activity` 改构造参数、`R` 仍为 `:app` 资源。
 */
@Composable
fun rememberAndroidVideoPageHost(
    activity: MainActivity,
    pipToggleDescription: String,
): AndroidVideoPageHost {
    var showNotificationPermissionReason by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showNotificationPermissionReason = true
            // 降级提示：state 位保留（供后续 UI 使用），即时反馈走 Toast（下载本身不受阻）。
            scope.launch {
                SonnerToast.warning(getString(Res.string.reason_for_download_notification))
            }
        }
    }
    val restoreLightSystemBars = when (SettingsRepository.useDarkMode) {
        "always_on" -> false
        "always_off" -> true
        else -> !isSystemInDarkTheme()
    }
    return remember(activity, pipToggleDescription, restoreLightSystemBars) {
        AndroidVideoPageHost(
            activity = activity,
            pipToggleDescription = pipToggleDescription,
            restoreLightSystemBars = restoreLightSystemBars,
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

class AndroidVideoPageHost(
    private val activity: MainActivity,
    private val pipToggleDescription: String,
    private val restoreLightSystemBars: Boolean,
    private val onRequestNotificationPermission: () -> Unit,
) : VideoPageHost {
    private var pipSourceRect: Rect? = null

    fun requestPostNotificationPermission() = onRequestNotificationPermission()

    override fun requestNotificationPermission() = requestPostNotificationPermission()

    override fun showCommentBadge(count: Int) {
        // 纯逻辑由共享 Host 内部对象承担（经 onRegisterPageHost 注册），此处无需处理。
    }
    override fun shouldEnterPip(): Boolean = false
    override fun enterPipMode() {}
    override fun onPipModeChanged(isInPip: Boolean) {}
    override fun togglePlayPause() {}

    override fun enterPipMode(isPlaying: Boolean, toggleDescription: String) {
        val intent = PendingIntent.getBroadcast(
            activity,
            0,
            android.content.Intent(MainActivity.ACTION_TOGGLE_PLAY)
                .setPackage(activity.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        activity.enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(
                    listOf(
                        RemoteAction(
                            Icon.createWithResource(
                                activity,
                                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                            ),
                            toggleDescription,
                            toggleDescription,
                            intent,
                        )
                    )
                )
                .apply { pipSourceRect?.let(::setSourceRectHint) }
                .build()
        )
    }

    override fun refreshPipAction(isPlaying: Boolean, toggleDescription: String) {
        if (!activity.isInPictureInPictureMode) return
        val intent = PendingIntent.getBroadcast(
            activity,
            0,
            android.content.Intent(MainActivity.ACTION_TOGGLE_PLAY)
                .setPackage(activity.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        activity.setPictureInPictureParams(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(
                    listOf(
                        RemoteAction(
                            Icon.createWithResource(
                                activity,
                                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                            ),
                            toggleDescription,
                            toggleDescription,
                            intent,
                        )
                    )
                )
                .build()
        )
    }

    override fun applyFullscreen(fullscreen: Boolean, forceLandscape: Boolean) {
        if (fullscreen) {
            // 竖屏视频非强制时保持竖屏（原 Host 逻辑），其余走传感器横屏。
            activity.requestedOrientation = if (forceLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            val controller =
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.window.statusBarColor = Color.BLACK
            val controller =
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    override fun currentBrightness(): Float {
        val overrideBrightness = activity.window.attributes.screenBrightness
        if (overrideBrightness in 0f..1f) return overrideBrightness
        return runCatching {
            Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            ) / 255f
        }.getOrDefault(0.5f).coerceIn(0.01f, 1f)
    }

    override fun applyBrightness(value: Float) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = value
        }
    }

    override fun setPipSourceRect(rect: ComposeRect?) {
        pipSourceRect = rect?.let {
            Rect(
                it.left.toInt(),
                it.top.toInt(),
                it.right.toInt(),
                it.bottom.toInt(),
            )
        }
    }

    override fun onHostStarted() {
        activity.window.statusBarColor = Color.BLACK
        activity.window.navigationBarColor = Color.TRANSPARENT
        val controller =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        activity.window.isStatusBarContrastEnforced = false
        activity.window.isNavigationBarContrastEnforced = false
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 注：host 注册（registerCurrentVideoHost）由共享 Host 经 onRegisterPageHost
        // 回调完成，注册的是带纯逻辑（badge/PiP 准入/切换）的内部对象，而非本实例。
    }

    override fun onHostStopped() {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
            isAppearanceLightStatusBars = restoreLightSystemBars
            isAppearanceLightNavigationBars = restoreLightSystemBars
        }
    }
}
