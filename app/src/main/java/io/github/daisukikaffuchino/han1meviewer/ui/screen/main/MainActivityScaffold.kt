package io.github.daisukikaffuchino.han1meviewer.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.MainDrawerDestination
import io.github.daisukikaffuchino.han1meviewer.ui.preview.ComponentPreview
import io.github.daisukikaffuchino.han1meviewer.ui.theme.HanimeDefaults
import io.github.daisukikaffuchino.utils.VibrationUtil
import kotlinx.coroutines.launch

@Composable
fun MainActivityScaffold(
    drawerState: DrawerState,
    drawerEnabled: Boolean,
    permanentDrawer: Boolean,
    applyHorizontalSafeInsets: Boolean,
    selectedDestination: MainDrawerDestination?,
    avatarUrl: String?,
    username: String?,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    currentSite: String,
    checkInEnabled: Boolean,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onSwitchSiteClick: () -> Unit,
    onDrawerItemSelected: (MainDrawerDestination) -> Boolean,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawerFraction by animateFloatAsState(
        targetValue = if (drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open) 1f else 0f,
        label = "drawer_fraction",
    )
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember {
        movableContentOf {
            currentContent()
        }
    }
    val horizontalSafeInsetsModifier = if (applyHorizontalSafeInsets) {
        Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
        )
    } else {
        Modifier
    }

    val drawerContent: @Composable ColumnScope.() -> Unit = {
        MainDrawerContent(
            selectedDestination = selectedDestination,
            avatarUrl = avatarUrl,
            username = username,
            isLoggedIn = isLoggedIn,
            isLoading = isLoading,
            currentSite = currentSite,
            checkInEnabled = checkInEnabled,
            onAvatarClick = onAvatarClick,
            onAvatarLongClick = onAvatarLongClick,
            onSwitchSiteClick = onSwitchSiteClick,
            onDrawerItemSelected = onDrawerItemSelected,
        )
    }

    if (permanentDrawer) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HanimeDefaults.Colors.pageSurface)
                .then(horizontalSafeInsetsModifier),
        ) {
            PermanentDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                windowInsets = WindowInsets(0, 0, 0, 0),
                content = drawerContent,
            )
            movableContent()
        }
    } else {
        ModalNavigationDrawer(
            modifier = Modifier
                .fillMaxSize()
                .background(HanimeDefaults.Colors.pageSurface)
                .then(horizontalSafeInsetsModifier),
            gesturesEnabled = drawerEnabled,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    content = drawerContent,
                )
            },
        ) {
            MainDrawerBody(drawerFraction = drawerFraction, content = movableContent)

            BackHandler(
                enabled = drawerState.currentValue == DrawerValue.Open ||
                    drawerState.targetValue == DrawerValue.Open,
            ) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun MainDrawerContent(
    selectedDestination: MainDrawerDestination?,
    avatarUrl: String?,
    username: String?,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    currentSite: String,
    checkInEnabled: Boolean,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onSwitchSiteClick: () -> Unit,
    onDrawerItemSelected: (MainDrawerDestination) -> Boolean,
) {
    MainDrawerHeader(
        avatarUrl = avatarUrl,
        username = username,
        isLoggedIn = isLoggedIn,
        isLoading = isLoading,
        currentSite = currentSite,
        onAvatarClick = onAvatarClick,
        onAvatarLongClick = onAvatarLongClick,
        onSwitchSiteClick = onSwitchSiteClick,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MainDrawerPrimaryItems(
            selectedDestination = selectedDestination,
            onDrawerItemSelected = onDrawerItemSelected,
            checkInEnabled = checkInEnabled,
        )
        MainDrawerSection(
            titleRes = R.string.my_list,
            items = listOf(
                MainDrawerDestination.WatchLater,
                MainDrawerDestination.FavVideo,
                MainDrawerDestination.Playlist,
                MainDrawerDestination.Subscription,
            ),
            selectedDestination = selectedDestination,
            onItemClick = { onDrawerItemSelected(it) },
        )
        MainDrawerSection(
            titleRes = R.string.video,
            items = listOf(
                MainDrawerDestination.WatchHistory,
                MainDrawerDestination.Download,
            ),
            selectedDestination = selectedDestination,
            onItemClick = { onDrawerItemSelected(it) },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MainDrawerBody(
    drawerFraction: Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HanimeDefaults.Colors.pageSurface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - (0.03f * drawerFraction)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (0.08f * drawerFraction)
                },
        ) {
            content()
            if (drawerFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.14f * drawerFraction)),
                )
            }
        }
    }
}

@Composable
private fun MainDrawerPrimaryItems(
    selectedDestination: MainDrawerDestination?,
    onDrawerItemSelected: (MainDrawerDestination) -> Boolean,
    checkInEnabled: Boolean,
) {
    val view = LocalView.current
    val primaryItems = buildList {
        add(MainDrawerDestination.Home)
        add(MainDrawerDestination.Settings)
        if (checkInEnabled) add(MainDrawerDestination.DailyCheckIn)
    }
    Column {
        primaryItems.forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.titleRes)) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.titleRes),
                    )
                },
                selected = selectedDestination == item,
                onClick = {
                    VibrationUtil.performHapticFeedback(view)
                    onDrawerItemSelected(item)
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

@Composable
private fun MainDrawerSection(
    titleRes: Int,
    items: List<MainDrawerDestination>,
    selectedDestination: MainDrawerDestination?,
    onItemClick: (MainDrawerDestination) -> Unit,
) {
    val view = LocalView.current
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp),
    )
    Column {
        items.forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.titleRes)) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.titleRes),
                    )
                },
                selected = selectedDestination == item,
                onClick = {
                    VibrationUtil.performHapticFeedback(view)
                    onItemClick(item)
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
private fun MainActivityScaffoldPreview() {
    ComponentPreview {
        MainActivityScaffold(
            drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
            drawerEnabled = true,
            permanentDrawer = true,
            applyHorizontalSafeInsets = true,
            selectedDestination = MainDrawerDestination.Home,
            avatarUrl = null,
            username = "Han1meViewer",
            isLoggedIn = true,
            isLoading = false,
            currentSite = "https://hanime1.me/",
            checkInEnabled = true,
            onAvatarClick = {},
            onAvatarLongClick = {},
            onSwitchSiteClick = {},
            onDrawerItemSelected = { true },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }
}
