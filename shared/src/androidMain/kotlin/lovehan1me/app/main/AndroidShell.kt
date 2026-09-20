package lovehan1me.app.main

import android.content.ClipData
import android.content.Intent
import lovehan1me.core.util.getDownloadedHanimeVideoUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import lovehan1me.app.App
import lovehan1me.HanimeCacheManager
import lovehan1me.Res
import lovehan1me.detect_ha1_related_link_in_clipboard
import lovehan1me.enter
import lovehan1me.ext_player
import lovehan1me.no
import lovehan1me.save_failed_message
import lovehan1me.save_failed_title
import lovehan1me.sure
import lovehan1me.sure_to_logout
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.app.navigation.main.AvatarCropRoute
import lovehan1me.app.navigation.main.CloudflareRoute
import lovehan1me.app.navigation.main.PlatformScreens
import lovehan1me.app.web.CloudflareRouteScreen
import lovehan1me.app.navigation.main.DownloadRouteScreen
import lovehan1me.app.navigation.main.VideoRoute
import lovehan1me.app.navigation.main.handleMainIntent
import lovehan1me.app.navigation.settings.DownloadSettingsRouteScreen
import lovehan1me.app.navigation.settings.NetworkDownloadSettingsRoute
import lovehan1me.app.navigation.settings.SettingsDestinationSpec
import lovehan1me.app.navigation.settings.SettingsScaffold
import lovehan1me.feature.player.rememberAndroidVideoPageHost
import lovehan1me.feature.account.AccountScreen
import lovehan1me.feature.account.UserAccountViewModel
import lovehan1me.understood
import lovehan1me.site.hanime1.videoUrlRegex
import lovehan1me.play_pause
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
 * 3. Android 专属覆盖层（应用锁遮罩 / 剪贴板检测 / intent 导航 / 登出对话框）。
 *
 * P2：抽屉（`MainDrawerContent` / `DrawerHost` / `drawerContent` 槽位）已整体退役，
 * 导航形态改由共享 [lovehan1me.app.navigation.main.MainScaffold] 提供（Compact 贴底
 * NavigationBar / Medium+ WideNavigationRail）。登出仍在账号页。
 *
 * 「切换站点」已迁到共享导航（`SharedTopNavigation` 的 `entry<MineTab>` → 我的页账号卡
 * 右侧按钮），因此本壳**不再**承载站点切换对话框与相关回调 —— 否则 Android 会多出一个
 * 走 `restartApp` 的旧入口，与热切换并存造成两个行为不一致的按钮。
 *
 * 这里只保留第 3 件，并通过 [PlatformScreens] 把 6 个依赖 Android 硬能力的页面
 * （WebView 登录、CF 验证、头像 picker + cropper、SAF 下载设置、WorkManager 下载）
 * 注入共享导航，从而在功能零损失的前提下消除导航双轨。
 *
 * G1-1B：随壳层下沉 shared。唯一对 `:app` 的耦合（`MainActivity`）改由 [MainActivityHost]
 * 承担：`openLogin` / `showLogoutConfirmDialog` / `showVideoDetailFragment` /
 * `mainBackStack` 走接口方法，需要 Activity/Context 本身的两处走 [MainActivityHost.componentActivity]。
 */
@Composable
fun MainActivityShell(
    activity: MainActivityHost,
    pendingNavigationRequests: Flow<Intent>,
    logoutDialogCloseCurrentPage: Boolean?,
    onDismissLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // P2：抽屉退役后 App() 不再收 drawerContent 槽位 —— 导航外壳改为
        // Compact 贴底 NavigationBar / Medium+ WideNavigationRail（见 MainScaffold）。
        App(platformScreens = platformScreens(activity))
        AndroidOverlays(
            activity = activity,
            pendingNavigationRequests = pendingNavigationRequests,
            logoutDialogCloseCurrentPage = logoutDialogCloseCurrentPage,
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
private fun platformScreens(activity: MainActivityHost): PlatformScreens = PlatformScreens(
    // M5-2：恢复 Android 窗口能力（下沉后该宿主无人注入而成死代码）
    videoPageHost = rememberAndroidVideoPageHost(
        activity = activity.componentActivity,
        pipToggleDescription = stringResource(Res.string.play_pause),
    ),
    download = {
        val context = androidx.compose.ui.platform.LocalContext.current
        val externalPlayerChooserTitle = stringResource(Res.string.ext_player)
        DownloadRouteScreen(
            onBack = onBack,
            onNavigateToVideo = { code -> backStack.add(VideoRoute(code)) },
            onNavigateToLocalVideo = { code, uri -> backStack.add(VideoRoute(code, uri)) },
            onExternalPlayback = { videoUriPath, onNotExist ->
                val externalUri = context.getDownloadedHanimeVideoUri(videoUriPath) { onNotExist() }
                if (externalUri != null) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(externalUri, "video/*")
                        clipData = ClipData.newRawUri("video", externalUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(intent, externalPlayerChooserTitle).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                }
            },
            onImportDownloaded = {},
        )
    },
    account = { pendingAvatarCropResult, onAvatarCropResultConsumed ->
        val accountViewModel: UserAccountViewModel = viewModel()
        // M5-4：头像选择器留在壳层（ActivityResult），经参数注入共享账号页
        val avatarPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let { backStack.add(AvatarCropRoute(it.toString())) }
        }
        AccountScreen(
            viewModel = accountViewModel,
            onBack = onBack,
            onOpenAvatarCrop = { uri -> backStack.add(AvatarCropRoute(uri)) },
            onPickAvatarImage = {
                avatarPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            pendingAvatarCropResult = pendingAvatarCropResult,
            onAvatarCropResultConsumed = onAvatarCropResultConsumed,
            onRefreshHome = { homeViewModel.getHomePage() },
            onNavigateToLogin = { activity.openLogin() },
            isLogoutConfirmedByCaller = true,
            onLogout = { activity.showLogoutConfirmDialog(closeCurrentPageOnConfirm = true) },
        )
    },
    // M2（决策 #8）：不再注入 login —— 登录页统一走 shared 的 FormLoginScreen
    // （SharedTopNavigation 的 entry<LoginRoute> 在 platformScreens.login == null 时
    // 自动回退到它）。:app 的 WebView 登录屏与其 createLoginWebView 已删除。
    // M2（决策 #8）：CF 验证页已下沉 shared/androidMain，这里只做一行委托
    cloudflare = { route: CloudflareRoute ->
        CloudflareRouteScreen(
            host = route.host,
            url = route.url,
            onBack = onBack,
        )
    },
    // 阶段一⑧：不再注入 mucute 裁剪页（已删），统一用共享的纯 Compose 裁剪页
    // （解码/落盘由 core/platform/AvatarImageIo 的 androidMain 实现完成）
    avatarCrop = null,
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

@Composable
private fun BoxScope.AndroidOverlays(
    activity: MainActivityHost,
    pendingNavigationRequests: Flow<Intent>,
    logoutDialogCloseCurrentPage: Boolean?,
    onDismissLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val showStorageSwitchNotice by HanimeCacheManager.storageSwitchNotice.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val clipboardText = clipboard.getClipEntry()
            ?.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(activity.componentActivity)
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

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
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
        onConfirm = HanimeCacheManager::dismissStorageSwitchNotice,
        onDismiss = HanimeCacheManager::dismissStorageSwitchNotice,
    )
}
