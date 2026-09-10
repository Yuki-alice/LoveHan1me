package lovehan1me.feature.home.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.sure_to_delete
import lovehan1me.prepare_to_delete_s
import lovehan1me.empty_content
import lovehan1me.confirm
import lovehan1me.cancel
import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import lovehan1me.core.domain.state.DownloadState
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.feature.preview.fakeHomePageVideos

/**
 * 下载中 Tab 页面（Content 层）。
 *
 * 接收 [DownloadUiState] + [DownloadEvent] 回调，不持有 ViewModel。
 *
 * @param uiState 页面 UI 状态
 * @param onEvent 用户交互事件回调
 */
@Composable
fun DownloadingScreen(
    uiState: DownloadUiState,
    onEvent: (DownloadEvent) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<HanimeDownloadEntity?>(null) }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = stringResource(Res.string.sure_to_delete),
        message = stringResource(Res.string.prepare_to_delete_s, pendingDelete?.title.orEmpty()),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            pendingDelete?.let { onEvent(DownloadEvent.OnDeleteDownloadingItem(it)) }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )

    if (uiState.downloadingItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyContent(
                hint = stringResource(Res.string.empty_content)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.downloadingItems, key = { it.id }) { item ->
                DownloadingItemCard(
                    item = item,
                    onPause = { onEvent(DownloadEvent.OnPauseItem(item)) },
                    onResume = { onEvent(DownloadEvent.OnResumeItem(item)) },
                    onDelete = { pendingDelete = item },
                )
            }
        }
    }
}
