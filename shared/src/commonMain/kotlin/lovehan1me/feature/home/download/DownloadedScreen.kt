package lovehan1me.feature.home.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.select_all
import lovehan1me.move_group
import lovehan1me.empty_content
import lovehan1me.downloaded
import lovehan1me.deselect_all
import lovehan1me.delete
import lovehan1me.confirm_delete_videos
import lovehan1me.confirm
import lovehan1me.close
import lovehan1me.cancel
import lovehan1me.ic_close
import lovehan1me.ic_delete
import lovehan1me.ic_move_group
import lovehan1me.ic_remove_selection
import lovehan1me.ic_select_all
import lovehan1me.data.database.entity.download.DownloadGroupEntity
import lovehan1me.data.database.entity.download.VideoWithCategories
import lovehan1me.core.domain.model.DownloadHeaderNode
import lovehan1me.core.domain.model.DownloadItemNode
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.feature.preview.fakeDownloadedGroups
import lovehan1me.feature.preview.fakeDownloadedNodes

/**
 * 已下载 Tab 页面（Content 层）。
 *
 * 接收 [DownloadUiState] + [DownloadEvent] 回调，支持多选批量移动和删除。
 *
 * @param uiState 页面 UI 状态
 * @param onEvent 用户交互事件回调
 */
@Composable
fun DownloadedScreen(
    uiState: DownloadUiState,
    listState: LazyListState,
    onEvent: (DownloadEvent) -> Unit,
) {
    var pendingRename by remember { mutableStateOf<DownloadHeaderNode?>(null) }
    var pendingMoveVideo by remember { mutableStateOf<VideoWithCategories?>(null) }
    var pendingBatchDeleteVideos by remember { mutableStateOf<List<VideoWithCategories>?>(null) }

    CreateGroupDialog(
        visible = uiState.showCreateGroupDialog,
        groups = uiState.displayGroups,
        onDismiss = { onEvent(DownloadEvent.OnCreateGroupDialogChange(false)) },
        onConfirm = {
            onEvent(DownloadEvent.OnCreateGroup(it))
            onEvent(DownloadEvent.OnCreateGroupDialogChange(false))
        },
        onDeleteGroup = { onEvent(DownloadEvent.OnDeleteGroup(it)) },
    )

    GroupRenameDialog(
        header = pendingRename,
        groups = uiState.displayGroups,
        onDismiss = { pendingRename = null },
        onConfirm = { header, newName ->
            uiState.displayGroups.find { it.name == header.groupKey }?.let { group ->
                onEvent(DownloadEvent.OnRenameGroup(group.id, newName))
            }
            pendingRename = null
        },
        onDelete = { header ->
            uiState.displayGroups.find { it.name == header.groupKey }
                ?.let { onEvent(DownloadEvent.OnDeleteGroup(it)) }
            pendingRename = null
        },
    )

    MoveGroupDialog(
        video = pendingMoveVideo,
        groups = uiState.displayGroups,
        onDismiss = { pendingMoveVideo = null },
        onConfirm = { video, groupId ->
            onEvent(DownloadEvent.OnMoveVideoGroup(video, groupId))
            pendingMoveVideo = null
        },
    )

    if (uiState.downloadedNodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyContent(
                hint = stringResource(Res.string.empty_content),
                subHint = stringResource(Res.string.downloaded),
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = if (uiState.multiSelectMode) 72.dp else 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.downloadedNodes, key = {
                when (it) {
                    is DownloadHeaderNode -> "header-${it.groupKey}"
                    is DownloadItemNode -> "item-${it.parentKey}-${it.data.video.id}"
                }
            }) { node ->
                when (node) {
                    is DownloadHeaderNode -> {
                        DownloadGroupHeader(
                            header = node,
                            onToggle = { onEvent(DownloadEvent.OnToggleGroup(node.groupKey)) },
                            onRename = {
                                val group = uiState.displayGroups.find { it.name == node.groupKey }
                                if (group?.id == DownloadGroupEntity.DEFAULT_GROUP_ID) {
                                    // 默认分组不可重命名
                                } else if (!uiState.multiSelectMode) {
                                    pendingRename = node
                                }
                            },
                        )
                    }

                    is DownloadItemNode -> {
                        val videoId = node.data.video.id
                        val isSelected = videoId in uiState.selectedVideoIds
                        DownloadedVideoCard(
                            item = node.data,
                            onOpenVideo = {
                                if (!uiState.multiSelectMode) {
                                    onEvent(DownloadEvent.OnOpenDownloadedVideo(node.data))
                                }
                            },
                            onLocalPlayback = {
                                if (!uiState.multiSelectMode) {
                                    onEvent(DownloadEvent.OnLocalPlayback(node.data))
                                }
                            },
                            onExternalPlayback = {
                                if (!uiState.multiSelectMode) {
                                    onEvent(DownloadEvent.OnExternalPlayback(node.data))
                                }
                            },
                            onDeleteVideo = {
                                if (!uiState.multiSelectMode) {
                                    onEvent(DownloadEvent.OnDeleteDownloadedVideo(node.data))
                                }
                            },
                            onMoveGroup = {
                                if (!uiState.multiSelectMode) {
                                    pendingMoveVideo = node.data
                                }
                            },
                            isMultiSelect = uiState.multiSelectMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                onEvent(DownloadEvent.OnToggleVideoSelection(videoId))
                            },
                        )
                    }
                }
            }
        }

        if (uiState.multiSelectMode) {
            val selectedVideos = uiState.downloadedNodes
                .filterIsInstance<DownloadItemNode>()
                .filter { it.data.video.id in uiState.selectedVideoIds }
                .map { it.data }

            BatchActionBar(
                selectedCount = selectedVideos.size,
                totalCount = uiState.downloadedNodes.filterIsInstance<DownloadItemNode>().size,
                onToggleSelectAll = {
                    val nodes = uiState.downloadedNodes.filterIsInstance<DownloadItemNode>()
                    if (selectedVideos.size == nodes.size) {
                        nodes.forEach { onEvent(DownloadEvent.OnToggleVideoSelection(it.data.video.id)) }
                    } else {
                        nodes.filter { it.data.video.id !in uiState.selectedVideoIds }
                            .forEach { onEvent(DownloadEvent.OnToggleVideoSelection(it.data.video.id)) }
                    }
                },
                onExitMultiSelect = { onEvent(DownloadEvent.OnToggleMultiSelect) },
                onDeleteSelected = {
                    if (selectedVideos.isNotEmpty()) {
                        pendingBatchDeleteVideos = selectedVideos
                    }
                },
                onMoveSelected = {
                    if (selectedVideos.isNotEmpty()) {
                        onEvent(DownloadEvent.OnBatchMoveRequest)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    pendingBatchDeleteVideos?.let { videos ->
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.delete),
            message = stringResource(Res.string.confirm_delete_videos, videos.size),
            confirmText = stringResource(Res.string.confirm),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                onEvent(DownloadEvent.OnBatchDelete(videos))
                pendingBatchDeleteVideos = null
            },
            onDismiss = { pendingBatchDeleteVideos = null },
        )
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onToggleSelectAll: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAllSelected = selectedCount == totalCount
    val hasSelection = selectedCount > 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onExitMultiSelect) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${selectedCount}/${totalCount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleSelectAll
                ) {
                    Icon(
                        painter = if (isAllSelected) {
                            painterResource(Res.drawable.ic_remove_selection)
                        } else {
                            painterResource(Res.drawable.ic_select_all)
                        },
                        contentDescription = if (isAllSelected) {
                            stringResource(Res.string.deselect_all)
                        } else {
                            stringResource(Res.string.select_all)
                        }
                    )
                }

                IconButton(
                    onClick = onMoveSelected,
                    enabled = hasSelection
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_move_group),
                        contentDescription = stringResource(Res.string.move_group),
                        tint = if (hasSelection) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }

                FilledIconButton(
                    onClick = onDeleteSelected,
                    enabled = hasSelection
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.delete),
                        tint = if (hasSelection) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }
    }
}
