package lovehan1me.app.navigation.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.documentfile.provider.DocumentFile
import lovehan1me.data.SettingsRepository
import lovehan1me.R
import lovehan1me.Res
import lovehan1me.directory_saved
import lovehan1me.no_directory_selected
import lovehan1me.default_path_restored
import lovehan1me.import_complete
import lovehan1me.no_exportable_files
import lovehan1me.no_limit
import lovehan1me.permission_error
import lovehan1me.understood
import lovehan1me.unknown_error
import lovehan1me.specify_path_first
import lovehan1me.select_folder_message
import lovehan1me.select_download_folder
import lovehan1me.restore_default_path
import lovehan1me.restore_default_message
import lovehan1me.path_permission_message
import lovehan1me.ok
import lovehan1me.importing
import lovehan1me.import_warning
import lovehan1me.import_progress_format
import lovehan1me.import_progress
import lovehan1me.confirm_import
import lovehan1me.cancel
import lovehan1me.data.database.dao.Han1meDatabases
import lovehan1me.data.network.interceptor.SpeedLimitInterceptor
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.TripleButtonDialog
import lovehan1me.feature.settings.DownloadSettingsScreen
import lovehan1me.feature.settings.DownloadSettingsUiState
import lovehan1me.core.util.SafFileManager
import lovehan1me.core.util.SafFileManager.KEY_TREE_URI
import lovehan1me.worker.HanimeDownloadManager
import lovehan1me.core.util.SonnerToast
import lovehan1me.core.util.toastText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DownloadSettingsRouteScreen(embedded: Boolean = false) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    var showDownloadPathDialog by remember { mutableStateOf(false) }
    var showRestoreDefaultConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showSpecifyPathDialog by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<ImportProgress?>(null) }
    val dao = remember { Han1meDatabases.download.hanimeDownloadDao }
    // P6d-3-C2：builder 在 remember{} 内无法调资源，字符串在外层预解析后传入
    val unknownErrorTop = stringResource(Res.string.unknown_error)
    val noLimitTop = stringResource(Res.string.no_limit)
    val uiState = remember(settings, context, unknownErrorTop, noLimitTop) {
        buildDownloadSettingsUiState(context, unknownErrorTop, noLimitTop)
    }

    val openDirectoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            coroutineScope.launch {
                SafFileManager.persistUriPermission(context, result.data)
                SonnerToast.success(getString(Res.string.directory_saved, result.data.toString()))
            }
        } else {
            coroutineScope.launch { SonnerToast.warning(getString(Res.string.no_directory_selected)) }
        }
    }

    DownloadSettingsScreen(
        state = uiState,
        maxDownloadCountLimit = 10,
        maxDownloadSpeedLimitIndex = SpeedLimitInterceptor.SPEED_BYTES.lastIndex,
        onOpenDownloadPath = { showDownloadPathDialog = true },
        onRestoreDefaultPath = { },
        onImportDownloadedFiles = {
            if (!SettingsRepository.isUsePrivateStorage &&
                !SettingsRepository.safDownloadPath.isNullOrBlank() &&
                SafFileManager.checkSafPermissions(context)
            ) {
                showImportConfirm = true
            } else {
                showSpecifyPathDialog = true
            }
        },
        onDownloadCountLimitChange = { value ->
            coroutineScope.launch {
                SettingsRepository.setDownloadCountLimit(value)
                HanimeDownloadManager.maxConcurrentDownloadCount = value
            }
        },
        onDownloadSpeedLimitChange = { value ->
            coroutineScope.launch { SettingsRepository.setDownloadSpeedLimitIndex(value) }
        },
        embedded = embedded,
    )

    if (!SettingsRepository.isUsePrivateStorage) {
        TripleButtonDialog(
            visible = showDownloadPathDialog,
            title = stringResource(Res.string.select_download_folder),
            message = stringResource(Res.string.select_folder_message),
            negativeText = stringResource(Res.string.cancel),
            neutralText = stringResource(Res.string.restore_default_path),
            positiveText = stringResource(Res.string.ok),
            onNegative = { showDownloadPathDialog = false },
            onNeutral = {
                showDownloadPathDialog = false
                showRestoreDefaultConfirm = true
            },
            onPositive = {
                showDownloadPathDialog = false
                openDirectoryPicker.launch(SafFileManager.buildOpenDirectoryIntent())
            },
            onDismiss = { showDownloadPathDialog = false },
        )
    } else {
        ConfirmDialog(
            visible = showDownloadPathDialog,
            title = stringResource(Res.string.select_download_folder),
            message = stringResource(Res.string.select_folder_message),
            confirmText = stringResource(Res.string.ok),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                showDownloadPathDialog = false
                openDirectoryPicker.launch(SafFileManager.buildOpenDirectoryIntent())
            },
            onDismiss = { showDownloadPathDialog = false },
        )
    }

    ConfirmDialog(
        visible = showRestoreDefaultConfirm,
        title = stringResource(Res.string.restore_default_path),
        message = stringResource(Res.string.restore_default_message),
        confirmText = stringResource(Res.string.ok),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            coroutineScope.launch {
                SettingsRepository.setDownloadStorage(usePrivate = true, path = null)
                showRestoreDefaultConfirm = false
                SonnerToast.success(getString(Res.string.default_path_restored))
            }
        },
        onDismiss = { showRestoreDefaultConfirm = false },
    )

    ConfirmDialog(
        visible = showImportConfirm,
        title = stringResource(Res.string.confirm_import),
        message = stringResource(Res.string.import_warning),
        confirmText = stringResource(Res.string.ok),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            showImportConfirm = false
            importProgress = ImportProgress()
            SafFileManager.migratePrivateToSaf(context, dao) { migrated, total ->
                when (total) {
                    0 -> {
                        importProgress = null
                        coroutineScope.launch {
                            SonnerToast.info(getString(Res.string.no_exportable_files))
                        }
                    }

                    -1 -> {
                        importProgress = null
                        coroutineScope.launch {
                            SonnerToast.error(getString(Res.string.permission_error))
                        }
                    }

                    else -> {
                        importProgress = ImportProgress(migrated, total)
                        if (migrated == total) {
                            importProgress = null
                            coroutineScope.launch {
                                SonnerToast.success(getString(Res.string.import_complete, total))
                            }
                        }
                    }
                }
            }
        },
        onDismiss = { showImportConfirm = false },
    )

    if (showSpecifyPathDialog) {
        AlertDialog(
            onDismissRequest = { showSpecifyPathDialog = false },
            title = { Text(stringResource(Res.string.specify_path_first)) },
            text = { Text(stringResource(Res.string.path_permission_message)) },
            confirmButton = {
                TextButton(onClick = { showSpecifyPathDialog = false }) {
                    Text(stringResource(Res.string.understood))
                }
            },
        )
    }

    importProgress?.let { progress ->
        ImportProgressDialog(progress = progress)
    }
}

private data class ImportProgress(
    val migrated: Int = 0,
    val total: Int = 0,
)

@Composable
private fun ImportProgressDialog(progress: ImportProgress) {
    val percent = if (progress.total > 0) {
        progress.migrated * 100 / progress.total
    } else {
        0
    }
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.import_progress),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(stringResource(Res.string.importing))
                LinearProgressIndicator(
                    progress = {
                        if (progress.total > 0) {
                            progress.migrated.toFloat() / progress.total.toFloat()
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.import_progress_format,
                        progress.migrated,
                        progress.total,
                        percent,
                    )
                )
            }
        }
    }
}

private fun buildDownloadSettingsUiState(context: Context, unknownError: String, noLimit: String): DownloadSettingsUiState {
    val uri = SafFileManager.getSavedUri()
    val pathSummary = if (SettingsRepository.isUsePrivateStorage) {
        context.getExternalFilesDir(null)?.absolutePath.orEmpty()
    } else {
        DocumentFile.fromTreeUri(
            context,
            uri ?: return DownloadSettingsUiState(
                downloadPathSummary = unknownError,
                downloadCountLimit = SettingsRepository.downloadCountLimit,
                downloadCountLimitSummary = toDownloadCountLimitPrettyString(
                    noLimit,
                    SettingsRepository.downloadCountLimit
                ),
                downloadSpeedLimitIndex = SettingsRepository.current.downloadSpeedLimitIndex,
                downloadSpeedLimitSummary = SpeedLimitInterceptor.SPEED_BYTES[
                    SettingsRepository.current.downloadSpeedLimitIndex
                ].toDownloadSpeedPrettyString(noLimit),
            )
        )?.name ?: uri.toString()
    }
    val speedIndex = SettingsRepository.current.downloadSpeedLimitIndex
    return DownloadSettingsUiState(
        downloadPathSummary = pathSummary,
        downloadCountLimit = SettingsRepository.downloadCountLimit,
        downloadCountLimitSummary = toDownloadCountLimitPrettyString(
            noLimit,
            SettingsRepository.downloadCountLimit
        ),
        downloadSpeedLimitIndex = speedIndex,
        downloadSpeedLimitSummary = SpeedLimitInterceptor.SPEED_BYTES[speedIndex]
            .toDownloadSpeedPrettyString(noLimit),
    )
}
