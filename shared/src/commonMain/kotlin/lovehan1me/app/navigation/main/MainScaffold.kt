@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package lovehan1me.app.navigation.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.core.domain.model.NavBarStyle
import lovehan1me.data.SettingsRepository
import lovehan1me.app.navigation.settings.AboutSettingsRoute
import lovehan1me.app.navigation.settings.AppearanceSettingsRoute
import lovehan1me.app.navigation.settings.DataPrivacySettingsRoute
import lovehan1me.app.navigation.settings.DeveloperOptionsSettingsRoute
import lovehan1me.app.navigation.settings.DownloadSettingsRoute
import lovehan1me.app.navigation.settings.HKeyframeSettingsRoute
import lovehan1me.app.navigation.settings.HKeyframesRoute
import lovehan1me.app.navigation.settings.HomeSettingsRoute
import lovehan1me.app.navigation.settings.InterfaceInteractionSettingsRoute
import lovehan1me.app.navigation.settings.MpvPlayerSettingsRoute
import lovehan1me.app.navigation.settings.NetworkDownloadSettingsRoute
import lovehan1me.app.navigation.settings.NetworkSettingsRoute
import lovehan1me.app.navigation.settings.OpenSourceLicensesRoute
import lovehan1me.app.navigation.settings.PlayerSettingsRoute
import lovehan1me.app.navigation.settings.SharedHKeyframesRoute
import lovehan1me.app.navigation.settings.VideoPlaybackSettingsRoute
import lovehan1me.feature.home.homepage.HomePageViewModel
import lovehan1me.ic_settings
import lovehan1me.settings
import lovehan1me.ui.adaptive.ProvideContentWidth
import lovehan1me.ui.adaptive.WindowWidthSizeClass
import lovehan1me.ui.adaptive.rememberWindowWidthSizeClass
import lovehan1me.ui.theme.HanimeDefaults
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 导航外壳 —— Bar / Rail 的宿主（P2）。
 *
 * 取代旧的 `PermanentNavigationDrawer` / `ModalNavigationDrawer` 双分支：**汉堡与抽屉
 * 全部退役**，一级目的地收敛为 [MainTab] 的 3 项（首页 / 发现 / 我的）。
 *
 * | 窗口宽度 | chrome | 形态 |
 * |---|---|---|
 * | Compact（< 600dp） | [NavigationBar] | 贴底 3 项；设置走「我的」顶栏齿轮 |
 * | Medium（600–1199dp） | [WideNavigationRail] **折叠** | 80dp 图标栏；底部一项「设置」 |
 * | Large+（≥ 1200dp） | [WideNavigationRail] **展开** | 220dp 图标 + 文字；底部一项「设置」 |
 *
 * 三个关键设计点：
 *
 * 1. **Bar / Rail 的分档读「窗口宽度」**（[rememberWindowWidthSizeClass]），不是内容宽度 ——
 *    「要不要上常驻 chrome」是宿主级决策。反过来，**内容宽度**由 [ProvideContentWidth]
 *    在 chrome 之后测量并下发，供网格列数 / 双栏判定等消费（扣除 Rail 占宽）。
 * 2. **全屏路由不挂 chrome**：视频详情页与设置页是「无 Rail 的独立全屏路由」（设计稿
 *    §11 决策 4），由 [hidesNavigationChrome] 判定后直接铺满。因为 `NavDisplay` 就在本
 *    组件的内容槽里，不显式摘掉 chrome 的话它们会带着 Rail 渲染。
 * 3. **一级目的地的路由键仍是 `TopLevelBackStack` 的顶层键**（`addTopLevel`），内核对
 *    旧实现零改动，只换类型参数 —— 每个 tab 各自保栈的语义原样保留。
 *
 * @param onSelectTab 切换一级目的地。走 `addTopLevel`，不做登录前置（「我的」本身对
 *   未登录用户可用），登录拦截下沉到「我的」的各 L2 入口。
 */
@Composable
fun MainScaffold(
    backStack: TopLevelBackStack<HanimeScreen>,
    homeViewModel: HomePageViewModel,
    platformScreens: PlatformScreens = PlatformScreens(),
) {
    // topLevelKey 是「当前属于哪个一级 tab」；currentKey 是「栈顶是谁」——决定是否全屏。
    val selectedTab = MainTab.fromRoute(backStack.topLevelKey) ?: MainTab.Fallback
    val onSelectTab: (MainTab) -> Unit = { backStack.addTopLevel(it.route) }
    val onOpenSettings: () -> Unit = { backStack.add(HomeSettingsRoute) }
    // 底栏形态走 settings flow（而非 `SettingsRepository.navBarStyle` 的快照 getter），
    // 否则改完设置要重启才生效。
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val navBarStyle = settings.navBarStyle

    val page: @Composable () -> Unit = {
        ProvideContentWidth {
            SharedTopNavigation(
                backStack = backStack,
                homeViewModel = homeViewModel,
                // P2 起首页顶栏由「搜索胶囊 + 新番列表 + 头像」三件套承载（P4 落地），
                // 不再需要汉堡；这两个参数届时随首页顶栏一起删除。
                showHomeNavigationIcon = false,
                onOpenDrawer = {},
                platformScreens = platformScreens,
            )
        }
    }

    if (backStack.currentKey.hidesNavigationChrome()) {
        Box(modifier = Modifier.fillMaxSize()) { page() }
        return
    }

    if (rememberWindowWidthSizeClass() >= WindowWidthSizeClass.Medium) {
        Row(modifier = Modifier.fillMaxSize()) {
            MainNavigationRail(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onOpenSettings = onOpenSettings,
                expanded = rememberWindowWidthSizeClass() >= WindowWidthSizeClass.Large,
            )
            // 权重 Box 负责把「剩余宽度」交给内容；ProvideContentWidth 内部的
            // BoxWithConstraints 才能测到已扣掉 Rail 的真实内容宽。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                page()
            }
        }
    } else if (navBarStyle == NavBarStyle.Floating) {
        // P6 悬浮胶囊：**不占布局高度**，所以 Scaffold 的 innerPadding 算不到它。
        // 解法是在 bottomBar 放一个等高的透明占位 —— 这样内容区底边距自动让出胶囊的位置，
        // 不必给每个列表页手动加 contentPadding（那要改十几处，且容易漏）。
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(FloatingNavBarReserve),
                    )
                },
                containerColor = HanimeDefaults.Colors.pageSurface,
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    page()
                }
            }
            MainFloatingNavBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    } else {
        Scaffold(
            bottomBar = {
                MainNavigationBar(selectedTab = selectedTab, onSelectTab = onSelectTab)
            },
            containerColor = HanimeDefaults.Colors.pageSurface,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                page()
            }
        }
    }
}

/** 悬浮胶囊总占位高度 = 胶囊 72dp + 距底 16dp（手势 inset 另计）。 */
private val FloatingNavBarReserve = 88.dp

/**
 * Compact 的悬浮形态（P6）：三个 pill 装在圆角胶囊容器内。
 *
 * M3 Expressive **没有重做 NavigationBar**，所以这是自研 composition —— 好处是完全掌控
 * 间距与遮挡补偿（见 [MainScaffold] 里透明占位的注释）。
 */
@Composable
private fun MainFloatingNavBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        shape = HanimeDefaults.Corners.pill,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                FloatingNavBarItem(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = stringResource(tab.titleRes),
                        )
                    },
                    label = { Text(stringResource(tab.titleRes)) },
                )
            }
        }
    }
}

@Composable
private fun FloatingNavBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        // 动效统一：走 motionScheme，不用硬编码 tween（P6）。
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "floatingNavBarItem",
    )
    Column(
        modifier = modifier
            .clip(HanimeDefaults.Corners.pill)
            .background(containerColor)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
            ProvideTextStyle(MaterialTheme.typography.labelSmall) { label() }
        }
    }
}

/** Compact：贴底导航栏。设置**不**进底栏 —— 它由「我的」顶栏齿轮进入。 */
@Composable
private fun MainNavigationBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = stringResource(tab.titleRes),
                    )
                },
                label = { Text(stringResource(tab.titleRes)) },
            )
        }
    }
}

/**
 * Medium+：可折叠侧栏。
 *
 * 折 / 展由宽度档驱动（Medium 折叠、Large 起展开），用 [LaunchedEffect] 把宽度档同步
 * 到 rail 自带状态上 —— 用 `snapTo` 而非 `expand()/collapse()`：后者是带方向的动画，
 * 拖拽窗口跨越断点时方向会来回打架，直接吸附到目标态更稳。
 *
 * ⚠️ `WideNavigationRail` 属于 M3 Expressive 的实验 API（`ExperimentalMaterial3ExpressiveApi`），
 * 文件级 opt-in 已开。它是官方唯一「折叠 ↔ 展开 220dp + 文字渐显」的现成实现，
 * 自研版本会丢掉预测性返回与动画插值，故采用之。
 */
@Composable
private fun MainNavigationRail(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onOpenSettings: () -> Unit,
    expanded: Boolean,
) {
    val target = if (expanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed
    val state = rememberWideNavigationRailState(initialValue = target)
    LaunchedEffect(target) { state.snapTo(target) }
    val railExpanded = state.currentValue == WideNavigationRailValue.Expanded

    WideNavigationRail(state = state) {
        // ⚠️ `WideNavigationRail` 的 content 槽**不是** `ColumnScope`（实测
        // `Modifier.weight` 报 Unresolved reference），所以「弹性占位 + 底部锚定」
        // 不能直接用 weight。改为在内容里自建一个撑满高度的 Column，用
        // `SpaceBetween` 把「设置」压到底部：撑满失败时（父容器高度无界）退化为
        // 紧跟其后，不会崩。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                MainTab.entries.forEach { tab ->
                    WideNavigationRailItem(
                        selected = tab == selectedTab,
                        onClick = { onSelectTab(tab) },
                        icon = {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = stringResource(tab.titleRes),
                            )
                        },
                        label = { Text(stringResource(tab.titleRes)) },
                        railExpanded = railExpanded,
                    )
                }
            }
            // 设置退出一级导航后固定落在 Rail 底部（设计稿 §1.3）。
            WideNavigationRailItem(
                selected = false,
                onClick = onOpenSettings,
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = stringResource(Res.string.settings),
                    )
                },
                label = { Text(stringResource(Res.string.settings)) },
                railExpanded = railExpanded,
            )
        }
    }
}

/**
 * 该路由是否**不显示**导航 chrome（Bar / Rail）。
 *
 * 两类：
 * - [VideoRoute] —— 视频详情，全屏沉浸播放；
 * - 全部设置路由 —— 设置是独立全屏路由（内部自己做响应式双栏），进去不显示 Rail。
 *
 * 首页 / 发现 / 我的 及其 L2 列表页要保留 chrome，故默认 false。
 */
private fun HanimeScreen.hidesNavigationChrome(): Boolean = when (this) {
    is VideoRoute -> true
    HomeSettingsRoute,
    VideoPlaybackSettingsRoute,
    NetworkDownloadSettingsRoute,
    AppearanceSettingsRoute,
    InterfaceInteractionSettingsRoute,
    DataPrivacySettingsRoute,
    DeveloperOptionsSettingsRoute,
    AboutSettingsRoute,
    OpenSourceLicensesRoute,
    PlayerSettingsRoute,
    NetworkSettingsRoute,
    DownloadSettingsRoute,
    MpvPlayerSettingsRoute,
    HKeyframesRoute,
    SharedHKeyframesRoute,
    HKeyframeSettingsRoute,
    -> true

    else -> false
}
