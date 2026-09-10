package lovehan1me.app.navigation.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HanimeDefaults.Spacing.contentHorizontal),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 宽屏限宽：设置页是文本密集型表单，桌面全屏下若把行宽拉满，可读性会明显下降。
            // 上限大于可用宽度时（手机 / 窄窗）该约束不生效，故窄屏零影响。
            // 注意 widthIn 必须排在 fillMaxSize 之前，否则先被 fillMaxSize 拉满再去限制会冲突。
            Box(
                modifier = Modifier
                    .widthIn(max = HanimeDefaults.Widths.contentMax)
                    .fillMaxSize(),
            ) {
                content()
            }
        }
    }
}
