package lovehan1me.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.video_code
import lovehan1me.title
import lovehan1me.sure_to_delete
import lovehan1me.share_to_others_tip
import lovehan1me.share_to_others
import lovehan1me.share
import lovehan1me.prompt
import lovehan1me.position_ms
import lovehan1me.modify_h_keyframe
import lovehan1me.here_is_empty
import lovehan1me.h_keyframe_title_prefix
import lovehan1me.edit
import lovehan1me.delete
import lovehan1me.copy_
import lovehan1me.confirm
import lovehan1me.cancel
import lovehan1me.data.database.entity.HKeyframeEntity
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.player.formatPlaybackTime

private enum class HKeyframeDialog {
    EditEntity,
    ShareEntity,
    DeleteEntity,
    EditKeyframe,
    DeleteKeyframe,
}

@Composable
fun HKeyframesScreen(
    items: List<HKeyframeEntity>,
    onOpenVideo: (String) -> Unit,
    onDeleteEntity: (HKeyframeEntity) -> Unit,
    onUpdateEntityTitle: (HKeyframeEntity, String) -> Unit,
    onDeleteKeyframe: (String, HKeyframeEntity.Keyframe) -> Unit,
    onUpdateKeyframe: (String, HKeyframeEntity.Keyframe, HKeyframeEntity.Keyframe) -> Unit,
    onCopyShareContent: (String) -> Unit,
) {
    var selectedEntity by remember { mutableStateOf<HKeyframeEntity?>(null) }
    var selectedKeyframe by remember { mutableStateOf<Pair<String, HKeyframeEntity.Keyframe>?>(null) }
    var activeDialog by rememberSaveable { mutableStateOf<HKeyframeDialog?>(null) }

    selectedEntity?.takeIf { activeDialog == HKeyframeDialog.EditEntity }?.let { entity ->
        EditEntityDialog(
            entity = entity,
            onDismiss = {
                activeDialog = null
                selectedEntity = null
            },
            onConfirm = { newTitle ->
                onUpdateEntityTitle(entity, newTitle)
                activeDialog = null
                selectedEntity = null
            },
        )
    }

    selectedEntity?.takeIf { activeDialog == HKeyframeDialog.DeleteEntity }?.let { entity ->
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.sure_to_delete),
            message = entity.title,
            confirmText = stringResource(Res.string.confirm),
            dismissText = stringResource(Res.string.cancel),
            onDismiss = {
                activeDialog = null
                selectedEntity = null
            },
            onConfirm = {
                onDeleteEntity(entity)
                activeDialog = null
                selectedEntity = null
            },
        )
    }

    selectedEntity?.takeIf { activeDialog == HKeyframeDialog.ShareEntity }?.let { entity ->
        ShareEntityDialog(
            entity = entity,
            onDismiss = {
                activeDialog = null
                selectedEntity = null
            },
            onCopy = {
                onCopyShareContent(it)
                activeDialog = null
                selectedEntity = null
            },
        )
    }

    selectedKeyframe?.takeIf { activeDialog == HKeyframeDialog.EditKeyframe }
        ?.let { (videoCode, keyframe) ->
            EditKeyframeDialog(
                keyframe = keyframe,
                onDismiss = {
                    activeDialog = null
                    selectedKeyframe = null
                },
                onConfirm = { newKeyframe ->
                    onUpdateKeyframe(videoCode, keyframe, newKeyframe)
                    activeDialog = null
                    selectedKeyframe = null
                },
            )
        }

    selectedKeyframe?.takeIf { activeDialog == HKeyframeDialog.DeleteKeyframe }
        ?.let { (videoCode, keyframe) ->
            ConfirmDialog(
                visible = true,
                title = stringResource(Res.string.sure_to_delete),
                message = formatPlaybackTime(keyframe.position),
                confirmText = stringResource(Res.string.confirm),
                dismissText = stringResource(Res.string.cancel),
                onDismiss = {
                    activeDialog = null
                    selectedKeyframe = null
                },
                onConfirm = {
                    onDeleteKeyframe(videoCode, keyframe)
                    activeDialog = null
                    selectedKeyframe = null
                },
            )
        }

    if (items.isEmpty()) {
        EmptyContent(hint = stringResource(Res.string.here_is_empty))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        enableItemAnimation = false,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.videoCode }) { entity ->
            HKeyframeEntityCard(
                entity = entity,
                onOpenVideo = { onOpenVideo(entity.videoCode) },
                onEdit = {
                    selectedEntity = entity
                    activeDialog = HKeyframeDialog.EditEntity
                },
                onDelete = {
                    selectedEntity = entity
                    activeDialog = HKeyframeDialog.DeleteEntity
                },
                onShare = {
                    selectedEntity = entity
                    activeDialog = HKeyframeDialog.ShareEntity
                },
                onEditKeyframe = { keyframe ->
                    selectedKeyframe = entity.videoCode to keyframe
                    activeDialog = HKeyframeDialog.EditKeyframe
                },
                onDeleteKeyframe = { keyframe ->
                    selectedKeyframe = entity.videoCode to keyframe
                    activeDialog = HKeyframeDialog.DeleteKeyframe
                },
            )
        }
    }
}

@Composable
private fun HKeyframeEntityCard(
    entity: HKeyframeEntity,
    onOpenVideo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onEditKeyframe: (HKeyframeEntity.Keyframe) -> Unit,
    onDeleteKeyframe: (HKeyframeEntity.Keyframe) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = HanimeDefaults.Corners.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entity.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) { Text(stringResource(Res.string.edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(Res.string.delete)) }
                    TextButton(onClick = onShare) { Text(stringResource(Res.string.share)) }
                }
            }

            Text(
                text = stringResource(Res.string.h_keyframe_title_prefix) + entity.videoCode,
                modifier = Modifier.clickable {
                    haptic()
                    onOpenVideo()
                },
                color = MaterialTheme.colorScheme.primary,
            )

            HorizontalDivider()

            entity.keyframes.forEachIndexed { index, keyframe ->
                HKeyframeRow(
                    index = index + 1,
                    keyframe = keyframe,
                    onEdit = { onEditKeyframe(keyframe) },
                    onDelete = { onDeleteKeyframe(keyframe) },
                )
            }
        }
    }
}

@Composable
private fun HKeyframeRow(
    index: Int,
    keyframe: HKeyframeEntity.Keyframe,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatPlaybackTime(keyframe.position),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "#$index",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            keyframe.prompt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "➥ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text(stringResource(Res.string.edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(Res.string.delete)) }
        }
    }
}

@Composable
private fun EditEntityDialog(
    entity: HKeyframeEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(entity.title) { mutableStateOf(entity.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.modify_h_keyframe)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = entity.videoCode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.video_code)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun EditKeyframeDialog(
    keyframe: HKeyframeEntity.Keyframe,
    onDismiss: () -> Unit,
    onConfirm: (HKeyframeEntity.Keyframe) -> Unit,
) {
    var positionText by remember(keyframe.position) { mutableStateOf(keyframe.position.toString()) }
    var prompt by remember(keyframe.prompt) { mutableStateOf(keyframe.prompt.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.modify_h_keyframe)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = positionText,
                    onValueChange = { positionText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(Res.string.position_ms)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(Res.string.prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    HKeyframeEntity.Keyframe(
                        position = positionText.toLongOrNull() ?: keyframe.position,
                        prompt = prompt,
                    )
                )
            }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun ShareEntityDialog(
    entity: HKeyframeEntity,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val content = remember(entity) {
        val toJson = kotlinx.serialization.json.Json.encodeToString(entity)
        // P6d-4：原 android.util.Base64 NO_WRAP；kotlin.io.encoding Base64.Default 同为无换行标准编码
        val toBase64 = kotlin.io.encoding.Base64.Default.encode(toJson.encodeToByteArray())
        ">>>${toBase64}<<<"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.share_to_others)) },
        text = {
            Text(
                text = stringResource(Res.string.share_to_others_tip, content),
                modifier = Modifier.heightIn(max = 260.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCopy(content) }) {
                Text(stringResource(Res.string.copy_))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
