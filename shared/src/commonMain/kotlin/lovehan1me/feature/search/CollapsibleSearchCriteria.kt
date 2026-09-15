package lovehan1me.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.type
import lovehan1me.tag
import lovehan1me.sort_option
import lovehan1me.reset
import lovehan1me.release_date
import lovehan1me.pair_widely
import lovehan1me.duration
import lovehan1me.brand
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.feature.search.SearchViewModel
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.search_video_hint
import lovehan1me.search_no_results
import lovehan1me.search_load_failed_with_reason
import lovehan1me.recent_searches
import lovehan1me.delete
import lovehan1me.clear_checkin
import lovehan1me.back
import lovehan1me.advanced_search
import lovehan1me.h_chan_speechless
import lovehan1me.h_chan_sad
import lovehan1me.ic_search
import lovehan1me.ic_filter_list
import lovehan1me.ic_close
import lovehan1me.ic_arrow_back
import lovehan1me.data.SettingsRepository
import lovehan1me.data.database.entity.SearchHistoryEntity
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeInfo.Companion.NORMAL
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.rememberRandomLoadingHint
import lovehan1me.ui.transition.coverSharedElementKey
import lovehan1me.ui.adaptive.rememberVideoCardMinWidth
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


@Composable
internal fun CollapsibleSearchCriteria(
    collapsedFraction: State<Float>,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Layout(
        content = {
            content(Modifier.onSizeChanged { onHeightChanged(it.height) })
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        )
        val collapsedHeight = (placeable.height * collapsedFraction.value)
            .toInt()
            .coerceIn(0, placeable.height)
        val visibleHeight = placeable.height - collapsedHeight

        layout(
            width = constraints.constrainWidth(placeable.width),
            height = constraints.constrainHeight(visibleHeight),
        ) {
            placeable.placeRelative(x = 0, y = -collapsedHeight)
        }
    }
}

@Composable
internal fun ActiveSearchCriteria(
    filter: SearchFilter,
    viewModel: SearchViewModel,
    onClearAll: () -> Unit,
    onClearGenre: () -> Unit,
    onClearSort: () -> Unit,
    onClearDuration: () -> Unit,
    onClearTagCount: () -> Unit,
    onClearBrandCount: () -> Unit,
    onClearBroad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!filter.isNotEmpty()) return

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filter.genre?.let {
            val label = viewModel.genres.find { option -> option.searchKey == it }?.name ?: it
            AssistChip(
                onClick = onClearGenre,
                label = { Text("${stringResource(Res.string.type)}: $label") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        if (filter.tagCount > 0) {
            AssistChip(
                onClick = onClearTagCount,
                label = { Text("${stringResource(Res.string.tag)} (${filter.tagCount})") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        filter.sort?.let {
            val label = viewModel.sortOptions.find { option -> option.searchKey == it }?.name ?: it
            AssistChip(
                onClick = onClearSort,
                label = { Text("${stringResource(Res.string.sort_option)}: $label") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        filter.releaseDate?.let {
            AssistChip(
                onClick = onClearDuration,
                label = { Text("${stringResource(Res.string.release_date)}: $it") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        filter.duration?.let {
            val label = viewModel.durations.find { option -> option.searchKey == it }?.name ?: it
            AssistChip(
                onClick = onClearDuration,
                label = { Text("${stringResource(Res.string.duration)}: $label") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        if (filter.brandCount > 0) {
            AssistChip(
                onClick = onClearBrandCount,
                label = { Text("${stringResource(Res.string.brand)} (${filter.brandCount})") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        if (filter.broad) {
            AssistChip(
                onClick = onClearBroad,
                label = { Text(stringResource(Res.string.pair_widely)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        AssistChip(
            onClick = onClearAll,
            label = { Text(stringResource(Res.string.reset)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

// ─────────────────────────────────────────────
// 辅助
// ─────────────────────────────────────────────

internal fun tagFlatten(map: Map<*, Set<SearchOption>>): Set<String> {
    val r = mutableSetOf<String>(); for (options in map.values) {
        options.mapNotNullTo(r) { it.searchKey }
    }; return r
}

internal fun brandFlatten(map: Map<Int, Set<SearchOption>>): Set<String> {
    val r = mutableSetOf<String>(); for (options in map.values) {
        options.mapNotNullTo(r) { it.searchKey }
    }; return r
}
