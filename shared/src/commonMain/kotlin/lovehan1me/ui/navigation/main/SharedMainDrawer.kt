package lovehan1me.ui.navigation.main

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.hanime_app_name
import lovehan1me.my_account
import lovehan1me.ic_person
import lovehan1me.login
import lovehan1me.logic.platform.appVersionDisplay

/**
 * M2：三端共享的最小抽屉（对标 `:app` `MainActivityScaffold` + `MainDrawerHeader` 的
 * 导航部分；头像/站点切换/打卡开关等 Android 富信息头留 `:app`）。
 *
 * 条目即 [MainDrawerDestination] 全集；点击经 [onDestinationClick] 交给调用方
 *（App 做登录拦截 + 关抽屉 + `addTopLevel`）。账号区只分登录/未登录两态，
 * 点透给 [onAccountClick] / [onLoginClick]。
 */
@Composable
fun SharedMainDrawer(
    selected: MainDrawerDestination?,
    isLoggedIn: Boolean,
    username: String?,
    onDestinationClick: (MainDrawerDestination) -> Unit,
    onAccountClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.hanime_app_name),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        Text(
            text = appVersionDisplay(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        MainDrawerDestination.entries.forEach { destination ->
            NavigationDrawerItem(
                label = { Text(stringResource(destination.titleRes)) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                    )
                },
                selected = destination == selected,
                onClick = { onDestinationClick(destination) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        NavigationDrawerItem(
            label = {
                Text(
                    if (isLoggedIn) username ?: stringResource(Res.string.my_account)
                    else stringResource(Res.string.login)
                )
            },
            icon = {
                Icon(
                    painter = painterResource(Res.drawable.ic_person),
                    contentDescription = null,
                )
            },
            selected = false,
            onClick = { if (isLoggedIn) onAccountClick() else onLoginClick() },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
