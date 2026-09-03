package io.github.daisukikaffuchino.han1meviewer.ui.viewmodel

import io.github.daisukikaffuchino.utils.LogUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import io.github.daisukikaffuchino.han1meviewer.EMPTY_STRING
import io.github.daisukikaffuchino.han1meviewer.HanimeResolution
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.add_success
import io.github.daisukikaffuchino.han1meviewer.delete_success
import io.github.daisukikaffuchino.han1meviewer.interval_must_greater_than_d
import io.github.daisukikaffuchino.han1meviewer.logic.DatabaseRepo
import io.github.daisukikaffuchino.han1meviewer.logic.LocalListRepository
import io.github.daisukikaffuchino.han1meviewer.logic.NetworkRepo
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.ioDispatcher
import io.github.daisukikaffuchino.han1meviewer.modify_success
import io.github.daisukikaffuchino.han1meviewer.logic.entity.HKeyframeEntity
import io.github.daisukikaffuchino.han1meviewer.logic.entity.WatchHistoryEntity
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.HanimeDownloadEntity
import io.github.daisukikaffuchino.han1meviewer.logic.model.HanimeInfo
import io.github.daisukikaffuchino.han1meviewer.logic.model.HanimeVideo
import io.github.daisukikaffuchino.han1meviewer.logic.platform.VideoCacheStore
import io.github.daisukikaffuchino.han1meviewer.logic.platform.videoCacheStore
import io.github.daisukikaffuchino.han1meviewer.logic.state.VideoLoadingState
import io.github.daisukikaffuchino.han1meviewer.logic.state.WebsiteState
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.CsrfTokenProvider.csrfToken
import io.github.daisukikaffuchino.han1meviewer.util.TagLocalizer
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.jetbrains.compose.resources.getString

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/17 017 19:01
 */
class VideoViewModel(
    // P6c：P6b-F 工厂（android=:app provider 注册；desktop/ios no-op）
    private val cacheStore: VideoCacheStore = videoCacheStore(),
) : ViewModel() {

    data class IntroScrollState(
        val firstVisibleItemIndex: Int = 0,
        val firstVisibleItemScrollOffset: Int = 0,
    )

    data class VideoHostUiState(
        val selectedTabIndex: Int = 0,
        val commentBadgeCount: Int = 0,
        val isScrollDisabled: Boolean = false,
        val isInPipMode: Boolean = false,
        val playerHeightDp: Dp? = 250.dp,
    )

    private data class VideoIntroUiState(
        val playlistFirstVisibleIndex: Int? = null,
        val cachedVideo: HanimeVideo? = null,
        val introRestored: Boolean = false,
        val scrollState: IntroScrollState = IntroScrollState(),
        val selectedTabIndex: Int = 0,
    )

    companion object {
        /**
         * 最小的 HKeyframe 保存間隔，暫定 5s
         */
        const val MIN_H_KEYFRAME_SAVE_INTERVAL = 5_000 // ms
    }
    private val videoIntroUiStateMap = mutableMapOf<String, VideoIntroUiState>()
    private val _videoCodeFlow = MutableStateFlow(EMPTY_STRING)
    var videoCode: String = EMPTY_STRING
        set(value) {
            field = value
            _videoCodeFlow.value = value
        }

    var fromDownload = false

    // 平板横屏模式下，左栏不显示相关视频（右栏已显示）
    var hideRelatedInIntro by mutableStateOf(false)
    var hKeyframes: HKeyframeEntity? = null
    // P4b（E 专项）：LiveData -> StateFlow（无 UI observe 消费点，转换安全，便于 P6 下沉 commonMain）
    private val _videoList = MutableStateFlow<List<HanimeInfo>>(emptyList())
    val videoList: StateFlow<List<HanimeInfo>> = _videoList.asStateFlow()
    private val _hanimeVideoStateFlow =
        MutableStateFlow<VideoLoadingState<HanimeVideo>>(VideoLoadingState.Loading)
    val hanimeVideoStateFlow = _hanimeVideoStateFlow.asStateFlow()

    private val _hanimeVideoFlow = MutableStateFlow<HanimeVideo?>(null)
    val hanimeVideoFlow = _hanimeVideoFlow.asStateFlow()

    /**
     * 详情页展示用视频流：未登录时把本地喜欢/清单状态合成到视频上，
     * 登录时与 [hanimeVideoFlow] 保持一致。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val displayVideoFlow: StateFlow<HanimeVideo?> = combine(
        _hanimeVideoFlow,
        _videoCodeFlow,
        SettingsRepository.loginStateFlow,
    ) { video, code, isLoggedIn -> Triple(video, code, isLoggedIn) }
        .flatMapLatest { (video, code, isLoggedIn) ->
            if (video == null || isLoggedIn || code.isBlank()) {
                flowOf(video)
            } else {
                combine(
                    LocalListRepository.observeIsFavorite(code),
                    LocalListRepository.observeIsWatchLater(code),
                    LocalListRepository.observeListCodes(code),
                    LocalListRepository.observePlaylists(),
                ) { isFavorite, isWatchLater, listCodes, playlists ->
                    video.copy(
                        isFav = isFavorite,
                        myList = HanimeVideo.MyList(
                            isWatchLater = isWatchLater,
                            myListInfo = buildList {
                                add(
                                    HanimeVideo.MyList.MyListInfo(
                                        code = LocalListRepository.WATCH_LATER_CODE,
                                        // P6c：commonMain 无同步资源读；由 UI 对 WATCH_LATER_CODE 条目特判显示
                                        title = "", 
                                        isSelected = isWatchLater,
                                    )
                                )
                                playlists.forEach { playlist ->
                                    add(
                                        HanimeVideo.MyList.MyListInfo(
                                            code = playlist.listCode,
                                            title = playlist.title,
                                            isSelected = playlist.listCode in listCodes,
                                        )
                                    )
                                }
                            },
                        ),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _videoHostUiStateFlow = MutableStateFlow(VideoHostUiState())
    val videoHostUiStateFlow = _videoHostUiStateFlow.asStateFlow()

    fun setVideoList(list: List<HanimeInfo>) {
        _videoList.value = list
    }

    fun getPlaylistFirstVisibleIndex(videoCode: String): Int? {
        return videoIntroUiStateMap[videoCode]?.playlistFirstVisibleIndex
    }

    fun setPlaylistFirstVisibleIndex(videoCode: String, index: Int) {
        updateVideoIntroUiState(videoCode) { copy(playlistFirstVisibleIndex = index) }
    }

    fun setVideoIntroCachedData(videoCode: String, video: HanimeVideo?) {
        updateVideoIntroUiState(videoCode) {
            copy(
                cachedVideo = video,
                introRestored = video != null,
            )
        }
    }

    fun clearVideoIntroRestoredFlag(videoCode: String) {
        updateVideoIntroUiState(videoCode) { copy(introRestored = false) }
    }

    fun getIntroScrollState(videoCode: String): IntroScrollState {
        return videoIntroUiStateMap[videoCode]?.scrollState ?: IntroScrollState()
    }

    fun getSelectedTabIndex(videoCode: String): Int {
        return _videoHostUiStateFlow.value.selectedTabIndex
    }

    fun setSelectedTabIndex(videoCode: String, selectedTabIndex: Int) {
        _videoHostUiStateFlow.update { it.copy(selectedTabIndex = selectedTabIndex) }
        updateVideoIntroUiState(videoCode) { copy(selectedTabIndex = selectedTabIndex) }
    }

    fun setCommentBadgeCount(commentBadgeCount: Int) {
        _videoHostUiStateFlow.update { it.copy(commentBadgeCount = commentBadgeCount) }
    }

    fun setScrollDisabled(isScrollDisabled: Boolean) {
        _videoHostUiStateFlow.update { it.copy(isScrollDisabled = isScrollDisabled) }
    }

    fun setPipMode(isInPipMode: Boolean) {
        _videoHostUiStateFlow.update { it.copy(isInPipMode = isInPipMode) }
    }

    fun setPlayerHeightDp(playerHeightDp: Dp?) {
        _videoHostUiStateFlow.update { it.copy(playerHeightDp = playerHeightDp) }
    }

    fun setIntroScrollState(
        videoCode: String,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        updateVideoIntroUiState(videoCode) {
            copy(
                scrollState = IntroScrollState(
                    firstVisibleItemIndex = firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
                )
            )
        }
    }

    private inline fun updateVideoIntroUiState(
        videoCode: String,
        transform: VideoIntroUiState.() -> VideoIntroUiState,
    ) {
        val current = videoIntroUiStateMap[videoCode] ?: VideoIntroUiState()
        videoIntroUiStateMap[videoCode] = current.transform()
    }

    fun resolveTagSearchKey(tag: String): String = TagLocalizer.resolveSearchKey(tag)

    private fun HanimeVideo.withLocalizedLabels(): HanimeVideo {
        val localArtist = artist
        return copy(
            tags = TagLocalizer.localizeTags(tags),
            artist = localArtist?.copy(genre = TagLocalizer.localizeTag(localArtist.genre)),
        )
    }

    fun buildLocalPlayInfo(localPath: String? = null): HanimeVideo {
        val resolution = HanimeResolution()
        resolution.parseResolution(
            HanimeResolution.RES_1080P,
            resLink = localPath?:"",
            type = "video/mp4"
        )
        return HanimeVideo(
            title = "",
            coverUrl = "",
            // P4b（E 专项）：androidx toUri 不可下沉 commonMain，改等价纯 Kotlin（剥 query 后取末段）
            chineseTitle = localPath?.substringBefore('?')?.substringAfterLast('/'),
            introduction = "",
            uploadTime = null,
            views = "0",
            videoUrls = resolution.toResolutionLinkMap(),
            tags = emptyList(),
        )
    }
    fun getHanimeVideo(videoCode: String,localUri: String? = null) {
        if (videoCode == "-1"){
            val localPlayInfo = buildLocalPlayInfo(localUri)
            _hanimeVideoStateFlow.value = VideoLoadingState.Success(localPlayInfo)
            _hanimeVideoFlow.value = localPlayInfo
            return
        }
        if (videoIntroUiStateMap[videoCode]?.introRestored == true) return
        viewModelScope.launch {
            val flow = if (fromDownload) {
                cacheStore.load(videoCode).map { hv ->
                    if (hv == null) {
                        VideoLoadingState.NoContent
                    } else {
                        VideoLoadingState.Success(hv)
                    }
                }
            } else {
                NetworkRepo.getHanimeVideo(videoCode)
            }
            flow.collect { state ->
                val emitState = when {
                    localUri != null && state is VideoLoadingState.Success -> {
                        val resolution = HanimeResolution()
                        resolution.parseResolution(
                            HanimeResolution.RES_1080P,
                            resLink = localUri,
                            type = "video/mp4"
                        )
                        VideoLoadingState.Success(
                            state.info.copy(videoUrls = resolution.toResolutionLinkMap())
                                .withLocalizedLabels()
                        )
                    }

                    state is VideoLoadingState.Success -> {
                        VideoLoadingState.Success(state.info.withLocalizedLabels())
                    }

                    else -> state
                }
                _hanimeVideoStateFlow.value = emitState
                if (emitState is VideoLoadingState.Success) {
                    _hanimeVideoFlow.update { emitState.info }
                    csrfToken = emitState.info.csrfToken
                }
            }
        }
    }

    fun restoreFromCacheIfExists(code: String): Boolean {
        val cached = videoIntroUiStateMap[code]?.cachedVideo?.withLocalizedLabels() ?: return false
        updateVideoIntroUiState(code) { copy(introRestored = true) }
        _hanimeVideoFlow.value = cached
        _hanimeVideoStateFlow.value = VideoLoadingState.Success(cached)
        return true
    }



    private val _addToFavVideoFlow = MutableSharedFlow<WebsiteState<Boolean>>()
    val addToFavVideoFlow = _addToFavVideoFlow.asSharedFlow()

    private val _loadDownloadedFlow = MutableSharedFlow<HanimeDownloadEntity?>()
    val loadDownloadedFlow = _loadDownloadedFlow.asSharedFlow()

    fun addToFavVideo(
        videoCode: String,
        currentUserId: String?,
    ) = modifyFavVideoInternal(videoCode, likeStatus = false, currentUserId)

    fun removeFromFavVideo(
        videoCode: String,
        currentUserId: String?,
    ) = modifyFavVideoInternal(videoCode, likeStatus = true, currentUserId)

    private fun modifyFavVideoInternal(
        videoCode: String,
        likeStatus: Boolean,
        currentUserId: String?,
    ) {
        viewModelScope.launch {
            NetworkRepo.addToMyFavVideo(
                videoCode, likeStatus, currentUserId, csrfToken
            ).collect { state ->
                _addToFavVideoFlow.emit(state)
                if (likeStatus) {
                    _hanimeVideoFlow.update { it?.rateVideo(isPositive = true) }
                } else {
                    _hanimeVideoFlow.update { it?.rateVideo(isPositive = true) }
                }
            }
        }
    }

    fun rateVideo(video: HanimeVideo, isPositive: Boolean) {
        viewModelScope.launch {
            NetworkRepo.rateVideo(
                videoCode = videoCode,
                isPositive = isPositive,
                likeStatus = video.isFav,
                unlikeStatus = video.isUnlike,
                likesCount = video.favTimes ?: 0,
                unlikesCount = video.unlikesCount ?: 0,
                currentUserId = video.currentUserId,
                token = csrfToken,
            ).collect { state ->
                _addToFavVideoFlow.emit(state)
                if (state is WebsiteState.Success) {
                    _hanimeVideoFlow.update { it?.rateVideo(isPositive) }
                }
            }
        }
    }

    private val _modifyMyListFlow = MutableSharedFlow<WebsiteState<Int>>()
    val modifyMyListFlow = _modifyMyListFlow.asSharedFlow()

    fun modifyMyList(
        listCode: String,
        videoCode: String,
        isChecked: Boolean,
        position: Int,
    ) {
        viewModelScope.launch {
            NetworkRepo.addToMyList(listCode, videoCode, isChecked, position, csrfToken).collect {
                _modifyMyListFlow.emit(it)
                _hanimeVideoFlow.update { prev ->
                    val myList = prev?.myList?.myListInfo.orEmpty().toMutableList()
                    myList[position] = myList[position].copy(isSelected = isChecked)
                    prev?.copy(myList = prev.myList?.copy(myListInfo = myList))
                }
            }
        }
    }

    private val _localFavoriteActionFlow = MutableSharedFlow<WebsiteState<Boolean>>()
    val localFavoriteActionFlow = _localFavoriteActionFlow.asSharedFlow()

    private val _localMyListActionFlow = MutableSharedFlow<WebsiteState<Boolean>>()
    val localMyListActionFlow = _localMyListActionFlow.asSharedFlow()

    fun toggleLocalFavorite() {
        val video = _hanimeVideoFlow.value ?: return
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val isFavorite = LocalListRepository.isFavorite(videoCode)
                if (isFavorite) {
                    LocalListRepository.removeItem(LocalListRepository.FAVORITE_CODE, videoCode)
                } else {
                    LocalListRepository.setFavorite(videoCode, video, add = true)
                }
                !isFavorite
            }.onSuccess { isFavorite ->
                _localFavoriteActionFlow.emit(WebsiteState.Success(isFavorite))
            }.onFailure {
                _localFavoriteActionFlow.emit(WebsiteState.Error(it))
            }
        }
    }

    fun updateLocalMyListSelection(
        myList: HanimeVideo.MyList,
        selectedStates: List<Boolean>,
    ) {
        val video = _hanimeVideoFlow.value ?: return
        val changes = myList.myListInfo.mapIndexedNotNull { index, info ->
            val newChecked = selectedStates.getOrNull(index) ?: return@mapIndexedNotNull null
            if (info.isSelected == newChecked) null else info to newChecked
        }
        if (changes.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                changes.forEach { (info, newChecked) ->
                    if (info.code == LocalListRepository.WATCH_LATER_CODE) {
                        LocalListRepository.setWatchLater(videoCode, video, newChecked)
                    } else {
                        LocalListRepository.setPlaylistContains(
                            info.code,
                            videoCode,
                            video,
                            newChecked,
                        )
                    }
                }
            }.onSuccess {
                _localMyListActionFlow.emit(WebsiteState.Success(true))
            }.onFailure {
                _localMyListActionFlow.emit(WebsiteState.Error(it))
            }
        }
    }

    fun insertWatchHistory(history: WatchHistoryEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.WatchHistory.insert(history)
            LogUtil.d("insert_watch_hty", "$history DONE!")
        }
    }

    fun insertWatchHistoryWithCover(history: WatchHistoryEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.WatchHistory.insert(history)
        }
    }

    fun findDownloadedHanime(videoCode: String) {
        viewModelScope.launch(ioDispatcher) {
            val info = DatabaseRepo.HanimeDownload.find(videoCode)
            _loadDownloadedFlow.emit(info)
        }
    }

    // true代表已关注成功，false代表取消关注成功
    private val _subscribeArtistFlow = MutableSharedFlow<WebsiteState<Boolean>>()
    val subscribeArtistFlow = _subscribeArtistFlow.asSharedFlow()

    fun subscribeArtist(
        userId: String,
        artistId: String,
    ) {
        viewModelScope.launch {
            NetworkRepo.subscribeArtist(csrfToken, userId, artistId, true).collect { state ->
                _subscribeArtistFlow.emit(state)
                if (state is WebsiteState.Success) {
                    _hanimeVideoFlow.update {
                        it?.copy(artist = it.artist?.let { artist -> artist.copy(post = artist.post?.copy(isSubscribed = true)) })
                    }
                }
            }
        }
    }

    fun unsubscribeArtist(
        userId: String,
        artistId: String,
    ) {
        viewModelScope.launch {
            NetworkRepo.subscribeArtist(csrfToken, userId, artistId, false).collect { state ->
                _subscribeArtistFlow.emit(state)
                if (state is WebsiteState.Success) {
                    _hanimeVideoFlow.update {
                        it?.copy(artist = it.artist?.let { artist -> artist.copy(post = artist.post?.copy(isSubscribed = false)) })
                    }
                }
            }
        }
    }

    // boolean: 成功 or 失敗，String: 提示信息（P6c：原 messageResId=R.int，改携带已本地化文本）
    data class HKeyframeResult(
        val succeeded: Boolean,
        val message: String,
    )

    private val _modifyHKeyframeFlow = MutableSharedFlow<HKeyframeResult>()
    val modifyHKeyframeFlow = _modifyHKeyframeFlow.asSharedFlow()
    private val _forceRefresh = MutableSharedFlow<Unit>(replay = 1)
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeKeyframe(videoCode: String): Flow<HKeyframeEntity?> {
        return _forceRefresh
            .onStart { emit(Unit) }
            .flatMapLatest {
                DatabaseRepo.HKeyframe.observe(videoCode).flowOn(ioDispatcher)
            }
    }
    fun appendHKeyframe(videoCode: String, title: String, hKeyframe: HKeyframeEntity.Keyframe) {
        viewModelScope.launch(ioDispatcher) {
            run {
                this@VideoViewModel.hKeyframes?.keyframes?.forEach { keyframeInDb ->
                    if (abs(keyframeInDb.position - hKeyframe.position) < MIN_H_KEYFRAME_SAVE_INTERVAL) {
                        LogUtil.d("HKeyframe", "append_hkeyframe:time conflict: $keyframeInDb")
                        _modifyHKeyframeFlow.emit(
                            HKeyframeResult(
                                succeeded = false,
                                message = getString(
                                    Res.string.interval_must_greater_than_d,
                                    MIN_H_KEYFRAME_SAVE_INTERVAL / 1_000L,
                                ),
                            )
                        )
                        return@run
                    }
                }
                DatabaseRepo.HKeyframe.appendKeyframe(videoCode, title, hKeyframe)
                LogUtil.d("HKeyframe", "append_hkeyframe:$hKeyframe DONE!")
                _modifyHKeyframeFlow.emit(HKeyframeResult(true, getString(Res.string.add_success)))
                _forceRefresh.emit(Unit)
            }
        }
    }

    fun modifyHKeyframe(
        videoCode: String,
        oldKeyframe: HKeyframeEntity.Keyframe,
        newKeyframe: HKeyframeEntity.Keyframe,
    ) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.modifyKeyframe(videoCode, oldKeyframe, newKeyframe)
            _modifyHKeyframeFlow.emit(HKeyframeResult(true, getString(Res.string.modify_success)))
            _forceRefresh.emit(Unit)
        }
    }

    fun removeHKeyframe(videoCode: String, hKeyframe: HKeyframeEntity.Keyframe) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.removeKeyframe(videoCode, hKeyframe)
            _modifyHKeyframeFlow.emit(HKeyframeResult(true, getString(Res.string.delete_success)))
            _forceRefresh.emit(Unit)
        }
    }
}
