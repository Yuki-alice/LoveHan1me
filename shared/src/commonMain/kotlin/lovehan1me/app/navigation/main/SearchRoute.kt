package lovehan1me.app.navigation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lovehan1me.feature.search.AdvancedSearchSheet
import lovehan1me.feature.search.AdvancedSearchSidePanel
import lovehan1me.feature.search.SearchScreen
import lovehan1me.feature.search.SearchViewModel
import lovehan1me.app.sharedViewModel
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.ui.adaptive.rememberContentWidthDp
import kotlinx.serialization.json.Json

@Composable
fun SearchRouteScreen(
    route: SearchRoute,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    /**
     * 发现页（DiscoverTab）复用本屏：无返回栈可退，隐藏顶栏返回箭头（设计稿 §1.6
     * 「返回 + 搜索 + 取消」中的返回在 tab 模式下无语义，TODO(P4) 到此落地）。
     */
    showBack: Boolean = true,
    /**
     * 发现页空参进入 = 浏览全部（对齐 hanime1.me/search 空参即全量 + 分页）。
     * 旧逻辑 hasSearched=false 时只显示历史/空白，发现页传 true 后自动 page=1 空搜。
     */
    autoBrowse: Boolean = false,
) {
    val viewModel: SearchViewModel = sharedViewModel(::SearchViewModel)
    var showAdvancedSearchSheet by remember { mutableStateOf(false) }

    LaunchedEffect(route.advancedSearchJson) {
        route.advancedSearchJson?.let { json ->
            runCatching { Json.decodeFromString<Map<String, String>>(json) }
                .onSuccess { params ->
                    params.forEach { (key, value) ->
                        when (key.uppercase()) {
                            "QUERY" -> viewModel.query = value
                            "GENRE" -> viewModel.genre = value
                            "SORT" -> viewModel.sort = value
                            "YEAR" -> viewModel.year = value.toIntOrNull()
                            "MONTH" -> viewModel.month = value.toIntOrNull()
                            "DURATION" -> viewModel.duration = value
                        }
                    }
                }
        }
    }

    if (showAdvancedSearchSheet) {
        AdvancedSearchSheet(
            viewModel = viewModel,
            onDismiss = { showAdvancedSearchSheet = false },
        )
    }

    // 宽屏（内容宽 ≥ 900dp）：左侧常驻筛选栏 + 右侧结果，对齐 hanime1.me/search；
    // 窄屏保持顶栏漏斗按钮 + 底栏弹窗。常驻栏自带搜索按钮，顶栏漏斗按钮隐藏。
    if (rememberContentWidthDp() >= 900.dp) {
        Row(modifier = Modifier.fillMaxSize()) {
            AdvancedSearchSidePanel(
                viewModel = viewModel,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                SearchScreen(
                    viewModel = viewModel,
                    initialQuery = route.query,
                    onBack = onBack,
                    onOpenVideo = onNavigateToVideo,
                    onOpenAdvancedSearch = { showAdvancedSearchSheet = true },
                    showBack = showBack,
                    autoBrowse = autoBrowse,
                    showFilterButton = false,
                )
            }
        }
        return
    }

    SearchScreen(
        viewModel = viewModel,
        initialQuery = route.query,
        onBack = onBack,
        onOpenVideo = onNavigateToVideo,
        onOpenAdvancedSearch = { showAdvancedSearchSheet = true },
        showBack = showBack,
        autoBrowse = autoBrowse,
    )
}
