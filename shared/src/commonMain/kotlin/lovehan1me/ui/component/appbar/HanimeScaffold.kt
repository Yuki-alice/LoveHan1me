package lovehan1me.ui.component.appbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import lovehan1me.ui.component.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import lovehan1me.Res
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.verticalBounce
import lovehan1me.ui.theme.HanimeDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanimeScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentHorizontalPadding: Dp = HanimeDefaults.Spacing.contentHorizontal,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    /**
     * 顶栏自己申请的 window insets。默认吃状态栏（顶栏挂独立 Scaffold 时正确）。
     *
     * 若调用点已经坐在外层 Scaffold 的内容槽里（外层 innerPadding 推过状态栏，
     * 如 MainScaffold 内的所有页面），必须传 `WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)`，
     * 否则顶上叠出双倍空白。
     *
     * 注意：该参数必须放在 `content` 之前 —— 几乎所有调用点都用 trailing lambda
     * 传 content，非函数类型的参数跟在后面会断掉 trailing 链，全员编译失败。
     */
    topBarWindowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    HanimeScaffold(
        topBar = {
            HanimeTopAppBar(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                actions = actions,
                scrollBehavior = scrollBehavior,
                windowInsets = topBarWindowInsets,
            )
        },
        modifier = modifier,
        contentHorizontalPadding = contentHorizontalPadding,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanimeScaffold(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentHorizontalPadding: Dp = HanimeDefaults.Spacing.contentHorizontal,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    /** 见上一个重载的同名参数：在 chrome 内必须传 `WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)`。 */
    topBarWindowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    HanimeScaffold(
        topBar = {
            HanimeTopAppBar(
                title = title,
                onBack = onBack,
                actions = actions,
                scrollBehavior = scrollBehavior,
                windowInsets = topBarWindowInsets,
            )
        },
        modifier = modifier,
        contentHorizontalPadding = contentHorizontalPadding,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
fun HanimeScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentHorizontalPadding: Dp = HanimeDefaults.Spacing.contentHorizontal,
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = HanimeDefaults.Colors.pageSurface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .verticalBounce()
                .fillMaxSize(),
        ) {
            HanimePageSurface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = contentHorizontalPadding),
            ) {
                content(PaddingValues())
            }
        }
    }
}

