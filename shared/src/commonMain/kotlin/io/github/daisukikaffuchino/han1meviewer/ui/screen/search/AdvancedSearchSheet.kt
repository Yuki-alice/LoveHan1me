package io.github.daisukikaffuchino.han1meviewer.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.daisukikaffuchino.han1meviewer.ui.component.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.appearance_and_figure
import io.github.daisukikaffuchino.han1meviewer.characteristics
import io.github.daisukikaffuchino.han1meviewer.duration
import io.github.daisukikaffuchino.han1meviewer.relationship
import io.github.daisukikaffuchino.han1meviewer.sex_position
import io.github.daisukikaffuchino.han1meviewer.sort_option
import io.github.daisukikaffuchino.han1meviewer.story_location
import io.github.daisukikaffuchino.han1meviewer.story_plot
import io.github.daisukikaffuchino.han1meviewer.tag
import io.github.daisukikaffuchino.han1meviewer.type
import io.github.daisukikaffuchino.han1meviewer.video_attr
import io.github.daisukikaffuchino.han1meviewer.switch_to_year
import io.github.daisukikaffuchino.han1meviewer.switch_to_year_month
import io.github.daisukikaffuchino.han1meviewer.type
import io.github.daisukikaffuchino.han1meviewer.tag
import io.github.daisukikaffuchino.han1meviewer.specific_y_m
import io.github.daisukikaffuchino.han1meviewer.sort_option
import io.github.daisukikaffuchino.han1meviewer.search_options_tips
import io.github.daisukikaffuchino.han1meviewer.search
import io.github.daisukikaffuchino.han1meviewer.save
import io.github.daisukikaffuchino.han1meviewer.reset
import io.github.daisukikaffuchino.han1meviewer.release_date
import io.github.daisukikaffuchino.han1meviewer.pair_widely_alert
import io.github.daisukikaffuchino.han1meviewer.pair_widely
import io.github.daisukikaffuchino.han1meviewer.duration
import io.github.daisukikaffuchino.han1meviewer.cancel
import io.github.daisukikaffuchino.han1meviewer.approximate_range
import io.github.daisukikaffuchino.han1meviewer.advanced_search_combination
import io.github.daisukikaffuchino.han1meviewer.advanced_search
import io.github.daisukikaffuchino.han1meviewer.ic_search
import io.github.daisukikaffuchino.han1meviewer.SEARCH_YEAR_RANGE_END
import io.github.daisukikaffuchino.han1meviewer.SEARCH_YEAR_RANGE_START
import io.github.daisukikaffuchino.han1meviewer.logic.DatabaseRepo
import io.github.daisukikaffuchino.han1meviewer.logic.HanimeAdvancedSearchRepo
import io.github.daisukikaffuchino.han1meviewer.logic.entity.HanimeAdvancedSearchHistoryEntity
import io.github.daisukikaffuchino.han1meviewer.logic.model.SearchOption
import io.github.daisukikaffuchino.han1meviewer.logic.model.SearchOption.Companion.flatten
import io.github.daisukikaffuchino.han1meviewer.ui.component.SelectableTag
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingChoiceItem
import io.github.daisukikaffuchino.han1meviewer.ui.component.lazy.LazyColumn
import io.github.daisukikaffuchino.han1meviewer.ui.component.lazy.LazyVerticalGrid
import io.github.daisukikaffuchino.han1meviewer.ui.model.AdvancedSearchDialogState
import io.github.daisukikaffuchino.han1meviewer.ui.model.SearchScopeSection
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.SearchViewModel
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
    val typeLabel = stringResource(Res.string.type)
    val sortLabel = stringResource(Res.string.sort_option)
    val tagLabel = stringResource(Res.string.tag)
    val releaseDateLabel = stringResource(Res.string.release_date)
    val durationLabel = stringResource(Res.string.duration)
    val tagScopes = remember(viewModel.tags) { buildTagScopeSections(viewModel.tags) }

    fun updateSelection(block: () -> Unit) {
        block()
        selectionVersion++
    }

    fun selectedOptionValue(options: List<SearchOption>, searchKey: String?): String? {
        return options.firstOrNull { it.searchKey == searchKey }?.value
    }

    val genreTitle = remember(selectionVersion, typeLabel) {
        selectedOptionValue(viewModel.genres, viewModel.genre)?.let { "$typeLabel: $it" }
            ?: typeLabel
    }
    val sortTitle = remember(selectionVersion, sortLabel) {
        selectedOptionValue(viewModel.sortOptions, viewModel.sort)?.let { "$sortLabel: $it" }
            ?: sortLabel
    }
    val selectedTagKeys = remember(selectionVersion) { viewModel.tagMap.flatten() }
    val selectedTagCount = remember(selectionVersion) { selectedTagKeys.size }
    val selectedTagOptions = remember(selectionVersion, tagScopes) {
        tagScopes.flatMap { it.options }
            .filter { it.searchKey in selectedTagKeys }
            .toSet()
    }
    val tagTitle = remember(selectionVersion, tagLabel) {
        if (selectedTagCount > 0) "$tagLabel ($selectedTagCount)" else tagLabel
    }
    val releaseDateTitle = remember(selectionVersion, releaseDateLabel) {
        viewModel.getSearchDate()?.let { "$releaseDateLabel: $it" } ?: releaseDateLabel
    }
    val durationTitle = remember(selectionVersion, durationLabel) {
        selectedOptionValue(viewModel.durations, viewModel.duration)?.let { "$durationLabel: $it" }
            ?: durationLabel
    }

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
            shape = RoundedCornerShape(28.dp),
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
                                viewModel.restoreSearchMap(history)
                                viewModel.triggerNewSearch()
                                onDismiss()
                            },
                        )
                    }
                }
                item {
                    AdvancedSearchFiltersSection(
                        genreTitle = genreTitle,
                        genreChecked = viewModel.genre != null,
                        onClearGenre = { updateSelection { viewModel.genre = null } },
                        onOpenGenre = {
                            dialogState = AdvancedSearchDialogState.SingleChoice(
                                key = "genre",
                                titleRes = Res.string.type,
                                options = viewModel.genres,
                                selectedIndex = viewModel.genres.indexOfFirst { it.searchKey == viewModel.genre },
                                onSelect = { option -> viewModel.genre = option.searchKey },
                                onReset = { viewModel.genre = null },
                            )
                        },
                        sortTitle = sortTitle,
                        sortChecked = viewModel.sort != null,
                        onClearSort = { updateSelection { viewModel.sort = null } },
                        onOpenSort = {
                            dialogState = AdvancedSearchDialogState.SingleChoice(
                                key = "sort",
                                titleRes = Res.string.sort_option,
                                options = viewModel.sortOptions,
                                selectedIndex = viewModel.sortOptions.indexOfFirst { it.searchKey == viewModel.sort },
                                onSelect = { option -> viewModel.sort = option.searchKey },
                                onReset = { viewModel.sort = null },
                            )
                        },
                        tagTitle = tagTitle,
                        tagChecked = viewModel.tagMap.isNotEmpty(),
                        onClearTag = { updateSelection { viewModel.tagMap.clear() } },
                        onOpenTag = {
                            dialogState = AdvancedSearchDialogState.MultiChoice(
                                key = "tag",
                                titleRes = Res.string.tag,
                                scopes = tagScopes,
                                selected = selectedTagOptions,
                                broad = viewModel.broad,
                                onSave = { selected, broad ->
                                    viewModel.broad = broad
                                    viewModel.tagMap =
                                        groupSelectedTagOptions(selected, viewModel.tags)
                                },
                                onReset = { viewModel.tagMap.clear() },
                            )
                        },
                        releaseDateTitle = releaseDateTitle,
                        releaseDateChecked =
                            viewModel.year != null || viewModel.month != null || viewModel.approxTime != null,
                        onClearReleaseDate = {
                            updateSelection {
                                viewModel.year = null
                                viewModel.month = null
                                viewModel.approxTime = null
                            }
                        },
                        onOpenReleaseDate = {
                            dialogState = AdvancedSearchDialogState.ReleaseDate(
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
                        },
                        durationTitle = durationTitle,
                        durationChecked = viewModel.duration != null,
                        onClearDuration = { updateSelection { viewModel.duration = null } },
                        onOpenDuration = {
                            dialogState = AdvancedSearchDialogState.SingleChoice(
                                key = "duration",
                                titleRes = Res.string.duration,
                                options = viewModel.durations,
                                selectedIndex = viewModel.durations.indexOfFirst { it.searchKey == viewModel.duration },
                                onSelect = { option -> viewModel.duration = option.searchKey },
                                onReset = { viewModel.duration = null },
                            )
                        },
                    )
                }
                item {
                    AdvancedSearchActionSection(
                        onSearch = {
                            viewModel.triggerNewSearch()
                            viewModel.insertAdvancedSearchHistory(
                                viewModel.query,
                                viewModel.genre,
                                viewModel.sort,
                                viewModel.broad,
                                viewModel.getSearchDate(),
                                viewModel.duration,
                                viewModel.tagMap.flatten().map { SearchOption(searchKey = it) }
                                    .toSet(),
                                viewModel.brandMap.flatten().map { SearchOption(searchKey = it) }
                                    .toSet(),
                            )
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(state.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    divider = {},
                ) {
                    state.scopes.forEachIndexed { index, scopeSection ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = stringResource(scopeSection.titleRes),
                                    softWrap = false,
                                )
                            },
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .weight(1f, fill = false),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val scopeSection = state.scopes[page]
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(scopeSection.spanCount),
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(scopeSection.options, key = { it.searchKey.orEmpty() }) { option ->
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

@Composable
private fun AdvancedSearchFiltersSection(
    genreTitle: String,
    genreChecked: Boolean,
    onClearGenre: () -> Unit,
    onOpenGenre: () -> Unit,
    sortTitle: String,
    sortChecked: Boolean,
    onClearSort: () -> Unit,
    onOpenSort: () -> Unit,
    tagTitle: String,
    tagChecked: Boolean,
    onClearTag: () -> Unit,
    onOpenTag: () -> Unit,
    releaseDateTitle: String,
    releaseDateChecked: Boolean,
    onClearReleaseDate: () -> Unit,
    onOpenReleaseDate: () -> Unit,
    durationTitle: String,
    durationChecked: Boolean,
    onClearDuration: () -> Unit,
    onOpenDuration: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AdvancedSearchChip(
            title = genreTitle,
            checked = genreChecked,
            modifier = Modifier.weight(1f),
            onLongClick = onClearGenre,
            onClick = onOpenGenre,
        )
        AdvancedSearchChip(
            title = sortTitle,
            checked = sortChecked,
            modifier = Modifier.weight(1f),
            onLongClick = onClearSort,
            onClick = onOpenSort,
        )
        AdvancedSearchChip(
            title = tagTitle,
            checked = tagChecked,
            modifier = Modifier.weight(1f),
            onLongClick = onClearTag,
            onClick = onOpenTag,
        )
        AdvancedSearchChip(
            title = releaseDateTitle,
            checked = releaseDateChecked,
            modifier = Modifier.weight(1f),
            onLongClick = onClearReleaseDate,
            onClick = onOpenReleaseDate,
        )
        AdvancedSearchChip(
            title = durationTitle,
            checked = durationChecked,
            modifier = Modifier.weight(1f),
            onLongClick = onClearDuration,
            onClick = onOpenDuration,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AdvancedSearchActionSection(
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
