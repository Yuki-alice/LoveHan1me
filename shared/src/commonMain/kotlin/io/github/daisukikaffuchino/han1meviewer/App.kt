package io.github.daisukikaffuchino.han1meviewer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
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
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.HomeRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.LoginRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.AccountRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.HanimeScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.MainDrawerDestination
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.SharedMainDrawer
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.DrawerHost
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
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
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
                        SharedMainDrawer(
                            selected = MainDrawerDestination.fromRoute(backStack.topLevelKey),
                            isLoggedIn = isLoggedIn,
                            username = null,
                            onDestinationClick = { destination ->
                                val handled = backStack.navigateDrawerDestination(
                                    destination = destination,
                                    isLoggedIn = isLoggedIn,
                                    onRequireLogin = {
                                        scope.launch {
                                            SonnerToast.warning(getString(Res.string.login_first))
                                        }
                                    },
                                )
                                if (handled) {
                                    scope.launch { drawerState.close() }
                                }
                            },
                            onAccountClick = {
                                scope.launch { drawerState.close() }
                                backStack.add(AccountRoute)
                            },
                            onLoginClick = {
                                scope.launch { drawerState.close() }
                                backStack.add(LoginRoute)
                            },
                        )
                    }
                },
            ) {
                SharedTopNavigation(
                    backStack = backStack,
                    homeViewModel = homeViewModel,
                    showHomeNavigationIcon = true,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    platformScreens = platformScreens,
                )
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
