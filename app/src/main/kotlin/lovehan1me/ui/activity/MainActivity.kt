package lovehan1me.ui.activity

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.StartupTrace
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import lovehan1me.data.SettingsRepository
import lovehan1me.R
import lovehan1me.data.logout
import lovehan1me.app.AppViewModelStore
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.app.navigation.main.AccountRoute
import lovehan1me.app.navigation.main.HanimeScreen
import lovehan1me.app.navigation.main.LoginRoute
import lovehan1me.app.navigation.main.TopLevelBackStack
import lovehan1me.app.navigation.main.SearchRoute
import lovehan1me.app.navigation.main.registerArtistSearchNavigator
import lovehan1me.app.navigation.main.VideoRoute
import lovehan1me.app.main.ACTION_TOGGLE_PLAY
import lovehan1me.app.main.MainActivityHost
import lovehan1me.app.main.MainActivityShell
import androidx.activity.ComponentActivity
import lovehan1me.feature.home.homepage.HomePageViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : BaseActivity(), MainActivityHost {

    /**
     * App 级 VM 必须从**进程级**单例取，不能用 `by viewModels()`。
     *
     * 热切换（`SiteSwitcher`）通过换 `ViewModelStoreOwner` 重建 App 级 VM，而
     * `by viewModels()` 绑的是 ComponentActivity 自己的 store —— 它既不认识切换后的
     * 新世代，也和 `App()` 内部 `LocalViewModelStoreOwner` 提供的那个不是同一个。
     * 两边一旦分叉，`mainBackStack` 就会出现"Activity 读到的"与"UI 渲染的"不是同一个，
     * 深层链接 / 返回键会莫名失效。
     *
     * 因此统一走 [AppViewModelStore.homePageViewModel]：`App()` 与这里解析到的是
     * 同一个 owner、同一个 VM 实例。
     */
    val viewModel: HomePageViewModel get() = AppViewModelStore.homePageViewModel()

    override val componentActivity: ComponentActivity get() = this

    override val mainBackStack: TopLevelBackStack<HanimeScreen>
        get() = viewModel.mainBackStack
    private val pendingNavigationRequests = MutableSharedFlow<Intent>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    private var currentVideoHost: VideoPageHost? = null
    private var logoutDialogCloseCurrentPage by mutableStateOf<Boolean?>(null)

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            LogUtil.i("pipmode", "✅ onReceive called with action: ${intent?.action}")
            when (intent?.action) {
                ACTION_TOGGLE_PLAY -> {
                    LogUtil.i("pipmode", "🎬 ACTION_TOGGLE_PLAY triggered")
                    togglePlayPause()
                }
            }
        }
    }

    private fun initData() {
        setHanimeContent {
            MainActivityShell(
                activity = this@MainActivity,
                pendingNavigationRequests = pendingNavigationRequests,
                logoutDialogCloseCurrentPage = logoutDialogCloseCurrentPage,
                onDismissLogout = { logoutDialogCloseCurrentPage = null },
                onConfirmLogout = ::confirmLogout,
            )
        }
        // M5-2：首帧打点 —— onPreDraw 在内容被画到屏幕之前触发，是"用户即将看到画面"的信号，
        // 比 onResume/onCreate 都更贴近"启动完成"的体感。
        // mark 返回 false 表示已记过（Activity 重建/旋转），此时不再重复打汇总。
        window.decorView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    if (StartupTrace.mark("first-frame")) StartupTrace.summary()
                    return true
                }
            },
        )
    }

    override fun beforeSuperOnCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 生物识别/应用锁已全端移除（阶段一决策③）：
            // 启动不再等待解锁，闪屏只按系统默认时序收起。
            installSplashScreen()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // P6d-4F：注册跨平台导航（VideoCardItem「搜索该作者」）
        registerArtistSearchNavigator { query -> mainBackStack.add(SearchRoute(query = query)) }

        initData()
        pendingNavigationRequests.tryEmit(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigationRequests.tryEmit(intent)
    }

    override fun onStart() {
        super.onStart()
        registerPipReceiver()
    }

    private fun registerPipReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAY)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
            LogUtil.i("pipmode", "✅ registerReceiver with RECEIVER_NOT_EXPORTED")
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, filter)
            LogUtil.i("pipmode", "✅ registerReceiver (legacy)")
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipActionReceiver)
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (mainBackStack.removeLast()) {
            true
        } else {
            super.onSupportNavigateUp()
        }
    }

    override fun openLogin() {
        mainBackStack.add(LoginRoute, launchSingleTop = true)
    }

    override fun showLogoutConfirmDialog(closeCurrentPageOnConfirm: Boolean) {
        logoutDialogCloseCurrentPage = closeCurrentPageOnConfirm
    }

    private fun confirmLogout() {
        val closeCurrentPage = logoutDialogCloseCurrentPage ?: return
        logoutDialogCloseCurrentPage = null
        if (closeCurrentPage) {
            mainBackStack.removeLast()
        }
        logoutWithRefresh()
    }

    fun logoutWithRefresh() {
        lifecycleScope.launch {
            logout()
            viewModel.getHomePage()
        }
    }

    override fun showVideoDetailFragment(videoCode: String, fileUri: String?) {
        mainBackStack.add(VideoRoute(videoCode, fileUri))
    }

    fun registerCurrentVideoHost(host: VideoPageHost?) {
        currentVideoHost = host
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val currentFragment = currentVideoHost

        val allowPip = SettingsRepository.current.allowPipMode

        LogUtil.i("pipmode", "enter pip mode?\n$currentFragment\nallowpip:$allowPip\n")

        if (currentFragment?.shouldEnterPip() == true && allowPip) {
            LogUtil.i("pipmode", "enter pip mode")
            currentFragment.enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        val currentFragment = currentVideoHost

        currentFragment?.onPipModeChanged(isInPictureInPictureMode)
    }

    fun togglePlayPause() {
        currentVideoHost?.togglePlayPause()
    }

    // 注：曾在此 `System.loadLibrary("chino")`（46b4dea 随 NDK 源码一起删了加载，
    // 但漏删这一处），导致非 x86_64 真机/模拟器启动即崩（UnsatisfiedLinkError）。
    // 该库已确认不可恢复也不该恢复：其 JNI 符号包名前缀是上游旧包
    // （`io.github.daisukikaffuchino...VideoRouteHostScreenKt_*`），在我方包名下
    // 永远链不上；且全仓无 `external fun` 调用它。删加载即根治，不重建 NDK。
}
