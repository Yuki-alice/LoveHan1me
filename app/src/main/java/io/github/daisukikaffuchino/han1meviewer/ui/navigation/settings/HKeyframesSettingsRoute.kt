package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.default_
import io.github.daisukikaffuchino.han1meviewer.h_keyframes_disable_tip
import io.github.daisukikaffuchino.han1meviewer.h_keyframes_enable_tip
import io.github.daisukikaffuchino.han1meviewer.shared_h_keyframe_detected_msg
import io.github.daisukikaffuchino.han1meviewer.will_remind_before_d_seconds
import io.github.daisukikaffuchino.han1meviewer.h_keyframes_shared_by_other_detected
import io.github.daisukikaffuchino.han1meviewer.h_keyframes_import_shared_hint
import io.github.daisukikaffuchino.han1meviewer.h_keyframes_import_shared
import io.github.daisukikaffuchino.han1meviewer.confirm
import io.github.daisukikaffuchino.han1meviewer.cancel
import io.github.daisukikaffuchino.han1meviewer.logic.entity.HKeyframeEntity
import io.github.daisukikaffuchino.han1meviewer.ui.component.ConfirmDialog
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.HKeyframeSettingsScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.HKeyframeSettingsUiState
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.HKeyframesScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.SharedHKeyframesScreen
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.SettingsViewModel
import io.github.daisukikaffuchino.utils.rememberCopyTextToClipboard
import io.github.daisukikaffuchino.utils.decodeFromStringByBase64
import io.github.daisukikaffuchino.utils.SonnerToast
import io.github.daisukikaffuchino.utils.toastText
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch

@Composable
fun HKeyframesRouteScreen(
    onOpenVideo: (String) -> Unit,
    showImportDialog: Boolean,
    onImportDialogDismiss: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel()
    val copyTextToClipboard = rememberCopyTextToClipboard()
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
                    SonnerToast.info(toastText(R.string.h_keyframes_shared_by_other_not_detected))
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
            SonnerToast.success(toastText(R.string.modify_success))
        },
        onDeleteKeyframe = { videoCode, keyframe ->
            viewModel.removeHKeyframe(videoCode, keyframe)
            SonnerToast.success(toastText(R.string.delete_success))
        },
        onUpdateKeyframe = { videoCode, oldKeyframe, newKeyframe ->
            viewModel.modifyHKeyframe(videoCode, oldKeyframe, newKeyframe)
            SonnerToast.success(toastText(R.string.modify_success))
        },
        onCopyShareContent = {
            copyTextToClipboard(it)
            SonnerToast.success(toastText(R.string.copy_to_clipboard))
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
                viewModel.insertHKeyframes(entity.copy(lastModifiedTime = System.currentTimeMillis()))
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
    val viewModel: SettingsViewModel = viewModel()
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
    val context = LocalContext.current
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
    val uiState = remember(settings, context, enableTipTop, disableTipTop, countdownSummaryTop) {
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
