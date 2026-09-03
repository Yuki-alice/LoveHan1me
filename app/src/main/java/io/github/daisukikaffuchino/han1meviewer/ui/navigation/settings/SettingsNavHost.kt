package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.ui.component.appbar.HanimeTopAppBar
import io.github.daisukikaffuchino.han1meviewer.ui.component.appbar.HanimeScaffold
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.HanimeScreen
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.TopLevelBackStack
import io.github.daisukikaffuchino.han1meviewer.ui.theme.HanimeDefaults

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
        ) {
            content()
        }
    }
}
