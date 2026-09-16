package lovehan1me.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.delete
import lovehan1me.filter_preset_empty
import lovehan1me.filter_preset_name_hint
import lovehan1me.filter_preset_name_title
import lovehan1me.filter_preset_overwrite_hint
import lovehan1me.filter_preset_save_current
import lovehan1me.filter_preset_title
import lovehan1me.ic_delete
import lovehan1me.sure_to_delete_s
import lovehan1me.ui.component.IconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.core.domain.model.SearchFilterPreset

/**
 * 命名筛选预设区：一行标题 + 「保存当前筛选」，下面是已存预设的芯片。
 *
 * 交互约定（与筛选芯片那套刻意错开，避免误触）：
 * - **点击芯片 = 应用该预设**（恢复条件并重新搜索）；
 * - **删除走芯片旁的独立按钮 + 二次确认**，不用长按 —— 长按在本项目里已经被
 *   "清空该筛选单元的已选项"占用了（见 [AdvancedSearchFiltersSection] 的 tips），
 *   同一块界面上再叠一层长按语义会让用户不敢点。
 *
 * 存/删都要落盘（suspend），所以这里只向上抛意图，由调用方的 coroutine scope 去写。
 */
@Composable
fun SearchPresetSection(
    presets: List<SearchFilterPreset>,
    /** 当前筛选是否非空。空条件存成预设没有意义，所以按钮置灰而不是存一条空的。 */
    canSave: Boolean,
    onApply: (SearchFilterPreset) -> Unit,
    onSave: (name: String) -> Unit,
    onDelete: (SearchFilterPreset) -> Unit,
    modifier: Modifier = Modifier,
    /** 与 [AdvancedSearchFiltersSection] 同一约定：底栏弹窗满宽用 2，240dp 常驻栏用 1。 */
    columns: Int = 1,
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SearchFilterPreset?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.filter_preset_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = { showNameDialog = true },
                enabled = canSave,
            ) {
                Text(
                    text = stringResource(Res.string.filter_preset_save_current),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }

        if (presets.isEmpty()) {
            Text(
                text = stringResource(Res.string.filter_preset_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presets.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AssistChip(
                            onClick = { onApply(preset) },
                            label = {
                                Text(
                                    text = preset.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                        IconButton(onClick = { pendingDelete = preset }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_delete),
                                contentDescription = stringResource(Res.string.delete),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        PresetNameDialog(
            existingNames = presets.mapTo(mutableSetOf()) { it.name },
            onDismiss = { showNameDialog = false },
            onConfirm = { name ->
                showNameDialog = false
                onSave(name)
            },
        )
    }

    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.filter_preset_title)) },
            // 复用已有的通用删除确认文案（"确定要删除 %1$s 吗？"），不为预设再造一条。
            text = { Text(stringResource(Res.string.sure_to_delete_s, preset.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(preset)
                    },
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/**
 * 命名对话框。
 *
 * 名字是唯一键（同名即覆盖已存的那条，见 [SearchFilterPresetStore.upsert]），所以：
 * - 归一化后为空的名字不允许确认（[SearchFilterPresetStore.normalizeName] 是同一个判据，
 *   避免"这里看着能存、存下去被静默丢弃"）；
 * - 与已有预设重名时给出即时提示，免得用户以为新建了一条、实际把旧的覆盖掉了。
 */
@Composable
private fun PresetNameDialog(
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val normalized = SearchFilterPresetStore.normalizeName(name)
    val willOverwrite = normalized != null && normalized in existingNames

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.filter_preset_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.filter_preset_name_hint)) },
                    singleLine = true,
                )
                if (willOverwrite) {
                    Text(
                        text = stringResource(Res.string.filter_preset_overwrite_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { normalized?.let(onConfirm) },
                enabled = normalized != null,
            ) {
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
