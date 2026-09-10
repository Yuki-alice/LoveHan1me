package lovehan1me.ui.navigation.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.ui.viewmodel.sharedViewModel
import lovehan1me.logic.SettingsRepository
import lovehan1me.logic.currentEpochMillis
import lovehan1me.Res
import lovehan1me.copy_to_clipboard
import lovehan1me.default_
import lovehan1me.delete_success
import lovehan1me.h_keyframes_disable_tip
import lovehan1me.h_keyframes_enable_tip
import lovehan1me.h_keyframes_shared_by_other_not_detected
import lovehan1me.modify_success
import lovehan1me.shared_h_keyframe_detected_msg
import lovehan1me.will_remind_before_d_seconds
import lovehan1me.h_keyframes_shared_by_other_detected
import lovehan1me.h_keyframes_import_shared_hint
import lovehan1me.h_keyframes_import_shared
import lovehan1me.confirm
import lovehan1me.cancel
import lovehan1me.data.database.entity.HKeyframeEntity
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.screen.settings.HKeyframeSettingsScreen
import lovehan1me.ui.screen.settings.HKeyframeSettingsUiState
import lovehan1me.ui.screen.settings.HKeyframesScreen
import lovehan1me.ui.screen.settings.SharedHKeyframesScreen
import lovehan1me.ui.viewmodel.SettingsViewModel
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.core.util.decodeFromStringByBase64
import lovehan1me.core.util.SonnerToast
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun HKeyframesRouteScreen(
    onOpenVideo: (String) -> Unit,
    showImportDialog: Boolean,
    onImportDialogDismiss: () -> Unit,
) {
    val viewModel: SettingsViewModel = sharedViewModel(::SettingsViewModel)
    val copyTextToClipboard = rememberCopyTextToClipboard()
    // P6d-3-C3：回调内 toast 转 suspend getString，经 scope 桥接
    val scope = rememberCoroutineScope()
    val items by viewModel.loadAllHKeyframes()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var sharedHKeyframeEntity by remember { mutableStateOf<HKeyframeEntity?>(null) }

    if (showImportDialog) {
        ImportSharedHKeyframeDialog(
            onDismiss = onImportDialogDismiss,
            onConfirm = { content ->
                val entity = parseSharedHKeyframe(content)
                if (entity != null) {
                    sharedHKeyframeEntity = entity
                    onImportDialogDismiss()
                } else {
                    scope.launch { SonnerToast.info(getString(Res.string.h_keyframes_shared_by_other_not_detected)) }
                }
            },
        )
    }

    HKeyframesScreen(
        items = items,
        onOpenVideo = onOpenVideo,
        onDeleteEntity = { entity ->
            viewModel.deleteHKeyframes(entity)
        },
        onUpdateEntityTitle = { entity, newTitle ->
            viewModel.updateHKeyframes(entity.copy(title = newTitle))
            scope.launch { SonnerToast.success(getString(Res.string.modify_success)) }
        },
        onDeleteKeyframe = { videoCode, keyframe ->
            viewModel.removeHKeyframe(videoCode, keyframe)
            scope.launch { SonnerToast.success(getString(Res.string.delete_success)) }
        },
        onUpdateKeyframe = { videoCode, oldKeyframe, newKeyframe ->
            viewModel.modifyHKeyframe(videoCode, oldKeyframe, newKeyframe)
            scope.launch { SonnerToast.success(getString(Res.string.modify_success)) }
        },
        onCopyShareContent = {
            copyTextToClipboard(it)
            scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
        },
    )

    sharedHKeyframeEntity?.let { entity ->
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.h_keyframes_shared_by_other_detected),
            message = stringResource(Res.string.shared_h_keyframe_detected_msg,
                entity.title,
                entity.videoCode,
                entity.keyframes.size,
            ).trimIndent(),
            confirmText = stringResource(Res.string.confirm),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                viewModel.insertHKeyframes(entity.copy(lastModifiedTime = currentEpochMillis()))
                sharedHKeyframeEntity = null
            },
            onDismiss = { sharedHKeyframeEntity = null },
        )
    }
}

private val shareRegex = Regex(">>>(.+)<<<")

private fun parseSharedHKeyframe(content: String): HKeyframeEntity? {
    return runCatching {
        val matchResult = shareRegex.find(content) ?: return@runCatching null
        val (toBase64) = matchResult.destructured
        val toJson = toBase64.decodeFromStringByBase64()
        Json.decodeFromString<HKeyframeEntity>(toJson)
    }.getOrNull()
}

@Composable
private fun ImportSharedHKeyframeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.h_keyframes_import_shared)) },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(Res.string.h_keyframes_import_shared_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(content) }) {
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
fun SharedHKeyframesRouteScreen(
    onOpenVideo: (String) -> Unit,
) {
    val viewModel: SettingsViewModel = sharedViewModel(::SettingsViewModel)
    val items by viewModel.loadAllSharedHKeyframes()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    SharedHKeyframesScreen(
        items = items,
        onOpenVideo = onOpenVideo,
    )
}

@Composable
fun HKeyframeSettingsRouteScreen(
    onNavigateToHKeyframes: () -> Unit,
    onNavigateToSharedHKeyframes: () -> Unit,
    embedded: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    // P6d-3-C2：builder 在 remember{} 内无法调 stringResource，字符串在外层预解析后传入
    val enableTipTop = stringResource(Res.string.h_keyframes_enable_tip)
    val disableTipTop = stringResource(Res.string.h_keyframes_disable_tip)
    val countdownSummaryTop = toPrettyCountdownRemindString(
        SettingsRepository.whenCountdownRemind / 1000,
        stringResource(Res.string.will_remind_before_d_seconds),
        stringResource(Res.string.default_),
    )
    val uiState = remember(settings, enableTipTop, disableTipTop, countdownSummaryTop) {
        buildHKeyframeSettingsUiState(enableTipTop, disableTipTop, countdownSummaryTop)
    }

    HKeyframeSettingsScreen(
        state = uiState,
        onHKeyframesEnableChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(hKeyframesEnable = it) } }
        },
        onOpenHKeyframeManage = onNavigateToHKeyframes,
        onSharedHKeyframesEnableChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(sharedHKeyframesEnable = it) } }
        },
        onSharedHKeyframesUseFirstChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(sharedHKeyframesUseFirst = it) } }
        },
        onOpenSharedHKeyframeManage = onNavigateToSharedHKeyframes,
        onShowCommentWhenCountdownChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(showCommentWhenCountdown = it) } }
        },
        onWhenCountdownRemindChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(whenCountdownRemindSeconds = it) } }
        },
        embedded = embedded,
    )
}

private fun buildHKeyframeSettingsUiState(
    enableTip: String,
    disableTip: String,
    countdownSummary: String,
): HKeyframeSettingsUiState {
    return HKeyframeSettingsUiState(
        hKeyframesEnable = SettingsRepository.hKeyframesEnable,
        hKeyframesSummary = if (SettingsRepository.hKeyframesEnable) {
            enableTip
        } else {
            disableTip
        },
        sharedHKeyframesEnable = SettingsRepository.sharedHKeyframesEnable,
        sharedHKeyframesUseFirst = SettingsRepository.sharedHKeyframesUseFirst,
        showCommentWhenCountdown = SettingsRepository.showCommentWhenCountdown,
        whenCountdownRemind = SettingsRepository.whenCountdownRemind / 1000,
        whenCountdownRemindSummary = countdownSummary,
    )
}
