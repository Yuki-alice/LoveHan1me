package lovehan1me.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import lovehan1me.data.SettingsRepository
import lovehan1me.ui.component.UsageNoticeDialog
import lovehan1me.ui.component.HapticTextButton as TextButton
import lovehan1me.app.navigation.main.CloudflareRoute
import lovehan1me.app.navigation.main.MainScaffold
import lovehan1me.data.network.CloudflareChallenges
import lovehan1me.app.navigation.main.PlatformScreens
import lovehan1me.app.crash.CRASH_PACKAGE_FILTER
import lovehan1me.app.crash.clearCrashReport
import lovehan1me.app.crash.takePendingCrashReport
import lovehan1me.app.crash.CrashScreen
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.feature.home.homepage.HomePageViewModel
import lovehan1me.app.main.AppSourceDialog
import lovehan1me.ui.theme.HanimeTheme
import lovehan1me.app.sharedViewModel
import lovehan1me.core.util.SonnerToast
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.app_source_illegal_message
import lovehan1me.app_source_illegal_title
import lovehan1me.app_source_repository_link
import lovehan1me.app_source_verify

/**
 * M2：三端共享的真入口（M1 骨架 + 首页 → 全导航）。
 *
 * - 门控：使用须知 → 来源确认 → 非法来源警告（三段与 `:app` `MainActivityContent`
 *   同语义；此前桌面/iOS 的自动置位已移除）；
 * - 导航：`TopLevelBackStack<HanimeScreen>` + `SharedTopNavigation`（35 个 entry，
 *   与 `:app` `TopNavigation` 同构）；
 * - 外壳：**P2 起为 [MainScaffold]**（Compact 贴底 NavigationBar / Medium+ WideNavigationRail）。
 *   旧的 `PermanentNavigationDrawer` / `ModalNavigationDrawer` 双分支与
 *   `drawerContent` 平台注入槽位一并退役 —— 汉堡与抽屉不再是导航形态的一部分。
 * - 登录成功后经提升的 [HomePageViewModel] 刷新首页（与 `:app` `activity.viewModel`
 *   语义一致）。
 */
@Composable
fun App(
    onExit: () -> Unit = {},
    platformScreens: PlatformScreens = PlatformScreens(),
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
        SonnerToast.Host()
        val homeViewModel: HomePageViewModel = sharedViewModel(::HomePageViewModel)
        // 复用 ViewModel 持有的回退栈：平台壳（Android intent 导航 / 返回键）与
        // 共享导航必须操作同一实例，否则会出现两套互不感知的栈。
        val backStack = homeViewModel.mainBackStack
        val scope = rememberCoroutineScope()

        LaunchedEffect(homeViewModel) {
            homeViewModel.sessionExpiredMessage.collect { event ->
                event.message?.let(SonnerToast::error)
                    ?: SonnerToast.error(getString(event.fallbackResId))
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

        var showUsageNotice by remember { mutableStateOf(!SettingsRepository.usageNoticeAccepted) }
        var showSourceDialog by remember {
            mutableStateOf(
                SettingsRepository.usageNoticeAccepted &&
                    !SettingsRepository.usageSourceVerified &&
                    !SettingsRepository.usageSourcePending,
            )
        }
        var showSourceWarning by rememberSaveable {
            mutableStateOf(
                SettingsRepository.usageNoticeAccepted &&
                    !SettingsRepository.usageSourceVerified &&
                    SettingsRepository.usageSourcePending,
            )
        }
        var sourceLink by rememberSaveable { mutableStateOf("") }
        var appAccessGranted by remember {
            mutableStateOf(SettingsRepository.usageNoticeAccepted && SettingsRepository.usageSourceVerified)
        }

        if (appAccessGranted) {
            // 导航外壳：Bar / Rail 的选择、全屏路由（视频详情 / 设置）摘掉 chrome、
            // 内容宽度的测量与下发（扣除 Rail 占宽）全部收在 MainScaffold 内部。
            MainScaffold(
                backStack = backStack,
                homeViewModel = homeViewModel,
                platformScreens = platformScreens,
            )
        }

        UsageNoticeDialog(
            visible = showUsageNotice,
            onAccepted = {
                scope.launch {
                    SettingsRepository.setUsageNoticeAccepted(true)
                    showUsageNotice = false
                    if (SettingsRepository.usageSourceVerified) {
                        appAccessGranted = true
                        homeViewModel.initializeHomePage()
                    } else if (SettingsRepository.usageSourcePending) {
                        showSourceWarning = true
                    } else {
                        showSourceDialog = true
                    }
                }
            },
            onDeclined = { onExit() },
        )
        AppSourceDialog(
            visible = showSourceDialog,
            onSelect = { source ->
                if (source.equals("github", ignoreCase = true)) {
                    scope.launch {
                        SettingsRepository.update {
                            it.copy(
                                usageSourceVerified = true,
                                usageSourcePending = false
                            )
                        }
                        showSourceDialog = false
                        appAccessGranted = true
                        homeViewModel.initializeHomePage()
                    }
                } else {
                    scope.launch {
                        SettingsRepository.setUsageSourcePending(true)
                        showSourceDialog = false
                        sourceLink = ""
                        showSourceWarning = true
                    }
                }
            },
        )
        if (showSourceWarning) {
            val expectedRepository = "https://github.com/daisukiKaffuChino/Han1meViewer"
            val linkValid = sourceLink.trim().equals(expectedRepository, ignoreCase = true)
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(Res.string.app_source_illegal_title)) },
                text = {
                    Column {
                        Text(stringResource(Res.string.app_source_illegal_message))
                        OutlinedTextField(
                            value = sourceLink,
                            onValueChange = { sourceLink = it },
                            label = { Text(stringResource(Res.string.app_source_repository_link)) },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = linkValid,
                        onClick = {
                            scope.launch {
                                SettingsRepository.update {
                                    it.copy(
                                        usageSourceVerified = true,
                                        usageSourcePending = false
                                    )
                                }
                                showSourceWarning = false
                                appAccessGranted = true
                                homeViewModel.initializeHomePage()
                            }
                        },
                    ) { Text(stringResource(Res.string.app_source_verify)) }
                },
            )
        }
    }
}
