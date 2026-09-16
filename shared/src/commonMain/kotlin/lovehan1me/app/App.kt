package lovehan1me.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import lovehan1me.data.SettingsRepository
import lovehan1me.feature.onboarding.OnboardingWizard
import lovehan1me.app.navigation.main.CloudflareRoute
import lovehan1me.app.navigation.main.MainScaffold
import lovehan1me.data.network.CloudflareChallenges
import lovehan1me.app.navigation.main.PlatformScreens
import lovehan1me.app.crash.CRASH_PACKAGE_FILTER
import lovehan1me.app.crash.clearCrashReport
import lovehan1me.app.crash.takePendingCrashReport
import lovehan1me.app.crash.CrashScreen
import lovehan1me.app.navigation.main.VideoRoute
import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.feature.home.homepage.HomePageViewModel
import lovehan1me.ui.theme.HanimeTheme
import lovehan1me.app.sharedViewModel
import lovehan1me.core.util.AppToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.loading
import lovehan1me.ui.component.content.LoadingContent

/**
 * 三端共享的真入口。
 *
 * - 门控：首次启动向导（欢迎 → 使用须知 → 基础设置），完成后才进导航；
 *   老用户（已接受须知）直接进；
 * - 导航：`TopLevelBackStack<HanimeScreen>` + `SharedTopNavigation`；
 * - 外壳：**P2 起为 [MainScaffold]**（Compact 贴底 NavigationBar / Medium+ WideNavigationRail）。
 *   旧的 `PermanentNavigationDrawer` / `ModalNavigationDrawer` 双分支与
 *   `drawerContent` 平台注入槽位一并退役 —— 汉堡与抽屉不再是导航形态的一部分。
 * - 登录成功后经提升的 [HomePageViewModel] 刷新首页（与 `:app` `activity.viewModel`
 *   语义一致）。
 */
/**
 * 三段门控未过时的底衬：主题背景 + 加载指示。
 *
 * 刻意保持"哑"：不碰任何门控状态、不抢焦点、不显示进度百分比 ——
 * 它的唯一职责是别让用户在对话框出现前后看到一片空白/白底。
 */
@Composable
private fun StartupGateBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        LoadingContent(message = stringResource(Res.string.loading))
    }
}

@Composable
fun App(
    onExit: () -> Unit = {},
    platformScreens: PlatformScreens = PlatformScreens(),
    /**
     * 性能探针（桌面 Main.kt 由 HAN1ME_AUTO_VIDEO 注入）：非空时首页就绪后
     * 自动压入 [VideoRoute]，无人工点击即可采集 PlayerTrace 全链路。
     * 正常运行为 null，零影响。
     */
    autoNavigateVideoCode: String? = null,
    /** 探针配套：进视频页后多久退出应用（毫秒），仅 [autoNavigateVideoCode] 非空时生效。 */
    autoNavigateExitAfterMs: Long = 150_000L,
) {
    HanimeTheme {
        // M5-3：上次崩溃残留的报告优先展示（桌面/iOS 崩溃后进程已退出，
        // 只能在下次启动时告知）。Android 走 CrashActivity，通常无残留。
        var crashReport by remember { mutableStateOf(takePendingCrashReport()) }
        if (crashReport != null) {
            val copyText = rememberCopyTextToClipboard()
            CrashScreen(
                crashReport = crashReport ?: "",
                packageName = CRASH_PACKAGE_FILTER,
                onCopyLog = { copyText(crashReport ?: "") },
                onRestartApp = {
                    clearCrashReport()
                    crashReport = null
                },
                onExitApp = {
                    clearCrashReport()
                    onExit()
                },
            )
            return@HanimeTheme
        }
        AppToast.Host()
        val homeViewModel: HomePageViewModel = sharedViewModel(::HomePageViewModel)
        // 复用 ViewModel 持有的回退栈：平台壳（Android intent 导航 / 返回键）与
        // 共享导航必须操作同一实例，否则会出现两套互不感知的栈。
        val backStack = homeViewModel.mainBackStack
        val scope = rememberCoroutineScope()

        LaunchedEffect(homeViewModel) {
            homeViewModel.sessionExpiredMessage.collect { event ->
                event.message?.let(AppToast::error)
                    ?: AppToast.error(getString(event.fallbackResId))
            }
        }

        // CF 挑战统一恢复入口：NetworkRepo 判定挑战页时经总线送达，此处压栈各端
        // 既有验证 UI（桌面 KCEF 弹窗 / iOS WKWebView / Android WebView）。
        // 去重：栈上已有 CF 页不再压（Android 拦截器链路自带开屏，重试失败才到这里）。
        LaunchedEffect(backStack) {
            CloudflareChallenges.requests.collect { challenge ->
                if (backStack.backStack.none { it is CloudflareRoute }) {
                    backStack.add(CloudflareRoute(challenge.url, challenge.host))
                }
            }
        }

        var showOnboarding by remember { mutableStateOf(!SettingsRepository.usageNoticeAccepted) }
        var appAccessGranted by remember {
            mutableStateOf(SettingsRepository.usageNoticeAccepted)
        }

        if (appAccessGranted) {
            // 导航外壳：Bar / Rail 的选择、全屏路由（视频详情 / 设置）摘掉 chrome、
            // 内容宽度的测量与下发（扣除 Rail 占宽）全部收在 MainScaffold 内部。
            MainScaffold(
                backStack = backStack,
                homeViewModel = homeViewModel,
                platformScreens = platformScreens,
            )
        } else {
            // 首次启动向导未完成时，下面叠加向导页。
            // 此前这里**什么都不画** —— 用户看到的是"白窗 + 弹窗浮在半空"，
            // 分不清"应用在启动"还是"界面挂了"。给一层主题化底衬（不改门控语义）。
            StartupGateBackdrop()
        }

        // 性能探针：首页就绪后自动进视频详情页（见 App 参数 KDoc）。
        autoNavigateVideoCode?.let { code ->
            LaunchedEffect(code) {
                delay(4000)
                LogUtil.i("AutoVideoProbe", "进入视频页 $code")
                backStack.add(VideoRoute(code), launchSingleTop = true)
                delay(autoNavigateExitAfterMs)
                onExit()
            }
        }

        if (showOnboarding) {
            OnboardingWizard(
                onFinished = {
                    scope.launch {
                        SettingsRepository.setUsageNoticeAccepted(true)
                        showOnboarding = false
                        appAccessGranted = true
                        homeViewModel.initializeHomePage()
                    }
                },
                onExit = onExit,
            )
        }
    }
}
