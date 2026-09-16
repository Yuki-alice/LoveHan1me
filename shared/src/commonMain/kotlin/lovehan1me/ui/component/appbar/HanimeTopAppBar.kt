package lovehan1me.ui.component.appbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import lovehan1me.ui.component.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.back
import lovehan1me.ic_arrow_back
import lovehan1me.ui.theme.HanimeDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanimeTopAppBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    HanimeTopAppBar(
        title = {
            if (subtitle != null) {
                Column {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle()
                }
            } else {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onBack = onBack,
        modifier = modifier,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors,
        windowInsets = windowInsets,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanimeTopAppBar(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors? = null,
    /**
     * 该顶栏要自己申请的 window insets。
     *
     * **默认值适用于「顶栏挂在 Scaffold 的 topBar 槽」**（状态栏 inset 由它消费）。
     * 若顶栏被放进 **已由外层让出状态栏 inset 的容器**里（例如设置页双栏的栏内标题栏，
     * 外层 `HanimeScaffold` 的 `innerPadding` 已经推下来了），必须传 `WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)`
     * —— 否则会叠成双倍顶距，Android 上肉眼可见。
     */
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    HanimeTopAppBar(
        title = title,
        navigationIcon = {
            if (onBack != null) {
                FilledIconButton(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                    onClick = onBack,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = stringResource(Res.string.back),
                    )
                }
            }
        },
        modifier = modifier,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors,
        windowInsets = windowInsets,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanimeTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors? = null,
    /** 见上一条重载的同名参数：被外层让出 inset 时传 `WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)`。 */
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        modifier = modifier,
        colors = colors ?: topAppBarColors(
            containerColor = HanimeDefaults.Colors.pageSurface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        windowInsets = windowInsets,
    )
}
