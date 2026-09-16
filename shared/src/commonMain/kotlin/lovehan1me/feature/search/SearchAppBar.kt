package lovehan1me.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.search_video_hint
import lovehan1me.clear_checkin
import lovehan1me.back
import lovehan1me.advanced_search
import lovehan1me.ic_filter_list
import lovehan1me.ic_close
import lovehan1me.ic_arrow_back
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.IconButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.type
import lovehan1me.tag
import lovehan1me.sort_option
import lovehan1me.search_no_results
import lovehan1me.search_load_failed_with_reason
import lovehan1me.reset
import lovehan1me.release_date
import lovehan1me.recent_searches
import lovehan1me.pair_widely
import lovehan1me.duration
import lovehan1me.delete
import lovehan1me.brand
import lovehan1me.h_chan_speechless
import lovehan1me.h_chan_sad
import lovehan1me.ic_search
import lovehan1me.data.SettingsRepository
import lovehan1me.data.database.entity.SearchHistoryEntity
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeInfo.Companion.NORMAL
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberRandomLoadingHint
import lovehan1me.ui.transition.coverSharedElementKey
import lovehan1me.ui.adaptive.rememberVideoCardMinWidth
import lovehan1me.feature.search.SearchViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds



// ─────────────────────────────────────────────
// 搜索 App Bar
// ─────────────────────────────────────────────

@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenAdvancedSearch: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    showBack: Boolean = true,
    showFilterButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val kb = LocalSoftwareKeyboardController.current
    Surface(
        color =
            MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                // tab 模式（发现页）无上层可退，隐藏返回箭头（返回键会误删顶层 tab）。
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = stringResource(Res.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { onFocusChanged(it.isFocused) }
                            .padding(horizontal = 12.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            kb?.hide()
                            onSearch()
                        }),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(Res.string.search_video_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        })
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.clear_checkin),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 宽屏常驻筛选栏时此按钮隐藏，筛选走左栏。
                if (showFilterButton) {
                    FilledIconButton(onClick = onOpenAdvancedSearch) {
                        Icon(
                            painterResource(Res.drawable.ic_filter_list),
                            contentDescription = stringResource(Res.string.advanced_search)
                        )
                    }
                }
            }
        }
    }
}
