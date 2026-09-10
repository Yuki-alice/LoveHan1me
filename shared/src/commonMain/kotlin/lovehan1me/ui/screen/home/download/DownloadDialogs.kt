package lovehan1me.ui.screen.home.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import lovehan1me.ui.component.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.rename_group
import lovehan1me.new_group_name
import lovehan1me.modify_video_group
import lovehan1me.delete_group_confirm
import lovehan1me.delete_group
import lovehan1me.current_group_name
import lovehan1me.create_new_group
import lovehan1me.confirm
import lovehan1me.cancel
import lovehan1me.ic_delete
import lovehan1me.ic_edit
import lovehan1me.data.database.entity.download.DownloadGroupEntity
import lovehan1me.data.database.entity.download.VideoWithCategories
import lovehan1me.core.domain.model.DownloadHeaderNode
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.component.verticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadDialogSurface(
    onDismiss: () -> Unit,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

/**
 * 新建/管理分组对话框。
 *
 * @param visible 是否可见
 * @param groups 当前所有分组
 * @param onDismiss 关闭回调
 * @param onConfirm 确认创建回调
 * @param onDeleteGroup 删除分组回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupDialog(
    visible: Boolean,
    groups: List<DownloadGroupEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDeleteGroup: (DownloadGroupEntity) -> Unit,
) {
    if (!visible) return
    var name by remember { mutableStateOf("") }
    var pendingDeleteGroup by remember { mutableStateOf<DownloadGroupEntity?>(null) }

    if (pendingDeleteGroup != null) {
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.delete_group),
            message = stringResource(Res.string.delete_group_confirm, pendingDeleteGroup!!.name),
            confirmText = stringResource(Res.string.confirm),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                pendingDeleteGroup?.let { onDeleteGroup(it) }
                pendingDeleteGroup = null
            },
            onDismiss = { pendingDeleteGroup = null },
        )
    }

    DownloadDialogSurface(onDismiss = onDismiss) {
                Text(
                    stringResource(Res.string.create_new_group),
                    style = MaterialTheme.typography.titleLarge
                )
                if (groups.isNotEmpty()) {
                    val scrollState = rememberLazyListState()
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 240.dp)
                            .verticalScrollbar(
                                state = scrollState,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                width = 4.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items = groups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                val isDefaultGroup =
                                    group.id == DownloadGroupEntity.DEFAULT_GROUP_ID
                                FilledTonalIconButton(
                                    onClick = { pendingDeleteGroup = group },
                                    modifier = Modifier.size(30.dp),
                                    enabled = !isDefaultGroup,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_delete),
                                        contentDescription = stringResource(Res.string.delete_group),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.new_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = name.trim()
                        if (trimmed.isNotBlank()) onConfirm(trimmed)
                    }),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                    TextButton(onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotBlank()) onConfirm(trimmed)
                    }) {
                        Text(stringResource(Res.string.confirm))
                    }
                }
    }
}

/**
 * 分组重命名对话框。
 *
 * @param header 目标分组节点
 * @param groups 当前所有分组
 * @param onDismiss 关闭回调
 * @param onConfirm 确认重命名回调
 * @param onDelete 删除此分组回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupRenameDialog(
    header: DownloadHeaderNode?,
    groups: List<DownloadGroupEntity>,
    onDismiss: () -> Unit,
    onConfirm: (DownloadHeaderNode, String) -> Unit,
    onDelete: (DownloadHeaderNode) -> Unit,
) {
    if (header == null) return
    var name by remember(header.groupKey) { mutableStateOf(header.groupKey) }
    val group = groups.find { it.name == header.groupKey }

    DownloadDialogSurface(
        onDismiss = onDismiss,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
                Text(
                    stringResource(Res.string.rename_group),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(Res.string.current_group_name, header.groupKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.new_group_name)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (group != null && group.id != DownloadGroupEntity.DEFAULT_GROUP_ID) {
                        TextButton(onClick = { onDelete(header) }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_delete),
                                contentDescription = null
                            )
                            Text(stringResource(Res.string.delete_group))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                    TextButton(onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotBlank() && trimmed != header.groupKey) {
                            onConfirm(header, trimmed)
                        }
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_edit),
                            contentDescription = null
                        )
                        Text(stringResource(Res.string.confirm))
                    }
                }
    }
}

/**
 * 移动视频到其他分组对话框。
 *
 * @param video 目标视频
 * @param groups 当前所有分组
 * @param onDismiss 关闭回调
 * @param onConfirm 确认移动回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveGroupDialog(
    video: VideoWithCategories?,
    groups: List<DownloadGroupEntity>,
    onDismiss: () -> Unit,
    onConfirm: (VideoWithCategories, Int) -> Unit,
) {
    if (video == null) return
    DownloadDialogSurface(onDismiss = onDismiss) {
                Text(
                    text = stringResource(Res.string.modify_video_group, video.video.title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (groups.isNotEmpty()) {
                    val scrollState = rememberLazyListState()
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .verticalScrollbar(
                                state = scrollState,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                width = 4.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(items = groups, key = { it.id }) { group ->
                            // P6d-4F：desktop 的 CMP m3 ListItem 为新签名（headlineContent，无 content/elevation）
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onConfirm(video, group.id) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_edit),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = null,
                                overlineContent = null,
                                supportingContent = null,
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                ),
                                headlineContent = {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(Res.string.cancel))
                }
    }
}
