package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.h_keyframes_import_shared
import io.github.daisukikaffuchino.han1meviewer.ic_add
import io.github.daisukikaffuchino.han1meviewer.ic_search
import io.github.daisukikaffuchino.han1meviewer.search
import io.github.daisukikaffuchino.han1meviewer.ui.component.IconButton
import io.github.daisukikaffuchino.han1meviewer.ui.component.rememberHapticFeedback
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.AboutSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.AppearanceSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.DataPrivacySettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.DeveloperOptionsSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.DownloadSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.HKeyframeSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.HKeyframeSettingsRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.HKeyframesRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.HKeyframesRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.HomeSettingsRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.InterfaceInteractionSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.MpvPlayerSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.MpvPlayerSettingsRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.NetworkDownloadSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.NetworkSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.NetworkSettingsRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.OpenSourceLicensesRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.PlayerSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.PlayerSettingsRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.SettingsDestinationSpec
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.SettingsScaffold
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.SharedHKeyframesRoute
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.SharedHKeyframesRouteScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.VideoPlaybackSettingsRoute
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.HomePageViewModel
import io.github.daisukikaffuchino.han1meviewer.ui.screen.login.FormLoginScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.HomeSettingsPage
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.OpenSourceLicensesScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.SettingsMainScreen
import io.github.daisukikaffuchino.han1meviewer.ui.theme.fadeScale
import io.github.daisukikaffuchino.han1meviewer.ui.theme.materialSharedAxisX
import io.github.daisukikaffuchino.utils.SonnerToast
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * M2：三端共享的导航装配（对标 `:app` `TopNavigation`，去掉 `MainActivity` 依赖；
 * `:app` 侧保持不动，Android 继续走旧装配）。
 *
 * 与 `:app` 版的差异：
 * - `activity` 参数（VM store / finish / Intent）全部改为回调 + [homeViewModel] 提升；
 *   原 `viewModel(viewModelStoreOwner = activity)` 与默认 owner 同义，改 sharedViewModel；
 * - `AccountRoute`（头像 picker）/ `LoginRoute`（WebView）/ `CloudflareRoute`（WebView）/
 *   `AvatarCropRoute`（cropper）/ `VideoRoute`（播放器 M3）/ `DownloadRoute` +
 *   `DownloadSettingsRoute`（SAF/WorkManager P7）/ `PreviewCommentRoute`（评论 UI 随 M3）
 *   为占位（参数回显 + 返回，不崩）；
 * - `OpenSourceLicensesRoute` 去 `BackHandler`（Android-only；显式搜索按钮可关）；
 * - `HKeyframesRoute` 的 FAB 触感改跨平台 `rememberHapticFeedback()`；
 * - 设置页 `downloadSettingsContent` 槽位传空（桌面下载目录 P7）。
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedTopNavigation(
    backStack: TopLevelBackStack<HanimeScreen>,
    homeViewModel: HomePageViewModel,
    showHomeNavigationIcon: Boolean,
    onOpenDrawer: () -> Unit,
    platformScreens: PlatformScreens = PlatformScreens(),
) {
    val onBack: () -> Unit = { backStack.removeLast() }
    val onNavigateToVideo: (String) -> Unit = { code -> backStack.add(VideoRoute(code)) }
    val scope = rememberCoroutineScope()

    // M4：注入实现使用的共享上下文
    val navScope = remember(backStack, homeViewModel) {
        PlatformNavScope(backStack, homeViewModel, onBack)
    }
    // 头像裁剪结果在 AccountRoute 与 AvatarCropRoute 之间传递（原 :app TopNavigation 同构）
    var pendingAvatarCropResult by remember { mutableStateOf<String?>(null) }

    fun pageTransition() = NavDisplay.transitionSpec {
        materialSharedAxisX(
            initialOffsetX = { (it * PageTransitionOffsetFactor).toInt() },
            targetOffsetX = { -(it * PageTransitionOffsetFactor).toInt() },
        )
    } + NavDisplay.popTransitionSpec {
        materialSharedAxisX(
            initialOffsetX = { -(it * PageTransitionOffsetFactor).toInt() },
            targetOffsetX = { (it * PageTransitionOffsetFactor).toInt() },
        )
    } + NavDisplay.predictivePopTransitionSpec {
        materialSharedAxisX(
            initialOffsetX = { -(it * PageTransitionOffsetFactor).toInt() },
            targetOffsetX = { (it * PageTransitionOffsetFactor).toInt() },
        )
    }

    fun videoTransition() = NavDisplay.transitionSpec {
        ContentTransform(EnterTransition.None, ExitTransition.None)
    } + NavDisplay.popTransitionSpec {
        ContentTransform(EnterTransition.None, ExitTransition.None)
    } + NavDisplay.predictivePopTransitionSpec {
        ContentTransform(EnterTransition.None, ExitTransition.None)
    }

    val defaultTransition = fadeScale(
        effectSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )

    SharedTransitionLayout {
    NavDisplay(
        backStack = backStack.backStack,
        onBack = onBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { defaultTransition },
        popTransitionSpec = { defaultTransition },
        predictivePopTransitionSpec = { defaultTransition },
        entryProvider = entryProvider {
        entry<HomeRoute> {
                SharedHomeRouteScreen(
                    viewModel = homeViewModel,
                    showNavigationIcon = showHomeNavigationIcon,
                    onOpenDrawer = onOpenDrawer,
                    onNavigateToPreview = { backStack.add(PreviewRoute) },
                onNavigateToSearch = { query -> backStack.add(SearchRoute(query = query)) },
                onNavigateToSearchAdvanced = { params ->
                    backStack.add(
                        SearchRoute(advancedSearchJson = Json.encodeToString(params))
                    )
                },
                onNavigateToVideo = onNavigateToVideo,
                onExit = {},
            )
        }
        entry<WatchHistoryRoute> {
            WatchHistoryRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        entry<MyFavVideoRoute> {
            FavVideoRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        entry<MyWatchLaterRoute> {
            WatchLaterRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        entry<MyPlaylistRoute> {
            MyPlaylistRouteScreen(
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        entry<SubscriptionRoute> {
            SubscriptionRouteScreen(
                onBack = onBack,
                onNavigateToSearch = { query -> backStack.add(SearchRoute(query = query)) },
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        entry<DailyCheckInRoute> {
            DailyCheckInRouteScreen(
                onBack = onBack,
            )
        }
        entry<DownloadRoute> {
            val injected = platformScreens.download
            if (injected != null) {
                navScope.injected()
            } else {
                NavPlaceholder(
                    title = "Download",
                    hint = "桌面下载随 P7（SAF/WorkManager 无跨平台对等）",
                    onBack = onBack,
                )
            }
        }
        entry<AccountRoute>(metadata = pageTransition()) {
            val injected = platformScreens.account
            if (injected != null) {
                navScope.injected(
                    pendingAvatarCropResult,
                    { pendingAvatarCropResult = null },
                )
            } else {
                NavPlaceholder(
                    title = "Account",
                    hint = "账号页（头像 picker）暂仅 Android",
                    onBack = onBack,
                    actionLabel = "手动填 Cookie 登录",
                    onAction = { backStack.add(ManualCookiesRoute) },
                )
            }
        }
        entry<LoginRoute>(metadata = pageTransition()) {
            val injected = platformScreens.login
            if (injected != null) {
                navScope.injected()
            } else {
                // M5：默认走共享表单登录（HTTP 直连，与网站 /login 同路径），
                // 替代原"WebView 登录暂仅 Android"占位。
                FormLoginScreen(
                    onBack = onBack,
                    onOpenManualCookies = { backStack.add(ManualCookiesRoute) },
                    onLoginSucceeded = {
                        backStack.popTo(LoginRoute, inclusive = true)
                        homeViewModel.getHomePage()
                    },
                )
            }
        }
        entry<ManualCookiesRoute>(metadata = pageTransition()) {
            ManualCookiesRouteScreen(
                onBack = onBack,
                onLoginSucceeded = {
                    backStack.popTo(LoginRoute, inclusive = true)
                    homeViewModel.getHomePage()
                },
            )
        }
        entry<CloudflareRoute>(metadata = pageTransition()) { route ->
            val injected = platformScreens.cloudflare
            if (injected != null) {
                navScope.injected(route)
            } else {
                NavPlaceholder(
                    title = "Cloudflare ${route.host}",
                    hint = "人机验证 WebView 暂仅 Android",
                    onBack = onBack,
                )
            }
        }
        entry<AvatarCropRoute>(metadata = pageTransition()) { route ->
            val injected = platformScreens.avatarCrop
            if (injected != null) {
                navScope.injected(route.sourceUri) { croppedPath ->
                    pendingAvatarCropResult = croppedPath
                    onBack()
                }
            } else {
                NavPlaceholder(
                    title = "AvatarCrop",
                    hint = "头像裁剪暂仅 Android：${route.sourceUri}",
                    onBack = onBack,
                )
            }
        }
        entry<HomeSettingsRoute> {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.Home,
                fallbackDestination = HomeRoute,
            ) {
                SettingsMainScreen(
                    onOpenVideoPlayback = { backStack.add(VideoPlaybackSettingsRoute) },
                    onOpenPlayerSettings = { backStack.add(PlayerSettingsRoute) },
                    onOpenNetworkDownload = { backStack.add(NetworkDownloadSettingsRoute) },
                    onOpenAppearance = { backStack.add(AppearanceSettingsRoute) },
                    onOpenInterfaceInteraction = {
                        backStack.add(InterfaceInteractionSettingsRoute)
                    },
                    onOpenDataPrivacy = { backStack.add(DataPrivacySettingsRoute) },
                    onOpenDeveloperOptions = { backStack.add(DeveloperOptionsSettingsRoute) },
                    onOpenAbout = { backStack.add(AboutSettingsRoute) },
                )
            }
        }
        entry<VideoPlaybackSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.VideoPlayback,
                fallbackDestination = HomeSettingsRoute,
            ) {
                HomeSettingsRouteScreen(
                    page = HomeSettingsPage.VideoPlayback,
                    onNavigateToHKeyframes = { backStack.add(HKeyframesRoute) },
                    onNavigateToSharedHKeyframes = { backStack.add(SharedHKeyframesRoute) },
                    downloadSettingsContent = {},
                )
            }
        }
        entry<NetworkDownloadSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.NetworkDownload,
                fallbackDestination = HomeSettingsRoute,
            ) {
                HomeSettingsRouteScreen(
                    page = HomeSettingsPage.NetworkDownload,
                    downloadSettingsContent = {},
                )
            }
        }
        entry<AppearanceSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.Appearance,
                fallbackDestination = HomeSettingsRoute,
            ) {
                HomeSettingsRouteScreen(
                    page = HomeSettingsPage.Appearance,
                    downloadSettingsContent = {},
                )
            }
        }
        entry<InterfaceInteractionSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.InterfaceInteraction,
                fallbackDestination = HomeSettingsRoute,
            ) {
                HomeSettingsRouteScreen(
                    page = HomeSettingsPage.InterfaceInteraction,
                    downloadSettingsContent = {},
                )
            }
        }
        entry<DataPrivacySettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.DataPrivacy,
                fallbackDestination = HomeSettingsRoute,
            ) {
                HomeSettingsRouteScreen(
                    page = HomeSettingsPage.DataPrivacy,
                    downloadSettingsContent = {},
                )
            }
        }
        entry<DeveloperOptionsSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.DeveloperOptions,
                fallbackDestination = HomeSettingsRoute,
            ) {
                HomeSettingsRouteScreen(
                    page = HomeSettingsPage.DeveloperOptions,
                    downloadSettingsContent = {},
                )
            }
        }
        entry<AboutSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.About,
                fallbackDestination = HomeSettingsRoute,
            ) {
                HomeSettingsRouteScreen(
                    page = HomeSettingsPage.About,
                    onNavigateToOpenSourceLicenses = {
                        backStack.add(OpenSourceLicensesRoute)
                    },
                    downloadSettingsContent = {},
                )
            }
        }
        entry<OpenSourceLicensesRoute>(metadata = pageTransition()) {
            var searchMode by remember { mutableStateOf(false) }
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.OpenSourceLicenses,
                fallbackDestination = AboutSettingsRoute,
                actions = {
                    AnimatedVisibility(
                        visible = !searchMode,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
                    ) {
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = { searchMode = true },
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_search),
                                contentDescription = stringResource(Res.string.search),
                            )
                        }
                    }
                },
            ) {
                OpenSourceLicensesScreen(
                    searchMode = searchMode,
                )
            }
        }
        entry<PlayerSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.Player,
                fallbackDestination = HomeSettingsRoute,
            ) {
                PlayerSettingsRouteScreen(
                    onNavigateToMpvSettings = { backStack.add(MpvPlayerSettingsRoute) },
                )
            }
        }
        entry<NetworkSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.Network,
                fallbackDestination = NetworkDownloadSettingsRoute,
            ) {
                NetworkSettingsRouteScreen()
            }
        }
        entry<DownloadSettingsRoute>(metadata = pageTransition()) {
            val injected = platformScreens.downloadSettings
            if (injected != null) {
                navScope.injected()
            } else {
                NavPlaceholder(
                    title = "DownloadSettings",
                    hint = "下载目录（SAF）随 P7",
                    onBack = onBack,
                )
            }
        }
        entry<MpvPlayerSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.Mpv,
                fallbackDestination = PlayerSettingsRoute,
            ) {
                MpvPlayerSettingsRouteScreen()
            }
        }
        entry<HKeyframesRoute>(metadata = pageTransition()) {
            var showImportDialog by remember { mutableStateOf(false) }
            val haptic = rememberHapticFeedback()
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.HKeyframes,
                fallbackDestination = VideoPlaybackSettingsRoute,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            haptic()
                            showImportDialog = true
                        },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_add),
                            contentDescription = stringResource(Res.string.h_keyframes_import_shared),
                        )
                    }
                },
            ) {
                HKeyframesRouteScreen(
                    onOpenVideo = onNavigateToVideo,
                    showImportDialog = showImportDialog,
                    onImportDialogDismiss = { showImportDialog = false },
                )
            }
        }
        entry<SharedHKeyframesRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.SharedHKeyframes,
                fallbackDestination = VideoPlaybackSettingsRoute,
            ) {
                SharedHKeyframesRouteScreen(
                    onOpenVideo = onNavigateToVideo,
                )
            }
        }
        entry<HKeyframeSettingsRoute>(metadata = pageTransition()) {
            SettingsScaffold(
                backStack = backStack,
                destination = SettingsDestinationSpec.HKeyframeSettings,
                fallbackDestination = VideoPlaybackSettingsRoute,
            ) {
                HKeyframeSettingsRouteScreen(
                    onNavigateToHKeyframes = { backStack.add(HKeyframesRoute) },
                    onNavigateToSharedHKeyframes = { backStack.add(SharedHKeyframesRoute) },
                )
            }
        }
        entry<SearchRoute>(metadata = pageTransition()) { route ->
            SearchRouteScreen(
                route = route,
                onBack = onBack,
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        entry<PreviewRoute>(metadata = pageTransition()) {
            PreviewRouteScreen(
                onBack = onBack,
                onNavigateToGetchuPreview = {
                    backStack.add(GetchuPreviewRoute)
                },
                onNavigateToPreviewComment = { date, dateCode ->
                    backStack.add(PreviewCommentRoute(date, dateCode))
                },
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        entry<GetchuPreviewRoute>(metadata = pageTransition()) {
            GetchuPreviewRouteScreen(
                onBack = onBack,
                onNavigateToDetail = { id -> backStack.add(GetchuPreviewDetailRoute(id)) },
            )
        }
        entry<GetchuPreviewDetailRoute>(metadata = pageTransition()) { route ->
            GetchuPreviewDetailRouteScreen(
                route = route,
                onBack = onBack,
                onNavigateToDetail = { id -> backStack.add(GetchuPreviewDetailRoute(id)) },
                onNavigateToVideoUrl = { url -> backStack.add(VideoRoute("-1", url)) },
            )
        }
        entry<PreviewCommentRoute>(metadata = pageTransition()) { route ->
            PreviewCommentRouteScreen(
                route = route,
                onBack = onBack,
            )
        }
        entry<VideoRoute>(metadata = videoTransition()) { route ->
            VideoRouteScreen(
                route = route,
                // M5-2：注入平台窗口宿主（此前漏传，PiP/全屏/亮度/常亮全部静默失效）
                platformHost = platformScreens.videoPageHost,
                onBack = onBack,
                onNavigateHome = { backStack.popTo(HomeRoute) },
                onNavigateToVideo = onNavigateToVideo,
                onOpenSearchRoute = { searchRoute -> backStack.add(searchRoute) },
                onEnqueueDownload = {
                    scope.launch {
                        SonnerToast.warning("桌面下载随 P7（当前仅可在线播放）")
                    }
                },
            )
        }
        },
    )
    }
}

private const val PageTransitionOffsetFactor = 0.10f

@Composable
private fun NavPlaceholder(
    title: String,
    hint: String,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(text = hint, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
        Button(onClick = onBack) {
            Text("返回")
        }
    }
}
