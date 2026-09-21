package lovehan1me.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import lovehan1me.core.domain.model.AppLanguage
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.DisplayDensity
import lovehan1me.core.domain.model.NavBarStyle
import lovehan1me.core.domain.model.ContrastLevel
import lovehan1me.core.domain.model.PlayerKernel
import lovehan1me.core.domain.model.ProxyType
import lovehan1me.core.domain.model.SearchFilterPreset
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.domain.model.ThemeMode
import lovehan1me.core.domain.model.DOWNLOAD_SPEED_BYTES
import lovehan1me.core.domain.model.normalizeLegacySlideSensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import okio.Path.Companion.toPath

/**
 * 设置项的实际存储实现（P2b：从 :app 下沉到 KMP 共享层，包名不变）。
 *
 * 与旧实现的差异只有「建库」这一步：
 * - 文件路径改由 [dataStoreFilePath] 提供（Android 端与旧 `preferencesDataStoreFile` 完全同址）；
 * - Android 的 SharedPreferences 历史迁移改由 [platformPreferenceMigrations] 在 androidMain 提供；
 * - Android 侧的 `initialize(context)` 入口见 androidMain 的同名扩展函数。
 */
object DataStoreManager : SettingsStore {
    // 旧实现是 preferencesDataStoreFile("settings")，实际落盘名带 .preferences_pb 后缀
    private const val FILE_NAME = "settings.preferences_pb"
    private const val SLIDE_MIGRATED = "slide_sensitivity_v2_migrated"

    /** 命名筛选预设整表序列化后放这个键上（见 [AppSettings.searchFilterPresets]）。 */
    private const val KEY_SEARCH_FILTER_PRESETS = "search_filter_presets"

    /** CF clearance 按域存档整表序列化后放这个键上（见 [AppSettings.cfCookies]）。 */
    private const val KEY_CF_COOKIES = "cf_cookies"

    /** 旧版单行 clearance（一个值 + 一个 host），只用于读侧升级回落。 */
    private const val LEGACY_KEY_CF_COOKIE = "cf_cookie"
    private const val LEGACY_KEY_CF_COOKIE_HOST = "cf_cookie_host"

    private val defaults = AppSettings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableSettings = MutableStateFlow(defaults)
    override val settings: StateFlow<AppSettings> = mutableSettings

    private val storedJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cfCookiesSerializer = MapSerializer(String.serializer(), String.serializer())

    private lateinit var dataStore: DataStore<Preferences>
    @Volatile private var initialized = false

    fun initialize() {
        if (initialized) return
        withInitLock {
            if (initialized) return@withInitLock
            dataStore = PreferenceDataStoreFactory.createWithPath(
                migrations = platformPreferenceMigrations(),
                produceFile = { dataStoreFilePath(FILE_NAME).toPath() },
            )
            runBlockingIo {
                normalizeLegacySlideSensitivity()
                val initial = dataStore.data.first().toAppSettings()
                dataStore.edit { it.write(initial) }
                mutableSettings.value = initial
            }
            initialized = true
            scope.launch {
                dataStore.data.map { it.toAppSettings() }.collect { mutableSettings.value = it }
            }
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        check(initialized) { "DataStoreManager must be initialized before use" }
        lateinit var updated: AppSettings
        dataStore.edit {
            updated = transform(it.toAppSettings())
            it.write(updated)
        }
        mutableSettings.value = updated
    }

    private val current: AppSettings get() = settings.value

    suspend fun restoreBackup(values: Map<String, Any>) {
        lateinit var restored: AppSettings
        dataStore.edit { preferences ->
            values.filterKeys { it !in AUTH_KEYS }.forEach { (name, value) -> preferences.putRaw(name, value) }
            restored = preferences.toAppSettings()
            preferences.write(restored)
        }
        mutableSettings.value = restored
    }

    fun exportBackup(): Map<String, Any> = current.toMap().filterKeys { it !in AUTH_KEYS }

    private suspend fun normalizeLegacySlideSensitivity() {
        dataStore.edit { preferences ->
            if (preferences.bool(SLIDE_MIGRATED, false)) return@edit
            val stored = preferences.intOrNull("slide_sensitivity")
            preferences[intPreferencesKey("slide_sensitivity")] =
                normalizeLegacySlideSensitivity(stored)
            preferences[booleanPreferencesKey(SLIDE_MIGRATED)] = true
        }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        appLanguage = AppLanguage.fromPreference(string("app_language", defaults.appLanguage.preferenceValue)),
        themeMode = ThemeMode.fromValue(string("use_dark_mode", defaults.themeMode.value)),
        themeId = string("app_theme_id", defaults.themeId),
        amoled = bool("amoled_black", defaults.amoled),
        contrastLevel = ContrastLevel.fromValue(string("app_contrast_level", defaults.contrastLevel.value)),
        allowPipMode = bool("allow_pip_mode", defaults.allowPipMode),
        secureMode = bool("secure_mode", defaults.secureMode),
        disableComments = bool("disable_comments", defaults.disableComments),
        hapticFeedbackEnabled = bool("haptic_feedback_enabled", defaults.hapticFeedbackEnabled),
        navBarStyle = NavBarStyle.fromValue(
            string("nav_bar_style", defaults.navBarStyle.value)
        ),
        usageNoticeAccepted = bool("usage_notice_accepted_v2", defaults.usageNoticeAccepted),
        isAlreadyLogin = bool("already_login", defaults.isAlreadyLogin),
        localListNoticeDismissed = bool(
            "local_list_notice_dismissed",
            defaults.localListNoticeDismissed,
        ),
        savedUserId = string("saved_user_id", defaults.savedUserId),
        loginCookie = string("cookie", defaults.loginCookie),
        cfCookies = decodeCfCookies(
            raw = nullableString(KEY_CF_COOKIES),
            legacyCookie = nullableString(LEGACY_KEY_CF_COOKIE),
            legacyHost = nullableString(LEGACY_KEY_CF_COOKIE_HOST),
        ),
        desktopBrowserUserAgent = string("desktop_browser_user_agent", defaults.desktopBrowserUserAgent),
        domainName = string("domain_name", defaults.domainName), selectedBaseUrl = string("selectedBaseUrl", defaults.selectedBaseUrl),
        useCustomMirrorSite = bool("use_custom_mirror_site", defaults.useCustomMirrorSite), customMirrorSite = string("custom_mirror_site", defaults.customMirrorSite),
        appendCustomMirrorPath = bool("append_custom_mirror_path", defaults.appendCustomMirrorPath), useBuiltInHosts = bool("use_built_in_hosts", defaults.useBuiltInHosts),
        customHostsData = string("custom_hosts_data", defaults.customHostsData), useDoH = bool("use_doh", defaults.useDoH), dohPreset = string("doh_preset", defaults.dohPreset),
        dohCustomUrl = string("doh_custom_url", defaults.dohCustomUrl), dohBootstrapIps = string("doh_bootstrap_ips", defaults.dohBootstrapIps), dohTimeoutSeconds = int("doh_timeout_seconds", defaults.dohTimeoutSeconds),
        proxyType = ProxyType.fromId(int("proxy_type", defaults.proxyType.id)), proxyIp = string("proxy_ip", defaults.proxyIp), proxyPort = int("proxy_port", defaults.proxyPort),
        cachedUpdateJson = nullableString("app_update_cached_json"), ignoredVersionCode = int("app_update_ignored_version_code", defaults.ignoredVersionCode),
        downloadCountLimit = int("download_count_limit", defaults.downloadCountLimit), downloadSpeedLimitIndex = intInRange("download_speed_limit", defaults.downloadSpeedLimitIndex, DOWNLOAD_SPEED_BYTES.indices),
        usePrivateStorage = bool("use_private_storage", defaults.usePrivateStorage), safDownloadPath = nullableString("saf_download_path"), collapseDownloadedGroup = bool("collapse_downloaded_group", defaults.collapseDownloadedGroup),
        playerKernel = PlayerKernel.fromValue(string("switch_player_kernel", defaults.playerKernel.value)),
        playerSpeed = floatString("player_speed", defaults.playerSpeed), slideSensitivity = intInRange("slide_sensitivity", defaults.slideSensitivity, 1..7), longPressSpeedTime = floatString("long_press_speed_times", defaults.longPressSpeedTime),
        videoLanguage = string("video_language", defaults.videoLanguage), videoQuality = string("default_video_quality", defaults.videoQuality), showPlayedIndicator = bool("show_played_indicator", defaults.showPlayedIndicator),
        allowResumePlayback = bool("allow_resume_playback", defaults.allowResumePlayback),
        autoPlayOnEnter = bool("auto_play_on_enter", defaults.autoPlayOnEnter),
        danmakuEnabled = bool("danmaku_enabled", defaults.danmakuEnabled), danmakuCommentEnabled = bool("danmaku_comment_enabled", defaults.danmakuCommentEnabled), danmakuProxyBase = string("danmaku_proxy_base", defaults.danmakuProxyBase), danmakuAppId = danmakuCredentials().first, danmakuAppSecret = danmakuCredentials().second,
        danmakuFontSizeSp = int("danmaku_font_size", defaults.danmakuFontSizeSp), danmakuOpacityPercent = int("danmaku_opacity", defaults.danmakuOpacityPercent), danmakuDisplayAreaPercent = int("danmaku_display_area", defaults.danmakuDisplayAreaPercent), danmakuSpeedPercent = int("danmaku_speed", defaults.danmakuSpeedPercent), danmakuShowScroll = bool("danmaku_show_scroll", defaults.danmakuShowScroll), danmakuShowTop = bool("danmaku_show_top", defaults.danmakuShowTop), danmakuShowBottom = bool("danmaku_show_bottom", defaults.danmakuShowBottom),
        mpvProfile = string("mpv_profile", defaults.mpvProfile), enableGpuNextRenderer = bool("mpv_gpu_next_render", defaults.enableGpuNextRenderer), mpvInterpolation = bool("mpv_interpolation", defaults.mpvInterpolation),
        mpvDeband = bool("mpv_deband", defaults.mpvDeband), mpvFramedrop = bool("mpv_framedrop", defaults.mpvFramedrop), mpvHwdec = string("mpv_hwdecx", defaults.mpvHwdec),
        mpvCacheSecs = int("mpv_cache_secs", defaults.mpvCacheSecs), mpvTlsVerify = bool("mpv_tls_verify", defaults.mpvTlsVerify), mpvNetworkTimeout = int("mpv_network_timeout", defaults.mpvNetworkTimeout), customMpvParams = string("mpv_custom_parameters", defaults.customMpvParams),
        searchArtistIgnoreVideoType = bool("search_artist_ignore_video_type", defaults.searchArtistIgnoreVideoType), disableMobileDataWarning = bool("disable_mobile_data_warning", defaults.disableMobileDataWarning),
        funLoadingHints = bool("fun_loading_hints", defaults.funLoadingHints), checkInEnabled = bool("check_in_enabled", defaults.checkInEnabled),
        searchGridColumnsCompact = int("search_grid_columns_compact", defaults.searchGridColumnsCompact), searchGridColumnsMedium = int("search_grid_columns_medium", defaults.searchGridColumnsMedium),
        searchGridColumnsExpanded = int("search_grid_columns_expanded", defaults.searchGridColumnsExpanded), searchGridColumnsLarge = int("search_grid_columns_large", defaults.searchGridColumnsLarge),
        searchGridColumnsExtraLarge = int("search_grid_columns_extra_large", defaults.searchGridColumnsExtraLarge),
        subscriptionArtistRows = intInRange("subscription_artist_rows", defaults.subscriptionArtistRows, 1..3),
        homeCategoryOrder = nullableString("home_category_order")?.split(',')?.filter(String::isNotBlank).orEmpty(),
        hiddenHomeCategoryKeys = nullableString("home_category_hidden")?.split(',')?.filter(String::isNotBlank)?.toSet().orEmpty(),
        searchFilterPresets = decodeFilterPresets(nullableString(KEY_SEARCH_FILTER_PRESETS)),
        alwaysShowUpdateCard = bool("developer_always_show_update_card", defaults.alwaysShowUpdateCard),
        displayDensity = DisplayDensity.fromPercent(int("developer_display_density_percent", defaults.displayDensity.percent)),
    )

    private fun MutablePreferences.write(value: AppSettings) {
        remove(stringPreferencesKey("app_update_cached_json"))
        remove(stringPreferencesKey("saf_download_path"))
        // 旧单行 clearance 已并入 cf_cookies，这里彻底删掉：留着的话用户清掉验证后，
        // 读侧升级回落会把它再捞回来（复活一把已经作废的钥匙）。
        remove(stringPreferencesKey(LEGACY_KEY_CF_COOKIE))
        remove(stringPreferencesKey(LEGACY_KEY_CF_COOKIE_HOST))
        value.toMap().forEach { (name, raw) -> putRaw(name, raw) }
        this[booleanPreferencesKey(SLIDE_MIGRATED)] = true
    }

    private fun AppSettings.toMap(): Map<String, Any> = buildMap {
        put("app_language", appLanguage.preferenceValue); put("use_dark_mode", themeMode.value); put("app_theme_id", themeId); put("amoled_black", amoled); put("app_contrast_level", contrastLevel.value)
        put("allow_pip_mode", allowPipMode); put("secure_mode", secureMode); put("disable_comments", disableComments); put("haptic_feedback_enabled", hapticFeedbackEnabled); put("nav_bar_style", navBarStyle.value)
        put("usage_notice_accepted_v2", usageNoticeAccepted); put("already_login", isAlreadyLogin); put("local_list_notice_dismissed", localListNoticeDismissed); put("saved_user_id", savedUserId); put("cookie", loginCookie); put(KEY_CF_COOKIES, encodeCfCookies(cfCookies)); put("desktop_browser_user_agent", desktopBrowserUserAgent)
        put("domain_name", domainName); put("selectedBaseUrl", selectedBaseUrl); put("use_custom_mirror_site", useCustomMirrorSite); put("custom_mirror_site", customMirrorSite); put("append_custom_mirror_path", appendCustomMirrorPath); put("use_built_in_hosts", useBuiltInHosts); put("custom_hosts_data", customHostsData); put("use_doh", useDoH); put("doh_preset", dohPreset); put("doh_custom_url", dohCustomUrl); put("doh_bootstrap_ips", dohBootstrapIps); put("doh_timeout_seconds", dohTimeoutSeconds); put("proxy_type", proxyType.id); put("proxy_ip", proxyIp); put("proxy_port", proxyPort)
        cachedUpdateJson?.let { put("app_update_cached_json", it) }; put("app_update_ignored_version_code", ignoredVersionCode); put("download_count_limit", downloadCountLimit); put("download_speed_limit", downloadSpeedLimitIndex); put("use_private_storage", usePrivateStorage); safDownloadPath?.let { put("saf_download_path", it) }; put("collapse_downloaded_group", collapseDownloadedGroup)
        put("switch_player_kernel", playerKernel.value); put("player_speed", playerSpeed.toString()); put("slide_sensitivity", slideSensitivity); put("long_press_speed_times", longPressSpeedTime.toString()); put("video_language", videoLanguage); put("default_video_quality", videoQuality); put("show_played_indicator", showPlayedIndicator); put("allow_resume_playback", allowResumePlayback); put("auto_play_on_enter", autoPlayOnEnter)
        put("danmaku_enabled", danmakuEnabled); put("danmaku_comment_enabled", danmakuCommentEnabled); put("danmaku_proxy_base", danmakuProxyBase)
        put("danmaku_font_size", danmakuFontSizeSp); put("danmaku_opacity", danmakuOpacityPercent); put("danmaku_display_area", danmakuDisplayAreaPercent); put("danmaku_speed", danmakuSpeedPercent); put("danmaku_show_scroll", danmakuShowScroll); put("danmaku_show_top", danmakuShowTop); put("danmaku_show_bottom", danmakuShowBottom)
        val (storedAppId, storedAppSecret) = danmakuCredentialsToPersist(
            danmakuAppId, danmakuAppSecret, defaults.danmakuAppId, defaults.danmakuAppSecret,
        )
        put("danmaku_app_id", storedAppId); put("danmaku_app_secret", storedAppSecret)
        put("mpv_profile", mpvProfile); put("mpv_gpu_next_render", enableGpuNextRenderer); put("mpv_interpolation", mpvInterpolation); put("mpv_deband", mpvDeband); put("mpv_framedrop", mpvFramedrop); put("mpv_hwdecx", mpvHwdec); put("mpv_cache_secs", mpvCacheSecs); put("mpv_tls_verify", mpvTlsVerify); put("mpv_network_timeout", mpvNetworkTimeout); put("mpv_custom_parameters", customMpvParams)
        put("search_artist_ignore_video_type", searchArtistIgnoreVideoType); put("disable_mobile_data_warning", disableMobileDataWarning); put("fun_loading_hints", funLoadingHints); put("check_in_enabled", checkInEnabled)
        put("search_grid_columns_compact", searchGridColumnsCompact); put("search_grid_columns_medium", searchGridColumnsMedium); put("search_grid_columns_expanded", searchGridColumnsExpanded); put("search_grid_columns_large", searchGridColumnsLarge); put("search_grid_columns_extra_large", searchGridColumnsExtraLarge)
        put("subscription_artist_rows", subscriptionArtistRows)
        put("home_category_order", homeCategoryOrder.joinToString(",")); put("home_category_hidden", hiddenHomeCategoryKeys.joinToString(","))
        put(KEY_SEARCH_FILTER_PRESETS, encodeFilterPresets(searchFilterPresets))
        put("developer_always_show_update_card", alwaysShowUpdateCard); put("developer_display_density_percent", displayDensity.percent)
    }

    /**
     * 预设整表 ↔ JSON 串。
     *
     * 读侧必须容错：这一串是「用户数据 + 未来版本可能写坏的形状」，解析失败只该丢掉预设，
     * 不该让 [toAppSettings] 直接抛异常把整个设置体系带崩（那样连主题都读不出来）。
     */
    private fun decodeFilterPresets(raw: String?): List<SearchFilterPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { storedJson.decodeFromString<List<SearchFilterPreset>>(raw) }
            .getOrElse { emptyList() }
    }

    private fun encodeFilterPresets(value: List<SearchFilterPreset>): String =
        runCatching { storedJson.encodeToString(value) }.getOrElse { "" }

    /**
     * 读 CF clearance 表。[raw] 缺失（老存档从没写过 `cf_cookies`）时，把旧版单行
     * `cf_cookie` + `cf_cookie_host` 升上来 —— 否则每次改存储形状都要用户重验一次。
     *
     * [raw] 存在但解析失败只当没有凭据，不能让设置整体读崩。
     */
    internal fun decodeCfCookies(
        raw: String?,
        legacyCookie: String?,
        legacyHost: String?,
    ): Map<String, String> {
        if (!raw.isNullOrBlank()) {
            val decoded = runCatching {
                storedJson.decodeFromString(cfCookiesSerializer, raw)
            }.getOrNull().orEmpty()
            return decoded.lowercaseKeys().filterValues { it.isNotBlank() }
        }
        val host = legacyHost?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val cookie = legacyCookie?.takeIf { it.isNotBlank() }
        return if (host != null && cookie != null) mapOf(host to cookie) else emptyMap()
    }

    internal fun encodeCfCookies(value: Map<String, String>): String =
        storedJson.encodeToString(cfCookiesSerializer, value.lowercaseKeys())

    private fun Map<String, String>.lowercaseKeys(): Map<String, String> =
        entries.associate { (host, cookie) -> host.lowercase() to cookie }

    private fun Preferences.bool(name: String, default: Boolean) = runCatching { this[booleanPreferencesKey(name)] }.getOrNull() ?: default
    private fun Preferences.int(name: String, default: Int) = intOrNull(name) ?: default
    private fun Preferences.intOrNull(name: String) = runCatching { this[intPreferencesKey(name)] }.getOrNull() ?: runCatching { this[stringPreferencesKey(name)]?.toIntOrNull() }.getOrNull()
    private fun Preferences.intInRange(name: String, default: Int, range: IntRange) = intOrNull(name)?.takeIf { it in range } ?: default
    private fun Preferences.string(name: String, default: String) = nullableString(name) ?: default
    private fun Preferences.nullableString(name: String) = runCatching { this[stringPreferencesKey(name)] }.getOrNull()
    private fun Preferences.floatString(name: String, default: Float) = nullableString(name)?.toFloatOrNull() ?: default
    private fun Preferences.danmakuCredentials(): Pair<String, String> = resolveDanmakuCredentials(
        storedAppId = nullableString("danmaku_app_id"),
        storedAppSecret = nullableString("danmaku_app_secret"),
        builtInAppId = defaults.danmakuAppId,
        builtInAppSecret = defaults.danmakuAppSecret,
    )
    private fun MutablePreferences.putRaw(name: String, value: Any) { when (value) { is Boolean -> this[booleanPreferencesKey(name)] = value; is Int -> this[intPreferencesKey(name)] = value; is String -> this[stringPreferencesKey(name)] = value } }
    /**
     * 备份导出/导入都跳过这些键。
     *
     * 弹幕 AppID 单独导出去没有意义（签名需要 secret 才成立），留着反而会让
     * 导入方出现"填了 ID 却总是鉴权失败"的半截配置，故与 secret 成对排除。
     */
    private val AUTH_KEYS = setOf(
        "already_login", "saved_user_id", "cookie", KEY_CF_COOKIES,
        "danmaku_app_id", "danmaku_app_secret",
    )
}

/**
 * 弹幕凭据的读法：**盘面上的空串不能把内置凭据顶掉**。
 *
 * [DataStoreManager.initialize] 首启就把全部默认值落盘（`write(initial)`），
 * 而 `string()` 只要键存在就返回盘上的值。于是"凭据改为构建期注入"之前
 * 跑过一次的那份数据，会留下 `danmaku_app_id=""`，此后无论构建里注了什么
 * 都读成空 —— `DanmakuProvider.from` 判为未配置，播放器上表现为"根本没有弹幕"。
 *
 * 两项**都**空才回落到内置（清空 = 用内置，与设置页文案同一套语义）；
 * 只填了一项则原样交出去，让 `DanmakuProvider.from` 按"配对不成立"处理 ——
 * 把用户手填的 ID 和项目的内置 secret 拼成一对四不像，报的会是更难查的 403。
 */
internal fun resolveDanmakuCredentials(
    storedAppId: String?,
    storedAppSecret: String?,
    builtInAppId: String,
    builtInAppSecret: String,
): Pair<String, String> =
    if (storedAppId.isNullOrBlank() && storedAppSecret.isNullOrBlank()) {
        builtInAppId to builtInAppSecret
    } else {
        storedAppId.orEmpty() to storedAppSecret.orEmpty()
    }

/**
 * 弹幕凭据的写法：[resolveDanmakuCredentials] 的反向操作 —— **内置那对不落盘**。
 *
 * 读侧把盘上的空值补成内置凭据，写侧若原样回写就把内置凭据伪装成了"用户自己填的"：
 * 下一次构建换了注入的那对，旧值仍然非空 ⇒ 继续顶在新值上面，弹幕会静默 403 且
 * 界面上看不出原因。顺带一个好处是明文密钥不再进 `settings.preferences_pb`
 * （那个文件不受 [DataStoreManager] 的备份排除保护）。
 *
 * 写空串而不是删键：读侧本来就按"空白 = 未配置"处理，于是老存档里已经写进去的
 * 那份也会在下次启动时被洗掉。
 */
internal fun danmakuCredentialsToPersist(
    appId: String,
    appSecret: String,
    builtInAppId: String,
    builtInAppSecret: String,
): Pair<String, String> =
    if (appId == builtInAppId && appSecret == builtInAppSecret) {
        "" to ""
    } else {
        appId to appSecret
    }
