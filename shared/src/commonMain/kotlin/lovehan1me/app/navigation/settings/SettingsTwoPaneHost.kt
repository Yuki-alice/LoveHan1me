package lovehan1me.app.navigation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.ic_captive_portal
import lovehan1me.ic_code
import lovehan1me.ic_data_table
import lovehan1me.ic_info
import lovehan1me.ic_interests
import lovehan1me.ic_palette
import lovehan1me.ic_video_settings
import lovehan1me.app.navigation.main.HanimeScreen
import lovehan1me.app.navigation.main.MainTab
import lovehan1me.app.navigation.main.TopLevelBackStack
import lovehan1me.core.util.isDebugBuild
import lovehan1me.feature.settings.HomeSettingsPage
import lovehan1me.feature.settings.SettingsMainScreen
import lovehan1me.ui.adaptive.rememberContentWidthDp
import lovehan1me.ui.theme.HanimeDefaults

/** 左栏分类栏宽度。280dp 是「图标 + 文字标签」的可读下限。 */
private val CategoryPaneWidth = 280.dp

/**
 * 双栏阈值：可用内容宽度 ≥ 900dp。
 *
 * 900 = 左栏 280 + 右栏窄内容区 620。低于此值走单栏，否则右栏会被挤得比手机还窄。
 * 用**内容宽度**而非窗口宽度 —— 内容宽度才是右栏真正拿到的空间。
 */
private val TwoPaneMinContentWidth = 900.dp

/**
 * 设置页首页宿主：宽屏双栏、窄屏钻取。
 *
 * **双栏形态下不再有二级页跳转** —— 左栏选分类，右栏直接渲染该分类内容，省掉
 * 「进二级页 → 返回 → 再进另一个」的来回。这是 MD3 点名的 List-Detail 适用场景：
 * "dividing app preferences into a list of categories with the preferences for each
 * category in the detail pane."
 *
 * 右栏**复用现有 `HomeSettingsRouteScreen(page = …)`**，不新增渲染逻辑 ——
 * `HomeSettingsPage` 与 [SettingsCategory] 一一对应，它本来就是按分类渲染的。
 *
 * 子页钻取（如「播放器设置」「MPV 高级设置」）仍 push 成全屏页，返回后回到双栏；
 * 子页不参与双栏，避免左栏在钻取后失去语义。
 */
@Composable
fun SettingsHomeHost(
    backStack: TopLevelBackStack<HanimeScreen>,
    onOpenVideoPlayback: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenNetworkDownload: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenInterfaceInteraction: () -> Unit,
    onOpenDataPrivacy: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenAbout: () -> Unit,
    onNavigateToHKeyframes: () -> Unit,
    onNavigateToSharedHKeyframes: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    downloadSettingsContent: @Composable () -> Unit = {},
) {
    val useTwoPane = rememberContentWidthDp() >= TwoPaneMinContentWidth

    if (!useTwoPane) {
        SettingsScaffold(
            backStack = backStack,
            destination = SettingsDestinationSpec.Home,
            fallbackDestination = MainTab.Fallback.route,
        ) {
            SettingsMainScreen(
                onOpenVideoPlayback = onOpenVideoPlayback,
                onOpenPlayerSettings = onOpenPlayerSettings,
                onOpenNetworkDownload = onOpenNetworkDownload,
                onOpenAppearance = onOpenAppearance,
                onOpenInterfaceInteraction = onOpenInterfaceInteraction,
                onOpenDataPrivacy = onOpenDataPrivacy,
                onOpenDeveloperOptions = onOpenDeveloperOptions,
                onOpenAbout = onOpenAbout,
            )
        }
        return
    }

    var selected by rememberSaveable { mutableStateOf(SettingsCategory.VideoPlayback) }
    SettingsScaffold(
        backStack = backStack,
        destination = selected.spec,
        fallbackDestination = MainTab.Fallback.route,
        // 关键：外壳不再限宽 —— 否则表单行宽会把左栏 280dp 一起吃掉，右栏被挤窄。
        contentMaxWidth = Dp.Unspecified,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            SettingsCategoryPane(
                selected = selected,
                onSelect = { selected = it },
            )
            // 右栏自己按表单档限宽并居中（外壳已放权）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(modifier = Modifier.widthIn(max = HanimeDefaults.Widths.formMax)) {
                    HomeSettingsRouteScreen(
                        page = selected.page,
                        onNavigateToHKeyframes = onNavigateToHKeyframes,
                        onNavigateToSharedHKeyframes = onNavigateToSharedHKeyframes,
                        onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                        downloadSettingsContent = downloadSettingsContent,
                    )
                }
            }
        }
    }
}

/**
 * 左栏：分类导航。
 *
 * 没有引入新的数据结构 —— `SettingsDestinationSpec` 枚举现成就是分类表，
 * 分类标题字符串已在其 `titleRes` 里。
 */
@Composable
private fun SettingsCategoryPane(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 「开发者选项」仅 debug 可见，与单栏 SettingsMainScreen 的规矩一致。
    val categories = if (isDebugBuild()) {
        SettingsCategory.entries
    } else {
        SettingsCategory.entries.filter { it != SettingsCategory.DeveloperOptions }
    }
    Column(
        modifier = modifier
            .width(CategoryPaneWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(vertical = HanimeDefaults.Spacing.medium),
    ) {
        categories.forEach { category ->
            NavigationDrawerItem(
                selected = category == selected,
                onClick = { onSelect(category) },
                icon = {
                    Icon(
                        painter = painterResource(category.iconRes),
                        contentDescription = null,
                    )
                },
                label = { Text(text = stringResource(category.spec.titleRes)) },
                modifier = Modifier
                    .padding(horizontal = HanimeDefaults.Spacing.medium)
                    .padding(bottom = HanimeDefaults.Spacing.extraSmall),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

/**
 * 左栏分类（7 项，与 `HomeSettingsPage` 一一对应）。
 *
 * 顺序沿用《导航重构与设计原型方案》§7.4 的分类表。
 */
private enum class SettingsCategory(
    val spec: SettingsDestinationSpec,
    val page: HomeSettingsPage,
    val iconRes: DrawableResource,
) {
    VideoPlayback(
        spec = SettingsDestinationSpec.VideoPlayback,
        page = HomeSettingsPage.VideoPlayback,
        iconRes = Res.drawable.ic_video_settings,
    ),
    NetworkDownload(
        spec = SettingsDestinationSpec.NetworkDownload,
        page = HomeSettingsPage.NetworkDownload,
        iconRes = Res.drawable.ic_captive_portal,
    ),
    Appearance(
        spec = SettingsDestinationSpec.Appearance,
        page = HomeSettingsPage.Appearance,
        iconRes = Res.drawable.ic_palette,
    ),
    InterfaceInteraction(
        spec = SettingsDestinationSpec.InterfaceInteraction,
        page = HomeSettingsPage.InterfaceInteraction,
        iconRes = Res.drawable.ic_interests,
    ),
    DataPrivacy(
        spec = SettingsDestinationSpec.DataPrivacy,
        page = HomeSettingsPage.DataPrivacy,
        iconRes = Res.drawable.ic_data_table,
    ),
    DeveloperOptions(
        spec = SettingsDestinationSpec.DeveloperOptions,
        page = HomeSettingsPage.DeveloperOptions,
        iconRes = Res.drawable.ic_code,
    ),
    About(
        spec = SettingsDestinationSpec.About,
        page = HomeSettingsPage.About,
        iconRes = Res.drawable.ic_info,
    ),
}
