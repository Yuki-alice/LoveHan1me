package lovehan1me.feature.home.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.core.util.AppToast
import lovehan1me.data.NetworkRepo
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.CsrfTokenProvider.csrfToken
import lovehan1me.site.hanime1.SiteAuthorHeader
import lovehan1me.site.hanime1.SiteAuthorPlaylists
import lovehan1me.site.hanime1.SitePlaylistSummary
import lovehan1me.Res
import lovehan1me.login_first
import org.jetbrains.compose.resources.getString

/**
 * G2-1b-1：作者页 VM（`/user/{id}` 独立拉取随 G2-1b-2；v1 作品列表走站内搜索）。
 *
 * 订阅走与详情页同一条链路（`NetworkRepo.subscribeArtist` + `CsrfTokenProvider`），
 * 状态本地持有，详情页的订阅态不受影响（切站/重进以服务端为准）。
 */
class ArtistViewModel : ViewModel() {

    private val _header = MutableStateFlow<SiteAuthorHeader?>(null)
    val header = _header.asStateFlow()

    /** 首页"影片"区近期作品（作者页自带 12 条；全量走 `works`）。 */
    private val _recent = MutableStateFlow<List<HanimeInfo>>(emptyList())
    val recent = _recent.asStateFlow()

    /** 首页"播放清单"区 preview（完整一览走 `playlists`）。 */
    private val _homePlaylists = MutableStateFlow<List<SitePlaylistSummary>>(emptyList())
    val homePlaylists = _homePlaylists.asStateFlow()

    private val _works = MutableStateFlow<List<HanimeInfo>>(emptyList())
    val works = _works.asStateFlow()

    private val _worksLoading = MutableStateFlow(false)
    val worksLoading = _worksLoading.asStateFlow()

    private val _worksError = MutableStateFlow<Throwable?>(null)
    val worksError = _worksError.asStateFlow()

    private var page = 1
    private var endReached = false
    private var loading = false
    private var loadedUserId: String? = null

    /**
     * G2-1b-2：信息头 + 作品全量（`/user/{id}` + `/uploaded?page=`）。
     * v1 的搜索兜底已移除——作者页自有列表为准，不再依赖搜索索引时效。
     */
    fun load(userId: String) {
        if (loading) return
        loading = true
        loadedUserId = userId
        page = 1
        endReached = false
        _works.value = emptyList()
        _worksLoading.value = true
        _worksError.value = null
        viewModelScope.launch {
            NetworkRepo.getArtistPage(userId).collect { state ->
                if (state is WebsiteState.Success) {
                    _header.value = state.info.header
                    _recent.value = state.info.recentVideos
                    _homePlaylists.value = state.info.homePlaylists
                } else if (state is WebsiteState.Error) {
                    _worksError.value = state.throwable
                }
            }
            loadMoreInternal(userId)
            loadPlaylists(userId)
        }
    }

    fun loadMore() {
        val userId = loadedUserId ?: return
        if (loading || endReached) return
        loading = true
        _worksLoading.value = true
        viewModelScope.launch { loadMoreInternal(userId) }
    }

    private suspend fun loadMoreInternal(userId: String) {
        NetworkRepo.getArtistUploaded(userId, page).collect { state ->
            when (state) {
                is PageLoadingState.Success -> {
                    if (state.info.isEmpty()) endReached = true
                    else {
                        _works.update { prev -> (prev + state.info).distinctBy(HanimeInfo::videoCode) }
                        page += 1
                    }
                    loading = false
                    _worksLoading.value = false
                }
                is PageLoadingState.Loading -> Unit
                is PageLoadingState.NoMoreData -> {
                    endReached = true
                    loading = false
                    _worksLoading.value = false
                }
                is PageLoadingState.Error -> {
                    _worksError.value = state.throwable
                    loading = false
                    _worksLoading.value = false
                }
            }
        }
    }

    fun retry() {
        val userId = loadedUserId ?: return
        if (page <= 1 && _works.value.isEmpty()) load(userId) else loadMore()
    }

    // ---------- 系列清单（`/user/{id}/playlists?sort=`，单页全量） ----------

    private val _playlists = MutableStateFlow<SiteAuthorPlaylists?>(null)
    val playlists = _playlists.asStateFlow()

    private val _playlistsLoading = MutableStateFlow(false)
    val playlistsLoading = _playlistsLoading.asStateFlow()

    fun loadPlaylists(userId: String, sort: String? = null) {
        loadedUserId = userId
        _playlistsLoading.value = true
        viewModelScope.launch {
            NetworkRepo.getAuthorPlaylists(userId, sort).collect { state ->
                when (state) {
                    is PageLoadingState.Success -> _playlists.value = state.info
                    is PageLoadingState.Error -> Unit
                    else -> Unit
                }
                _playlistsLoading.value = false
            }
        }
    }

    private val _subscribed = MutableStateFlow<Boolean?>(null)
    val subscribed = _subscribed.asStateFlow()

    fun initSubscribed(value: Boolean) {
        if (_subscribed.value == null) _subscribed.value = value
    }

    fun toggleSubscribe(postUserId: String?, postArtistId: String?) {
        if (postUserId == null || postArtistId == null) return
        if (!SettingsRepository.isAlreadyLogin) {
            viewModelScope.launch { AppToast.warning(getString(Res.string.login_first)) }
            return
        }
        val target = !(_subscribed.value ?: false)
        viewModelScope.launch {
            NetworkRepo.subscribeArtist(csrfToken, postUserId, postArtistId, target).collect { state ->
                if (state is WebsiteState.Success) _subscribed.value = target
            }
        }
    }
}
