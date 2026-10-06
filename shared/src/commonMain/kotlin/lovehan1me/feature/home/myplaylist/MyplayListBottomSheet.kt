package lovehan1me.feature.home.myplaylist

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
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.delete_failed
import lovehan1me.delete_playlist
import lovehan1me.delete_success
import lovehan1me.delete_the_playlist
import lovehan1me.modify_failed
import lovehan1me.modify_success
import lovehan1me.modify_title_or_desc
import lovehan1me.unknown_error
import lovehan1me.load_failed_retry
import lovehan1me.load_complete_with_pages
import lovehan1me.empty_content
import lovehan1me.edit
import lovehan1me.delete
import lovehan1me.h_chan_load_failed
import lovehan1me.sure_to_delete
import lovehan1me.sure_to_delete_s
import lovehan1me.h_chan_loading
import lovehan1me.ic_delete
import lovehan1me.ic_edit_square
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.component.appbar.HanimeTopAppBar
import lovehan1me.ui.component.content.EmptyContent
import lovehan1me.ui.component.lazy.LazyVerticalGrid
import lovehan1me.ui.component.RetryableImage
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.adaptive.PageMetrics
import lovehan1me.feature.library.PlaylistController
import lovehan1me.core.util.AppToast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged

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
    val playlistState by vm.playlistStateFlow.collectAsStateWithLifecycle()
    val playlist by vm.playlistFlow.collectAsStateWithLifecycle()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    // 原来这行是 `if (listCode.isNotEmpty()) vm.setListInfo(...)`，**写在组合体里**。
    // 组合阶段写 ViewModel（也就是写 State）属于"反向写"：每次重组都触发一次，
    // 且写在快照里会让本次重组作废重来。改挂到 LaunchedEffect，只在入参真变时执行一次。
    LaunchedEffect(listCode, playListTitle) {
        if (listCode.isNotEmpty()) {
            vm.setListInfo(listCode, playListTitle)
        }
    }

    val listInfo by vm.currentListInfo.collectAsStateWithLifecycle()
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
            AppToast.error(getString(Res.string.unknown_error))
        }
    }

    LaunchedEffect(Unit) { sheetState.show() }

    LaunchedEffect(gridState, currentCode) {
        // 滚动位置每移动一像素就会发射一次；不去重的话等于"每帧写一次 ViewModel"，
        // 写回去又触发本页重组 → 滚动掉帧。distinctUntilChanged 把写入压到真正变化时。
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                vm.updatePlaylistSheetScrollState(currentCode, index, offset)
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        // 浮层自带 M3 sheet 底色，不跟页面底色走 —— 页面底是 surfaceContainerLowest（纯白），
        // 直接套用会让 sheet 与身后内容同色，浮不起来。
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                is WebsiteState.Error -> AppToast.error(getString(Res.string.modify_failed))
                WebsiteState.Loading -> {}
                is WebsiteState.Success -> {
                    if (result.info.isDeleted) {
                        sheetState.hide()
                        onDismiss()
                        AppToast.success(getString(Res.string.delete_success))
                        vm.loadMyPlayList()
                        return@collect
                    }
                    AppToast.success(getString(Res.string.modify_success))
                    vm.getPlaylistItems(1, currentCode, true)
                    vm.loadMyPlayList()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.deleteFromPlaylistFlow.collect { result ->
            when (result) {
                is WebsiteState.Error -> AppToast.error(getString(Res.string.delete_failed))
                is WebsiteState.Loading -> {}
                is WebsiteState.Success -> {
                    AppToast.success(getString(Res.string.delete_success))
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
    val desc by playlistDesc.collectAsStateWithLifecycle()

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
            // 弹窗宽度 ≠ 窗口内容宽度，用容器实宽分档
            val cardMinWidth = PageMetrics.videoCardMinWidthFor(maxWidth, simplified = false)
            val columns = maxOf(2, (maxWidth / cardMinWidth).toInt())

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = cardMinWidth),
                contentPadding = PaddingValues(HanimeDefaults.Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium)
            ) {
                itemsIndexed(
                    playlist,
                    // 不传 key 时 Lazy 用**下标**当身份：删除中间一项后，后面的卡片
                    // 全部"换人"，组合状态与入场动画全乱。videoCode 是服务端稳定 ID。
                    key = { _, item -> item.videoCode },
                ) { index, item ->
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
                snapshotFlow {
                    val layoutInfo = gridState.layoutInfo
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisibleItem >= layoutInfo.totalItemsCount - 3
                }
                    // layoutInfo 每滚动一像素都是新对象 —— 直接 collect 它等于每帧都跑一遍
                    // 分页判断，且触底那一瞬间会连着发好几个 true 打重复请求。
                    // 先映射成"是否接近末尾"再去重，只在翻转时才往下走。
                    .distinctUntilChanged()
                    .collect { nearEnd ->
                        if (nearEnd &&
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
