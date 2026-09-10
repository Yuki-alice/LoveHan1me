package me.lovehan1me.ui.screen.home.myplaylist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import me.lovehan1me.ui.component.FilledIconButton
import me.lovehan1me.ui.component.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.lovehan1me.Res
import me.lovehan1me.cancel
import me.lovehan1me.confirm
import me.lovehan1me.delete_failed
import me.lovehan1me.delete_playlist
import me.lovehan1me.delete_success
import me.lovehan1me.delete_the_playlist
import me.lovehan1me.modify_failed
import me.lovehan1me.modify_success
import me.lovehan1me.modify_title_or_desc
import me.lovehan1me.unknown_error
import me.lovehan1me.load_failed_retry
import me.lovehan1me.load_complete_with_pages
import me.lovehan1me.empty_content
import me.lovehan1me.edit
import me.lovehan1me.delete
import me.lovehan1me.h_chan_load_failed
import me.lovehan1me.sure_to_delete
import me.lovehan1me.sure_to_delete_s
import me.lovehan1me.h_chan_loading
import me.lovehan1me.ic_delete
import me.lovehan1me.ic_edit_square
import me.lovehan1me.logic.model.HanimeInfo
import me.lovehan1me.logic.state.PageLoadingState
import me.lovehan1me.logic.state.WebsiteState
import me.lovehan1me.ui.component.ConfirmDialog
import me.lovehan1me.ui.component.VideoCardItem
import me.lovehan1me.ui.component.appbar.HanimeTopAppBar
import me.lovehan1me.ui.component.content.EmptyContent
import me.lovehan1me.ui.component.lazy.LazyVerticalGrid
import me.lovehan1me.ui.screen.RetryableImage
import me.lovehan1me.ui.theme.HanimeDefaults
import me.lovehan1me.ui.theme.SpacingNormal
import me.lovehan1me.ui.theme.VideoNormalCardMinWidth
import me.lovehan1me.ui.viewmodel.PlaylistController
import me.lovehan1me.utils.SonnerToast

/**
 * 播放列表详情底部弹窗。
 *
 * @param listCode 播放列表代码
 * @param onDismiss 关闭回调
 * @param playListTitle 播放列表标题
 * @param onClickItem 点击视频项回调
 * @param onLongClickItem 长按视频项回调
 * @param vm 播放列表 ViewModel（弹窗内需要直接观察 ViewModel StateFlow）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    listCode: String,
    onDismiss: () -> Unit,
    playListTitle: String,
    onClickItem: (String) -> Unit,
    onLongClickItem: (String, String) -> Unit,
    vm: PlaylistController,
) {
    val playlistState by vm.playlistStateFlow.collectAsState()
    val playlist by vm.playlistFlow.collectAsState()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    if (listCode.isNotEmpty()) {
        vm.setListInfo(listCode, playListTitle)
    }

    val listInfo by vm.currentListInfo.collectAsState()
    val currentCode = listInfo?.first ?: ""
    val currentTitle = listInfo?.second ?: ""
    val savedScrollState = remember(currentCode, vm) {
        vm.getPlaylistSheetScrollState(currentCode)
    }
    val gridState = remember(currentCode) {
        LazyGridState(
            firstVisibleItemIndex = savedScrollState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = savedScrollState.firstVisibleItemScrollOffset,
        )
    }

    LaunchedEffect(currentCode) {
        if (currentCode.isNotEmpty()) {
            if (playlist.isEmpty()) {
                vm.getPlaylistItems(1, currentCode, true)
            }
        } else {
            SonnerToast.error(getString(Res.string.unknown_error))
        }
    }

    LaunchedEffect(Unit) { sheetState.show() }

    LaunchedEffect(gridState, currentCode) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                vm.updatePlaylistSheetScrollState(currentCode, index, offset)
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = HanimeDefaults.Colors.pageSurface,
    ) {
        if (playlist.isEmpty() && playlistState is PageLoadingState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (playlist.isEmpty() && playlistState is PageLoadingState.Error) {
            Box(Modifier
                .fillMaxSize()
                .height(200.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.load_failed_retry))
            }
        } else {
            AnimatedVisibility(visible = true, enter = fadeIn()) {
                PlaylistSheetContent(
                    gridState = gridState,
                    listCode = currentCode,
                    playlist = playlist,
                    playListTitle = currentTitle,
                    playlistDesc = vm.playlistDesc,
                    playlistState = playlistState,
                    onClickItem = onClickItem,
                    viewModel = vm,
                )
                if (playlist.isEmpty()) {
                    EmptyContent(stringResource(Res.string.empty_content))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.modifyPlaylistFlow.collect { result ->
            when (result) {
                is WebsiteState.Error -> SonnerToast.error(getString(Res.string.modify_failed))
                WebsiteState.Loading -> {}
                is WebsiteState.Success -> {
                    if (result.info.isDeleted) {
                        sheetState.hide()
                        onDismiss()
                        SonnerToast.success(getString(Res.string.delete_success))
                        vm.loadMyPlayList()
                        return@collect
                    }
                    SonnerToast.success(getString(Res.string.modify_success))
                    vm.getPlaylistItems(1, currentCode, true)
                    vm.loadMyPlayList()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.deleteFromPlaylistFlow.collect { result ->
            when (result) {
                is WebsiteState.Error -> SonnerToast.error(getString(Res.string.delete_failed))
                is WebsiteState.Loading -> {}
                is WebsiteState.Success -> {
                    SonnerToast.success(getString(Res.string.delete_success))
                    vm.loadMyPlayList()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheetContent(
    gridState: LazyGridState,
    listCode: String,
    playlist: List<HanimeInfo>,
    playListTitle: String,
    playlistDesc: kotlinx.coroutines.flow.StateFlow<String?>,
    playlistState: PageLoadingState<*>,
    onClickItem: (String) -> Unit,
    viewModel: PlaylistController,
) {
    var showDeletePlaylistConfirm by remember { mutableStateOf(false) }
    var showDeleteItemConfirm by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    val desc by playlistDesc.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier
            .fillMaxWidth()
            .height(240.dp)) {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (playlist.isNotEmpty()) {
                RetryableImage(
                    model = playlist.first().coverUrl,
                    contentDescription = playlist.first().title,
                    placeholder = painterResource(Res.drawable.h_chan_loading),
                    error = painterResource(Res.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            ),
                            startY = 0f, endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            HanimeTopAppBar(
                title = { Text(playListTitle, color = Color.White) },
                onBack = null,
                colors = topAppBarColors(containerColor = Color.Transparent)
            )

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier
                        .weight(1f)
                        .padding(start = 8.dp)) {
                        Text(
                            desc ?: "",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledIconButton(
                        onClick = { showDeletePlaylistConfirm = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_delete),
                            stringResource(Res.string.delete)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { showEditPlaylistDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_edit_square),
                            stringResource(Res.string.edit)
                        )
                    }
                }
            }
        }

        if (showEditPlaylistDialog) {
            PlaylistEditDialog(
                title = stringResource(Res.string.modify_title_or_desc),
                initialTitle = playListTitle,
                initialDescription = desc.orEmpty(),
                onConfirm = { title, description ->
                    viewModel.modifyPlaylist(listCode, title, description, false)
                },
                onDismiss = { showEditPlaylistDialog = false },
            )
        }

        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = maxOf(2, (maxWidth / VideoNormalCardMinWidth).toInt())

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = VideoNormalCardMinWidth),
                contentPadding = PaddingValues(SpacingNormal),
                horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                verticalArrangement = Arrangement.spacedBy(SpacingNormal)
            ) {
                itemsIndexed(playlist) { index, item ->
                    VideoCardItem(
                        videoItem = item,
                        isHorizontalCard = true,
                        showDeleteAction = true,
                        onClickVideosItem = onClickItem
                    ) { videoCode, _ ->
                        showDeleteItemConfirm = Triple(listCode, videoCode, index)
                    }
                }

                item(span = { GridItemSpan(columns) }) {
                    if (playlistState is PageLoadingState.Loading && viewModel.currentPage > 1) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (playlistState is PageLoadingState.NoMoreData && playlist.isNotEmpty()) {
                    item(span = { GridItemSpan(columns) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(Res.string.load_complete_with_pages,
                                    viewModel.currentPage - 1
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }

            LaunchedEffect(gridState, playlistState) {
                snapshotFlow { gridState.layoutInfo }.collect { layoutInfo ->
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    if (lastVisibleItem >= totalItems - 3 &&
                        playlistState !is PageLoadingState.Loading &&
                        playlistState !is PageLoadingState.NoMoreData &&
                        !viewModel.isLoadingMore
                    ) {
                        viewModel.currentPage++
                        viewModel.getPlaylistItems(viewModel.currentPage, listCode)
                    }
                }
            }

            showDeleteItemConfirm?.let { (code, videoCode, index) ->
                val item = playlist.find { it.videoCode == videoCode }
                ConfirmDialog(
                    visible = true,
                    title = stringResource(Res.string.delete_playlist),
                    message = stringResource(Res.string.sure_to_delete_s, item?.title ?: ""),
                    confirmText = stringResource(Res.string.confirm),
                    dismissText = stringResource(Res.string.cancel),
                    onConfirm = {
                        viewModel.deleteFromPlaylist(
                            code,
                            videoCode,
                            index
                        ); showDeleteItemConfirm = null
                    },
                    onDismiss = { showDeleteItemConfirm = null },
                )
            }

            ConfirmDialog(
                visible = showDeletePlaylistConfirm,
                title = stringResource(Res.string.delete_the_playlist),
                message = stringResource(Res.string.sure_to_delete),
                confirmText = stringResource(Res.string.confirm),
                dismissText = stringResource(Res.string.cancel),
                onConfirm = {
                    viewModel.modifyPlaylist(
                        listCode,
                        playListTitle,
                        desc.orEmpty(),
                        true
                    ); showDeletePlaylistConfirm = false
                },
                onDismiss = { showDeletePlaylistConfirm = false },
            )
        }
    }
}
