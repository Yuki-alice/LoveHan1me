package lovehan1me.feature.home.homepage

import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.StartupTrace
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lovehan1me.data.AnnouncementRepository
import lovehan1me.data.SettingsRepository
import lovehan1me.core.platform.ioDispatcher
import lovehan1me.Res
import lovehan1me.login_state_expired
import org.jetbrains.compose.resources.StringResource
import lovehan1me.data.AppUpdateChecker
import lovehan1me.data.readCachedHomeHtml
import lovehan1me.data.homePageCacheKey
import lovehan1me.core.domain.model.HomePage
import lovehan1me.site.hanime1.Parser
import lovehan1me.core.platform.performAccountLogout
import lovehan1me.data.AppUpdateState
import lovehan1me.data.DatabaseRepo
import lovehan1me.data.NetworkRepo
import lovehan1me.data.database.entity.WatchHistoryEntity
import lovehan1me.core.domain.exception.LoginStateExpiredException
import lovehan1me.core.domain.model.Announcement
import lovehan1me.core.domain.state.PageState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.app.AppViewModel
import lovehan1me.app.navigation.main.HanimeScreen
import lovehan1me.app.navigation.main.HomeRoute
import lovehan1me.app.navigation.main.TopLevelBackStack
import lovehan1me.ui.foundation.launchSafely
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

    /**
     * 更新 JSON 里那条公告。
     *
     * **只作为 [AnnouncementRepository] 的 legacy 来源存在，不再直接上屏**：
     * 直接渲染会让它和合并后的公告列表重复显示一次（它同时也在
     * `announcement.json` 的候选集里）。上屏一律走 [announcements]。
     */
    private val _updateAnnouncement = MutableStateFlow<Announcement?>(null)
    val updateAnnouncement = _updateAnnouncement.asStateFlow()

    /**
     * 该展示的公告（已去重、已剔过期、已剔已读、已排序）。
     *
     * 与首页内容分开成独立流：公告和首页来自**两个不同的远端**，
     * 塞进 `HomeData` 会导致"刷新公告要跟着重新拉整个首页"。
     */
    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements = _announcements.asStateFlow()

    private var homePageJob: Job? = null
    private var initializationJob: Job? = null

    init {
        launchSafely("HomePageViewModel.init") {
            // 初始化默认已下载分组，防止[FOREIGN KEY constraint failed]
            DatabaseRepo.HanimeDownload.insertDefaultGroup()
        }
    }

    fun initializeHomePage() {
        if (!SettingsRepository.usageNoticeAccepted) return
        if (initializationJob != null || _appUpdateState.value !is AppUpdateState.Checking) return
        initializationJob = launchSafely("HomePageViewModel.initializeHomePage-1") {
            StartupTrace.mark("home-update-check-start")
            val updateResult = AppUpdateChecker.checkForUpdate()
            StartupTrace.mark("home-update-check-end")
            _updateAnnouncement.value = updateResult.announcement
            // 冷启动公告只读缓存（~0ms），远端刷新放后台：公告曾因 404/timeout
            // 拖慢启动数秒，而它不挡首屏内容。手动下拉仍走网络版 refreshAnnouncements()。
            _announcements.value = AnnouncementRepository.visibleFromCache(_updateAnnouncement.value)
            launchSafely("HomePageViewModel.initializeHomePage-2") {
                _announcements.value = AnnouncementRepository.load(_updateAnnouncement.value)
            }
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
        launchSafely("HomePageViewModel.ignoreUpdate") {
            AppUpdateChecker.ignoreUpdate(versionCode)
            _appUpdateState.value = AppUpdateState.NoUpdate
        }
    }

    fun getHomePage(isRefresh: Boolean = false){
        if (!SettingsRepository.usageNoticeAccepted) return
        when (val updateState = _appUpdateState.value) {
            AppUpdateState.Checking -> {
                initializeHomePage()
                return
            }
            is AppUpdateState.Available -> if (updateState.info.forceUpdate) return
            AppUpdateState.NoUpdate -> Unit
        }
        homePageJob?.cancel()
        homePageJob = launchSafely("HomePageViewModel.getHomePage") {
            val current = _homePageFlow.value
            val cachedErrorInfo = if (current is PageState.Error) current.cachedInfo else null
            if (isRefresh && current is PageState.Success) {
                _homePageFlow.value = current.copy(isRefreshing = true)
            } else if (isRefresh && cachedErrorInfo != null) {
                _homePageFlow.value = PageState.Success(info = cachedErrorInfo, isRefreshing = true)
            } else if (!isRefresh && current !is PageState.Success){
                _homePageFlow.value = PageState.Loading
                // E2：冷启动先展缓存（解析 ~0.2s），网络新鲜到达后覆盖；
                // 坏文件解析失败就当 miss，下次成功覆盖。
                runCatching {
                    readCachedHomeHtml(homePageCacheKey())?.takeIf { it.isNotBlank() }?.let { html ->
                        val cached = Parser.homePageVer2(html)
                        if (cached is WebsiteState.Success<HomePage>) {
                            _homePageFlow.value = PageState.Success(
                                info = HomeData(page = cached.info),
                                isRefreshing = false,
                            )
                            StartupTrace.mark("home-content-ready")
                        }
                    }
                }
            }
            StartupTrace.mark("home-fetch-start")
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
                        // 首屏内容到达（mark 首写胜出，刷新不覆盖冷启动值）。
                        StartupTrace.mark("home-content-ready")
                        // 只有用户主动刷新才重拉公告：初次进页时 initializeHomePage 已经拉过一次，
                        // 在这里再拉一次就是同一次启动发两倍请求。
                        if (isRefresh) refreshAnnouncements()
                    }
                    is WebsiteState.Loading -> { }
                }
            }
        }
    }

    /** 拉取并重算公告。失败时 [AnnouncementRepository] 内部降级到缓存，不会清空已有列表。 */
    private suspend fun refreshAnnouncements() {
        _announcements.value = AnnouncementRepository.load(_updateAnnouncement.value)
    }

    /**
     * 把 [keys] 对应的公告标记为已读。
     *
     * 旧实现叫 `dismissAnnouncements`，语义是"清空内存里的列表" —— 于是杀进程重进又全回来，
     * 而那条公告的卡片又是 `onClose = null`（压根关不掉）。现在落盘记已读，
     * 且立刻用**缓存**重算（不发网络请求），保证点完就消失。
     */
    fun markAnnouncementsRead(keys: Collection<String>) {
        if (keys.isEmpty()) return
        launchSafely("HomePageViewModel.markAnnouncementsRead") {
            SettingsRepository.markAnnouncementsRead(keys)
            _announcements.value = AnnouncementRepository.visibleFromCache(_updateAnnouncement.value)
        }
    }

    fun deleteWatchHistory(history: WatchHistoryEntity) {
        launchSafely("HomePageViewModel.deleteWatchHistory", ioDispatcher) {
            DatabaseRepo.WatchHistory.delete(history)
            LogUtil.d("delete_watch_hty", "$history DONE!")
        }
    }

    fun deleteAllWatchHistories() {
        launchSafely("HomePageViewModel.deleteAllWatchHistories", ioDispatcher) {
            DatabaseRepo.WatchHistory.deleteAll()
            LogUtil.d("del_all_watch_hty", "DONE!")
        }
    }

    fun loadAllWatchHistories() =
        DatabaseRepo.WatchHistory.loadAll()
            .catch { e -> e.printStackTrace() }
            .flowOn(ioDispatcher)
}
