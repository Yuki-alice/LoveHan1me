package lovehan1me.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.start_all
import lovehan1me.read_download_dir_title
import lovehan1me.pause_all
import lovehan1me.move_group
import lovehan1me.edit
import lovehan1me.downloading
import lovehan1me.downloaded
import lovehan1me.download
import lovehan1me.create_new_group
import lovehan1me.confirm_move_videos
import lovehan1me.confirm
import lovehan1me.close
import lovehan1me.cancel
import lovehan1me.ic_add
import lovehan1me.ic_close
import lovehan1me.ic_download
import lovehan1me.ic_format_list_bulleted
import lovehan1me.ic_menu
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.logic.entity.download.DownloadGroupEntity
import lovehan1me.logic.entity.download.HanimeDownloadEntity
import lovehan1me.logic.entity.download.VideoWithCategories
import lovehan1me.logic.model.DownloadHeaderNode
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.screen.home.download.DownloadEvent
import lovehan1me.ui.screen.home.download.DownloadUiState
import lovehan1me.ui.screen.home.download.DownloadedScreen
import lovehan1me.ui.screen.home.download.DownloadingScreen
import lovehan1me.ui.screen.home.download.MoveGroupDialog
import lovehan1me.ui.screen.home.download.toDisplayGroups
import lovehan1me.ui.screen.home.download.toFlatNodeList
import lovehan1me.ui.screen.home.download.toNodeList
import lovehan1me.ui.component.rememberHapticFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * 下载页面 Screen 层。
 *
 * 接收数据流和 [DownloadEvent] 回调，管理 Tab、分组展开等 UI 编排状态。
 * 本地 UI 事件（Tab 切换、分组折叠）由 Screen 处理；业务事件透传给 Route。
 * Content 组件仅接收 UiState + Event 回调。
 *
 * @param downloadingFlow 下载中任务流
 * @param downloadedFlow 已下载视频流
 * @param downloadedGroupsFlow 分组列表流
 * @param collapseDownloadedGroup 默认折叠分组
 * @param onBack 返回回调
 * @param onLoadDownloaded 初始加载已下载列表（一次性调用）
 * @param onEvent 业务事件回调（透传给 Route 层处理）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadScreen(
    downloadingFlow: Flow<List<HanimeDownloadEntity>>,
    downloadedFlow: StateFlow<List<VideoWithCategories>>,
    downloadedGroupsFlow: StateFlow<List<DownloadGroupEntity>>,
    collapseDownloadedGroup: Boolean,
    onBack: () -> Unit,
    onLoadDownloaded: () -> Unit,
    onEvent: (DownloadEvent) -> Unit,
) {
    val downloadingItems by downloadingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedItems by downloadedFlow.collectAsStateWithLifecycle()
    val downloadedGroups by downloadedGroupsFlow.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var downloadedGroupExpandedState by rememberSaveable(stateSaver = stringBooleanMapSaver()) {
        mutableStateOf(emptyMap())
    }
    val downloadedLazyListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val scope = rememberCoroutineScope()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var downloadedHeaderNodes by remember { mutableStateOf<List<DownloadHeaderNode>>(emptyList()) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedVideoIds by remember { mutableStateOf(setOf<Int>()) }
    var pendingBatchMove by remember { mutableStateOf(false) }
    var pendingBatchMoveConfirm by remember {
        mutableStateOf<Pair<List<VideoWithCategories>, Int>?>(
            null
        )
    }

    val displayGroups = downloadedGroups.toDisplayGroups()

    val downloadedNodes =
        remember(
            downloadedItems,
            displayGroups,
            collapseDownloadedGroup,
            downloadedHeaderNodes,
            downloadedGroupExpandedState
        ) {
            val groupIdToNameMap = displayGroups.associate { it.id to it.name }
            if (downloadedItems.isEmpty()) {
                downloadedHeaderNodes = emptyList()
                emptyList()
            } else {
                val newHeaders =
                    downloadedItems.toNodeList(groupIdToNameMap, collapseDownloadedGroup)
                downloadedHeaderNodes = newHeaders.map { newHeader ->
                    newHeader.copy(
                        isExpanded = downloadedGroupExpandedState[newHeader.groupKey]
                            ?: downloadedHeaderNodes.firstOrNull { it.groupKey == newHeader.groupKey }?.isExpanded
                            ?: !collapseDownloadedGroup
                    )
                }
                downloadedHeaderNodes.toFlatNodeList()
            }
        }

    val uiState = DownloadUiState(
        downloadingItems = downloadingItems,
        downloadedNodes = downloadedNodes,
        displayGroups = displayGroups,
        currentPage = pagerState.currentPage,
        showCreateGroupDialog = showCreateGroupDialog,
        multiSelectMode = multiSelectMode,
        selectedVideoIds = selectedVideoIds,
    )

    val handleEvent: (DownloadEvent) -> Unit = { event ->
        when (event) {
            // 本地 UI 事件：Screen 自行处理
            is DownloadEvent.OnToggleGroup -> {
                downloadedHeaderNodes = downloadedHeaderNodes.map {
                    if (it.groupKey == event.groupKey) {
                        val expanded = !it.isExpanded
                        downloadedGroupExpandedState =
                            downloadedGroupExpandedState + (it.groupKey to expanded)
                        it.copy(isExpanded = expanded)
                    } else {
                        it
                    }
                }
            }

            is DownloadEvent.OnCreateGroupDialogChange -> showCreateGroupDialog = event.visible
            is DownloadEvent.OnPageChange -> {
                scope.launch { pagerState.animateScrollToPage(event.page) }
            }
            // 多选事件：Screen 自行处理
            is DownloadEvent.OnToggleMultiSelect -> {
                multiSelectMode = !multiSelectMode
                if (!multiSelectMode) selectedVideoIds = emptySet()
            }

            is DownloadEvent.OnToggleVideoSelection -> {
                selectedVideoIds = if (event.videoId in selectedVideoIds) {
                    selectedVideoIds - event.videoId
                } else {
                    selectedVideoIds + event.videoId
                }
            }

            is DownloadEvent.OnSelectAllCurrentGroup -> {
                val groupVideos =
                    downloadedNodes.filterIsInstance<lovehan1me.logic.model.DownloadItemNode>()
                        .filter { it.parentKey == event.groupKey }
                selectedVideoIds = if (event.select) {
                    selectedVideoIds + groupVideos.map { it.data.video.id }.toSet()
                } else {
                    selectedVideoIds - groupVideos.map { it.data.video.id }.toSet()
                }
            }

            is DownloadEvent.OnBatchMoveRequest -> {
                pendingBatchMove = true
            }
            // 业务事件：透传给 Route
            else -> onEvent(event)
        }
    }

    LaunchedEffect(Unit) {
        onLoadDownloaded()
    }

    HanimeScaffold(
        title = stringResource(Res.string.download),
        onBack = onBack,
        contentHorizontalPadding = 0.dp,
        floatingActionButton = {
            DownloadFabMenu(
                currentPage = uiState.currentPage,
                multiSelectMode = uiState.multiSelectMode,
                hasDownloadingItems = uiState.downloadingItems.isNotEmpty(),
                onResumeAll = { handleEvent(DownloadEvent.OnResumeAll(uiState.downloadingItems)) },
                onPauseAll = { handleEvent(DownloadEvent.OnPauseAll(uiState.downloadingItems)) },
                onToggleMultiSelect = { handleEvent(DownloadEvent.OnToggleMultiSelect) },
                onCreateGroup = { handleEvent(DownloadEvent.OnCreateGroupDialogChange(true)) },
                onImportDownloaded = { handleEvent(DownloadEvent.OnImportDownloaded) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = uiState.currentPage) {
                Tab(
                    selected = uiState.currentPage == 0,
                    onClick = { handleEvent(DownloadEvent.OnPageChange(0)) },
                    text = { Text(stringResource(Res.string.downloading)) },
                )
                Tab(
                    selected = uiState.currentPage == 1,
                    onClick = { handleEvent(DownloadEvent.OnPageChange(1)) },
                    text = { Text(stringResource(Res.string.downloaded)) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> DownloadingScreen(
                        uiState = uiState,
                        onEvent = handleEvent,
                    )

                    else -> DownloadedScreen(
                        uiState = uiState,
                        listState = downloadedLazyListState,
                        onEvent = handleEvent,
                    )
                }
            }
        }
    }

    if (pendingBatchMove) {
        val selectedVideos = downloadedNodes
            .filterIsInstance<lovehan1me.logic.model.DownloadItemNode>()
            .filter { it.data.video.id in selectedVideoIds }
            .map { it.data }
        if (selectedVideos.isNotEmpty()) {
            MoveGroupDialog(
                video = selectedVideos.first(),
                groups = displayGroups,
                onDismiss = { pendingBatchMove = false },
                onConfirm = { _, groupId ->
                    pendingBatchMoveConfirm = Pair(selectedVideos, groupId)
                    pendingBatchMove = false
                },
            )
        } else {
            pendingBatchMove = false
        }
    }

    pendingBatchMoveConfirm?.let { (videos, groupId) ->
        val groupName = displayGroups.find { it.id == groupId }?.name ?: "ID:$groupId"
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.move_group),
            message = stringResource(Res.string.confirm_move_videos, videos.size, groupName),
            confirmText = stringResource(Res.string.confirm),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                handleEvent(DownloadEvent.OnBatchMoveGroup(videos, groupId))
                pendingBatchMoveConfirm = null
                multiSelectMode = false
                selectedVideoIds = emptySet()
            },
            onDismiss = { pendingBatchMoveConfirm = null },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadFabMenu(
    currentPage: Int,
    multiSelectMode: Boolean,
    hasDownloadingItems: Boolean,
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onCreateGroup: () -> Unit,
    onImportDownloaded: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hapticFeedback = rememberHapticFeedback()
    val visible = !multiSelectMode && (currentPage != 0 || hasDownloadingItems)

    LaunchedEffect(currentPage, multiSelectMode, hasDownloadingItems) {
        expanded = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {

        FloatingActionButtonMenu(
            expanded = expanded,
            button = {
                if (expanded) {
                    FilledIconButton(
                        onClick = {
                            hapticFeedback()
                            expanded = !expanded
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.close),
                        )
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            hapticFeedback()
                            expanded = !expanded
                        },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_menu),
                            contentDescription = stringResource(Res.string.download),
                        )
                    }
                }

            },
        ) {
            if (currentPage == 0) {
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(Res.string.start_all)) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_play_arrow),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        hapticFeedback()
                        onResumeAll()
                        expanded = false
                    },
                )
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(Res.string.pause_all)) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_pause),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        hapticFeedback()
                        onPauseAll()
                        expanded = false
                    },
                )
            } else {
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(Res.string.edit)) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_format_list_bulleted),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        hapticFeedback()
                        onToggleMultiSelect()
                        expanded = false
                    },
                )
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(Res.string.create_new_group)) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_add),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        hapticFeedback()
                        onCreateGroup()
                        expanded = false
                    },
                )
                FloatingActionButtonMenuItem(
                    text = { Text(stringResource(Res.string.read_download_dir_title)) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_download),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        hapticFeedback()
                        onImportDownloaded()
                        expanded = false
                    },
                )
            }
        }

    }
}

private fun stringBooleanMapSaver(): Saver<Map<String, Boolean>, ArrayList<String>> {
    return Saver(
        save = { state -> ArrayList(state.map { (key, value) -> "$key=$value" }) },
        restore = { saved ->
            saved.mapNotNull { item ->
                val separatorIndex = item.lastIndexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                val key = item.substring(0, separatorIndex)
                val value = item.substring(separatorIndex + 1).toBooleanStrictOrNull()
                    ?: return@mapNotNull null
                key to value
            }.toMap()
        },
    )
}
