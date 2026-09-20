package lovehan1me

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import lovehan1me.data.SettingsRepository
import lovehan1me.data.datastore.DataStoreManager
// DataStoreManager 已下沉共享层，Android 的 Context 初始化入口是 androidMain 的扩展函数
import lovehan1me.data.datastore.initialize
import lovehan1me.core.platform.AndroidDownloadWorkController
import lovehan1me.core.platform.AndroidVideoCacheStore
import lovehan1me.core.platform.setDownloadWorkControllerProvider
import lovehan1me.core.platform.setVideoCacheStoreProvider
import lovehan1me.data.network.CloudflareVerificationCoordinator
import lovehan1me.data.network.HanimeProxySelector
import lovehan1me.ui.activity.MainActivity
import lovehan1me.app.crash.CrashHandler
import lovehan1me.core.util.AnimeShaders
import lovehan1me.core.util.AppLanguageManager
import lovehan1me.core.platform.CurrentActivityHolder
import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.StartupTrace
import lovehan1me.core.util.setApplicationContext
import `is`.xyz.mpv.MPVLib
import java.net.ProxySelector

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/08 008 17:32
 */
class HanimeApplication : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val TAG = "HanimeApplication"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        setApplicationContext(this)
        // LogUtil 已下沉共享层（commonMain 拿不到 BuildConfig），在此恢复原来的 DEBUG 开关语义
        LogUtil.enabled = BuildConfig.DEBUG
    }

    override fun onCreate() {
        super.onCreate()
        // M5-2：冷启动埋点起点。放在**最前面** —— 下面每一步（DataStore/设置/语言/通知渠道/
        // MPV 初始化）都算启动耗时，而那正是用户感受到的部分。
        StartupTrace.begin("android:Application")
        // 与上面互补的一条对照：**进程起点 → onCreate** 的耗时（类加载、ContentProvider
        // 初始化都在这一段里，它发生在 begin 之前，故单独打一条而不是塞进段落表）。
        runCatching {
            StartupTrace.logExternal(
                "进程启动→onCreate",
                android.os.SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis(),
            )
        }
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext))
        DataStoreManager.initialize(this)
        StartupTrace.mark("datastore")
        // P6a-F：平台件 provider 注册（实现依赖 :app 的 HanimeCacheManager/WorkManager）
        setVideoCacheStoreProvider { AndroidVideoCacheStore }
        setDownloadWorkControllerProvider { AndroidDownloadWorkController }
        SettingsRepository.install(DataStoreManager)
        StartupTrace.mark("settings")
        AppLanguageManager.applyStoredLanguage(this)
        StartupTrace.mark("language")
        registerActivityLifecycleCallbacks(this)
        ProxySelector.setDefault(HanimeProxySelector())
        HanimeProxySelector.rebuildNetwork()
        // M2：CF 验证的协调器与验证页都已下沉 shared/androidMain，:app 只交出
        // 「哪个 Activity 承载验证页」这一点壳信息（决策 #9 划给壳的 Activity 能力）。
        CloudflareVerificationCoordinator.verificationActivityClass = MainActivity::class.java
        initNotificationChannel()
        MPVLib.create(applicationContext)
        MPVLib.init()
        StartupTrace.mark("mpv")

        if (AnimeShaders.copyCertAssets(applicationContext) <= 0) {
            LogUtil.w(TAG, "cert 复制失败")
        }
    }

    /**
     * 只保留下载前台服务必需的渠道。
     * 阶段一决策⑥：应用更新渠道原先「创建了但从未发过通知」，属死代码，已删。
     */
    private fun initNotificationChannel() {
        val nm = NotificationManagerCompat.from(this)
        val hanimeDownloadChannel = NotificationChannelCompat.Builder(
            DOWNLOAD_NOTIFICATION_CHANNEL,
            NotificationManagerCompat.IMPORTANCE_LOW
        ).setName("Hanime Download").build()
        nm.createNotificationChannel(hanimeDownloadChannel)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        CurrentActivityHolder.set(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
