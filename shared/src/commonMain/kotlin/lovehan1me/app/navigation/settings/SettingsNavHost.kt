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

@Composable
fun SettingsScaffold(
    backStack: TopLevelBackStack<HanimeScreen>,
    destination: SettingsDestinationSpec,
    fallbackDestination: HanimeScreen,
    onNavigateBack: (() -> Boolean)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
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
        if (!backStack.removeLast()) {
            backStack.add(fallbackDestination, launchSingleTop = true)
        }
    }

    HanimeScaffold(
        topBar = {
            if (destination.showToolbar) {
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
            modifier = Modifier.padding(horizontal = HanimeDefaults.Spacing.contentHorizontal),
        ) {
            content()
        }
    }
}
