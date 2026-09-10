package lovehan1me.ui.viewmodel

import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.ModifiedPlaylistArgs
import lovehan1me.core.domain.model.MyListItems
import lovehan1me.core.domain.model.Playlists
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.ui.screen.home.myplaylist.PlaylistUiState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放清单页在线/本地共用接口，使 [PlaylistScreen] 与 [PlaylistBottomSheet]
 * 可以按登录状态复用同一套 UI。
 */
interface PlaylistController {
    val myPlaylistsFlow: StateFlow<WebsiteState<Playlists>>
    val mainUiState: StateFlow<PlaylistUiState>
    val refreshCompleted: SharedFlow<Unit>
    val playlistStateFlow: StateFlow<PageLoadingState<MyListItems<HanimeInfo>>>
    val playlistFlow: StateFlow<List<HanimeInfo>>
    val playlistDesc: StateFlow<String?>
    val currentListInfo: StateFlow<Pair<String, String>?>
    val modifyPlaylistFlow: SharedFlow<WebsiteState<ModifiedPlaylistArgs>>
    val deleteFromPlaylistFlow: SharedFlow<WebsiteState<Int>>
    val createPlaylistFlow: SharedFlow<WebsiteState<Unit>>

    var currentPage: Int
    var playlistPage: Int
    val isLoadingMore: Boolean

    fun loadMyPlayList(page: Int = 1, forceReload: Boolean = false)
    fun setShowSheet(value: Boolean)
    fun setListInfo(code: String, title: String)
    fun clearCurrentList()
    fun getPlaylistItems(page: Int = 1, listCode: String, refresh: Boolean = false)
    fun getPlaylistSheetScrollState(listCode: String): PlaylistSheetScrollState
    fun updatePlaylistSheetScrollState(
        listCode: String,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    )
    fun modifyPlaylist(listCode: String, title: String, desc: String, delete: Boolean)
    fun deleteFromPlaylist(listCode: String, videoCode: String, position: Int)
    fun createPlaylist(title: String, description: String)
}
