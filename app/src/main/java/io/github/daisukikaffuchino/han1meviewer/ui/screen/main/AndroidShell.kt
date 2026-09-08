package io.github.daisukikaffuchino.han1meviewer.ui.screen.main

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.daisukikaffuchino.han1meviewer.App
import io.github.daisukikaffuchino.han1meviewer.HCacheManager
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.confirm_switch_site
import io.github.daisukikaffuchino.han1meviewer.detect_ha1_related_link_in_clipboard
import io.github.daisukikaffuchino.han1meviewer.enter
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.state.PageState
import io.github.daisukikaffuchino.han1meviewer.no
import io.github.daisukikaffuchino.han1meviewer.save_failed_message
import io.github.daisukikaffuchino.han1meviewer.save_failed_title
import io.github.daisukikaffuchino.han1meviewer.sure
import io.github.daisukikaffuchino.han1meviewer.sure_to_logout
import io.github.daisukikaffuchino.han1meviewer.ui.activity.MainActivity
import io.github.daisukikaffuchino.han1meviewer.ui.component.ConfirmDialog
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.AvatarCropRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.CloudflareRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.DrawerHost
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.LoginRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.MainDrawerDestination
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.ManualCookiesRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.PlatformScreens
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.CloudflareRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.DownloadRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.LoginRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.VideoRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.handleMainIntent
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.DownloadSettingsRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.NetworkDownloadSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.SettingsDestinationSpec
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.SettingsScaffold
import io.github.daisukikaffuchino.han1meviewer.ui.screen.account.AccountScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.account.AvatarCropScreen
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.UserAccountViewModel
import io.github.daisukikaffuchino.han1meviewer.understood
import io.github.daisukikaffuchino.han1meviewer.videoUrlRegex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * M4：Android 壳的 Compose 装配（替代原 `MainActivityContent`）。
 *
 * 原 `MainActivityContent` 同时承担三件事：
 * 1. 三段门控（使用须知 / 来源确认 / 非法来源警告）→ 已由共享 `App()` 承担；
 * 2. 导航装配（35 路由）→ 已由 `SharedTopNavigation` 承担，路由表与之一致；
 * 3. Android 专属覆盖层（应用锁遮罩 / 剪贴板检测 / intent 导航 / 站点切换与登出对话框）。
 *
 * 这里只保留第 3 件，并通过 [PlatformScreens] 把 6 个依赖 Android 硬能力的页面
 * （WebView 登录、CF 验证、头像 picker + cropper、SAF 下载设置、WorkManager 下载）
 * 注入共享导航，从而在功能零损失的前提下消除导航双轨。
 */
@Composable
fun MainActivityShell(
    activity: MainActivity,
    pendingNavigationRequests: Flow<Intent>,
    showAuthGuard: Boolean,
    showSiteSwitchConfirm: Boolean,
    logoutDialogCloseCurrentPage: Boolean?,
    onSwitchSiteClick: () -> Unit,
    onDismissSiteSwitch: () -> Unit,
    onConfirmSiteSwitch: () -> Unit,
    onDismissLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        App(
            platformScreens = platformScreens(activity),
            drawerContent = { host ->
                AndroidDrawer(
                    host = host,
                    onSwitchSiteClick = onSwitchSiteClick,
                    onLogoutClick = { activity.showLogoutConfirmDialog() },
                )
            },
        )
        AndroidOverlays(
            activity = activity,
            pendingNavigationRequests = pendingNavigationRequests,
            showAuthGuard = showAuthGuard,
            showSiteSwitchConfirm = showSiteSwitchConfirm,
            logoutDialogCloseCurrentPage = logoutDialogCloseCurrentPage,
            onDismissSiteSwitch = onDismissSiteSwitch,
            onConfirmSiteSwitch = onConfirmSiteSwitch,
            onDismissLogout = onDismissLogout,
            onConfirmLogout = onConfirmLogout,
        )
    }
}

/**
 * 六个平台页面的 Android 实现。每个 lambda 的 receiver 是 [PlatformNavScope]，
 * 可直接访问 `onBack` / `backStack` / `homeViewModel`。
 */
@Composable
private fun platformScreens(activity: MainActivity): PlatformScreens = PlatformScreens(
    download = {
        DownloadRouteScreen(
            onBack = onBack,
            onNavigateToVideo = { code -> backStack.add(VideoRoute(code)) },
            onNavigateToLocalVideo = { code, uri -> backStack.add(VideoRoute(code, uri)) },
        )
    },
    account = { pendingAvatarCropResult, onAvatarCropResultConsumed ->
        val accountViewModel: UserAccountViewModel = viewModel()
        AccountScreen(
            viewModel = accountViewModel,
            onBack = onBack,
            onOpenAvatarCrop = { uri -> backStack.add(AvatarCropRoute(uri)) },
            pendingAvatarCropResult = pendingAvatarCropResult,
            onAvatarCropResultConsumed = onAvatarCropResultConsumed,
            onRefreshHome = { homeViewModel.getHomePage() },
            onLogout = { activity.showLogoutConfirmDialog(closeCurrentPageOnConfirm = true) },
        )
    },
    login = {
        LoginRouteScreen(
            activity = activity,
            onBack = onBack,
            onOpenManualCookies = { backStack.add(ManualCookiesRoute) },
            onLoginSucceeded = {
                backStack.popTo(LoginRoute, inclusive = true)
                homeViewModel.getHomePage()
            },
        )
    },
    cloudflare = { route: CloudflareRoute ->
        CloudflareRouteScreen(
            activity = activity,
            route = route,
            onBack = onBack,
        )
    },
    avatarCrop = { sourceUri, onConfirm ->
        AvatarCropScreen(
            sourceUri = sourceUri,
            onBack = onBack,
            onConfirm = { file -> onConfirm(file.absolutePath) },
        )
    },
    downloadSettings = {
        SettingsScaffold(
            backStack = backStack,
            destination = SettingsDestinationSpec.Download,
            fallbackDestination = NetworkDownloadSettingsRoute,
        ) {
            DownloadSettingsRouteScreen()
        }
    },
)

/**
 * Android 完整抽屉（头像 / 用户名 / 当前站点 / 切换站点 / 打卡入口）。
 *
 * 共享 `SharedMainDrawer` 是最小版，此处复用 `MainActivityScaffold` 里的
 * [MainDrawerContent] 注入，保证切换后抽屉功能不降级。
 *
 * 已知差异：平板横屏的常驻抽屉（`PermanentDrawerSheet`）暂由普通抽屉代替，
 * 随共享层补齐窗口尺寸能力后恢复。
 */
@Composable
private fun AndroidDrawer(
    host: DrawerHost,
    onSwitchSiteClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val homeState by host.homeViewModel.homePageFlow.collectAsStateWithLifecycle()
    val checkInEnabled by SettingsRepository.checkInEnabledFlow.collectAsStateWithLifecycle()
    val headerAvatarUrl = if (host.isLoggedIn) {
        (homeState as? PageState.Success)?.info?.page?.avatarUrl
    } else {
        null
    }
    val headerUsername = if (host.isLoggedIn) {
        (homeState as? PageState.Success)?.info?.page?.username
    } else {
        null
    }
    val headerIsLoading = host.isLoggedIn && homeState is PageState.Loading

    BackHandler(enabled = host.drawerState.isOpen) {
        scope.launch { host.drawerState.close() }
    }
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        MainDrawerContent(
            selectedDestination = MainDrawerDestination.fromRoute(host.backStack.topLevelKey),
            avatarUrl = headerAvatarUrl,
            username = headerUsername,
            isLoggedIn = host.isLoggedIn,
            isLoading = headerIsLoading,
            currentSite = SettingsRepository.baseUrl,
            checkInEnabled = checkInEnabled,
            onAvatarClick = {
                if (host.isLoggedIn) host.onOpenAccount() else host.onRequireLogin()
            },
            onAvatarLongClick = onLogoutClick,
            onSwitchSiteClick = onSwitchSiteClick,
            onDrawerItemSelected = { destination ->
                val handled = host.onDrawerItemSelected(destination)
                if (handled) scope.launch { host.drawerState.close() }
                handled
            },
        )
    }
}

/** Android 专属覆盖层：应用锁遮罩、剪贴板链接检测、intent 导航、平台对话框。 */
@Composable
private fun BoxScope.AndroidOverlays(
    activity: MainActivity,
    pendingNavigationRequests: Flow<Intent>,
    showAuthGuard: Boolean,
    showSiteSwitchConfirm: Boolean,
    logoutDialogCloseCurrentPage: Boolean?,
    onDismissSiteSwitch: () -> Unit,
    onConfirmSiteSwitch: () -> Unit,
    onDismissLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val showStorageSwitchNotice by HCacheManager.storageSwitchNotice.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val clipboardText = clipboard.getClipEntry()
            ?.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(activity)
        val videoCode = clipboardText?.toString()?.let { videoUrlRegex.find(it)?.groupValues?.get(1) }
        if (videoCode != null) {
            val result = snackbarHostState.showSnackbar(
                message = getString(Res.string.detect_ha1_related_link_in_clipboard),
                actionLabel = getString(Res.string.enter),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                activity.showVideoDetailFragment(videoCode)
            }
        }
    }
    LaunchedEffect(Unit) {
        pendingNavigationRequests.collect { intent ->
            activity.mainBackStack.handleMainIntent(intent)
        }
    }

    if (showAuthGuard) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
    )

    ConfirmDialog(
        visible = showSiteSwitchConfirm,
        title = stringResource(Res.string.confirm_switch_site),
        message = "",
        confirmText = stringResource(Res.string.sure),
        dismissText = stringResource(Res.string.no),
        onConfirm = onConfirmSiteSwitch,
        onDismiss = onDismissSiteSwitch,
    )
    ConfirmDialog(
        visible = logoutDialogCloseCurrentPage != null,
        title = stringResource(Res.string.sure_to_logout),
        message = "",
        confirmText = stringResource(Res.string.sure),
        dismissText = stringResource(Res.string.no),
        onConfirm = onConfirmLogout,
        onDismiss = onDismissLogout,
    )
    ConfirmDialog(
        visible = showStorageSwitchNotice,
        title = stringResource(Res.string.save_failed_title),
        message = stringResource(Res.string.save_failed_message),
        confirmText = stringResource(Res.string.understood),
        dismissText = null,
        onConfirm = HCacheManager::dismissStorageSwitchNotice,
        onDismiss = HCacheManager::dismissStorageSwitchNotice,
    )
}
