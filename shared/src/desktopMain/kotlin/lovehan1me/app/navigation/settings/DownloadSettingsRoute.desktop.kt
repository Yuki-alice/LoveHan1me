package lovehan1me.app.navigation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.core.platform.DesktopDownloadWorkController
import lovehan1me.core.platform.desktopDownloadWorkController
import lovehan1me.core.util.AppToast
import lovehan1me.data.SettingsRepository
import lovehan1me.default_path_restored
import lovehan1me.directory_saved
import lovehan1me.feature.settings.DownloadSettingsScreen
import lovehan1me.feature.settings.DownloadSettingsUiState
import lovehan1me.no_directory_selected
import lovehan1me.no_limit
import lovehan1me.ok
import lovehan1me.read_download_dir_message
import lovehan1me.read_download_dir_title
import lovehan1me.read_success
import lovehan1me.restore_default_message
import lovehan1me.restore_default_path
import lovehan1me.select_download_folder
import lovehan1me.select_folder_message
import lovehan1me.permission_error
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.TripleButtonDialog
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.JFileChooser

/**
 * 桌面下载设置真页（此前 `NavPlaceholder"下载目录（SAF）随 P7"`）。
 *
 * 与 Android 页同源 UI（commonMain `DownloadSettingsScreen`），三处平台差异：
 * - 目录选择用 JFileChooser（DIRECTORIES_ONLY，后台线程弹，避免卡 EDT）；
 * - 路径展示与落盘同源（`currentDownloadDir()`），不分头写第二份推导；
 * - 恢复默认 = 清掉配置（回 `~/LoveHan1me/downloads`）。
 * 并发数改完即调 `updateDownloadLimit`（P1-3 已热生效，无需重启）。
 */
@Composable
fun DownloadSettingsRouteScreen(embedded: Boolean = false) {
    val scope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    var showImportConfirm by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    var showRestoreDefaultConfirm by remember { mutableStateOf(false) }
    val noLimitText = stringResource(Res.string.no_limit)

    val pathSummary = remember(settings.safDownloadPath) {
        DesktopDownloadWorkController.currentDownloadDir().absolutePath
    }
    val uiState = remember(settings.downloadCountLimit, pathSummary) {
        DownloadSettingsUiState(
            downloadPathSummary = pathSummary,
            downloadCountLimit = settings.downloadCountLimit,
            downloadCountLimitSummary = toDownloadCountLimitPrettyString(
                noLimitText,
                settings.downloadCountLimit,
            ),
        )
    }

    fun pickDirectory() {
        Thread {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                val current = File(pathSummary)
                if (current.exists()) currentDirectory = current
            }
            val picked = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile?.absolutePath
            } else {
                null
            }
            scope.launch {
                if (picked != null) {
                    SettingsRepository.setDownloadStorage(false, picked)
                    AppToast.success(getString(Res.string.directory_saved, picked))
                } else {
                    AppToast.warning(getString(Res.string.no_directory_selected))
                }
            }
        }.apply { isDaemon = true }.start()
    }

    DownloadSettingsScreen(
        state = uiState,
        maxDownloadCountLimit = 10,
        onOpenDownloadPath = { showPathDialog = true },
        onRestoreDefaultPath = {
            scope.launch { SettingsRepository.setDownloadStorage(false, null) }
        },
        onImportDownloadedFiles = { showImportConfirm = true },
        onDownloadCountLimitChange = { value ->
            scope.launch {
                SettingsRepository.setDownloadCountLimit(value)
                desktopDownloadWorkController().updateDownloadLimit(value)
            }
        },
        embedded = embedded,
    )

    // 对齐上游三键目录对话框（自选目录 / 恢复默认 / 取消）。
    TripleButtonDialog(
        visible = showPathDialog,
        title = stringResource(Res.string.select_download_folder),
        message = stringResource(Res.string.select_folder_message),
        negativeText = stringResource(Res.string.cancel),
        neutralText = stringResource(Res.string.restore_default_path),
        positiveText = stringResource(Res.string.ok),
        onNegative = { showPathDialog = false },
        onNeutral = {
            showPathDialog = false
            showRestoreDefaultConfirm = true
        },
        onPositive = {
            showPathDialog = false
            pickDirectory()
        },
        onDismiss = { showPathDialog = false },
    )

    ConfirmDialog(
        visible = showRestoreDefaultConfirm,
        title = stringResource(Res.string.restore_default_path),
        message = stringResource(Res.string.restore_default_message),
        confirmText = stringResource(Res.string.ok),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            showRestoreDefaultConfirm = false
            scope.launch {
                SettingsRepository.setDownloadStorage(usePrivate = false, path = null)
                AppToast.success(getString(Res.string.default_path_restored))
            }
        },
        onDismiss = { showRestoreDefaultConfirm = false },
    )

    ConfirmDialog(
        visible = showImportConfirm,
        title = stringResource(Res.string.read_download_dir_title),
        message = stringResource(Res.string.read_download_dir_message),
        confirmText = stringResource(Res.string.ok),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            showImportConfirm = false
            scope.launch {
                val ok = runCatching { desktopDownloadWorkController().importDownloaded() }
                    .getOrDefault(false)
                if (ok) {
                    AppToast.success(getString(Res.string.read_success))
                } else {
                    AppToast.error(getString(Res.string.permission_error))
                }
            }
        },
        onDismiss = { showImportConfirm = false },
    )
}
