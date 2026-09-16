package lovehan1me.app.navigation.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.ui.adaptive.ContentColumn
import lovehan1me.ui.component.appbar.HanimeTopAppBar
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.app.navigation.main.HanimeScreen
import lovehan1me.app.navigation.main.TopLevelBackStack
import lovehan1me.ui.theme.HanimeDefaults

/**
 * 设置流统一的返回动作：能弹栈就弹，弹不动（深链直接进设置、栈里只剩一项）时
 * 落到 [fallbackDestination]。
 *
 * 抽出来是因为**双栏形态下返回按钮长在左栏栏内**，不再由 [SettingsScaffold] 的顶栏
 * 提供 —— 两处必须是同一套语义，否则双栏与单栏的返回行为会分叉。
 */
internal fun TopLevelBackStack<HanimeScreen>.navigateBackOrFallback(
    fallbackDestination: HanimeScreen,
) {
    if (!removeLast()) add(fallbackDestination, launchSingleTop = true)
}

@Composable
fun SettingsScaffold(
    backStack: TopLevelBackStack<HanimeScreen>,
    destination: SettingsDestinationSpec,
    fallbackDestination: HanimeScreen,
    onNavigateBack: (() -> Boolean)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    /**
     * 是否渲染全局顶栏。
     *
     * 双栏形态（[SettingsRouteHost]）必须传 `false`：双栏把标题**按栏拆开**了 ——
     * 左栏顶部是「返回 + 设置」，右栏顶部是当前分类（二级页）的标题。
     * 全局顶栏会横跨两栏、切掉右栏 sheet 的 28dp 顶角，还会多出第三条标题。
     */
    showTopBar: Boolean = true,
    /**
     * 内容区左右外边距。
     *
     * 双栏形态（[SettingsRouteHost]）必须传 `0.dp`：双栏自己就是**整窗布局** ——
     * 左栏贴窗左缘、右栏 sheet 顶到窗右缘。留着 16dp 页边距会在窗侧露出一圈页底色，
     * 把 M3E 内容 sheet 的满幅观感切掉（首页宽屏分支就是满幅、无外边距）。
     */
    contentHorizontalPadding: Dp = HanimeDefaults.Spacing.contentHorizontal,
    /**
     * 内容区最大宽度。默认 [HanimeDefaults.Widths.formMax]（单栏表单页的可读行宽）。
     *
     * 双栏形态（P5.5）必须传 [Dp.Unspecified]：外壳的限宽会**连同左栏 280dp 一起**算进
     * 表单行宽，导致右栏被挤窄。双栏由右栏自己限宽。
     */
    contentMaxWidth: Dp = HanimeDefaults.Widths.formMax,
    content: @Composable () -> Unit,
) {
    fun navigateBack() {
        if (onNavigateBack?.invoke() == true) return
        backStack.navigateBackOrFallback(fallbackDestination)
    }

    HanimeScaffold(
        topBar = {
            if (showTopBar && destination.showToolbar) {
                HanimeTopAppBar(
                    title = stringResource(destination.titleRes),
                    onBack = ::navigateBack,
                    actions = actions,
                )
            }
        },
        contentHorizontalPadding = 0.dp,
        floatingActionButton = floatingActionButton,
    ) {
        // 宽屏限宽：设置页是文本密集型表单，桌面全屏下若把行宽拉满，可读性会明显下降。
        // 上限大于可用宽度时（手机 / 窄窗）该约束不生效，故窄屏零影响。
        ContentColumn(
            maxWidth = contentMaxWidth,
            modifier = Modifier.padding(horizontal = contentHorizontalPadding),
        ) {
            content()
        }
    }
}
