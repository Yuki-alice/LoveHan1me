package me.lovehan1me.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import me.lovehan1me.ui.viewmodel.sharedViewModel
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.Res
import me.lovehan1me.action_not_support
import me.lovehan1me.cancel
import me.lovehan1me.confirm
import me.lovehan1me.create_group_success
import me.lovehan1me.delete
import me.lovehan1me.delete_success
import me.lovehan1me.ext_player
import me.lovehan1me.group_name_empty
import me.lovehan1me.group_renamed
import me.lovehan1me.ok
import me.lovehan1me.permission_error
import me.lovehan1me.prepare_to_delete_s
import me.lovehan1me.read_download_dir_message
import me.lovehan1me.read_download_dir_title
import me.lovehan1me.read_success
import me.lovehan1me.select_custom_directory
import me.lovehan1me.sure_to_delete
import me.lovehan1me.video_deleted_sure_to_delete_item
import me.lovehan1me.video_not_exist
import me.lovehan1me.logic.dao.DownloadDatabase
import me.lovehan1me.logic.dao.Han1meDatabases
import me.lovehan1me.logic.entity.download.HanimeDownloadEntity
import me.lovehan1me.logic.entity.download.VideoWithCategories
import me.lovehan1me.ui.component.ConfirmDialog
import me.lovehan1me.ui.screen.home.DownloadScreen
import me.lovehan1me.ui.screen.home.download.DownloadEvent
import me.lovehan1me.ui.viewmodel.DownloadViewModel
import me.lovehan1me.logic.platform.downloadWorkController
import me.lovehan1me.utils.SonnerToast
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
                    scope.launch { SonnerToast.warning(getString(Res.string.select_custom_directory)) }
                }
            }

            is DownloadEvent.OnOpenDownloadedVideo -> onNavigateToVideo(event.video.video.videoCode)
            is DownloadEvent.OnLocalPlayback -> onNavigateToLocalVideo(
                event.video.video.videoCode, event.video.video.videoUri
            )

            is DownloadEvent.OnExternalPlayback -> onExternalPlayback?.invoke(
                event.video.video.videoUri,
            ) { showVideoNotExistConfirm = event.video }

            is DownloadEvent.OnDeleteDownloadedVideo -> showDeleteVideoConfirm = event.video

            is DownloadEvent.OnMoveVideoGroup -> viewModel.updateVideoGroup(
                event.video.video.videoCode, event.groupId
            )

            is DownloadEvent.OnRenameGroup -> {
                viewModel.updateGroupName(event.groupId, event.newName)
                scope.launch { SonnerToast.success(getString(Res.string.group_renamed, event.newName)) }
            }

            is DownloadEvent.OnCreateGroup -> {
                if (event.name.isBlank()) {
                    scope.launch { SonnerToast.warning(getString(Res.string.group_name_empty)) }
                } else {
                    viewModel.createNewGroup(event.name)
                    scope.launch {
                        SonnerToast.success(
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
                scope.launch { SonnerToast.success(getString(Res.string.delete_success)) }
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
                    SonnerToast.success(getString(Res.string.read_success))
                } else {
                    SonnerToast.error(getString(Res.string.permission_error))
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
