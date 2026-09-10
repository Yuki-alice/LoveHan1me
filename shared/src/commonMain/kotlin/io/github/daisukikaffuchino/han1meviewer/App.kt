package io.github.daisukikaffuchino.han1meviewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.ui.component.UsageNoticeDialog
import io.github.daisukikaffuchino.han1meviewer.ui.adaptive.ProvideContentWidth
import io.github.daisukikaffuchino.han1meviewer.ui.adaptive.WindowWidthSizeClass
import io.github.daisukikaffuchino.han1meviewer.ui.adaptive.rememberWindowWidthSizeClass
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.HomeRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.LoginRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.AccountRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.HanimeScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.MainDrawerDestination
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.CloudflareRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.DrawerHost
import io.github.daisukikaffuchino.han1meviewer.logic.network.CloudflareChallenges
import io.github.daisukikaffuchino.han1meviewer.ui.screen.main.MainDrawerContent
import io.github.daisukikaffuchino.han1meviewer.logic.state.PageState
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.PlatformScreens
import io.github.daisukikaffuchino.han1meviewer.ui.crash.CRASH_PACKAGE_FILTER
import io.github.daisukikaffuchino.han1meviewer.ui.crash.clearCrashReport
import io.github.daisukikaffuchino.han1meviewer.ui.crash.takePendingCrashReport
import io.github.daisukikaffuchino.han1meviewer.ui.screen.crash.CrashScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.SharedTopNavigation
import io.github.daisukikaffuchino.utils.rememberCopyTextToClipboard
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.TopLevelBackStack
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.navigateDrawerDestination
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.HomePageViewModel
import io.github.daisukikaffuchino.han1meviewer.ui.screen.main.AppSourceDialog
import io.github.daisukikaffuchino.han1meviewer.ui.theme.HanimeTheme
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.sharedViewModel
import io.github.daisukikaffuchino.utils.SonnerToast
import kotlinx.coroutines.launch

/**
 * M2：三端共享的真入口（M1 骨架 + 首页 → 全导航）。
 *
 * - 门控：使用须知 → 来源确认 → 非法来源警告（三段与 `:app` `MainActivityContent`
 *   同语义；此前桌面/iOS 的自动置位已移除）；
 * - 导航：`TopLevelBackStack<HanimeScreen>` + [SharedTopNavigation]（30+ entry，
 *   与 `:app` `TopNavigation` 同构）；
 * - 抽屉：[SharedMainDrawer]（最小版；头像/站点切换等富头留 `:app`）；
 * - 登录成功后经提升的 [HomePageViewModel] 刷新首页（与 `:app` `activity.viewModel`
 *   语义一致）。
 */
@Composable
fun App(
    onExit: () -> Unit = {},
    platformScreens: PlatformScreens = PlatformScreens(),
    drawerContent: (@Composable (DrawerHost) -> Unit)? = null,
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
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val isLoggedIn by SettingsRepository.loginStateFlow.collectAsStateWithLifecycle()

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
            // 宽屏常驻抽屉（对齐参考 MainActivityContent:124 的横屏常驻规则；桌面无
            // orientation 语义，改用宽度分档实现——阈值统一收敛到 ui/adaptive/WindowSize，
            // 不再散落魔数；窄屏保持 Modal）。与参考的差异：不限首页路由，宽窗下 chrome 全程稳定。
            // 抽屉内容维持平台差异（手机富头 drawerContent / 桌面简版 SharedMainDrawer）。
            val usePermanentDrawer =
                rememberWindowWidthSizeClass() >= WindowWidthSizeClass.Expanded
            LaunchedEffect(usePermanentDrawer) {
                if (usePermanentDrawer) drawerState.close()
            }
            // 抽屉**内容**（不含 sheet 外壳）。外壳由本函数按宽度分档统一提供——
            // 平台注入的 drawerContent 只负责内容、不得自建 sheet。
            // 缘由：[MainDrawerContent] 是裸 Column，自身没有背景；此前共享默认分支
            // （桌面 / iOS）直接调用它而没有包 sheet，导致抽屉全透明、页面内容透过抽屉
            // 显示（Android 侧因 :app 自己包了 ModalDrawerSheet 才看起来正常）。
            val drawerInnerContent: @Composable () -> Unit = {
                if (drawerContent != null) {
                    drawerContent(
                        DrawerHost(
                            backStack = backStack,
                            homeViewModel = homeViewModel,
                            isLoggedIn = isLoggedIn,
                            drawerState = drawerState,
                            onDrawerItemSelected = { destination ->
                                backStack.navigateDrawerDestination(
                                    destination = destination,
                                    isLoggedIn = isLoggedIn,
                                    onRequireLogin = {
                                        scope.launch {
                                            SonnerToast.warning(getString(Res.string.login_first))
                                        }
                                    },
                                )
                            },
                            onOpenAccount = { backStack.add(AccountRoute) },
                            onRequireLogin = {
                                scope.launch {
                                    SonnerToast.warning(getString(Res.string.login_first))
                                }
                            },
                        )
                    )
                } else {
                    // 无平台注入（桌面/iOS）时同样用富内容：与手机同一套，仅
                    // onAvatarLongClick（登出）/onSwitchSiteClick（切站）传 null
                    // （Android 专属能力；切站列隐藏，长按无动作）。
                    val homeState by homeViewModel.homePageFlow.collectAsStateWithLifecycle()
                    val checkInEnabled by SettingsRepository.checkInEnabledFlow.collectAsStateWithLifecycle()
                    MainDrawerContent(
                        selectedDestination = MainDrawerDestination.fromRoute(backStack.topLevelKey),
                        avatarUrl = (homeState as? PageState.Success)?.info?.page?.avatarUrl,
                        username = (homeState as? PageState.Success)?.info?.page?.username,
                        isLoggedIn = isLoggedIn,
                        isLoading = isLoggedIn && homeState is PageState.Loading,
                        currentSite = SettingsRepository.baseUrl,
                        checkInEnabled = checkInEnabled,
                        onAvatarClick = {
                            scope.launch { drawerState.close() }
                            if (isLoggedIn) backStack.add(AccountRoute)
                            else backStack.add(LoginRoute)
                        },
                        onAvatarLongClick = null,
                        onSwitchSiteClick = null,
                        onDrawerItemSelected = { destination ->
                            backStack.navigateDrawerDestination(
                                destination = destination,
                                isLoggedIn = isLoggedIn,
                                onRequireLogin = {
                                    scope.launch {
                                        SonnerToast.warning(getString(Res.string.login_first))
                                    }
                                },
                            )
                        },
                    )
                }
            }
            if (usePermanentDrawer) {
                // 常驻抽屉占宽约 360dp：在 content 槽内测量真实内容区宽度并下发，
                // 供网格列数 / 卡片密度按**实际可用宽度**分档（此前读整窗宽会超算列数，
                // 840dp 窗口下内容区仅约 480dp 却按 840dp 算，卡片被压扁）。
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            drawerInnerContent()
                        }
                    },
                ) {
                    ProvideContentWidth {
                        SharedTopNavigation(
                            backStack = backStack,
                            homeViewModel = homeViewModel,
                            showHomeNavigationIcon = false,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            platformScreens = platformScreens,
                        )
                    }
                }
            } else {
                // 模态抽屉悬浮覆盖、不侵占内容宽度，故此处内容宽度≈整窗宽度。
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            drawerInnerContent()
                        }
                    },
                ) {
                    ProvideContentWidth {
                        SharedTopNavigation(
                            backStack = backStack,
                            homeViewModel = homeViewModel,
                            showHomeNavigationIcon = true,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            platformScreens = platformScreens,
                        )
                    }
                }
            }
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
