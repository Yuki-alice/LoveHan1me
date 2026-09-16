package lovehan1me.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.Res
import lovehan1me.recent_searches
import lovehan1me.duration
import lovehan1me.data.SettingsRepository
import lovehan1me.data.database.entity.SearchHistoryEntity
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.feature.search.SearchViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import lovehan1me.type
import lovehan1me.tag
import lovehan1me.sort_option
import lovehan1me.search_video_hint
import lovehan1me.search_no_results
import lovehan1me.search_load_failed_with_reason
import lovehan1me.reset
import lovehan1me.release_date
import lovehan1me.pair_widely
import lovehan1me.delete
import lovehan1me.clear_checkin
import lovehan1me.brand
import lovehan1me.back
import lovehan1me.advanced_search
import lovehan1me.h_chan_speechless
import lovehan1me.h_chan_sad
import lovehan1me.ic_search
import lovehan1me.ic_filter_list
import lovehan1me.ic_close
import lovehan1me.ic_arrow_back
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeInfo.Companion.NORMAL
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberRandomLoadingHint
import lovehan1me.ui.transition.coverSharedElementKey
import lovehan1me.ui.adaptive.rememberVideoCardMinWidth
import kotlinx.coroutines.flow.map

// ─────────────────────────────────────────────
// 搜索主屏幕
// ─────────────────────────────────────────────

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    FlowPreview::class
)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenAdvancedSearch: () -> Unit,
    initialQuery: String? = null,
    /**
     * tab 模式（发现页）隐藏返回箭头；子页/首页压栈进入时保留。见 SearchRouteScreen。
     */
    showBack: Boolean = true,
    /**
     * 发现页空参进入自动 page=1 空搜（浏览全部），对齐 hanime1.me/search。
     */
    autoBrowse: Boolean = false,
    /**
     * 宽屏常驻筛选栏时隐藏顶栏漏斗按钮（筛选直接在左栏改，不必再开弹窗）。
     */
    showFilterButton: Boolean = true,
) {
    val searchState by viewModel.searchStateFlow.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchFlow.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable(initialQuery) { mutableStateOf(initialQuery ?: "") }
    var histories by remember { mutableStateOf<List<SearchHistoryEntity>>(emptyList()) }
    var hasSearched by rememberSaveable(initialQuery) { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val criteriaCollapsedFraction = rememberSaveable { mutableFloatStateOf(0f) }
    var criteriaHeightPx by remember { mutableIntStateOf(0) }
    var isLeavingScreen by remember { mutableStateOf(false) }

    val refreshState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = viewModel.gridFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = viewModel.gridFirstVisibleItemScrollOffset,
    )
    val focusReq = remember { FocusRequester() }
    val focusMgr = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val showPlayedIndicator = SettingsRepository.showPlayedIndicator

    // 搜索执行
    fun executeSearch() {
        viewModel.getHanimeSearchResult(
            viewModel.page,
            viewModel.query,
            viewModel.genre,
            viewModel.sort,
            viewModel.broad,
            viewModel.getSearchDate(),
            viewModel.duration,
            tagFlatten(viewModel.tagMap),
            brandFlatten(viewModel.brandMap)
        )
    }

    fun doSearch(resetScroll: Boolean = false) {
        viewModel.page = 1
        viewModel.clearHanimeSearchResult()
        if (resetScroll) {
            viewModel.gridFirstVisibleItemIndex = 0
            viewModel.gridFirstVisibleItemScrollOffset = 0
            criteriaCollapsedFraction.floatValue = 0f
            scope.launch {
                gridState.scrollToItem(0)
            }
        }
        executeSearch()
    }

    fun hasAdvancedFilters(): Boolean {
        return viewModel.genre != null ||
                viewModel.sort != null ||
                viewModel.duration != null ||
                viewModel.getSearchDate() != null ||
                viewModel.tagMap.isNotEmpty() ||
                viewModel.brandMap.isNotEmpty() ||
                viewModel.broad
    }

    val hasSearchResults = searchResults.isNotEmpty() ||
            ((searchState as? PageLoadingState.Success)?.info?.isNotEmpty() == true)
    val filter = remember(
        viewModel.genre,
        viewModel.sort,
        viewModel.duration,
        viewModel.getSearchDate(),
        viewModel.broad,
        viewModel.tagMap.size,
        viewModel.brandMap.size,
    ) {
        SearchFilter(
            genre = viewModel.genre,
            sort = viewModel.sort,
            duration = viewModel.duration,
            releaseDate = viewModel.getSearchDate(),
            tagCount = tagFlatten(viewModel.tagMap).size,
            brandCount = brandFlatten(viewModel.brandMap).size,
            broad = viewModel.broad,
        )
    }

    // 初始 query 自动搜索
    LaunchedEffect(initialQuery) {
        val query = initialQuery?.trim().orEmpty()
        if (query.isNotBlank() && !hasSearched) {
            hasSearched = true
            searchQuery = query
            viewModel.query = query
            focusMgr.clearFocus()
            kb?.hide()
            viewModel.insertSearchHistory(SearchHistoryEntity(query = query))
            doSearch()
        }
    }
    // 高级搜索参数（genre/sort 等）自动搜索
    LaunchedEffect(Unit) {
        if (!hasSearched && hasAdvancedFilters()) {
            hasSearched = true
            focusMgr.clearFocus()
            kb?.hide()
            doSearch()
        }
    }
    // 发现页空参进入：无 query、无高级筛选时也自动 page=1 空搜（浏览全部 + 分页），
    // 空搜参数全 null 时服务端即返回全量列表。只触发一次（hasSearched 守卫）。
    LaunchedEffect(autoBrowse) {
        if (autoBrowse && !hasSearched && initialQuery.isNullOrBlank() && !hasAdvancedFilters()) {
            hasSearched = true
            focusMgr.clearFocus()
            kb?.hide()
            doSearch()
        }
    }
    // refreshTriggerFlow
    LaunchedEffect(Unit) {
        viewModel.refreshTriggerFlow.collect {
            hasSearched = true
            searchQuery = viewModel.query.orEmpty()
            doSearch(resetScroll = true)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (index, offset) ->
            viewModel.gridFirstVisibleItemIndex = index
            viewModel.gridFirstVisibleItemScrollOffset = offset
        }
    }

    // 历史建议防抖
    @OptIn(FlowPreview::class)
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(300.milliseconds)
            viewModel.loadAllSearchHistories(searchQuery).collect { histories = it }
        } else {
            viewModel.loadAllSearchHistories().collect { histories = it.take(10) }
        }
    }

    LaunchedEffect(initialQuery, hasSearchResults, isRefreshing, autoBrowse) {
        if (!isRefreshing && initialQuery.isNullOrBlank() && !hasAdvancedFilters() && !hasSearchResults) {
            // 发现页自动浏览模式不抢焦点：用户进来是看列表，不是立刻打字。
            if (!autoBrowse) focusReq.requestFocus()
        } else {
            focusMgr.clearFocus()
            kb?.hide()
        }
    }
    LaunchedEffect(searchState) {
        if (searchState !is PageLoadingState.Loading) isRefreshing = false
    }
    LaunchedEffect(filter.isNotEmpty(), hasSearchResults) {
        if (!filter.isNotEmpty() || !hasSearchResults) criteriaCollapsedFraction.floatValue = 0f
    }

    val criteriaScrollEnabled = filter.isNotEmpty() && hasSearchResults && criteriaHeightPx > 0
    val criteriaNestedScrollConnection = remember(criteriaScrollEnabled, criteriaHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!criteriaScrollEnabled || available.y == 0f) return Offset.Zero

                val oldCollapsedPx = criteriaCollapsedFraction.floatValue * criteriaHeightPx
                val newCollapsedPx = (oldCollapsedPx - available.y)
                    .coerceIn(0f, criteriaHeightPx.toFloat())
                if (newCollapsedPx == oldCollapsedPx) return Offset.Zero

                criteriaCollapsedFraction.floatValue = newCollapsedPx / criteriaHeightPx
                return Offset(x = 0f, y = oldCollapsedPx - newCollapsedPx)
            }
        }
    }

    fun clearSearchCriteria(
        clearGenre: Boolean = false,
        clearSort: Boolean = false,
        clearDuration: Boolean = false,
        clearReleaseDate: Boolean = false,
        clearTags: Boolean = false,
        clearBrands: Boolean = false,
        clearBroad: Boolean = false,
    ) {
        if (clearGenre) viewModel.genre = null
        if (clearSort) viewModel.sort = null
        if (clearDuration) viewModel.duration = null
        if (clearReleaseDate) {
            viewModel.year = null
            viewModel.month = null
            viewModel.approxTime = null
        }
        if (clearTags) viewModel.tagMap.clear()
        if (clearBrands) viewModel.brandMap.clear()
        if (clearBroad) viewModel.broad = false
        // 本次改动发生在筛选面板之外：常驻栏靠版本号才能重新读到新值（否则显示漂移）。
        viewModel.bumpFilterRevision()
        doSearch(resetScroll = true)
    }

    fun handleBack() {
        if (isLeavingScreen) return
        isLeavingScreen = true
        onBack()
    }

    // M2：BackHandler 是 Android-only；桌面无系统返回，应用内返回键已足够。
    // 失去的只是"搜索框聚焦时按返回先收键盘"（桌面无返回键概念，可接受）。
    // 如需补回，走 M-后续的 navigationevent expect 抽象。

    Column(modifier = Modifier
        .fillMaxSize()
        .background(HanimeDefaults.Colors.pageSurface)
    ) {
        SearchAppBar(searchQuery, { searchQuery = it }, onSearch = {
            val q = searchQuery.trim()
            val shouldSearch = q.isNotBlank() || hasAdvancedFilters()
            if (shouldSearch) {
                hasSearched = true; viewModel.query = q.ifBlank { null }
                focusMgr.clearFocus(); kb?.hide()
                if (q.isNotBlank()) {
                    viewModel.insertSearchHistory(
                        SearchHistoryEntity(query = q)
                    )
                }
                doSearch(resetScroll = true)
            }
        }, ::handleBack, onOpenAdvancedSearch, { isSearchFocused = it }, focusReq, showBack, showFilterButton)

        if (filter.isNotEmpty()) {
            CollapsibleSearchCriteria(
                collapsedFraction = criteriaCollapsedFraction,
                onHeightChanged = { criteriaHeightPx = it },
            ) { criteriaModifier ->
                ActiveSearchCriteria(
                    filter = filter,
                    viewModel = viewModel,
                    onClearAll = {
                        clearSearchCriteria(
                            clearGenre = true,
                            clearSort = true,
                            clearDuration = true,
                            clearReleaseDate = true,
                            clearTags = true,
                            clearBrands = true,
                            clearBroad = true,
                        )
                    },
                    onClearGenre = { clearSearchCriteria(clearGenre = true) },
                    onClearSort = { clearSearchCriteria(clearSort = true) },
                    onClearDuration = { clearSearchCriteria(clearDuration = true) },
                    onClearReleaseDate = { clearSearchCriteria(clearReleaseDate = true) },
                    onClearTagCount = { clearSearchCriteria(clearTags = true) },
                    onClearBrandCount = { clearSearchCriteria(clearBrands = true) },
                    onClearBroad = { clearSearchCriteria(clearBroad = true) },
                    modifier = criteriaModifier,
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                doSearch()
            },
            state = refreshState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(criteriaNestedScrollConnection),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            },
        ) {
            if (hasSearched) {
                // 已触发搜索，显示结果
                val showResults = searchResults.ifEmpty {
                    (searchState as? PageLoadingState.Success)?.info ?: emptyList()
                }
                Box(Modifier.fillMaxSize()) {
                    if (!isRefreshing) SearchStateIndicator(searchState, showResults.size)
                    if (showResults.isNotEmpty()) SearchResultsGrid(
                        showResults,
                        searchState,
                        showPlayedIndicator,
                        onOpenVideo,
                        { viewModel.page++; executeSearch() },
                        searchState !is PageLoadingState.NoMoreData,
                        gridState
                    )
                }
            } else if (searchQuery.isBlank() && histories.isNotEmpty()) {
                // 未搜索 + 搜索框为空 → 显示历史
                Column(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(Res.string.recent_searches),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    SearchHistoryList(
                        histories,
                        { query ->
                            searchQuery = query; hasSearched = true; viewModel.query =
                            query; focusMgr.clearFocus(); kb?.hide(); viewModel.insertSearchHistory(
                            SearchHistoryEntity(query = query)
                        ); doSearch(resetScroll = true)
                        },
                        { h ->
                            viewModel.deleteSearchHistory(h); histories =
                            histories.filter { it.id != h.id }
                        })
                }
            }
        }
    }
}
