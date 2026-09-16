package lovehan1me.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import lovehan1me.ui.component.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.appearance_and_figure
import lovehan1me.core.domain.model.SearchFilterPreset
import lovehan1me.core.domain.model.SearchFilterSnapshot
import lovehan1me.data.SettingsRepository
import lovehan1me.brand
import lovehan1me.characteristics
import lovehan1me.duration
import lovehan1me.relationship
import lovehan1me.sex_position
import lovehan1me.sort_option
import lovehan1me.story_location
import lovehan1me.story_plot
import lovehan1me.tag
import lovehan1me.type
import lovehan1me.video_attr
import lovehan1me.switch_to_year
import lovehan1me.switch_to_year_month
import lovehan1me.type
import lovehan1me.tag
import lovehan1me.specific_y_m
import lovehan1me.sort_option
import lovehan1me.search_options_tips
import lovehan1me.search
import lovehan1me.save
import lovehan1me.reset
import lovehan1me.release_date
import lovehan1me.pair_widely_alert
import lovehan1me.pair_widely
import lovehan1me.duration
import lovehan1me.cancel
import lovehan1me.approximate_range
import lovehan1me.advanced_search_combination
import lovehan1me.advanced_search
import lovehan1me.ic_search
import lovehan1me.filter_all
import lovehan1me.filter_count
import lovehan1me.filter_no_match
import lovehan1me.filter_search_in_category
import lovehan1me.filter_selected_count
import lovehan1me.core.constant.SEARCH_YEAR_RANGE_END
import lovehan1me.core.constant.SEARCH_YEAR_RANGE_START
import lovehan1me.data.DatabaseRepo
import lovehan1me.site.hanime1.HanimeAdvancedSearchRepo
import lovehan1me.data.database.entity.HanimeAdvancedSearchHistoryEntity
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.core.domain.model.SearchOption.Companion.flatten
import lovehan1me.data.database.entity.toSnapshot
import lovehan1me.ui.component.SelectableTag
import lovehan1me.ui.component.SettingChoiceItem
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.model.AdvancedSearchDialogState
import lovehan1me.ui.model.SearchScopeSection
import lovehan1me.feature.search.SearchViewModel
import kotlinx.coroutines.launch

/**
 * P6c：R.string 标题 id 与 scope 名（tags Map 的 key）成对；原 SearchOption.Companion.get(Int)
 * operator 已随模型下沉删除，这里直接以 scope 名索引。
 */
private val advancedSearchTagScopes = listOf(
    Res.string.video_attr to "video_attributes",
    Res.string.relationship to "character_relationships",
    Res.string.characteristics to "characteristics",
    Res.string.appearance_and_figure to "appearance_and_figure",
    Res.string.story_plot to "story_plot",
    Res.string.story_location to "story_location",
    Res.string.sex_position to "sex_positions",
)

/**
 * 一个筛选维度在面板上的全部信息（展示 + 两种交互）。
 *
 * 抽它的直接原因：底栏弹窗 [AdvancedSearchSheet] 与宽屏常驻栏 [AdvancedSearchSidePanel]
 * 原本各抄了一份「算 6 个标题 → 拼 6 组回调」的约 110 行，任何维度增删都要改两处。
 */
private class FilterBlockSpec(
    val label: String,
    val value: String?,
    val checked: Boolean,
    val onClear: () -> Unit,
    val onOpen: () -> Unit,
)

/** 6 个维度的维度名（已 stringResource），在弹窗/常驻栏内共享一份，避免重复取串。 */
private class FilterLabels(
    val genre: String,
    val sort: String,
    val tag: String,
    val brand: String,
    val releaseDate: String,
    val duration: String,
)

@Composable
private fun rememberFilterLabels(): FilterLabels {
    return FilterLabels(
        genre = stringResource(Res.string.type),
        sort = stringResource(Res.string.sort_option),
        tag = stringResource(Res.string.tag),
        brand = stringResource(Res.string.brand),
        releaseDate = stringResource(Res.string.release_date),
        duration = stringResource(Res.string.duration),
    )
}

private fun optionValue(options: List<SearchOption>, searchKey: String?): String? =
    options.firstOrNull { it.searchKey == searchKey }?.value

/**
 * 把 [SearchViewModel] 的当前筛选态映射成 6 个 [FilterBlockSpec]。
 *
 * 必须是 @Composable：维度值都是普通 `var`（不是 State），靠调用方传入的 [selectionVersion]
 * 被读取来注册 State 依赖 —— 参数求值发生在组合期，改版本号即触发本函数重算。
 *
 * @param openDialog 由调用方决定弹窗挂在哪（底栏弹窗 or 常驻栏各有自己的 dialogState）。
 */
@Composable
private fun buildFilterBlocks(
    viewModel: SearchViewModel,
    labels: FilterLabels,
    selectionVersion: Int,
    tagScopes: List<SearchScopeSection>,
    brandScope: List<SearchScopeSection>,
    openDialog: (AdvancedSearchDialogState) -> Unit,
): List<FilterBlockSpec> {
    val tagKeys = viewModel.tagMap.flatten()
    val brandKeys = viewModel.brandMap.flatten()
    val selectedTagOptions = tagScopes.flatMap { it.options }
        .filterTo(mutableSetOf()) { it.searchKey in tagKeys }
    val selectedBrandOptions = viewModel.brands
        .filterTo(mutableSetOf()) { it.searchKey in brandKeys }

    return listOf(
        FilterBlockSpec(
            label = labels.genre,
            value = optionValue(viewModel.genres, viewModel.genre),
            checked = viewModel.genre != null,
            onClear = { viewModel.genre = null },
            onOpen = {
                openDialog(
                    AdvancedSearchDialogState.SingleChoice(
                        key = "genre",
                        titleRes = Res.string.type,
                        options = viewModel.genres,
                        selectedIndex = viewModel.genres.indexOfFirst { it.searchKey == viewModel.genre },
                        onSelect = { option -> viewModel.genre = option.searchKey },
                        onReset = { viewModel.genre = null },
                    )
                )
            },
        ),
        FilterBlockSpec(
            label = labels.sort,
            value = optionValue(viewModel.sortOptions, viewModel.sort),
            checked = viewModel.sort != null,
            onClear = { viewModel.sort = null },
            onOpen = {
                openDialog(
                    AdvancedSearchDialogState.SingleChoice(
                        key = "sort",
                        titleRes = Res.string.sort_option,
                        options = viewModel.sortOptions,
                        selectedIndex = viewModel.sortOptions.indexOfFirst { it.searchKey == viewModel.sort },
                        onSelect = { option -> viewModel.sort = option.searchKey },
                        onReset = { viewModel.sort = null },
                    )
                )
            },
        ),
        FilterBlockSpec(
            label = labels.tag,
            value = if (tagKeys.isNotEmpty()) countLabel(tagKeys.size) else null,
            checked = viewModel.tagMap.isNotEmpty(),
            onClear = { viewModel.tagMap.clear() },
            onOpen = {
                openDialog(
                    AdvancedSearchDialogState.MultiChoice(
                        key = "tag",
                        titleRes = Res.string.tag,
                        scopes = tagScopes,
                        selected = selectedTagOptions,
                        broad = viewModel.broad,
                        showBroad = true,
                        onSave = { selected, broad ->
                            viewModel.broad = broad
                            viewModel.tagMap = groupSelectedTagOptions(selected, viewModel.tags)
                        },
                        onReset = { viewModel.tagMap.clear() },
                    )
                )
            },
        ),
        FilterBlockSpec(
            label = labels.brand,
            value = if (brandKeys.isNotEmpty()) countLabel(brandKeys.size) else null,
            checked = viewModel.brandMap.isNotEmpty(),
            onClear = { viewModel.brandMap.clear() },
            onOpen = {
                openDialog(
                    AdvancedSearchDialogState.MultiChoice(
                        key = "brand",
                        titleRes = Res.string.brand,
                        scopes = brandScope,
                        selected = selectedBrandOptions,
                        broad = false,
                        // 品牌没有「宽泛配对」语义（站点只对 tags[] 生效），显示开关会误导。
                        showBroad = false,
                        onSave = { selected, _ -> viewModel.brandMap = mutableMapOf(0 to selected) },
                        onReset = { viewModel.brandMap.clear() },
                    )
                )
            },
        ),
        FilterBlockSpec(
            label = labels.releaseDate,
            value = viewModel.getSearchDate(),
            checked = viewModel.year != null || viewModel.month != null || viewModel.approxTime != null,
            onClear = {
                viewModel.year = null
                viewModel.month = null
                viewModel.approxTime = null
            },
            onOpen = {
                openDialog(
                    AdvancedSearchDialogState.ReleaseDate(
                        key = "date",
                        options = viewModel.timeList,
                        initialApproximate = viewModel.approxTime,
                        initialYear = viewModel.year,
                        initialMonth = viewModel.month,
                        onSaveApproximate = { searchKey ->
                            viewModel.approxTime = searchKey
                            viewModel.year = null
                            viewModel.month = null
                        },
                        onSaveSpecific = { year, month ->
                            viewModel.year = year
                            viewModel.month = month
                            viewModel.approxTime = null
                        },
                        onReset = {
                            viewModel.year = null
                            viewModel.month = null
                            viewModel.approxTime = null
                        },
                    )
                )
            },
        ),
        FilterBlockSpec(
            label = labels.duration,
            value = optionValue(viewModel.durations, viewModel.duration),
            checked = viewModel.duration != null,
            onClear = { viewModel.duration = null },
            onOpen = {
                openDialog(
                    AdvancedSearchDialogState.SingleChoice(
                        key = "duration",
                        titleRes = Res.string.duration,
                        options = viewModel.durations,
                        selectedIndex = viewModel.durations.indexOfFirst { it.searchKey == viewModel.duration },
                        onSelect = { option -> viewModel.duration = option.searchKey },
                        onReset = { viewModel.duration = null },
                    )
                )
            },
        ),
    )
}

/**
 * 多选维度的值文案：只给个数。逐个列出「标签 A、标签 B、…」在半栏宽里必然换到多行，
 * 反而看不清，个数 + 维度名已足够用户判断是否要去改。
 */
@Composable
private fun countLabel(count: Int): String = stringResource(Res.string.filter_count, count)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchSheet(
    viewModel: SearchViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val histories by remember {
        HanimeAdvancedSearchRepo.getSearchHistories()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    var dialogState by remember { mutableStateOf<AdvancedSearchDialogState?>(null) }
    var selectionVersion by remember { mutableIntStateOf(0) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.Expanded,
        ),
    )
    val labels = rememberFilterLabels()
    val presets by SettingsRepository.searchFilterPresetsFlow.collectAsStateWithLifecycle()
    val tagScopes = remember(viewModel.tags) { buildTagScopeSections(viewModel.tags) }
    val brandScope = remember(viewModel.brands) {
        listOf(SearchScopeSection(Res.string.brand, viewModel.brands))
    }

    fun updateSelection(block: () -> Unit) {
        block()
        selectionVersion++
    }

    val blocks = buildFilterBlocks(
        viewModel = viewModel,
        labels = labels,
        selectionVersion = selectionVersion,
        tagScopes = tagScopes,
        brandScope = brandScope,
        openDialog = { dialogState = it },
    )
    val selectedDimensionCount = blocks.count { it.checked }

    AdvancedSearchDialogHost(
        dialogState = dialogState,
        onDismiss = { dialogState = null },
        updateSelection = ::updateSelection,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.advanced_search),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                if (histories.isNotEmpty()) {
                    item {
                        AdvancedSearchHistorySection(
                            histories = histories,
                            onDeleteHistory = { history ->
                                scope.launch {
                                    HanimeAdvancedSearchRepo.deleteHistory(history.id)
                                }
                            },
                            onSelectHistory = { history ->
                                applySnapshotAndSearch(viewModel, history.toSnapshot())
                                onDismiss()
                            },
                        )
                    }
                }
                item {
                    SearchPresetSection(
                        presets = presets,
                        // 至少要选中一个筛选维度才让存：空条件存成预设没意义，纯关键词搜索由历史负责。
                        // 判据取自 blocks（它已挂上 selectionVersion 的重组 key），而不是
                        // currentFilterSnapshot()——后者读的是普通 var，不会触发重组，会拿到过期结果。
                        canSave = selectedDimensionCount > 0,
                        onApply = { preset -> applySnapshotAndSearch(viewModel, preset.snapshot) },
                        onSave = { name ->
                            scope.launch {
                                SearchFilterPresetStore.save(name, viewModel.currentFilterSnapshot())
                            }
                        },
                        onDelete = { preset ->
                            scope.launch { SearchFilterPresetStore.remove(preset.id) }
                        },
                        columns = 2,
                    )
                }
                item {
                    AdvancedSearchFiltersSection(
                        blocks = blocks,
                        // 底栏弹窗是满宽：两列（默认值），一行一个反而过高。
                        columns = 2,
                        onClear = ::updateSelection,
                    )
                }
                item {
                    AdvancedSearchActionSection(
                        selectedCount = selectedDimensionCount,
                        onReset = {
                            updateSelection {
                                blocks.forEach { it.onClear() }
                                viewModel.broad = false
                            }
                        },
                        onSearch = {
                            triggerSearchWithHistory(viewModel)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/**
 * 宽屏常驻筛选栏（内容宽 ≥ 900dp）：与 [AdvancedSearchSheet] 同一套筛选能力，
 * 不经底栏弹窗，直接嵌在发现页左侧（对齐 hanime1.me/search 的常驻筛选区）。
 *
 * 自带 dialogState：各芯片点开的单选/多选/日期弹窗挂在这里，与底栏弹窗互不干扰。
 * 点搜索按钮即 triggerNewSearch（经 refreshTriggerFlow 驱动结果网格刷新）+ 写高级搜索历史。
 */
@Composable
fun AdvancedSearchSidePanel(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    var dialogState by remember { mutableStateOf<AdvancedSearchDialogState?>(null) }
    var selectionVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val labels = rememberFilterLabels()
    val presets by SettingsRepository.searchFilterPresetsFlow.collectAsStateWithLifecycle()
    val tagScopes = remember(viewModel.tags) { buildTagScopeSections(viewModel.tags) }
    val brandScope = remember(viewModel.brands) {
        listOf(SearchScopeSection(Res.string.brand, viewModel.brands))
    }

    fun updateSelection(block: () -> Unit) {
        block()
        selectionVersion++
    }

    val blocks = buildFilterBlocks(
        viewModel = viewModel,
        labels = labels,
        // 本地 selectionVersion 管面板内改动；viewModel.filterRevision 管面板外改动
        // （右栏「已生效条件」chip 逐项移除、历史/预设恢复…），两者任一变化都要重读。
        selectionVersion = selectionVersion + viewModel.filterRevision,
        tagScopes = tagScopes,
        brandScope = brandScope,
        openDialog = { dialogState = it },
    )
    val selectedDimensionCount = blocks.count { it.checked }

    AdvancedSearchDialogHost(
        dialogState = dialogState,
        onDismiss = { dialogState = null },
        updateSelection = ::updateSelection,
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.advanced_search),
            style = MaterialTheme.typography.titleMedium,
        )
        SearchPresetSection(
            presets = presets,
            // 与底栏弹窗同一判据：至少一个筛选维度被选中才允许保存。
            canSave = selectedDimensionCount > 0,
            onApply = { preset -> applySnapshotAndSearch(viewModel, preset.snapshot) },
            onSave = { name ->
                scope.launch {
                    SearchFilterPresetStore.save(name, viewModel.currentFilterSnapshot())
                }
            },
            onDelete = { preset ->
                scope.launch { SearchFilterPresetStore.remove(preset.id) }
            },
            // 240dp 常驻栏：单列，两列会把预设名压到只剩两三个字。
            columns = 1,
        )
        AdvancedSearchFiltersSection(
            blocks = blocks,
            // 常驻栏只有 240dp 宽：两列会把「发行日期: 2026 年 3 月」压成两行截断，
            // 单列 + 值独占一行反而能让整个栏更窄。
            columns = 1,
            onClear = ::updateSelection,
        )
        AdvancedSearchActionSection(
            selectedCount = selectedDimensionCount,
            onReset = {
                updateSelection {
                    blocks.forEach { it.onClear() }
                    viewModel.broad = false
                }
            },
            onSearch = { triggerSearchWithHistory(viewModel) },
        )
    }
}

/**
 * 应用一份筛选快照 → 立刻重搜 → 记一条高级搜索历史。
 *
 * 历史卡片与预设芯片的"点击即应用"走同一条路；把 tagMap/brandMap → [SearchOption] 的搬运
 * 收在这里，免得两份 UI（底栏弹窗 / 常驻栏）各抄一遍。
 */
private fun applySnapshotAndSearch(viewModel: SearchViewModel, snapshot: SearchFilterSnapshot) {
    viewModel.restoreSearchMap(snapshot)
    triggerSearchWithHistory(viewModel)
}

/**
 * 用**当前**筛选态重搜并写高级搜索历史（两处「搜索」按钮用）。
 *
 * 与 [applySnapshotAndSearch] 分开，是为了不改变「搜索按钮直接读当前态」这一既有语义
 * （不绕 `currentFilterSnapshot()` 往返一遍）。
 */
private fun triggerSearchWithHistory(viewModel: SearchViewModel) {
    viewModel.triggerNewSearch()
    viewModel.insertAdvancedSearchHistory(
        viewModel.query,
        viewModel.genre,
        viewModel.sort,
        viewModel.broad,
        viewModel.getSearchDate(),
        viewModel.duration,
        viewModel.tagMap.flatten().map { SearchOption(searchKey = it) }.toSet(),
        viewModel.brandMap.flatten().map { SearchOption(searchKey = it) }.toSet(),
    )
}

@Composable
private fun AdvancedSearchDialogHost(
    dialogState: AdvancedSearchDialogState?,
    onDismiss: () -> Unit,
    updateSelection: (block: () -> Unit) -> Unit,
) {
    when (dialogState) {
        null -> Unit
        is AdvancedSearchDialogState.SingleChoice -> AdvancedSearchSingleChoiceDialog(
            state = dialogState,
            onDismiss = onDismiss,
            updateSelection = updateSelection,
        )

        is AdvancedSearchDialogState.MultiChoice -> AdvancedSearchMultiChoiceDialog(
            state = dialogState,
            onDismiss = onDismiss,
            updateSelection = updateSelection,
        )

        is AdvancedSearchDialogState.ReleaseDate -> AdvancedSearchReleaseDateDialog(
            state = dialogState,
            onDismiss = onDismiss,
            updateSelection = updateSelection,
        )
    }
}

@Composable
private fun AdvancedSearchSingleChoiceDialog(
    state: AdvancedSearchDialogState.SingleChoice,
    onDismiss: () -> Unit,
    updateSelection: (block: () -> Unit) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(state.titleRes)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.options) { index, option ->
                    SettingChoiceItem(
                        title = option.value,
                        selected = index == state.selectedIndex,
                        onClick = {
                            updateSelection { state.onSelect(option) }
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            AdvancedSearchDialogDismissButtons(
                onReset = {
                    updateSelection(state.onReset)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
private fun AdvancedSearchMultiChoiceDialog(
    state: AdvancedSearchDialogState.MultiChoice,
    onDismiss: () -> Unit,
    updateSelection: (block: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selected = remember(state.key) {
        mutableStateListOf<SearchOption>().apply { addAll(state.selected) }
    }
    var broad by remember(state.key) { mutableStateOf(state.broad) }
    val pagerState = rememberPagerState(pageCount = { state.scopes.size })
    // 分类内搜索：按页独立。标签总量 200+，没有搜索框等于不可用。
    val queries = remember(state.key) { mutableStateMapOf<Int, String>() }
    val currentPage = pagerState.currentPage
    val currentScopeTitle = stringResource(state.scopes.getOrNull(currentPage)?.titleRes ?: state.titleRes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(state.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.showBroad) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.pair_widely),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(Res.string.pair_widely_alert),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = broad,
                            onCheckedChange = { broad = it },
                        )
                    }
                }
                PrimaryScrollableTabRow(
                    selectedTabIndex = currentPage,
                    edgePadding = 16.dp,
                    divider = {},
                ) {
                    state.scopes.forEachIndexed { index, scopeSection ->
                        Tab(
                            selected = currentPage == index,
                            onClick = {
                                queries[index] = ""
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    // 带条目数：让用户知道哪些分类值得翻（最多的一组 50+，最少 9）。
                                    text = "${
                                        stringResource(scopeSection.titleRes)
                                    } (${scopeSection.options.size})",
                                    softWrap = false,
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = queries[currentPage].orEmpty(),
                    onValueChange = { queries[currentPage] = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(Res.string.filter_search_in_category, currentScopeTitle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_search),
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .weight(1f, fill = false),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val scopeSection = state.scopes[page]
                    val query = queries[page].orEmpty()
                    val filtered = if (query.isBlank()) {
                        scopeSection.options
                    } else {
                        scopeSection.options.filter {
                            it.value.contains(query, ignoreCase = true)
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.filter_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(scopeSection.spanCount),
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it.searchKey.orEmpty() }) { option ->
                                SelectableTag(
                                    text = option.value,
                                    selected = option in selected,
                                    onClick = {
                                        if (option in selected) {
                                            selected.remove(option)
                                        } else {
                                            selected.add(option)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(Res.string.filter_selected_count, selected.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                updateSelection { state.onSave(selected.toSet(), broad) }
                onDismiss()
            }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            AdvancedSearchDialogDismissButtons(
                onReset = {
                    updateSelection(state.onReset)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
private fun AdvancedSearchReleaseDateDialog(
    state: AdvancedSearchDialogState.ReleaseDate,
    onDismiss: () -> Unit,
    updateSelection: (block: () -> Unit) -> Unit,
) {
    var selectedTab by remember(state.key) {
        mutableIntStateOf(if (state.initialApproximate != null) 1 else 0)
    }
    var yearOnly by remember(state.key) {
        mutableStateOf(state.initialMonth == null)
    }
    var selectedYear by remember(state.key) {
        mutableIntStateOf(state.initialYear ?: SEARCH_YEAR_RANGE_END)
    }
    var selectedMonth by remember(state.key) {
        mutableIntStateOf(state.initialMonth ?: 1)
    }
    var selectedApproximate by remember(state.key) {
        mutableStateOf(state.initialApproximate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.release_date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 0 }
                    ) {
                        Text(stringResource(Res.string.specific_y_m))
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 1 }
                    ) {
                        Text(stringResource(Res.string.approximate_range))
                    }
                }
                if (selectedTab == 0) {
                    TextButton(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = { yearOnly = !yearOnly }
                    ) {
                        Text(
                            stringResource(
                                if (yearOnly) Res.string.switch_to_year_month else Res.string.switch_to_year
                            )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WheelLikeColumn(
                            title = "Year",
                            values = (SEARCH_YEAR_RANGE_START..SEARCH_YEAR_RANGE_END).toList(),
                            selectedValue = selectedYear,
                            modifier = Modifier.weight(1f),
                            label = { value -> value.toString() },
                            onSelect = { selectedYear = it },
                        )
                        if (!yearOnly) {
                            WheelLikeColumn(
                                title = "Month",
                                values = (1..12).toList(),
                                selectedValue = selectedMonth,
                                modifier = Modifier.weight(1f),
                                label = { value -> value.toString() },
                                onSelect = { selectedMonth = it },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.options, key = { it.searchKey.orEmpty() }) { option ->
                            SettingChoiceItem(
                                title = option.value,
                                selected = selectedApproximate == option.searchKey,
                                onClick = { selectedApproximate = option.searchKey },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedTab == 0) {
                    updateSelection {
                        state.onSaveSpecific(selectedYear, if (yearOnly) null else selectedMonth)
                    }
                } else {
                    updateSelection { state.onSaveApproximate(selectedApproximate) }
                }
                onDismiss()
            }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            AdvancedSearchDialogDismissButtons(
                onReset = {
                    updateSelection(state.onReset)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
private fun AdvancedSearchDialogDismissButtons(
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onReset) {
            Text(stringResource(Res.string.reset))
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(Res.string.cancel))
        }
    }
}

@Composable
private fun AdvancedSearchHistorySection(
    histories: List<HanimeAdvancedSearchHistoryEntity>,
    onDeleteHistory: (HanimeAdvancedSearchHistoryEntity) -> Unit,
    onSelectHistory: (HanimeAdvancedSearchHistoryEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.advanced_search_combination),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(histories, key = { it.id }) { history ->
                AdvancedSearchHistoryCard(
                    history = history,
                    onDelete = { onDeleteHistory(history) },
                    onClick = { onSelectHistory(history) },
                )
            }
        }
    }
}

/**
 * 6 个筛选维度的条件块网格。
 *
 * @param columns 每行的块数：底栏弹窗（满宽）用 2，宽屏常驻窄栏用 1。
 *   注意 [FilterBlockSpec.checked] 只影响配色，不影响是否占位 —— 未设置显示「全部」。
 * @param onClear chip 长按清除的收口：清完还要 bump 版本号，交给调用方。
 */
@Composable
private fun AdvancedSearchFiltersSection(
    blocks: List<FilterBlockSpec>,
    onClear: (block: () -> Unit) -> Unit,
    columns: Int = 2,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block ->
            AdvancedSearchChip(
                label = block.label,
                value = block.value,
                checked = block.checked,
                // FlowRow 的 weight 按行内剩余空间分配：单列时即占满整行。
                modifier = Modifier.weight(1f),
                onLongClick = { onClear(block.onClear) },
                onClick = block.onOpen,
            )
        }
    }
}

@Composable
private fun AdvancedSearchActionSection(
    selectedCount: Int,
    onReset: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.filter_selected_count, selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedCount > 0) {
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.reset))
                }
            }
        }
        Text(
            text = stringResource(Res.string.search_options_tips),
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledIconButton(
            onClick = onSearch,
            modifier = Modifier.size(60.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = stringResource(Res.string.search),
            )
        }
    }
}

private fun buildTagScopeSections(
    tags: Map<String, List<SearchOption>>,
): List<SearchScopeSection> {
    return advancedSearchTagScopes.map { (titleRes, scopeName) ->
        SearchScopeSection(titleRes, tags[scopeName].orEmpty())
    }
}

private fun groupSelectedTagOptions(
    selected: Set<SearchOption>,
    tags: Map<String, List<SearchOption>>,
): MutableMap<String, Set<SearchOption>> {
    // P6d-3-C4：key 原为 titleRes Int（P6c 占位分组 id，语义从未被读取）；
    // titleRes 改 StringResource 后改用 scopeName 作 key（同样仅占位）
    val grouped = linkedMapOf<String, Set<SearchOption>>()
    advancedSearchTagScopes.forEach { (_, scopeName) ->
        val selectedInScope = tags[scopeName].orEmpty().filterTo(mutableSetOf()) { it in selected }
        if (selectedInScope.isNotEmpty()) {
            grouped[scopeName] = selectedInScope
        }
    }
    return grouped
}
