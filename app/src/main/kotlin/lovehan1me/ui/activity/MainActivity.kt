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
import lovehan1me.core.util.LogUtil
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import lovehan1me.core.constant.HanimeConstants.ANIME_URL
import lovehan1me.core.constant.HanimeConstants.HANIME_URL
import lovehan1me.BuildConfig
import lovehan1me.data.SettingsRepository
import lovehan1me.R
import lovehan1me.data.logout
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.app.navigation.main.AccountRoute
import lovehan1me.app.navigation.main.HanimeScreen
import lovehan1me.app.navigation.main.LoginRoute
import lovehan1me.app.navigation.main.TopLevelBackStack
import lovehan1me.app.navigation.main.SearchRoute
import lovehan1me.app.navigation.main.registerArtistSearchNavigator
import lovehan1me.app.navigation.main.VideoRoute
import lovehan1me.app.main.MainActivityShell
import lovehan1me.feature.home.homepage.HomePageViewModel
import lovehan1me.core.util.ActivityManager
import lovehan1me.core.util.isX86_64Device
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    val viewModel by viewModels<HomePageViewModel>()

    val mainBackStack: TopLevelBackStack<HanimeScreen>
        get() = viewModel.mainBackStack
    private val pendingNavigationRequests = MutableSharedFlow<Intent>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    private var currentVideoHost: VideoPageHost? = null
    private var showSiteSwitchConfirm by mutableStateOf(false)
    private var logoutDialogCloseCurrentPage by mutableStateOf<Boolean?>(null)

    companion object {
        const val ACTION_TOGGLE_PLAY = "lovehan1me.ui.activity.ACTION_TOGGLE_PLAY"
    }

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
                showSiteSwitchConfirm = showSiteSwitchConfirm,
                logoutDialogCloseCurrentPage = logoutDialogCloseCurrentPage,
                onSwitchSiteClick = { showSiteSwitchConfirm = true },
                onDismissSiteSwitch = { showSiteSwitchConfirm = false },
                onConfirmSiteSwitch = ::confirmSiteSwitch,
                onDismissLogout = { logoutDialogCloseCurrentPage = null },
                onConfirmLogout = ::confirmLogout,
            )
        }
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

    private fun confirmSiteSwitch() {
        showSiteSwitchConfirm = false
        val currentSite = SettingsRepository.baseUrl
        val avSite = HANIME_URL[3]
        val selectedBaseUrl = SettingsRepository.selectedBaseUrl
        lifecycleScope.launch {
            SettingsRepository.update {
                if (currentSite in ANIME_URL) it.copy(selectedBaseUrl = currentSite, domainName = avSite)
                else it.copy(selectedBaseUrl = selectedBaseUrl, domainName = selectedBaseUrl)
            }
            delay(500)
            ActivityManager.restart(killProcess = true)
        }
    }

    fun openLogin() {
        mainBackStack.add(LoginRoute, launchSingleTop = true)
    }

    fun showLogoutConfirmDialog(closeCurrentPageOnConfirm: Boolean = false) {
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

    fun showVideoDetailFragment(videoCode: String, fileUri: String? = null) {
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

    init {
        if (!(BuildConfig.DEBUG && isX86_64Device)) {
            System.loadLibrary("chino")
        }
    }
}
