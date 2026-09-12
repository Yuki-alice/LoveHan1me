package lovehan1me.app.navigation.main

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
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.h_keyframes_import_shared
import lovehan1me.ic_add
import lovehan1me.ic_search
import lovehan1me.login_first
import lovehan1me.search
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.app.navigation.settings.AboutSettingsRoute
import lovehan1me.app.navigation.settings.AppearanceSettingsRoute
import lovehan1me.app.navigation.settings.DataPrivacySettingsRoute
import lovehan1me.app.navigation.settings.DeveloperOptionsSettingsRoute
import lovehan1me.app.navigation.settings.DownloadSettingsRoute
import lovehan1me.app.navigation.settings.HKeyframeSettingsRoute
import lovehan1me.app.navigation.settings.HKeyframeSettingsRouteScreen
import lovehan1me.app.navigation.settings.HKeyframesRoute
import lovehan1me.app.navigation.settings.HKeyframesRouteScreen
import lovehan1me.app.navigation.settings.HomeSettingsRoute
import lovehan1me.app.navigation.settings.HomeSettingsRouteScreen
import lovehan1me.app.navigation.settings.InterfaceInteractionSettingsRoute
import lovehan1me.app.navigation.settings.MpvPlayerSettingsRoute
import lovehan1me.app.navigation.settings.MpvPlayerSettingsRouteScreen
import lovehan1me.app.navigation.settings.NetworkDownloadSettingsRoute
import lovehan1me.app.navigation.settings.NetworkSettingsRoute
import lovehan1me.app.navigation.settings.NetworkSettingsRouteScreen
import lovehan1me.app.navigation.settings.OpenSourceLicensesRoute
import lovehan1me.app.navigation.settings.PlayerSettingsRoute
import lovehan1me.app.navigation.settings.PlayerSettingsRouteScreen
import lovehan1me.app.navigation.settings.SettingsDestinationSpec
import lovehan1me.app.navigation.settings.SettingsHomeHost
import lovehan1me.app.navigation.settings.SettingsScaffold
import lovehan1me.app.navigation.settings.SharedHKeyframesRoute
import lovehan1me.app.navigation.settings.SharedHKeyframesRouteScreen
import lovehan1me.app.navigation.settings.VideoPlaybackSettingsRoute
import lovehan1me.feature.home.homepage.HomePageViewModel
import lovehan1me.core.platform.downloadWorkController
import lovehan1me.feature.login.FormLoginScreen
import lovehan1me.data.logout
import lovehan1me.feature.account.AccountScreen
import lovehan1me.feature.account.AvatarCropScreen
import lovehan1me.feature.account.UserAccountViewModel
import lovehan1me.app.sharedViewModel
import lovehan1me.feature.settings.HomeSettingsPage
import lovehan1me.feature.settings.OpenSourceLicensesScreen
import lovehan1me.ui.theme.fadeScale
import lovehan1me.ui.theme.sharedAxisX
import lovehan1me.core.util.SonnerToast
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

    // 审计 P1：转场规格必须在 composable 上下文先算好再交给 NavDisplay ——
    // `transitionSpec {}` / entry metadata 的 lambda 都不是 @Composable，
    // 里面直接调 motionScheme 驱动的工厂函数会编译不过。
    val pageForward = sharedAxisX(
        initialOffsetX = { (it * PageTransitionOffsetFactor).toInt() },
        targetOffsetX = { -(it * PageTransitionOffsetFactor).toInt() },
    )
    val pageBackward = sharedAxisX(
        initialOffsetX = { -(it * PageTransitionOffsetFactor).toInt() },
        targetOffsetX = { (it * PageTransitionOffsetFactor).toInt() },
    )

    // ⚠️ `+` 必须留在上一行行尾：行首的 `+` 会被 Kotlin 解析成一元运算符而报
    //    "Unresolved reference 'unaryPlus'"。
    fun pageTransition() = NavDisplay.transitionSpec { pageForward } +
        NavDisplay.popTransitionSpec { pageBackward } +
        NavDisplay.predictivePopTransitionSpec { pageBackward }

    fun videoTransition() = NavDisplay.transitionSpec {
        ContentTransform(EnterTransition.None, ExitTransition.None)
    } + NavDisplay.popTransitionSpec {
        ContentTransform(EnterTransition.None, ExitTransition.None)
    } + NavDisplay.predictivePopTransitionSpec {
        ContentTransform(EnterTransition.None, ExitTransition.None)
    }

    // 顶层 Tab 切换也是「页面级」转场（整屏换内容），同样走 slow spatial 拿呼吸感。
    val defaultTransition = fadeScale(
        effectSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
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
                onNavigateToMine = { backStack.navigateMainTab(MainTab.Mine) },
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
        // 一级目的地「发现」：内容就是原来的搜索界面（SearchRouteScreen + SearchScreen），
        // 只是语义从「一个输入框」升格为「一个探索空间」（历史 / 热门 / 高级筛选都在这栏）。
        //
        // ⚠️ 必须显式注册 `DiscoverTab`：P1 收敛一级目的地时只加了这个键，没加 entry，
        //    而 P2 的底栏/Rail 让它第一次变得可点 —— 不补的话点「发现」是空白、甚至
        //    因 `NavDisplay` 找不到 key 而抛异常。
        //
        // `onBack` 接成「回首页」：tab 模式下没有可返回的上层节点，而搜索页顶栏那个
        // 返回箭头必须接一个真实动作，否则就是死按钮。
        // TODO(P4)：按设计稿 §1.6 把该箭头在 tab 模式下改成「取消」/ 直接隐藏。
        entry<DiscoverTab> {
            SearchRouteScreen(
                route = SearchRoute(),
                onBack = { backStack.addTopLevel(MainTab.Home.route) },
                onNavigateToVideo = onNavigateToVideo,
            )
        }
        // 一级目的地「我的」：签到首卡 + 登录账户卡 + 6 个 L2 内容入口。
        // 登录拦截在这里就地处理（「我的」本身不要求登录，只有「订阅」这类
        // 无本地降级的入口需要）：提示 + 跳登录页。
        entry<MineTab> {
            MineRouteScreen(
                homeViewModel = homeViewModel,
                onOpenSettings = { backStack.add(HomeSettingsRoute) },
                onOpenAccount = { backStack.add(AccountRoute) },
                onOpenLogin = { backStack.add(LoginRoute) },
                onLockedSection = {
                    scope.launch { SonnerToast.warning(getString(Res.string.login_first)) }
                    backStack.add(LoginRoute)
                },
                onOpenCheckIn = { backStack.add(DailyCheckInRoute) },
                onNavigateToSection = { section -> backStack.add(section.route) },
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
                // M6：默认走共享下载页（Room KMP 三端同库，浏览/分组/删除全可用）；
                // 外部播放仍为 Android 专属回调（null）。
                // 阶段一⑦：自家目录导入扫描桌面/iOS 均已实现（importDownloaded），
                // 传非空即启用确认框；:app 侧 AndroidShell 同理自理。
                DownloadRouteScreen(
                    onBack = onBack,
                    onNavigateToVideo = { code -> backStack.add(VideoRoute(code)) },
                    onNavigateToLocalVideo = { code, uri -> backStack.add(VideoRoute(code, uri)) },
                    onImportDownloaded = {},
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
                // M5-4：默认走共享账号页（UserAccountViewModel 已在 commonMain）。
                // 头像上传依赖平台 picker + 裁剪，桌面/iOS 传 null（UI 隐藏上传入口），
                // 资料/密码等其余账号功能三端一致。
                val accountViewModel: UserAccountViewModel = sharedViewModel(::UserAccountViewModel)
                AccountScreen(
                    viewModel = accountViewModel,
                    onBack = onBack,
                    onOpenAvatarCrop = { backStack.add(AvatarCropRoute(it)) },
                    onPickAvatarImage = null,
                    pendingAvatarCropResult = pendingAvatarCropResult,
                    onAvatarCropResultConsumed = { pendingAvatarCropResult = null },
                    onRefreshHome = { homeViewModel.getHomePage() },
                    onLogout = {
                        scope.launch {
                            logout()
                            homeViewModel.getHomePage()
                            backStack.popTo(HomeRoute)
                        }
                    },
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
                // 阶段一⑧：没有平台注入时用共享的纯 Compose 裁剪页，
                // 桌面/iOS 因此不再是占位页（Android 仍走 mucute 注入）。
                AvatarCropScreen(
                    sourceUri = route.sourceUri,
                    onBack = onBack,
                    onConfirm = { croppedPath ->
                        pendingAvatarCropResult = croppedPath
                        onBack()
                    },
                )
            }
        }
        entry<HomeSettingsRoute> {
            // P5.5：宽屏（内容宽 ≥ 900dp）走 List-Detail 双栏，窄屏仍是原来的钻取式。
            SettingsHomeHost(
                backStack = backStack,
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
                onNavigateToHKeyframes = { backStack.add(HKeyframesRoute) },
                onNavigateToSharedHKeyframes = { backStack.add(SharedHKeyframesRoute) },
                onNavigateToOpenSourceLicenses = { backStack.add(OpenSourceLicensesRoute) },
            )
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
                onEnqueueDownload = { request ->
                    scope.launch {
                        downloadWorkController().addTask(
                            video = request.video,
                            videoCode = request.videoCode,
                            quality = request.quality,
                            groupId = request.groupId,
                            redownload = request.redownload,
                        )
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
