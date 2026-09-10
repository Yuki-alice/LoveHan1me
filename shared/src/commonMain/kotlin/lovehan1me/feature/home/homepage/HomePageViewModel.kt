package lovehan1me.feature.home.homepage

import lovehan1me.core.util.LogUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lovehan1me.data.SettingsRepository
import lovehan1me.core.platform.ioDispatcher
import lovehan1me.Res
import lovehan1me.login_state_expired
import org.jetbrains.compose.resources.StringResource
import lovehan1me.data.AppUpdateChecker
import lovehan1me.core.platform.performAccountLogout
import lovehan1me.data.AppUpdateState
import lovehan1me.data.DatabaseRepo
import lovehan1me.data.NetworkRepo
import lovehan1me.data.database.entity.HKeyframeEntity
import lovehan1me.data.database.entity.WatchHistoryEntity
import lovehan1me.core.domain.exception.LoginStateExpiredException
import lovehan1me.core.domain.model.Announcement
import lovehan1me.core.domain.state.PageState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.app.AppViewModel
import lovehan1me.app.navigation.main.HanimeScreen
import lovehan1me.app.navigation.main.HomeRoute
import lovehan1me.app.navigation.main.TopLevelBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class HomePageViewModel: ViewModel() {
    val mainBackStack = TopLevelBackStack<HanimeScreen>(HomeRoute)

    data class SessionExpiredMessage(
        val message: String?,
        val fallbackResId: StringResource,
    )

    private val _homePageFlow = MutableStateFlow<PageState<HomeData>>(PageState.Loading)
    val homePageFlow = _homePageFlow.asStateFlow()

    private val _sessionExpiredMessage = MutableSharedFlow<SessionExpiredMessage>()
    val sessionExpiredMessage = _sessionExpiredMessage

    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Checking)
    val appUpdateState = _appUpdateState.asStateFlow()

    private val _updateAnnouncement = MutableStateFlow<Announcement?>(null)
    val updateAnnouncement = _updateAnnouncement.asStateFlow()

    private var homePageJob: Job? = null
    private var initializationJob: Job? = null

    init {
        viewModelScope.launch {
            // 初始化默认已下载分组，防止[FOREIGN KEY constraint failed]
            DatabaseRepo.HanimeDownload.insertDefaultGroup()
        }
    }

    fun initializeHomePage() {
        if (!SettingsRepository.usageNoticeAccepted || !SettingsRepository.usageSourceVerified) return
        if (initializationJob != null || _appUpdateState.value !is AppUpdateState.Checking) return
        initializationJob = viewModelScope.launch {
            val updateResult = AppUpdateChecker.checkForUpdate()
            _updateAnnouncement.value = updateResult.announcement
            val updateInfo = updateResult.updateInfo
            _appUpdateState.value = updateInfo
                ?.let { AppUpdateState.Available(it) }
                ?: AppUpdateState.NoUpdate
            if (updateInfo?.forceUpdate != true) {
                getHomePage()
            }
        }
    }

    fun ignoreUpdate(versionCode: Int) {
        val available = _appUpdateState.value as? AppUpdateState.Available ?: return
        if (available.info.forceUpdate || available.info.versionCode != versionCode) return
        viewModelScope.launch {
            AppUpdateChecker.ignoreUpdate(versionCode)
            _appUpdateState.value = AppUpdateState.NoUpdate
        }
    }

    fun getHomePage(isRefresh: Boolean = false){
        if (!SettingsRepository.usageNoticeAccepted || !SettingsRepository.usageSourceVerified) return
        when (val updateState = _appUpdateState.value) {
            AppUpdateState.Checking -> {
                initializeHomePage()
                return
            }
            is AppUpdateState.Available -> if (updateState.info.forceUpdate) return
            AppUpdateState.NoUpdate -> Unit
        }
        homePageJob?.cancel()
        homePageJob = viewModelScope.launch {
            val current = _homePageFlow.value
            val cachedErrorInfo = if (current is PageState.Error) current.cachedInfo else null
            if (isRefresh && current is PageState.Success) {
                _homePageFlow.value = current.copy(isRefreshing = true)
            } else if (isRefresh && cachedErrorInfo != null) {
                _homePageFlow.value = PageState.Success(info = cachedErrorInfo, isRefreshing = true)
            } else if (!isRefresh && current !is PageState.Success){
                _homePageFlow.value = PageState.Loading
            }
            NetworkRepo.getHomePage().collect { networkState ->
                when (networkState){
                    is WebsiteState.Error -> {
                        if (networkState.throwable is LoginStateExpiredException) {
                            performAccountLogout()
                            _sessionExpiredMessage.emit(
                                SessionExpiredMessage(
                                    message = networkState.throwable.message,
                                    fallbackResId = Res.string.login_state_expired,
                                )
                            )
                        }
                        val previousData = (_homePageFlow.value as? PageState.Success)?.info
                        _homePageFlow.value = PageState.Error(networkState.throwable, cachedInfo = previousData)
                    }
                    is WebsiteState.Success -> {
                        AppViewModel.csrfToken = networkState.info.csrfToken
                        networkState.info.userId.takeIf { it.isNotEmpty() }?.let { userId ->
                            SettingsRepository.setSavedUserId(userId)
                        }
                        val homeData = HomeData(page = networkState.info)
                        _homePageFlow.value = PageState.Success(info = homeData, isRefreshing = false)
                    }
                    is WebsiteState.Loading -> { }
                }
            }
        }
    }

    fun dismissAnnouncements(){
        val current = _homePageFlow.value
        if (current is PageState.Success) {
            _homePageFlow.value = current.copy(info = current.info.copy(announcements = emptyList()))
        }
    }

    fun deleteWatchHistory(history: WatchHistoryEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.WatchHistory.delete(history)
            LogUtil.d("delete_watch_hty", "$history DONE!")
        }
    }

    fun deleteAllWatchHistories() {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.WatchHistory.deleteAll()
            LogUtil.d("del_all_watch_hty", "DONE!")
        }
    }

    fun loadAllWatchHistories() =
        DatabaseRepo.WatchHistory.loadAll()
            .catch { e -> e.printStackTrace() }
            .flowOn(ioDispatcher)
    private val _modifyHKeyframeFlow = MutableSharedFlow<Boolean>()
    fun removeHKeyframe(videoCode: String, hKeyframe: HKeyframeEntity.Keyframe) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.removeKeyframe(videoCode, hKeyframe)
            LogUtil.d("HKeyframe", "removeHKeyframe:$hKeyframe DONE!")
            _modifyHKeyframeFlow.emit(true)
        }
    }
    fun modifyHKeyframe(
        videoCode: String,
        oldKeyframe: HKeyframeEntity.Keyframe, keyframe: HKeyframeEntity.Keyframe,
    ) {
        viewModelScope.launch {
            DatabaseRepo.HKeyframe.modifyKeyframe(videoCode, oldKeyframe, keyframe)
            LogUtil.d("HKeyframe", "modifyHKeyframe:$keyframe DONE!")
            _modifyHKeyframeFlow.emit(true)
        }
    }
    fun deleteHKeyframes(entity: HKeyframeEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.delete(entity)
        }
    }

    fun updateHKeyframes(entity: HKeyframeEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.update(entity)
        }
    }
}
