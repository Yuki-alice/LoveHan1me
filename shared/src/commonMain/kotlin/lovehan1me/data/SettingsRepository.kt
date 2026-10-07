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
import lovehan1me.core.domain.model.ThemeConfig
import lovehan1me.core.domain.model.ThemeMode
import lovehan1me.core.domain.model.VideoAspectMode
import lovehan1me.core.domain.model.PictureAdjust
import lovehan1me.core.domain.model.cfCookieFor
import lovehan1me.core.domain.model.cfCookieKeyFor
import lovehan1me.core.domain.model.themeConfig
import lovehan1me.data.network.CloudflareChallenges
import lovehan1me.data.network.egress.ForceMode
import lovehan1me.feature.danmaku.DanmakuRenderOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object SettingsRepository : SettingsStore {
    /** 「已读公告」表的保留上限，见 [markAnnouncementsRead]。 */
    private const val MAX_READ_ANNOUNCEMENT_KEYS = 200

    /**
     * 一次「安装」所对应的全部状态：被装的 [store]，以及由它派生的响应式流。
     *
     * **为什么派生流也要放进来**：它们是 `by lazy` + `stateIn(Eagerly)` 的，一旦求值就会
     * 记住**求值当时那个 store** 的 upstream。若只替换 `store` 而不同步重建这些 lazy，
     * 替换后会继续读到上一份设置的残影（例如登录态仍是上个测试的 store 算出来的值）。
     *
     * 反过来，**不能在 install 时就急切创建**这些流：那会把 5 条 `stateIn(Eagerly)`
     * 推到启动关键路径上，与项目既有的启动优化（`StartupTrace` / 首页 stale 缓存）冲突。
     * 放进 [Session] 既保住了懒惰性，又能随换代整体丢弃。
     */
    private class Session(val store: SettingsStore) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val loginStateFlow by lazy {
            store.settings.map { it.isAlreadyLogin }
                .stateIn(scope, SharingStarted.Eagerly, store.settings.value.isAlreadyLogin)
        }

        val checkInEnabledFlow by lazy {
            store.settings.map { it.checkInEnabled }
                .stateIn(scope, SharingStarted.Eagerly, store.settings.value.checkInEnabled)
        }

        /**
         * B4 主题派生流：只含主题子树关心的四量（mode/id/对比度/AMOLED）。
         *
         * 照既有 `loginStateFlow` 模式（`map + stateIn(Eagerly)`），外加
         * `distinctUntilChanged` —— 改弹幕字号、切代理等无关写操作不再发射，
         * `HanimeTheme` / `SubjectThemeOverride` 随之免重组。
         * 去重键是 [ThemeConfig] 的 data class `equals`，四字段全稳定，不断言。
         */
        val themeConfigFlow: StateFlow<ThemeConfig> by lazy {
            store.settings.map { it.themeConfig() }
                .distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, store.settings.value.themeConfig())
        }

        /**
         * B4 弹幕观感派生流：只含绘制四量（字号/不透明度/显示区/速度）。
         *
         * 映射与 `DanmakuRenderWiring.danmakuRenderOptions()` 同源（字段一一对应，
         * 不分头写）；`DanmakuRenderOptions` 是 data class，去重语义同上。
         * 设置页滑杆拖动时只重组弹幕绘制订阅者，不碰主题树。
         */
        val danmakuRenderOptionsFlow: StateFlow<DanmakuRenderOptions> by lazy {
            store.settings.map { it.toDanmakuRenderOptions() }
                .distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, store.settings.value.toDanmakuRenderOptions())
        }

        /** 命名筛选预设：给 UI 用的响应式流（增删改都要立刻反映到常驻栏/弹窗上）。 */
        val searchFilterPresetsFlow by lazy {
            store.settings.map { it.searchFilterPresets }
                .stateIn(scope, SharingStarted.Eagerly, store.settings.value.searchFilterPresets)
        }

        /** 取消本代次的派生流收集。旧代次被替换后不该再有任何协程存活。 */
        fun dispose() = scope.cancel()
    }

    @Volatile
    private var session: Session? = null

    /**
     * 安装（或替换）设置存储。**可重复调用**。
     *
     * 早先的实现是 `lateinit var store` + `check(!::store.isInitialized)` —— 全局只能装一次。
     * 生产侧（`HanimeApplication` / `Main.kt` / `MainViewController`）确实各只装一次，
     * 但测试共享同一 JVM：第一个测试装上后，后续测试再装就抛 `IllegalStateException`。
     * 于是测试侧被迫写成 `runCatching { install(...) }` —— **静默吞掉异常**，意味着
     * "我以为装上了自己的 store，实际用的是上一个测试留下的那个"，测试在测别的东西而不报错。
     *
     * 现在改为可替换：旧 [Session] 连同它的 scope 一起丢弃，派生流随新代次重建。
     * 代价是失去了"重复安装即编程错误"的运行时检查 —— 但那个检查的收益远小于
     * 它逼出来的 16 处 `runCatching`。
     */
    fun install(store: SettingsStore) {
        synchronized(this) {
            val previous = session
            session = Session(store)
            previous?.dispose()
        }
    }

    /**
     * 仅在**尚未安装**时安装，已装则原样保留（含已累积的设置写入）。
     *
     * 与 [install] 的区别只在"已装时怎么办"：[install] 会**替换**（丢掉旧 store 与它承载的
     * 设置写入），本方法什么都不做。
     *
     * 存在的理由是一个真实踩过的坑：测试里的 `ensureStoreInstalled()` 这类助手**会被调用多次**
     * （每个用例开头）且**必须幂等** —— 若它每次都用 [install] 重置成出厂默认，会把外层
     * 已经写好的前置设置（例如 `withProxy` 设的代理）一并清掉，表现为"用例单独跑绿、
     * 一起跑红"。名字叫 ensure 就该只是 ensure。
     */
    fun installIfAbsent(store: SettingsStore) {
        synchronized(this) {
            if (session == null) session = Session(store)
        }
    }

    private val active: Session
        get() = session ?: error("SettingsRepository is not installed —— 启动路径应先调用 install(store)")

    override val settings: StateFlow<AppSettings> get() = active.store.settings
    override suspend fun update(transform: (AppSettings) -> AppSettings) = active.store.update(transform)
    val current: AppSettings get() = settings.value

    val loginStateFlow get() = active.loginStateFlow
    val checkInEnabledFlow get() = active.checkInEnabledFlow
    val themeConfigFlow get() = active.themeConfigFlow
    val danmakuRenderOptionsFlow get() = active.danmakuRenderOptionsFlow
    val searchFilterPresetsFlow get() = active.searchFilterPresetsFlow

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
    val longPressSpeedTime get() = current.longPressSpeedTime
    val videoLanguage get() = current.videoLanguage
    val videoQuality get() = current.videoQuality
    /** G2-3b：画面比例偏好。引擎不支持时会降级，实际生效值看引擎状态。 */
    val videoAspect get() = current.videoAspect
    /** 超分档位偏好。存"用户选的"，无效档位由调用侧收敛，实际生效值看引擎。 */
    val superResolutionLevel get() = current.superResolutionLevel
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
    /** 手动强制选路（Phase 4 设置页写入；Auto = 调度器全权）。 */
    val egressForceMode get() = ForceMode.fromName(current.egressForceMode)
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
    /** 系列视频播完是否自动连播下一集（默认开，仅系列有效）。 */
    val autoPlayNext get() = current.autoPlayNext
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

    val searchGridColumnsConfig get() = SearchGridColumnsConfig(current.searchGridColumnsCompact, current.searchGridColumnsMedium, current.searchGridColumnsExpanded, current.searchGridColumnsLarge, current.searchGridColumnsExtraLarge)
    val subscriptionArtistRows get() = current.subscriptionArtistRows
    val alwaysShowUpdateCard get() = current.alwaysShowUpdateCard
    val displayDensity get() = current.displayDensity
    val searchFilterPresets get() = current.searchFilterPresets
    val cachedAnnouncementJson get() = current.cachedAnnouncementJson
    /** 已读公告键；仓储据此把已读条目从待展示列表里剔掉。 */
    val readAnnouncementKeys get() = current.readAnnouncementKeys

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

    /** 系列视频播完是否自动连播下一集（默认开）。 */
    suspend fun setAutoPlayNext(value: Boolean) = update { it.copy(autoPlayNext = value) }

    /** G2-3b：画面比例偏好（存"用户选的"，不管引擎能否生效）。 */
    suspend fun setVideoAspect(value: VideoAspectMode) = update { it.copy(videoAspect = value) }

    /** 超分档位偏好（同样存"用户选的"；引擎降级不回写，免得换引擎后选择丢失）。 */
    suspend fun setSuperResolutionLevel(value: Int) =
        update { it.copy(superResolutionLevel = value) }

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
    suspend fun setDynamicSubjectTheme(value: Boolean) = update { it.copy(dynamicSubjectTheme = value) }
    suspend fun setContrastLevel(value: ContrastLevel) = update { it.copy(contrastLevel = value) }
    suspend fun setHapticFeedback(value: Boolean) = update { it.copy(hapticFeedbackEnabled = value) }
    suspend fun setCheckInEnabled(value: Boolean) = update { it.copy(checkInEnabled = value) }
    suspend fun setUsePrivateStorage(value: Boolean) = update { it.copy(usePrivateStorage = value) }
    suspend fun setDownloadStorage(usePrivate: Boolean, path: String?) = update { it.copy(usePrivateStorage = usePrivate, safDownloadPath = path) }
    suspend fun setDownloadCountLimit(value: Int) = update { it.copy(downloadCountLimit = value) }
    suspend fun setSubscriptionArtistRows(value: Int) = update { it.copy(subscriptionArtistRows = value.coerceIn(1, 3)) }
    suspend fun setHomeCategories(order: List<String>, hidden: Set<String>) = update { it.copy(homeCategoryOrder = order, hiddenHomeCategoryKeys = hidden) }
    suspend fun setCachedUpdateJson(value: String?) = update { it.copy(cachedUpdateJson = value) }
    suspend fun setIgnoredVersionCode(value: Int) = update { it.copy(ignoredVersionCode = value) }
    suspend fun setUpdateCheckedAtMs(value: Long) = update { it.copy(updateCheckedAtMs = value) }

    /** 缓存远端公告 JSON 的最近一次成功响应。 */
    suspend fun setCachedAnnouncementJson(value: String?) =
        update { it.copy(cachedAnnouncementJson = value) }

    /**
     * 把 [keys] 标记为已读。
     *
     * 追加到表尾并保留最后 [MAX_READ_ANNOUNCEMENT_KEYS] 条：远端一旦出错不停下发新公告，
     * 无上限的表会跟着长；而公告本身极低频，200 条足够覆盖到「用户早就不会再看到」的程度。
     * 裁剪的是表头（最旧的那些），所以必须保持写入顺序，不能用 Set。
     */
    suspend fun markAnnouncementsRead(keys: Collection<String>) {
        if (keys.isEmpty()) return
        update { settings ->
            val merged = (settings.readAnnouncementKeys + keys).distinct()
            settings.copy(
                readAnnouncementKeys = merged.takeLast(MAX_READ_ANNOUNCEMENT_KEYS),
            )
        }
    }
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

/**
 * 弹幕绘制四量的投影。
 *
 * 定义在**文件级**（而非 `SettingsRepository` 的私有成员）是因为 [SettingsRepository.Session]
 * 要调用它 —— Kotlin 的嵌套类拿不到外层 `object` 的私有实例成员。
 */
private fun AppSettings.toDanmakuRenderOptions(): DanmakuRenderOptions = DanmakuRenderOptions(
    fontSizeSp = danmakuFontSizeSp,
    opacityPercent = danmakuOpacityPercent,
    displayAreaPercent = danmakuDisplayAreaPercent,
    speedPercent = danmakuSpeedPercent,
)
