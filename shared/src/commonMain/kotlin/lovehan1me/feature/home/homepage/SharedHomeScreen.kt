package lovehan1me.feature.home.homepage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.HA1_GITHUB_URL
import lovehan1me.HanimeConstants
import lovehan1me.Res
import lovehan1me.checking_for_updates
import lovehan1me.ic_menu
import lovehan1me.ic_newspaper
import lovehan1me.ic_refresh
import lovehan1me.ic_search
import lovehan1me.data.AppUpdateState
import lovehan1me.data.SettingsRepository
import lovehan1me.core.domain.model.AppUpdateInfo
import lovehan1me.core.domain.state.PageState
import lovehan1me.core.domain.state.dataOrNull
import lovehan1me.simulated_update_description
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.PageContent
import lovehan1me.ui.component.PullRefreshOverlay
import lovehan1me.ui.component.appbar.HanimeTopAppBar
import lovehan1me.ui.component.isFirstPageEmpty
import lovehan1me.ui.component.isFirstPageError
import lovehan1me.ui.component.isFirstPageLoading
import lovehan1me.feature.home.homepage.component.AnnouncementCard
import lovehan1me.feature.home.homepage.component.AppUpdateCard
import lovehan1me.ui.screen.rememberRandomLoadingHint
import lovehan1me.core.util.toNetworkErrorMessageRes
import lovehan1me.core.util.isDebugBuild

/**
 * M1/M2：三端共享的首页容器（对标 `:app` 的 `HomePageScreen`，去掉 Android 专属依赖）。
 *
 * 与 `:app` 版的差异（刻意）：
 * - 无 `BackHandler`（桌面无系统返回；由调用方显式返回按钮承担）；
 * - 无 `BuildConfig.DEBUG`，改 [isDebugBuild]；
 * - M2：下拉刷新（CMP pulltorefresh）+ 错误码映射（`toNetworkErrorMessageRes` 已下沉）已补齐；
 * - 顶栏用通用 [HanimeTopAppBar]，`:app` 的 `HomePageTopBar`（自定义字体 R.font）未搬。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedHomeScreen(
    viewModel: HomePageViewModel,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    showNavigationIcon: Boolean = false,
    onOpenDrawer: () -> Unit = {},
) {
    val pageState by viewModel.homePageFlow.collectAsStateWithLifecycle()
    val updateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val updateAnnouncement by viewModel.updateAnnouncement.collectAsStateWithLifecycle()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val homeListState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState()
    val loadingHint = rememberRandomLoadingHint()
    val density = LocalDensity.current
    val contentTopPadding = with(density) {
        WindowInsets.statusBars.getTop(this).toDp() + 72.dp
    }
    val isAVSite = SettingsRepository.baseUrl == HanimeConstants.HANIME_URL[3]

    // 门控改用 settings 驱动而非 Unit：桌面/iOS 的 DataStore 初始化时机与 composition
    // 存在竞态（iOS 还是后台线程初始化），VM 内部有 initializationJob 去重守卫，可重复调用。
    LaunchedEffect(settings.usageNoticeAccepted, settings.usageSourceVerified) {
        viewModel.initializeHomePage()
    }

    val isCurrentlyRefreshing = (pageState as? PageState.Success)?.isRefreshing == true
    val simulatedUpdateDescription = stringResource(Res.string.simulated_update_description)
    val simulatedUpdate = AppUpdateInfo(
        versionName = "Debug Preview",
        versionCode = Int.MAX_VALUE,
        downloadUrl = HA1_GITHUB_URL,
        updateDescription = simulatedUpdateDescription,
        forceUpdate = false,
    )
    val showSimulatedUpdate = isDebugBuild() && settings.alwaysShowUpdateCard
    val availableUpdate = if (showSimulatedUpdate) {
        simulatedUpdate
    } else {
        (updateState as? AppUpdateState.Available)?.info
    }
    val forcedUpdate = availableUpdate?.takeIf { it.forceUpdate }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (forcedUpdate != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentTopPadding,
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.Center,
            ) {
                updateAnnouncement?.let { announcement ->
                    item(key = "forced_update_announcement") {
                        AnnouncementCard(
                            announcements = listOf(announcement),
                            onAnnouncementClick = { selectedAnnouncement ->
                                onEvent(HomeUiEvent.ShowAnnouncementDialog(selectedAnnouncement))
                            },
                            onClose = null,
                        )
                    }
                }
                item(key = "forced_update_${forcedUpdate.versionCode}") {
                    AppUpdateCard(
                        updateInfo = forcedUpdate,
                        onUpdateClick = {
                            onEvent(HomeUiEvent.OpenUpdatePage(forcedUpdate.downloadUrl))
                        },
                        onIgnoreClick = {},
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullToRefresh(
                        state = refreshState,
                        isRefreshing = isCurrentlyRefreshing,
                        enabled = showSimulatedUpdate || updateState !is AppUpdateState.Checking,
                        onRefresh = { viewModel.getHomePage(isRefresh = true) },
                    )
            ) {
            PageContent(
                isLoading = (!showSimulatedUpdate && updateState is AppUpdateState.Checking) ||
                    pageState.isFirstPageLoading,
                isError = pageState.isFirstPageError,
                isEmpty = pageState.isFirstPageError || pageState.isFirstPageEmpty,
                errorMessage = (pageState as? PageState.Error)?.throwable
                    ?.toNetworkErrorMessageRes()
                    ?.let { stringResource(it) }
                    ?: "",
                onRetry = { viewModel.getHomePage(isRefresh = false) },
                loadingMessage = if (!showSimulatedUpdate && updateState is AppUpdateState.Checking) {
                    stringResource(Res.string.checking_for_updates)
                } else {
                    loadingHint
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val homeData = pageState.dataOrNull
                if (homeData != null) {
                    AnimatedContent(
                        targetState = homeData,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                        },
                        label = "HomeContentAnimation",
                    ) { data ->
                        HomePageContent(
                            data = data,
                            updateInfo = availableUpdate,
                            updateAnnouncement = updateAnnouncement,
                            isAVSite = isAVSite,
                            onEvent = onEvent,
                            onCloseAnnouncement = viewModel::dismissAnnouncements,
                            contentTopPadding = contentTopPadding,
                            listState = homeListState,
                        )
                    }
                }
            }
            PullRefreshOverlay(
                state = refreshState,
                isRefreshing = isCurrentlyRefreshing,
            )
            }
        }
        HanimeTopAppBar(
            title = { Text("Han1meViewer") },
            navigationIcon = {
                if (showNavigationIcon) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_menu),
                            contentDescription = null,
                        )
                    }
                }
            },
            modifier = Modifier.zIndex(1f),
            actions = {
                IconButton(onClick = { viewModel.getHomePage(isRefresh = true) }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = null,
                    )
                }
                IconButton(onClick = { onEvent(HomeUiEvent.OpenSearchPage()) }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_search),
                        contentDescription = null,
                    )
                }
                IconButton(onClick = { onEvent(HomeUiEvent.NavigateToPreview) }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_newspaper),
                        contentDescription = null,
                    )
                }
            },
        )
    }
}
