package lovehan1me.ui.screen.home.preview.getchupreview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.ic_arrow_drop_down
import lovehan1me.ic_chevron_left
import lovehan1me.ic_chevron_right
import lovehan1me.core.domain.state.PageState
import lovehan1me.core.domain.state.dataOrNull
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.PageContent
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.component.isFirstPageEmpty
import lovehan1me.ui.component.isFirstPageError
import lovehan1me.ui.component.isFirstPageLoading
import lovehan1me.ui.screen.rememberRandomLoadingHint
import lovehan1me.core.util.toNetworkErrorMessageRes
import lovehan1me.ui.component.HapticTextButton as TextButton

@Composable
fun GetchuPreviewScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: GetchuPreviewViewModel,
) {
    var dateCode by rememberSaveable { mutableStateOf(currentGetchuDateCode()) }
    var monthMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val currentMonthCode = remember { currentGetchuDateCode() }
    val state = viewModel.previewFlow.collectAsStateWithLifecycle().value
    val dateLabel = remember(dateCode) { getchuDateLabel(dateCode) }
    val monthOptions = remember(currentMonthCode) { getchuMonthOptions(currentMonthCode) }
    val loadingHint = rememberRandomLoadingHint()
    val isInspectionMode = LocalInspectionMode.current
    val imageLoader = rememberGetchuImageLoader()
    LaunchedEffect(dateCode, isInspectionMode) {
        if (!isInspectionMode) viewModel.getPreview(dateCode)
    }

    HanimeScaffold(
            title = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { monthMenuExpanded = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dateLabel,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                painter = painterResource(Res.drawable.ic_arrow_drop_down),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = monthMenuExpanded,
                        onDismissRequest = { monthMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        monthOptions.forEach { optionDateCode ->
                            DropdownMenuItem(
                                text = { Text(getchuDateLabel(optionDateCode)) },
                                onClick = {
                                    dateCode = optionDateCode
                                    monthMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            },
            onBack = onBack,
            contentHorizontalPadding = 0.dp,
            actions = {
                IconButton(onClick = { dateCode = shiftGetchuMonthCode(dateCode, -1) }) {
                    Icon(painterResource(Res.drawable.ic_chevron_left), null)
                }
                IconButton(onClick = { dateCode = shiftGetchuMonthCode(dateCode, 1) }) {
                    Icon(painterResource(Res.drawable.ic_chevron_right), null)
                }
            },
        ) {
            PageContent(
            isLoading = state.isFirstPageLoading,
            isError = state.isFirstPageError,
            isEmpty = state.isFirstPageEmpty || state.dataOrNull?.groups?.isEmpty() == true,
            errorMessage = (state as? PageState.Error)?.throwable?.toNetworkErrorMessageRes()?.let {
                stringResource(it)
            } ?: "",
            onRetry = { if (!isInspectionMode) viewModel.getPreview(dateCode) },
            modifier = Modifier.fillMaxSize(),
            loadingMessage = loadingHint
            ) {
                state.dataOrNull?.let { preview ->
                    GetchuPreviewContent(
                        preview = preview,
                        onOpenDetail = onNavigateToDetail,
                        imageLoader = imageLoader
                    )
                }
            }
        }
}
