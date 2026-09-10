package me.lovehan1me

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.logic.datastore.DataStoreManager
// DataStoreManager 已下沉共享层，Android 的 Context 初始化入口是 androidMain 的扩展函数
import me.lovehan1me.logic.datastore.initialize
import me.lovehan1me.logic.platform.AndroidDownloadWorkController
import me.lovehan1me.logic.platform.AndroidVideoCacheStore
import me.lovehan1me.logic.platform.setDownloadWorkControllerProvider
import me.lovehan1me.logic.platform.setVideoCacheStoreProvider
import me.lovehan1me.logic.network.CloudflareVerificationCoordinator
import me.lovehan1me.logic.network.CloudflareVerifier
import me.lovehan1me.logic.network.HProxySelector
import me.lovehan1me.ui.crash.CrashHandler
import me.lovehan1me.util.AnimeShaders
import me.lovehan1me.util.AppLanguageManager
import me.lovehan1me.utils.ActivityManager
import me.lovehan1me.utils.LogUtil
import me.lovehan1me.utils.applicationContext as globalApplicationContext
import `is`.xyz.mpv.MPVLib
import java.lang.ref.WeakReference
import java.net.ProxySelector

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 17:32
 */
class HanimeApplication : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val TAG = "HanimeApplication"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        globalApplicationContext = this
        // LogUtil 已下沉共享层（commonMain 拿不到 BuildConfig），在此恢复原来的 DEBUG 开关语义
        LogUtil.enabled = BuildConfig.DEBUG
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext))
        DataStoreManager.initialize(this)
        // P6a-F：平台件 provider 注册（实现依赖 :app 的 HCacheManager/WorkManager）
        setVideoCacheStoreProvider { AndroidVideoCacheStore }
        setDownloadWorkControllerProvider { AndroidDownloadWorkController }
        SettingsRepository.install(DataStoreManager)
        AppLanguageManager.applyStoredLanguage(this)
        registerActivityLifecycleCallbacks(this)
        ProxySelector.setDefault(HProxySelector())
        HProxySelector.rebuildNetwork()
        // Cloudflare 拦截器已下沉 shared androidMain，验证动作经此回调回到 :app 的 Coordinator
        CloudflareVerifier.launcher = { ctx, url -> CloudflareVerificationCoordinator.verify(ctx, url) }
        initNotificationChannel()
        MPVLib.create(applicationContext)
        MPVLib.init()

        if (AnimeShaders.copyShaderAssets(applicationContext) <= 0) {
            LogUtil.w(TAG, "Shader 复制失败")
        }
        if (AnimeShaders.copyCertAssets(applicationContext) <= 0) {
            LogUtil.w(TAG, "cert 复制失败")
        }
        val selected = SettingsRepository.fakeLauncherIcon
        switchLauncher(selected)
    }

    private fun initNotificationChannel() {
        val nm = NotificationManagerCompat.from(this)

        val hanimeDownloadChannel = NotificationChannelCompat.Builder(
            DOWNLOAD_NOTIFICATION_CHANNEL,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName("Hanime Download").build()
        nm.createNotificationChannel(hanimeDownloadChannel)

        val appUpdateChannel = NotificationChannelCompat.Builder(
            UPDATE_NOTIFICATION_CHANNEL,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName("App Update").build()
        nm.createNotificationChannel(appUpdateChannel)
    }
    fun switchLauncher(alias: String) {
        val pm = packageManager

        val allAliases = listOf(
            "me.lovehan1me.LauncherAliasDefault",
            "me.lovehan1me.LauncherFakeCalc",
            "me.lovehan1me.LauncherFakeCornhub",
            "me.lovehan1me.LauncherFakeXxt"
        )

        allAliases.forEach { a ->
            val state = if (a == alias)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            pm.setComponentEnabledSetting(
                ComponentName(this, a),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        ActivityManager.currentActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
