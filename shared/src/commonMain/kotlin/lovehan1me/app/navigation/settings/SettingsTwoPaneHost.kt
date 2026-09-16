package lovehan1me.app.navigation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.ic_captive_portal
import lovehan1me.ic_code
import lovehan1me.ic_data_table
import lovehan1me.ic_dvr
import lovehan1me.ic_info
import lovehan1me.ic_interests
import lovehan1me.ic_palette
import lovehan1me.ic_video_settings
import lovehan1me.settings
import lovehan1me.app.navigation.main.HanimeScreen
import lovehan1me.app.navigation.main.MainTab
import lovehan1me.app.navigation.main.TopLevelBackStack
import lovehan1me.core.util.isDebugBuild
import lovehan1me.feature.settings.HomeSettingsPage
import lovehan1me.feature.settings.SettingsMainScreen
import lovehan1me.ui.adaptive.rememberContentWidthDp
import lovehan1me.ui.component.appbar.HanimeTopAppBar
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
 * 栏内标题栏要申请的 window insets：**全零**。
 *
 * 状态栏 inset 已由外层 `HanimeScaffold` 的 `innerPadding` 统一让出（双栏态没有全局
 * 顶栏，Scaffold 会把系统栏 inset 直接交给内容）。栏内两条标题栏若各自再申请一次，
 * Android 上会叠成**双倍顶距**，且左右两条标题会一起被推下去。
 */
private val PaneHeaderInsets = WindowInsets(0, 0, 0, 0)

/**
 * 设置流的**统一宿主**：宽屏双栏、窄屏钻取，二选一。
 *
 * 九个设置路由（`HomeSettingsRoute` + 8 个分类）全部走这一个入口 —— 分类页不再各自
 * 拼一遍 `SettingsScaffold`，避免"同一种形态两套实现"再次分叉。
 *
 * ## 为什么分类页也必须走这里（2026-09-16 修复）
 * 此前只有 `entry<HomeSettingsRoute>` 调本宿主，分类页（`PlayerSettingsRoute` 等）在
 * `SharedTopNavigation` 里各自渲染**全屏单栏** `SettingsScaffold`。后果是：
 * 用户在小窗里点进「播放器设置」（被 push 成单栏全屏），**再把窗口拉宽，双栏不出现**
 * —— 双栏只在栈顶是设置首页时才存在。宽屏用户于是"永远进不去双栏的播放器设置"。
 *
 * 现在每个分类路由都声明自己代表哪个 [SettingsCategory]，宿主据此决定：
 * - **宽屏**：双栏，右栏即该分类内容；
 * - **窄屏**：分类单栏页（等价于修复前各 entry 自己拼的那份）。
 *
 * 「播放器设置」是其中唯一的特例：它不是 `HomeSettingsPage` 那一族（自带整屏），
 * 见 [SettingsCategory.page]。
 *
 * ## 边界处理对齐项目首页（`MainScaffold` 宽屏分支）
 * 窗底 `surfaceContainerLow`、右栏是 `surface` + 左上 28dp 圆角的 M3E 内容 sheet，
 * 左栏**透明坐底**。分界靠「底色差 + 圆角」而不是一条细分隔线（同系底色下细线约等于
 * 没有，那条 `VerticalDivider` 已退役）。圆角与首页共用 `Corners.contentSheet`。
 *
 * **标题按栏拆开**（不是横跨两栏的全局顶栏）：左栏顶部是「返回 + 设置」，右栏顶部是
 * 当前分类标题。因此双栏分支 [SettingsScaffold] 必须 `showTopBar = false`，否则会得到
 * 第三条标题，且它会横跨两栏、把右栏 sheet 的顶角切掉。
 *
 * ## 「当前分类」为什么以路由为准，而不是纯内部状态
 * 双栏下左栏切换**不动路由**（避免整棵双栏被 NavHost 的页面转场动画搬来搬去），
 * 但 [category]（路由声明的分类）变化时会把选中态同步过来 —— 这正是"窄屏 push 分类页
 * → 拉宽窗口"能恢复双栏选中态的机制。反过来，宽屏切过分类后缩窄窗口时，把选中态
 * 写回路由（[TopLevelBackStack.replaceTop]），窄屏看到的就不是"进来时那个分类"。
 */
@Composable
fun SettingsRouteHost(
    backStack: TopLevelBackStack<HanimeScreen>,
    /**
     * 当前路由声明的分类。`null` = 设置首页（`HomeSettingsRoute`）——
     * 窄屏时渲染分类卡片列表，宽屏时等价于默认选中第一项。
     */
    category: SettingsCategory?,
    onOpenVideoPlayback: () -> Unit = {},
    onOpenPlayerSettings: () -> Unit = {},
    onOpenNetworkDownload: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenInterfaceInteraction: () -> Unit = {},
    onOpenDataPrivacy: () -> Unit = {},
    onOpenDeveloperOptions: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    onOpenThemeAudit: () -> Unit = {},
    downloadSettingsContent: @Composable () -> Unit = {},
) {
    val contentWidth = rememberContentWidthDp()
    val useTwoPane = contentWidth >= TwoPaneMinContentWidth
    // 超宽屏右栏放宽到正文档（720dp），避免 640dp 在 1600dp+ 窗口下显得像小纸条；
    // 1200–1599dp 仍用表单档（640dp）保信息密度。
    val rightMaxWidth = if (contentWidth >= 1400.dp) {
        HanimeDefaults.Widths.readingMax
    } else {
        HanimeDefaults.Widths.formMax
    }

    // 选中态：初值取路由声明的分类（首页 → 默认第一项）。
    var selected by rememberSaveable { mutableStateOf(category ?: DefaultCategory) }

    // 路由 → 选中态（窄屏 push 分类页后拉宽窗口、或从三级页返回时走这里）。
    LaunchedEffect(category) {
        if (category != null && category != selected) selected = category
    }
    // 选中态 → 路由：宽屏切过分类后缩窄窗口时补一次，保证窄屏看到的是用户最后选的分类。
    LaunchedEffect(useTwoPane) {
        if (!useTwoPane && category != null && selected != category) {
            backStack.replaceTop(selected.spec.route)
        }
    }

    if (!useTwoPane) {
        if (category == null) {
            // 设置首页：分类卡片列表（钻取式）。
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
        } else {
            // 分类单栏页：返回落到设置首页（不是 fallback 到主界面 —— 那是"退出设置"）。
            SettingsScaffold(
                backStack = backStack,
                destination = category.spec,
                fallbackDestination = HomeSettingsRoute,
            ) {
                SettingsCategoryContent(
                    category = category,
                    onNavigateToMpvSettings = { backStack.add(MpvPlayerSettingsRoute) },
                    onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                    onOpenThemeAudit = onOpenThemeAudit,
                    downloadSettingsContent = downloadSettingsContent,
                )
            }
        }
        return
    }

    // 双栏直铺 Row，不经过 SettingsScaffold/HanimeScaffold 的 M3 Scaffold：
    // 上一版把 Row 抽成 TwoPaneRow 却仍放在 SettingsScaffold 的 content 里，
    // 外层三件套（内容边距、共享回弹、包浆 surface）一个没少，等于没改。
    // 双栏自己就是 chrome（无全局顶栏、无 FAB、无 snackbar），直接铺满即可；
    // 窄屏两条路保持走 SettingsScaffold，一点不动。
    TwoPaneRow(
        backStack = backStack,
        selected = selected,
        onSelect = { selected = it },
        rightMaxWidth = rightMaxWidth,
        onNavigateToMpvSettings = { backStack.add(MpvPlayerSettingsRoute) },
        onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
        onOpenThemeAudit = onOpenThemeAudit,
        downloadSettingsContent = downloadSettingsContent,
    )
}

/**
 * 双栏 Row（左右两栏的直接容器）。
 *
 * 刻意**不**走 [SettingsScaffold]/`HanimeScaffold` 的 M3 Scaffold：双栏自己就是
 * chrome（无全局顶栏、无 FAB、无 snackbar），套 Scaffold 只会继承三样不需要的东西 —
 * 内容边距（顶上凭空多出一截空白）、共享的回弹位移（右栏滚到边时左栏跟着晃，
 * 即"右滑动带动左栏"），以及 HanimePageSurface 的包浆。Row 直铺满幅，
 * 两栏各自独立、互不干扰。
 */
@Composable
private fun TwoPaneRow(
    backStack: TopLevelBackStack<HanimeScreen>,
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    rightMaxWidth: Dp,
    onNavigateToMpvSettings: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onOpenThemeAudit: () -> Unit,
    downloadSettingsContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            // M3E 内容 sheet 的窗底：与 MainScaffold 宽屏分支同规。
            // 左栏因此**不需要自己的底色**，透明即可（再铺一层会深一号、把层次压平）。
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        SettingsCategoryPane(
            title = stringResource(Res.string.settings),
            onBack = { backStack.navigateBackOrFallback(MainTab.Fallback.route) },
            selected = selected,
            // 双栏下左栏切换只改选中态，不动路由：路由一变，NavHost 会拿整棵双栏
            // 去播页面转场动画（左栏跟着滑出去），观感被破坏。缩窄时的路由修正见上。
            onSelect = onSelect,
        )
        // 右栏自己按表单档限宽并居中（外壳已放权）
        SettingsDetailPane(
            modifier = Modifier.weight(1f),
            title = stringResource(selected.spec.titleRes),
            maxWidth = rightMaxWidth,
        ) {
            SettingsCategoryContent(
                category = selected,
                onNavigateToMpvSettings = onNavigateToMpvSettings,
                onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                onOpenThemeAudit = onOpenThemeAudit,
                downloadSettingsContent = downloadSettingsContent,
            )
        }
    }
}

/**
 * 单个分类的内容 —— **宽窄两态共用**。
 *
 * 抽出来是因为它此前在"窄屏分类 entry"和"双栏右栏"里各写了一遍，改一处忘一处就会
 * 出现"宽屏能设置、窄屏不能"这类分叉。谁能用同样的方式渲染，就该只写一次。
 */
@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    onNavigateToMpvSettings: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onOpenThemeAudit: () -> Unit,
    downloadSettingsContent: @Composable () -> Unit,
) {
    val page = category.page
    if (page == null) {
        // 「播放器设置」：独立目的地、自带整屏（不是 HomeSettingsPage 的分类分支）。
        // 它是播放类设置的**同层兄弟**，双栏下必须能在左栏直接选中。
        // 其子页「MPV 高级设置」仍按既定规则 push 成全屏页（钻取后左栏不失去语义）。
        PlayerSettingsRouteScreen(onNavigateToMpvSettings = onNavigateToMpvSettings)
    } else {
        HomeSettingsRouteScreen(
            page = page,
            onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
            onOpenThemeAudit = onOpenThemeAudit,
            downloadSettingsContent = downloadSettingsContent,
        )
    }
}

/** 双栏默认选中的分类（`SettingsCategory` 的第一项）。 */
private val DefaultCategory: SettingsCategory = SettingsCategory.entries.first()

/**
 * 左栏：分类导航 + 栏内标题栏（**返回按钮 + 「设置」**）。
 *
 * 没有引入新的数据结构 —— [SettingsDestinationSpec] 枚举现成就是分类表，
 * 分类标题字符串已在其 `titleRes` 里。
 */
@Composable
private fun SettingsCategoryPane(
    title: String,
    onBack: () -> Unit,
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
            .fillMaxHeight(),
    ) {
        // 栏内标题栏用**真正的 HanimeTopAppBar**，不自绘 Row：返回按钮的
        // FilledIconButton 规格、标题字阶、64dp 高度全部由它统一，将来改一处全局跟着变。
        HanimeTopAppBar(
            title = title,
            onBack = onBack,
            colors = paneTopBarColors(),
            windowInsets = PaneHeaderInsets,
        )
        Column(
            // 宽屏双栏左栏钉死不滚：分类只有 8 项，任何正常窗口都放得下；
            // 且左栏若可滚，它会和右栏通过外层嵌套滚动链路互相带动
            // （右栏滚到边时左栏跟着晃）。右栏保持自己的独立滚动。
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
}

/**
 * 右栏：M3E 内容 sheet —— `surface` 底 + 左上 28dp 圆角（与首页宽屏同规）。
 *
 * 标题栏与内容**放在同一条限宽列里**：标题若挂在栏宽上、内容居中，窗口一宽
 * 两者就各走各的（标题贴栏左、内容浮中间）。同列之后标题恒与内容左缘对齐。
 */
@Composable
private fun SettingsDetailPane(
    title: String,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(HanimeDefaults.Corners.contentSheet)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
        ) {
            // 二级页标题（当前分类）：无返回按钮 —— 返回在左栏顶上，语义是「退出整个设置」。
            HanimeTopAppBar(
                title = title,
                onBack = null,
                colors = paneTopBarColors(),
                windowInsets = PaneHeaderInsets,
            )
            // weight 给内容一个**有界高度**，否则内部 LazyColumn 量到无界会崩。
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * 栏内标题栏配色：透明容器 + 常规标题色。
 *
 * 透明是必要的 —— 左栏坐在窗底 `surfaceContainerLow` 上、右栏坐在 sheet 的 `surface`
 * 上，标题栏若自带 `pageSurface` 底色，会在左栏留下一块颜色不同的横带
 * （与首页 Rail「透明坐底」同一处理）。
 */
@Composable
private fun paneTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * 设置分类（8 项，覆盖单栏卡片列表的全部入口；`HomeSettingsPage` 只覆盖其中 7 项）。
 *
 * 顺序沿用《导航重构与设计原型方案》§7.4 的分类表。
 *
 * **公开**是因为每个分类都是独立路由，`SharedTopNavigation` 要按路由声明自己是哪一项
 * （见 [SettingsRouteHost] 的 KDoc）。
 */
enum class SettingsCategory(
    val spec: SettingsDestinationSpec,
    val iconRes: DrawableResource,
    /**
     * 该分类渲染的 `HomeSettingsPage`。**为 null 表示它不是那一族** ——
     * 目前只有「播放器设置」：那 7 个 page 是 `HomeSettingsScreen` 内部分类分支，
     * 而播放器设置自带整屏（[PlayerSettingsRouteScreen]），是独立目的地。
     */
    val page: HomeSettingsPage? = null,
) {
    // 声明顺序 = 单栏 SettingsMainScreen 的展示顺序（双栏左栏直接按 entries 排），
    // 两处改一边必须改另一边。默认选中 entries.first()，即与单栏首项一致的「主题与外观」。
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
    VideoPlayback(
        spec = SettingsDestinationSpec.VideoPlayback,
        page = HomeSettingsPage.VideoPlayback,
        iconRes = Res.drawable.ic_video_settings,
    ),
    Player(
        spec = SettingsDestinationSpec.Player,
        // page = null：见上方 KDoc，内容走 PlayerSettingsRouteScreen。
        // 图标与单栏卡片列表「播放器设置」那一项保持一致（ic_dvr）。
        iconRes = Res.drawable.ic_dvr,
    ),
    NetworkDownload(
        spec = SettingsDestinationSpec.NetworkDownload,
        page = HomeSettingsPage.NetworkDownload,
        iconRes = Res.drawable.ic_captive_portal,
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
