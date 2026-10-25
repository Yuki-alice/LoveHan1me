package lovehan1me.app.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import lovehan1me.app.sharedViewModel
import lovehan1me.data.SettingsRepository
import lovehan1me.Res
import lovehan1me.action_not_support
import lovehan1me.backup_import_failed
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.create_group_success
import lovehan1me.delete
import lovehan1me.delete_success
import lovehan1me.ext_player
import lovehan1me.group_name_empty
import lovehan1me.group_renamed
import lovehan1me.ok
import lovehan1me.permission_error
import lovehan1me.prepare_to_delete_s
import lovehan1me.read_download_dir_message
import lovehan1me.read_download_dir_title
import lovehan1me.read_success
import lovehan1me.select_custom_directory
import lovehan1me.sure_to_delete
import lovehan1me.video_deleted_sure_to_delete_item
import lovehan1me.video_not_exist
import lovehan1me.data.database.dao.DownloadDatabase
import lovehan1me.data.database.dao.Han1meDatabases
import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import lovehan1me.data.database.entity.download.VideoWithCategories
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.feature.home.DownloadScreen
import lovehan1me.feature.home.download.DownloadEvent
import lovehan1me.feature.library.DownloadViewModel
import lovehan1me.core.platform.ExternalOpenOutcome
import lovehan1me.core.platform.downloadWorkController
import lovehan1me.core.platform.ioDispatcher
import lovehan1me.core.platform.rememberBackupImportLauncher
import lovehan1me.core.platform.toPlayableLocalUri
import lovehan1me.core.util.AppToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DownloadRouteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToLocalVideo: (String, String?) -> Unit,
    /** 外部播放器打开已下载视频（Android=ACTION_VIEW chooser）；null=平台不支持。 */
    onExternalPlayback: ((videoUriPath: String, onNotExist: () -> Unit) -> Unit)? = null,
    /** 从下载目录导入（Android=SAF 扫描）；null=平台不支持。 */
    onImportDownloaded: (() -> Unit)? = null,
) {
    val viewModel: DownloadViewModel = sharedViewModel(::DownloadViewModel)
    val scope = rememberCoroutineScope()
    val dao = remember { Han1meDatabases.download.hanimeDownloadDao }
    var showVideoNotExistConfirm by remember { mutableStateOf<VideoWithCategories?>(null) }
    var showDeleteVideoConfirm by remember { mutableStateOf<VideoWithCategories?>(null) }
    var showImportDownloadedConfirm by remember { mutableStateOf(false) }
    var isImportingDownloaded by remember { mutableStateOf(false) }
    var isImportingExternalFile by remember { mutableStateOf(false) }

    // 阶段一⑦：外部文件导入 picker（三端 launcher 复用；仅 supportsExternalImport
    // 平台收到非空回调——iOS 回传临时路径，Android/desktop 因入口隐藏调不到）。
    val externalImportLauncher = rememberBackupImportLauncher { picked ->
        if (picked == null || isImportingExternalFile) return@rememberBackupImportLauncher
        isImportingExternalFile = true
        scope.launch(ioDispatcher) {
            val ok = runCatching { downloadWorkController().importExternalFile(picked) }
                .getOrDefault(false)
            withContext(Dispatchers.Main) {
                isImportingExternalFile = false
                if (ok) {
                    viewModel.loadAllDownloadedHanime(
                        sortedBy = HanimeDownloadEntity.SortedBy.ID,
                        ascending = false,
                    )
                    AppToast.success(getString(Res.string.read_success))
                } else {
                    AppToast.error(getString(Res.string.backup_import_failed))
                }
            }
        }
    }

    val handleEvent: (DownloadEvent) -> Unit = { event ->
        when (event) {
            is DownloadEvent.OnPauseAll -> event.items.forEach { entity ->
                if (entity.isDownloading) downloadWorkController().pauseTask(entity)
            }

            is DownloadEvent.OnResumeAll -> event.items.forEach { entity ->
                if (!entity.isDownloading) downloadWorkController().resumeTask(entity)
            }

            is DownloadEvent.OnPauseItem -> downloadWorkController().pauseTask(event.item)
            is DownloadEvent.OnResumeItem -> downloadWorkController().resumeTask(event.item)
            is DownloadEvent.OnDeleteDownloadingItem -> downloadWorkController().deleteTask(event.item)

            is DownloadEvent.OnImportDownloaded -> {
                if (onImportDownloaded != null && !isImportingDownloaded) {
                    showImportDownloadedConfirm = true
                } else {
                    scope.launch { AppToast.warning(getString(Res.string.select_custom_directory)) }
                }
            }

            is DownloadEvent.OnImportExternalFile -> {
                if (downloadWorkController().supportsExternalImport() && !isImportingExternalFile) {
                    externalImportLauncher()
                } else {
                    scope.launch { AppToast.warning(getString(Res.string.action_not_support)) }
                }
            }

            is DownloadEvent.OnOpenDownloadedVideo -> onNavigateToVideo(event.video.video.videoCode)
            is DownloadEvent.OnLocalPlayback -> {
                // 本地播放走“缓存优先”：DB 行 + 文件双判定在各端 videoCacheStore 里做，
                // 这里只负责把 DB 原始 videoUri 归一化成引擎可播的 file:// 后下发。
                // 缺文件时 VideoViewModel 会 emit NoContent（播放页错误卡），与此前一致；
                // 若需删库确认框，调用方点删除即可（外部打开缺文件才弹确认框，避免误杀）。
                val playable = event.video.video.videoUri.toPlayableLocalUri()
                // 遗留 `local://` 占位未经 resolver 还原时不下发死链：直接提示缺文件，
                // 引导用户走删除/重新下载（各端 initialize/首播自愈后该分支即消失）。
                if (playable.startsWith("local://")) {
                    showVideoNotExistConfirm = event.video
                } else {
                    onNavigateToLocalVideo(event.video.video.videoCode, playable)
                }
            }

            is DownloadEvent.OnExternalPlayback -> {
                if (onExternalPlayback != null) {
                    onExternalPlayback.invoke(
                        event.video.video.videoUri,
                    ) { showVideoNotExistConfirm = event.video }
                } else {
                    // 桌面/iOS 无壳层注入：走控制器跨端回退（Desktop.open / 分享面板）。
                    scope.launch(ioDispatcher) {
                        val outcome = runCatching {
                            downloadWorkController().openVideoExternally(event.video.video.videoUri)
                        }.getOrDefault(ExternalOpenOutcome.Unsupported)
                        withContext(Dispatchers.Main) {
                            when (outcome) {
                                ExternalOpenOutcome.Opened -> Unit
                                ExternalOpenOutcome.FileMissing ->
                                    showVideoNotExistConfirm = event.video

                                ExternalOpenOutcome.Unsupported ->
                                    AppToast.warning(getString(Res.string.action_not_support))
                            }
                        }
                    }
                }
            }

            is DownloadEvent.OnDeleteDownloadedVideo -> showDeleteVideoConfirm = event.video

            is DownloadEvent.OnMoveVideoGroup -> viewModel.updateVideoGroup(
                event.video.video.videoCode, event.groupId
            )

            is DownloadEvent.OnRenameGroup -> {
                viewModel.updateGroupName(event.groupId, event.newName)
                scope.launch { AppToast.success(getString(Res.string.group_renamed, event.newName)) }
            }

            is DownloadEvent.OnCreateGroup -> {
                if (event.name.isBlank()) {
                    scope.launch { AppToast.warning(getString(Res.string.group_name_empty)) }
                } else {
                    viewModel.createNewGroup(event.name)
                    scope.launch {
                        AppToast.success(
                            getString(
                                Res.string.create_group_success,
                                event.name
                            )
                        )
                    }
                }
            }

            is DownloadEvent.OnDeleteGroup -> {
                viewModel.deleteGroup(event.group)
                scope.launch { AppToast.success(getString(Res.string.delete_success)) }
            }

            is DownloadEvent.OnBatchDelete -> event.videos.forEach { video ->
                viewModel.deleteDownloadHanimeBy(video.video.videoCode, video.video.quality)
                downloadWorkController().deleteVideoFolder(video.video.videoCode)
            }

            is DownloadEvent.OnBatchMoveGroup -> event.videos.forEach { video ->
                viewModel.updateVideoGroup(video.video.videoCode, event.groupId)
            }

            // 以下事件由 Screen 层自行处理，Route 不关心
            is DownloadEvent.OnToggleGroup,
            is DownloadEvent.OnCreateGroupDialogChange,
            is DownloadEvent.OnPageChange,
            is DownloadEvent.OnToggleMultiSelect,
            is DownloadEvent.OnToggleVideoSelection,
            is DownloadEvent.OnSelectAllCurrentGroup,
            is DownloadEvent.OnBatchMoveRequest -> Unit
        }
    }

    DownloadScreen(
        downloadingFlow = viewModel.loadAllDownloadingHanime(),
        downloadedFlow = viewModel.downloaded,
        downloadedGroupsFlow = viewModel.downloadedGroups,
        collapseDownloadedGroup = SettingsRepository.collapseDownloadedGroup,
        onBack = onBack,
        onLoadDownloaded = {
            viewModel.loadAllDownloadedHanime(
                sortedBy = HanimeDownloadEntity.SortedBy.ID,
                ascending = false,
            )
        },
        onEvent = handleEvent,
        showImportExternalFile = downloadWorkController().supportsExternalImport(),
    )

    ConfirmDialog(
        visible = showImportDownloadedConfirm,
        title = stringResource(Res.string.read_download_dir_title),
        message = stringResource(Res.string.read_download_dir_message),
        confirmText = stringResource(Res.string.ok),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            showImportDownloadedConfirm = false
            isImportingDownloaded = true
            scope.launch {
                val importSucceeded = downloadWorkController().importDownloaded()
                isImportingDownloaded = false
                if (importSucceeded) {
                    viewModel.loadAllDownloadedHanime(
                        sortedBy = HanimeDownloadEntity.SortedBy.ID,
                        ascending = false,
                    )
                    AppToast.success(getString(Res.string.read_success))
                } else {
                    AppToast.error(getString(Res.string.permission_error))
                }
            }
        },
        onDismiss = { showImportDownloadedConfirm = false },
    )

    showVideoNotExistConfirm?.let { video ->
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.video_not_exist),
            message = stringResource(Res.string.video_deleted_sure_to_delete_item),
            confirmText = stringResource(Res.string.delete),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                viewModel.deleteDownloadHanimeBy(video.video.videoCode, video.video.quality)
                showVideoNotExistConfirm = null
            },
            onDismiss = { showVideoNotExistConfirm = null },
        )
    }

    showDeleteVideoConfirm?.let { video ->
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.sure_to_delete),
            message = stringResource(Res.string.prepare_to_delete_s, video.video.title),
            confirmText = stringResource(Res.string.confirm),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                downloadWorkController().deleteVideoFolder(video.video.videoCode)
                viewModel.deleteDownloadHanimeBy(video.video.videoCode, video.video.quality)
                showDeleteVideoConfirm = null
            },
            onDismiss = { showDeleteVideoConfirm = null },
        )
    }
}
