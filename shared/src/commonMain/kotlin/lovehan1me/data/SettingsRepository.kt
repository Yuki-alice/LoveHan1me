package lovehan1me.data

import lovehan1me.ui.model.SearchGridColumnsConfig
import lovehan1me.core.domain.model.AppLanguage
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.DisplayDensity
import lovehan1me.core.domain.model.NavBarStyle
import lovehan1me.core.domain.model.ContrastLevel
import lovehan1me.core.domain.model.PlayerKernel
import lovehan1me.core.domain.model.SearchFilterPreset
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.domain.model.ThemeMode
import lovehan1me.core.domain.model.VideoAspectMode
import lovehan1me.core.domain.model.PictureAdjust
import lovehan1me.core.domain.model.DOWNLOAD_SPEED_BYTES
import lovehan1me.core.domain.model.cfCookieFor
import lovehan1me.core.domain.model.cfCookieKeyFor
import lovehan1me.data.network.CloudflareChallenges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object SettingsRepository : SettingsStore {
    private lateinit var store: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun install(store: SettingsStore) {
        check(!::store.isInitialized) { "SettingsRepository is already installed" }
        this.store = store
    }

    override val settings: StateFlow<AppSettings> get() = store.settings
    override suspend fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)
    val current: AppSettings get() = settings.value

    val loginStateFlow by lazy { settings.map { it.isAlreadyLogin }.stateIn(scope, SharingStarted.Eagerly, current.isAlreadyLogin) }
    val checkInEnabledFlow by lazy { settings.map { it.checkInEnabled }.stateIn(scope, SharingStarted.Eagerly, current.checkInEnabled) }

    /** 命名筛选预设：给 UI 用的响应式流（增删改都要立刻反映到常驻栏/弹窗上）。 */
    val searchFilterPresetsFlow by lazy {
        settings.map { it.searchFilterPresets }
            .stateIn(scope, SharingStarted.Eagerly, current.searchFilterPresets)
    }

    val isAlreadyLogin get() = current.isAlreadyLogin
    val localListNoticeDismissed get() = current.localListNoticeDismissed
    val usageNoticeAccepted get() = current.usageNoticeAccepted
    val savedUserId get() = current.savedUserId
    /** 该主机可用的 CF clearance（精确域 → 父域回落），无则 null。 */
    fun cfCookieFor(host: String): String? = current.cfCookies.cfCookieFor(host)
    /** 桌面：CF 验证浏览器采集到的真实 UA（空 = 未采集）。 */
    val desktopBrowserUserAgent get() = current.desktopBrowserUserAgent
    val switchPlayerKernel get() = current.playerKernel.value
    val playerSpeed get() = current.playerSpeed
    val slideSensitivity get() = current.slideSensitivity
    val longPressSpeedTime get() = current.longPressSpeedTime
    val videoLanguage get() = current.videoLanguage
    val videoQuality get() = current.videoQuality
    /** G2-3b：画面比例偏好。引擎不支持时会降级，实际生效值看引擎状态。 */
    val videoAspect get() = current.videoAspect
    /** G2-3b：画面调节（仅 mpv 内核生效）。 */
    val pictureAdjust get() = PictureAdjust(
        brightness = current.pictureBrightness,
        contrast = current.pictureContrast,
        saturation = current.pictureSaturation,
    )
    val showPlayedIndicator get() = current.showPlayedIndicator
    val isCheckInEnabled get() = current.checkInEnabled
    val baseUrl: String get() {
        if (current.useCustomMirrorSite && current.customMirrorSite.isNotBlank()) {
            val value = if (current.appendCustomMirrorPath) current.customMirrorSite else rootUrl(current.customMirrorSite)
            return value.withTrailingSlash()
        }
        return current.domainName
    }

    /**
     * 用户在网域设置里**选中**的站点（形如 `https://hanime1.me/`）。
     *
     * 与 [baseUrl] 的差别：`baseUrl` 在开启自定义镜像时返回**镜像地址**，本属性永远返回
     * 用户选中的那个站点。语义上镜像只是"同站的另一个入口"，不改变站点身份 ——
     * 因此**站点身份判定只认本属性**（见 `lovehan1me.site.SiteIdentity`）。
     */
    val domainName get() = current.domainName
    val homeUrl get() = if (current.useCustomMirrorSite && current.customMirrorSite.isNotBlank()) current.customMirrorSite else baseUrl
    val useCustomMirrorSite get() = current.useCustomMirrorSite
    val customMirrorSite get() = current.customMirrorSite
    val appendCustomMirrorPath get() = current.appendCustomMirrorPath
    val selectedBaseUrl get() = current.selectedBaseUrl
    val useBuiltInHosts get() = current.useBuiltInHosts
    val autoBuiltInHosts get() = current.autoBuiltInHosts
    val useEchGate get() = current.useEchGate
    val customHostsData get() = current.customHostsData
    val useDoH get() = current.useDoH
    val dohPreset get() = current.dohPreset
    val dohCustomUrl get() = current.dohCustomUrl
    val dohBootstrapIps get() = current.dohBootstrapIps
    val dohTimeoutSeconds get() = current.dohTimeoutSeconds
    val proxyType get() = current.proxyType.id
    val proxyIp get() = current.proxyIp
    val proxyPort get() = current.proxyPort
    val downloadCountLimit get() = current.downloadCountLimit
    val collapseDownloadedGroup get() = current.collapseDownloadedGroup
    val isUsePrivateStorage get() = current.usePrivateStorage
    val safDownloadPath get() = current.safDownloadPath
    val useDarkMode get() = current.themeMode.value
    val allowResumePlayback get() = current.allowResumePlayback
    /** 进入详情页是否自动播放（默认 false）。 */
    val autoPlayOnEnter get() = current.autoPlayOnEnter
    val searchArtistIgnoreVideoType get() = current.searchArtistIgnoreVideoType
    val disableMobileDataWarning get() = current.disableMobileDataWarning
    val navBarStyle get() = current.navBarStyle
    val hapticFeedbackEnabled get() = current.hapticFeedbackEnabled
    val funLoadingHints get() = current.funLoadingHints
    val secureMode get() = current.secureMode
    val mpvProfile get() = current.mpvProfile
    val enableGPUNextRenderer get() = current.enableGpuNextRenderer
    val mpvInterpolation get() = current.mpvInterpolation
    val mpvDeband get() = current.mpvDeband
    val mpvFramedrop get() = current.mpvFramedrop
    val mpvHwdec get() = current.mpvHwdec
    val mpvCacheSecs get() = current.mpvCacheSecs
    val mpvTlsVerify get() = current.mpvTlsVerify
    val mpvNetworkTimeout get() = current.mpvNetworkTimeout
    val customMpvParams get() = current.customMpvParams
    val downloadSpeedLimit get() = DOWNLOAD_SPEED_BYTES[current.downloadSpeedLimitIndex]
    val searchGridColumnsConfig get() = SearchGridColumnsConfig(current.searchGridColumnsCompact, current.searchGridColumnsMedium, current.searchGridColumnsExpanded, current.searchGridColumnsLarge, current.searchGridColumnsExtraLarge)
    val subscriptionArtistRows get() = current.subscriptionArtistRows
    val alwaysShowUpdateCard get() = current.alwaysShowUpdateCard
    val displayDensity get() = current.displayDensity
    val searchFilterPresets get() = current.searchFilterPresets

    suspend fun setLoginState(value: Boolean) = update { it.copy(isAlreadyLogin = value) }
    suspend fun dismissLocalListNotice() = update { it.copy(localListNoticeDismissed = true) }
    /**
     * 写入某域的 CF clearance，并广播"该域验证已通过"。
     *
     * 这是全仓唯一的 clearance 写入口，所以"通过"信号挂在这里而不是各端验证 UI 上——
     * 桌面 CDP / 桌面手动粘贴 / Android WebView / iOS WKWebView 四条成功路径自动共用
     * 同一套重试语义（等信号的请求见 `NetworkRepo.ioRequest`）。
     */
    suspend fun setCloudFlareCookie(host: String, value: String) {
        val key = host.lowercase()
        update { it.copy(cfCookies = it.cfCookies + (key to value)) }
        CloudflareChallenges.passed(key)
    }

    /**
     * 作废某域 clearance。403 命中挑战即说明这把钥匙已死（过期、或出口 IP 变了），
     * 留着它只会让"要不要再弹验证"的判断继续基于一个假前提。
     *
     * 删的是 [cfCookieKeyFor] 命中的那一条（可能是父域），与请求实际用了谁保持一致。
     */
    suspend fun clearCloudFlareCookie(host: String) {
        update { settings ->
            val key = settings.cfCookies.cfCookieKeyFor(host)
            if (key == null) settings else settings.copy(cfCookies = settings.cfCookies - key)
        }
    }

    /** 退出登录用：清掉全部域的 clearance（与旧单行实现同语义）。 */
    suspend fun clearAllCloudFlareCookies() = update { it.copy(cfCookies = emptyMap()) }

    /** 进入详情页是否自动播放（默认关）。 */
    suspend fun setAutoPlayOnEnter(value: Boolean) = update { it.copy(autoPlayOnEnter = value) }

    /** G2-3b：画面比例偏好（存"用户选的"，不管引擎能否生效）。 */
    suspend fun setVideoAspect(value: VideoAspectMode) = update { it.copy(videoAspect = value) }

    /** G2-3b：画面亮度（-100~100）。 */
    suspend fun setPictureBrightness(value: Float) =
        update { it.copy(pictureBrightness = PictureAdjust.clamp(value)) }

    /** G2-3b：画面对比度（-100~100）。 */
    suspend fun setPictureContrast(value: Float) =
        update { it.copy(pictureContrast = PictureAdjust.clamp(value)) }

    /** G2-3b：画面饱和度（-100~100）。 */
    suspend fun setPictureSaturation(value: Float) =
        update { it.copy(pictureSaturation = PictureAdjust.clamp(value)) }

    /** G2-3b：一次性复位画面调节三件套。 */
    suspend fun resetPictureAdjust() = update {
        it.copy(pictureBrightness = 0f, pictureContrast = 0f, pictureSaturation = 0f)
    }

    /** 落盘 CF 验证浏览器自报的真实 UA（供 HTTP 层对齐，见 currentHttpUserAgent 的 KDoc）。 */
    suspend fun setDesktopBrowserUserAgent(value: String) =
        update { it.copy(desktopBrowserUserAgent = value.trim()) }
    suspend fun setSavedUserId(value: String) = update { it.copy(savedUserId = value) }
    suspend fun setUsageNoticeAccepted(value: Boolean) = update { it.copy(usageNoticeAccepted = value) }
    suspend fun setLanguage(value: AppLanguage) = update { it.copy(appLanguage = value) }
    suspend fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }
    suspend fun setThemeId(value: String) = update { it.copy(themeId = value) }
    suspend fun setAmoled(value: Boolean) = update { it.copy(amoled = value) }
    suspend fun setContrastLevel(value: ContrastLevel) = update { it.copy(contrastLevel = value) }
    suspend fun setHapticFeedback(value: Boolean) = update { it.copy(hapticFeedbackEnabled = value) }
    suspend fun setCheckInEnabled(value: Boolean) = update { it.copy(checkInEnabled = value) }
    suspend fun setUsePrivateStorage(value: Boolean) = update { it.copy(usePrivateStorage = value) }
    suspend fun setDownloadStorage(usePrivate: Boolean, path: String?) = update { it.copy(usePrivateStorage = usePrivate, safDownloadPath = path) }
    suspend fun setDownloadCountLimit(value: Int) = update { it.copy(downloadCountLimit = value) }
    suspend fun setDownloadSpeedLimitIndex(value: Int) = update { it.copy(downloadSpeedLimitIndex = value.coerceIn(DOWNLOAD_SPEED_BYTES.indices)) }
    suspend fun setSlideSensitivity(value: Int) = update { it.copy(slideSensitivity = value.coerceIn(1, 7)) }
    suspend fun setSubscriptionArtistRows(value: Int) = update { it.copy(subscriptionArtistRows = value.coerceIn(1, 3)) }
    suspend fun setHomeCategories(order: List<String>, hidden: Set<String>) = update { it.copy(homeCategoryOrder = order, hiddenHomeCategoryKeys = hidden) }
    suspend fun setCachedUpdateJson(value: String?) = update { it.copy(cachedUpdateJson = value) }
    suspend fun setIgnoredVersionCode(value: Int) = update { it.copy(ignoredVersionCode = value) }
    suspend fun setAlwaysShowUpdateCard(value: Boolean) = update { it.copy(alwaysShowUpdateCard = value) }
    suspend fun setDisplayDensity(value: DisplayDensity) = update { it.copy(displayDensity = value) }
    suspend fun setNavBarStyle(value: NavBarStyle) = update { it.copy(navBarStyle = value) }

    /** 整表覆写命名筛选预设（增删改都由 [lovehan1me.feature.search.SearchFilterPresetStore] 先算好新表）。 */
    suspend fun setSearchFilterPresets(value: List<SearchFilterPreset>) =
        update { it.copy(searchFilterPresets = value) }

    private fun String.withTrailingSlash() = if (endsWith('/')) this else "$this/"

    /**
     * 取 URL 的 scheme + authority（等价于旧实现的 `URI(value).let { "${it.scheme}://${it.rawAuthority}" }`）。
     *
     * 原来用 java.net.URI，但 iOS 端没有 java.*，故改为纯 Kotlin 解析：
     * 以 `://` 定位 scheme 结尾，authority 到第一个 `/`、`?` 或 `#` 为止；
     * 解析不出（无 scheme 或 authority 为空）时按旧行为返回原值。
     */
    private fun rootUrl(value: String): String = parseRootUrl(value)

    internal fun parseRootUrl(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return url
        val authorityStart = schemeEnd + 3
        val authorityEnd = url.drop(authorityStart)
            .indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) url.length else authorityStart + it }
        val authority = url.substring(authorityStart, authorityEnd)
        if (authority.isEmpty()) return url
        return "${url.substring(0, schemeEnd)}://$authority"
    }
}
