package lovehan1me.app.navigation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.constant.HA1_GITHUB_FORUM_URL
import lovehan1me.core.constant.HA1_GITHUB_ISSUE_URL
import lovehan1me.data.BackupManager
import lovehan1me.data.SettingsRepository
import lovehan1me.core.platform.appVersionDisplay
import lovehan1me.core.platform.applyAppLanguage
import lovehan1me.core.platform.applySecureMode
import lovehan1me.core.platform.clearCacheDir
import lovehan1me.core.platform.getCacheDirSize
import lovehan1me.core.platform.isPipPermissionGranted
import lovehan1me.core.platform.openPipPermissionSettings
import lovehan1me.core.platform.recreateActivity
import lovehan1me.core.platform.readBackupText
import lovehan1me.core.platform.restartApp
import lovehan1me.core.platform.supportsPerAppLinks
import lovehan1me.core.platform.switchLauncherIcon
import lovehan1me.core.platform.writeBackupText
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.platform.ioDispatcher
import lovehan1me.core.platform.openPerAppLinksSettings
import lovehan1me.core.platform.rememberBackupExportLauncher
import lovehan1me.core.platform.rememberBackupImportLauncher
import lovehan1me.Res
import lovehan1me.action_app_open_by_default_settings_not_support
import lovehan1me.backup_export_failed
import lovehan1me.backup_export_success
import lovehan1me.backup_import_failed
import lovehan1me.backup_import_success
import lovehan1me.cache_empty
import lovehan1me.clear_failed
import lovehan1me.clear_success
import lovehan1me.current_version
import lovehan1me.fake_icon_hint
import lovehan1me.follow_system
import lovehan1me.local_data_export_failed
import lovehan1me.local_data_export_success
import lovehan1me.local_data_import_failed
import lovehan1me.local_data_import_success
import lovehan1me.login_first
import lovehan1me.online_data_export_failed
import lovehan1me.online_data_export_success
import lovehan1me.online_data_import_failed
import lovehan1me.online_data_import_success
import lovehan1me.request_pip_alert
import lovehan1me.simplified_chinese
import lovehan1me.success_value
import lovehan1me.sure_to_clear_cache
import lovehan1me.traditional_chinese
import lovehan1me.sure_to_clear
import lovehan1me.restart_needed
import lovehan1me.hanime_app_name
import lovehan1me.go_to_settings
import lovehan1me.fake_app_icon
import lovehan1me.confirm
import lovehan1me.cancel
import lovehan1me.backup_import_title
import lovehan1me.backup_import_confirm_message
import lovehan1me.attention
import lovehan1me.apply_deep_links_tips
import lovehan1me.apply_deep_links_summary
import lovehan1me.apply_deep_links
import lovehan1me.app_name_fake_xxt
import lovehan1me.app_name_fake_cornhub
import lovehan1me.app_name_fake_calc
import lovehan1me.ic_launcher_xxt
import lovehan1me.ic_launcher_new
import lovehan1me.ic_launcher_cornhub
import lovehan1me.ic_launcher_calc
import lovehan1me.data.LocalListRepository
import lovehan1me.data.OnlineListsBackup
import lovehan1me.core.domain.model.AppLanguage
import lovehan1me.core.domain.model.DisplayDensity
import lovehan1me.core.domain.model.NavBarStyle
import lovehan1me.core.domain.model.ContrastLevel
import lovehan1me.core.domain.model.ThemeMode
import lovehan1me.core.domain.model.VideoLandscapeLayoutStyle
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.feature.settings.HomeSettingsPage
import lovehan1me.feature.settings.HomeSettingsScreen
import lovehan1me.feature.settings.model.HomeSettingsUiState
import lovehan1me.feature.settings.model.HomeSettingsActions
import lovehan1me.feature.home.homepage.defaultHomeCategoryPreferenceItems
import lovehan1me.feature.home.homepage.hiddenHomeCategoryKeys
import lovehan1me.feature.home.homepage.homeCategoryOrder
import lovehan1me.feature.home.homepage.saveHomeCategoryPreferences
import lovehan1me.core.util.SonnerToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSettingsRouteScreen(
    page: HomeSettingsPage,
    onNavigateToHKeyframes: () -> Unit = {},
    onNavigateToSharedHKeyframes: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    onOpenThemeAudit: () -> Unit = {},
    // P6d-4E：下载设置页依赖 :app 的 SAF（SafFileManager/WorkManager），由 Android 壳注入；
    // 桌面/iOS 下载目录能力随 P7 提供，默认空占位
    downloadSettingsContent: @Composable () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    // P6d-3-C3：回调内非 suspend，固定串在此预解析
    val loginFirstText = stringResource(Res.string.login_first)
    val requestPipText = stringResource(Res.string.request_pip_alert)
    val deepLinksWarnText = stringResource(Res.string.action_app_open_by_default_settings_not_support)
    val cacheEmptyText = stringResource(Res.string.cache_empty)
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val isLoggedIn by SettingsRepository.loginStateFlow.collectAsStateWithLifecycle()
    var cacheKey by remember { mutableIntStateOf(0) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showRestartConfirmDialog by remember { mutableStateOf(false) }
    var showLauncherPicker by remember { mutableStateOf(false) }
    var showApplyDeepLinksDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberBackupExportLauncher { uri ->
        uri ?: return@rememberBackupExportLauncher
        coroutineScope.launch(ioDispatcher) {
            runCatching { BackupManager.exportTo(uri) }
                .onSuccess { SonnerToast.success(getString(Res.string.backup_export_success)) }
                .onFailure { SonnerToast.error(getString(Res.string.backup_export_failed)) }
        }
    }
    val importLauncher = rememberBackupImportLauncher { pendingImportUri = it }
    val localListsExportLauncher = rememberBackupExportLauncher { uri ->
        uri ?: return@rememberBackupExportLauncher
        coroutineScope.launch(ioDispatcher) {
            runCatching {
                val jsonText = LocalListRepository.exportLocalListsJson()
                check(writeBackupText(uri, jsonText)) { "Unable to open output file" }
            }.onSuccess {
                SonnerToast.success(getString(Res.string.local_data_export_success))
            }.onFailure {
                SonnerToast.error(it.message ?: getString(Res.string.local_data_export_failed))
            }
        }
    }
    val localListsImportLauncher = rememberBackupImportLauncher { uri ->
        uri ?: return@rememberBackupImportLauncher
        coroutineScope.launch(ioDispatcher) {
            runCatching {
                val jsonText = readBackupText(uri) ?: error("Unable to open input file")
                LocalListRepository.importLocalListsJson(jsonText, merge = true)
            }.onSuccess {
                SonnerToast.success(getString(Res.string.local_data_import_success))
            }.onFailure {
                SonnerToast.error(it.message ?: getString(Res.string.local_data_import_failed))
            }
        }
    }
    val onlineListsExportLauncher = rememberBackupExportLauncher { uri ->
        uri ?: return@rememberBackupExportLauncher
        coroutineScope.launch(ioDispatcher) {
            runCatching {
                val jsonText = OnlineListsBackup.exportOnlineListsJson()
                check(writeBackupText(uri, jsonText)) { "Unable to open output file" }
            }.onSuccess {
                SonnerToast.success(getString(Res.string.online_data_export_success))
            }.onFailure {
                SonnerToast.error(it.message ?: getString(Res.string.online_data_export_failed))
            }
        }
    }
    val onlineListsImportLauncher = rememberBackupImportLauncher {
        if (!SettingsRepository.isAlreadyLogin) {
            SonnerToast.warning(loginFirstText)
            return@rememberBackupImportLauncher
        }
        val uri = it ?: return@rememberBackupImportLauncher
        coroutineScope.launch(ioDispatcher) {
            runCatching {
                val jsonText = readBackupText(uri) ?: error("Unable to open input file")
                OnlineListsBackup.importOnlineListsJson(jsonText)
            }.onSuccess {
                SonnerToast.success(getString(Res.string.online_data_import_success))
            }.onFailure {
                SonnerToast.error(it.message ?: getString(Res.string.online_data_import_failed))
            }
        }
    }
    val hanimeAppName = stringResource(Res.string.hanime_app_name)
    val fakeNameCalc = stringResource(Res.string.app_name_fake_calc)
    val fakeNameCornhub = stringResource(Res.string.app_name_fake_cornhub)
    val fakeNameXXT = stringResource(Res.string.app_name_fake_xxt)

    val launcherItems = remember {
        listOf(
            LauncherItem(
                name = hanimeAppName,
                iconRes = Res.drawable.ic_launcher_new,
                alias = "lovehan1me.LauncherAliasDefault",
            ),
            LauncherItem(
                name = fakeNameCalc,
                iconRes = Res.drawable.ic_launcher_calc,
                alias = "lovehan1me.LauncherFakeCalc",
            ),
            LauncherItem(
                name = fakeNameCornhub,
                iconRes = Res.drawable.ic_launcher_cornhub,
                alias = "lovehan1me.LauncherFakeCornhub",
            ),
            LauncherItem(
                name = fakeNameXXT,
                iconRes = Res.drawable.ic_launcher_xxt,
                alias = "lovehan1me.LauncherFakeXxt",
            ),
        )
    }

    var cacheSummary by remember { mutableStateOf("") }

    LaunchedEffect(cacheKey) {
        cacheSummary = withContext(ioDispatcher) {
            generateClearCacheSummary(getCacheDirSize()).toString()
        }
    }
    // P6d-3-C2：builder 在 remember{} 内无法调资源，标签在外层预解析后传入
    val traditionalChineseLabel = stringResource(Res.string.traditional_chinese)
    val simplifiedChineseLabel = stringResource(Res.string.simplified_chinese)
    val followSystemLabel = stringResource(Res.string.follow_system)
    val versionSummaryTop = stringResource(
        Res.string.current_version,
        appVersionDisplay()
    )
    val uiState = remember(
        settings, cacheSummary, launcherItems,
        traditionalChineseLabel, simplifiedChineseLabel, followSystemLabel, versionSummaryTop,
    ) {
        buildHomeSettingsUiState(
            videoLanguageLabels = mapOf(
                "zht" to traditionalChineseLabel,
                "zhs" to simplifiedChineseLabel,
            ),
            followSystemLabel = followSystemLabel,
            launcherItems = launcherItems,
            cacheSummary = cacheSummary,
            versionSummary = versionSummaryTop,
        )
    }

    HomeSettingsScreen(
        page = page,
        state = uiState,
        isLoggedIn = isLoggedIn,
        actions = HomeSettingsActions(
            videoLanguageChange = { value ->
                if (value != SettingsRepository.videoLanguage) {
                    coroutineScope.launch {
                        SettingsRepository.update { it.copy(videoLanguage = value) }
                        showRestartConfirmDialog = true
                    }
                }
            },
            videoQualityChange = { value ->
                coroutineScope.launch {
                    SettingsRepository.update { it.copy(videoQuality = value) }
                    SonnerToast.success(getString(Res.string.success_value, value))
                }
            },
            darkModeChange = { value ->
                if (value != SettingsRepository.useDarkMode) {
                    coroutineScope.launch { SettingsRepository.setThemeMode(ThemeMode.fromValue(value)) }
                }
            },
            themeIdChange = { id ->
                coroutineScope.launch { SettingsRepository.setThemeId(id) }
            },
            amoledChange = { enabled ->
                coroutineScope.launch { SettingsRepository.setAmoled(enabled) }
            },
            hapticFeedbackChange = { enabled ->
                coroutineScope.launch { SettingsRepository.setHapticFeedback(enabled) }
            },
            funLoadingHintsChange = { enabled ->
                coroutineScope.launch { SettingsRepository.update { it.copy(funLoadingHints = enabled) } }
            },
            contrastLevelChange = { value ->
                coroutineScope.launch { SettingsRepository.setContrastLevel(ContrastLevel.fromValue(value)) }
            },
            allowPipModeChange = { enabled ->
                if (enabled && !isPipPermissionGranted()) {
                    SonnerToast.warning(requestPipText)
                    openPipPermissionSettings()
                    coroutineScope.launch { SettingsRepository.update { it.copy(allowPipMode = false) } }
                } else {
                    coroutineScope.launch { SettingsRepository.update { it.copy(allowPipMode = enabled) } }
                }
            },
            allowResumePlaybackChange = {
                coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(allowResumePlayback = it) } }
            },
            showPlayedIndicatorChange = {
                coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(showPlayedIndicator = it) } }
            },
            searchArtistIgnoreVideoTypeChange = {
                coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(searchArtistIgnoreVideoType = it) } }
            },
            disableMobileDataWarningChange = {
                coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(disableMobileDataWarning = it) } }
            },
            disablePredictiveBackChange = {
                coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(disablePredictiveBack = it) } }
            },
            videoLandscapeLayoutStyleChange = { value ->
                coroutineScope.launch {
                    SettingsRepository.setVideoLandscapeLayoutStyle(
                        VideoLandscapeLayoutStyle.fromValue(value)
                    )
                }
            },
            navBarStyleChange = { value ->
                coroutineScope.launch {
                    SettingsRepository.setNavBarStyle(NavBarStyle.fromValue(value))
                }
            },
            checkInEnabledChange = {
                coroutineScope.launch {
                    SettingsRepository.setCheckInEnabled(it)
                }
            },
            disableCommentsChange = {
                coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(disableComments = it) } }
            },
            collapseDownloadedGroupChange = {
                coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(collapseDownloadedGroup = it) } }
            },
            searchGridColumnsConfigChange = { config ->
                coroutineScope.launch { SettingsRepository.update { it.copy(searchGridColumnsCompact = config.compactColumns, searchGridColumnsMedium = config.mediumColumns, searchGridColumnsExpanded = config.expandedColumns, searchGridColumnsLarge = config.largeColumns) } }
            },
            horizontalCardCountConfigChange = { config ->
                coroutineScope.launch { SettingsRepository.update { it.copy(horizontalCardCountNarrow = config.narrowCount, horizontalCardCountCompact = config.compactCount, horizontalCardCountMedium = config.mediumCount, horizontalCardCountExpanded = config.expandedCount) } }
            },
            homeCategoryPreferencesChange = { order, hiddenKeys ->
                coroutineScope.launch { saveHomeCategoryPreferences(order, hiddenKeys) }
            },
            secureModeChange = { enabled ->
                coroutineScope.launch {
                    SettingsRepository.update { it.copy(secureMode = enabled) }
                    applySecureMode(enabled)
                }
            },
            alwaysShowUpdateCardChange = { enabled ->
                coroutineScope.launch { SettingsRepository.setAlwaysShowUpdateCard(enabled) }
            },
            displayDensityChange = { percent ->
                coroutineScope.launch {
                    SettingsRepository.setDisplayDensity(DisplayDensity.fromPercent(percent))
                }
            },
            triggerCrash = {
                throw RuntimeException("Crash triggered from developer options")
            },
            openAppLanguageSettings = { value ->
                val language = AppLanguage.fromPreference(value)
                if (SettingsRepository.current.appLanguage != language) {
                    coroutineScope.launch {
                        SettingsRepository.setLanguage(language)
                        applyAppLanguage(language)
                    }
                }
            },
            openApplyDeepLinks = {
                if (!supportsPerAppLinks()) {
                    SonnerToast.warning(deepLinksWarnText)
                } else {
                    showApplyDeepLinksDialog = true
                }
            },
            openFakeLauncherIcon = { showLauncherPicker = true },
            openOpenSourceLicense = onNavigateToOpenSourceLicenses,
            clearCache = {
                coroutineScope.launch {
                    if (getCacheDirSize() == 0L) SonnerToast.info(cacheEmptyText)
                    else showClearCacheConfirm = true
                }
            },
            exportBackup = {
                exportLauncher("Han1meViewer-backup-${currentEpochMillis()}.json")
            },
            importBackup = {
                importLauncher()
            },
            exportLocalLists = {
                localListsExportLauncher(
                    "Han1meViewer-local-lists-${currentEpochMillis()}.json"
                )
            },
            importLocalLists = {
                localListsImportLauncher()
            },
            exportOnlineLists = {
                if (!SettingsRepository.isAlreadyLogin) {
                    SonnerToast.warning(loginFirstText)
                } else {
                    onlineListsExportLauncher(
                        "Han1meViewer-online-lists-${currentEpochMillis()}.json"
                    )
                }
            },
            importOnlineLists = {
                if (!SettingsRepository.isAlreadyLogin) {
                    SonnerToast.warning(loginFirstText)
                } else {
                    onlineListsImportLauncher()
                }
            },
            submitBug = { uriHandler.openUri(HA1_GITHUB_ISSUE_URL) },
            openForum = { uriHandler.openUri(HA1_GITHUB_FORUM_URL) },
        ),
        hKeyframeSettingsContent = {
            HKeyframeSettingsRouteScreen(
                onNavigateToHKeyframes = onNavigateToHKeyframes,
                onNavigateToSharedHKeyframes = onNavigateToSharedHKeyframes,
                embedded = true,
            )
        },
        networkSettingsContent = { NetworkSettingsRouteScreen(embedded = true) },
        downloadSettingsContent = downloadSettingsContent,
        onOpenThemeAudit = onOpenThemeAudit,
    )

    ConfirmDialog(
        visible = pendingImportUri != null,
        title = stringResource(Res.string.backup_import_title),
        message = stringResource(Res.string.backup_import_confirm_message),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            val uri = pendingImportUri ?: return@ConfirmDialog
            pendingImportUri = null
            coroutineScope.launch(ioDispatcher) {
                runCatching { BackupManager.importFrom(uri) }
                    .onSuccess {
                        withContext(Dispatchers.Main) {
                            SonnerToast.success(getString(Res.string.backup_import_success))
                            recreateActivity()
                        }
                    }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            SonnerToast.error(getString(Res.string.backup_import_failed))
                        }
                    }
            }
        },
        onDismiss = { pendingImportUri = null },
    )

    ConfirmDialog(
        visible = showClearCacheConfirm,
        title = stringResource(Res.string.sure_to_clear),
        message = stringResource(Res.string.sure_to_clear_cache),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            showClearCacheConfirm = false
            coroutineScope.launch(ioDispatcher) {
                val success = clearCacheDir()
                cacheKey++
                if (success) SonnerToast.success(getString(Res.string.clear_success)) else SonnerToast.error(getString(Res.string.clear_failed))
            }
        },
        onDismiss = { showClearCacheConfirm = false },
    )

    if (showApplyDeepLinksDialog) {
        AlertDialog(
            onDismissRequest = { showApplyDeepLinksDialog = false },
            title = { Text(stringResource(Res.string.apply_deep_links)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.apply_deep_links_summary))
                    Text(stringResource(Res.string.apply_deep_links_tips))
                    Image(
                        // P6d-4：教学截图迁 composeResources/drawable（原 res/raw/apply_deep_links.png）
                        painter = painterResource(Res.drawable.apply_deep_links),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApplyDeepLinksDialog = false
                        if (supportsPerAppLinks()) {
                            openPerAppLinksSettings()
                        }
                    },
                ) {
                    Text(stringResource(Res.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyDeepLinksDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    ConfirmDialog(
        visible = showRestartConfirmDialog,
        title = stringResource(Res.string.attention),
        message = stringResource(Res.string.restart_needed),
        confirmText = stringResource(Res.string.confirm),
        dismissText = stringResource(Res.string.cancel),
        cancelable = false,
        onConfirm = {
            restartApp(killProcess = true)
        },
        onDismiss = { showRestartConfirmDialog = false },
    )

    // 阶段一④：伪装图标选择器原用 androidx.compose.ui.window.Dialog（JVM 专属，
    // commonMain 不可用），改 Material3 AlertDialog 承载——iOS 复用同一套。
    if (showLauncherPicker) {
        AlertDialog(
            onDismissRequest = { showLauncherPicker = false },
            title = {
                Text(
                    stringResource(Res.string.fake_app_icon),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    launcherItems.forEach { item ->
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    SettingsRepository.setLauncherIcon(item.alias)
                                    switchLauncherIcon(item.alias)
                                    SonnerToast.info(getString(Res.string.fake_icon_hint))
                                    showLauncherPicker = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(30.dp),
                                )
                                Text(item.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private data class LauncherItem(
    val name: String,
    val iconRes: DrawableResource,
    val alias: String,
)

private fun buildHomeSettingsUiState(
    videoLanguageLabels: Map<String, String>,
    followSystemLabel: String,
    launcherItems: List<LauncherItem>,
    cacheSummary: String,
    versionSummary: String,
): HomeSettingsUiState {
    val currentAlias = SettingsRepository.fakeLauncherIcon
    val currentItem = launcherItems.find { it.alias == currentAlias } ?: launcherItems.first()
    val videoLanguageLabel = videoLanguageLabels[SettingsRepository.videoLanguage]
        ?: SettingsRepository.videoLanguage
    val appLanguage = SettingsRepository.current.appLanguage
    val appLanguageLabel = when (appLanguage) {
        AppLanguage.SYSTEM -> followSystemLabel
        AppLanguage.ENGLISH -> "English"
        AppLanguage.CHINESE_SIMPLIFIED -> "简体中文"
        AppLanguage.CHINESE_TRADITIONAL -> "繁體中文"
    }
    val searchGridColumnsConfig = SettingsRepository.searchGridColumnsConfig
    val horizontalCardCountConfig = SettingsRepository.horizontalCardCountConfig
    return HomeSettingsUiState(
        videoLanguage = SettingsRepository.videoLanguage,
        videoLanguageLabel = videoLanguageLabel,
        defaultVideoQuality = SettingsRepository.videoQuality,
        darkMode = SettingsRepository.useDarkMode,
        appLanguage = appLanguage.preferenceValue,
        appLanguageLabel = appLanguageLabel,
        allowPipMode = SettingsRepository.current.allowPipMode,
        allowResumePlayback = SettingsRepository.allowResumePlayback,
        showPlayedIndicator = SettingsRepository.showPlayedIndicator,
        searchArtistIgnoreVideoType = SettingsRepository.searchArtistIgnoreVideoType,
        disableMobileDataWarning = SettingsRepository.disableMobileDataWarning,
        disablePredictiveBack = SettingsRepository.disablePredictiveBack,
        videoLandscapeLayoutStyle = SettingsRepository.videoLandscapeLayoutStyle.value,
        // 只存值，标签在 UI 层用 stringResource 算 —— 本函数是**非 composable** 的
        // buildHomeSettingsUiState，在这里调 stringResource 编译不过。
        navBarStyle = SettingsRepository.navBarStyle.value,
        disableComments = SettingsRepository.current.disableComments,
        collapseDownloadedGroup = SettingsRepository.collapseDownloadedGroup,
        themeId = SettingsRepository.current.themeId,
        amoled = SettingsRepository.current.amoled,
        hapticFeedbackEnabled = SettingsRepository.hapticFeedbackEnabled,
        funLoadingHints = SettingsRepository.funLoadingHints,
        secureMode = SettingsRepository.secureMode,
        fakeLauncherIconName = currentItem.name,
        cacheSummary = cacheSummary,
        versionSummary = versionSummary,
        contrastLevel = SettingsRepository.current.contrastLevel.value,
        searchGridColumnsSummary = listOf(
            searchGridColumnsConfig.compactColumns,
            searchGridColumnsConfig.mediumColumns,
            searchGridColumnsConfig.expandedColumns,
            searchGridColumnsConfig.largeColumns,
        ).joinToString(" / "),
        searchGridColumnsConfig = searchGridColumnsConfig,
        horizontalCardCountSummary = "${horizontalCardCountConfig.narrowCount}~${horizontalCardCountConfig.expandedCount}",
        horizontalCardCountConfig = horizontalCardCountConfig,
        checkInEnabled = SettingsRepository.isCheckInEnabled,
        homeCategoryItems = defaultHomeCategoryPreferenceItems,
        homeCategoryOrder = homeCategoryOrder,
        hiddenHomeCategoryKeys = hiddenHomeCategoryKeys,
        useAvHomeCategoryTitles = SettingsRepository.baseUrl == HanimeConstants.HANIME_URL[3],
        alwaysShowUpdateCard = SettingsRepository.alwaysShowUpdateCard,
        displayDensityPercent = SettingsRepository.displayDensity.percent,
    )
}
