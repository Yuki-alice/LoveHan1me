package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.daisukikaffuchino.han1meviewer.HanimeConstants
import io.github.daisukikaffuchino.han1meviewer.HA1_GITHUB_FORUM_URL
import io.github.daisukikaffuchino.han1meviewer.HA1_GITHUB_ISSUE_URL
import io.github.daisukikaffuchino.han1meviewer.logic.BackupManager
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.platform.appVersionDisplay
import io.github.daisukikaffuchino.han1meviewer.logic.platform.applyAppLanguage
import io.github.daisukikaffuchino.han1meviewer.logic.platform.applySecureMode
import io.github.daisukikaffuchino.han1meviewer.logic.platform.clearCacheDir
import io.github.daisukikaffuchino.han1meviewer.logic.platform.getCacheDirSize
import io.github.daisukikaffuchino.han1meviewer.logic.platform.isDeviceSecure
import io.github.daisukikaffuchino.han1meviewer.logic.platform.isPipPermissionGranted
import io.github.daisukikaffuchino.han1meviewer.logic.platform.openPipPermissionSettings
import io.github.daisukikaffuchino.han1meviewer.logic.platform.recreateActivity
import io.github.daisukikaffuchino.han1meviewer.logic.platform.readBackupText
import io.github.daisukikaffuchino.han1meviewer.logic.platform.restartApp
import io.github.daisukikaffuchino.han1meviewer.logic.platform.supportsPerAppLinks
import io.github.daisukikaffuchino.han1meviewer.logic.platform.switchLauncherIcon
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.updateCheckInWidget
import io.github.daisukikaffuchino.han1meviewer.logic.platform.writeBackupText
import io.github.daisukikaffuchino.han1meviewer.logic.currentEpochMillis
import io.github.daisukikaffuchino.han1meviewer.logic.platform.openPerAppLinksSettings
import io.github.daisukikaffuchino.han1meviewer.logic.platform.rememberBackupExportLauncher
import io.github.daisukikaffuchino.han1meviewer.logic.platform.rememberBackupImportLauncher
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.updateCheckInWidget
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.action_app_open_by_default_settings_not_support
import io.github.daisukikaffuchino.han1meviewer.backup_export_failed
import io.github.daisukikaffuchino.han1meviewer.backup_export_success
import io.github.daisukikaffuchino.han1meviewer.backup_import_failed
import io.github.daisukikaffuchino.han1meviewer.backup_import_success
import io.github.daisukikaffuchino.han1meviewer.cache_empty
import io.github.daisukikaffuchino.han1meviewer.clear_failed
import io.github.daisukikaffuchino.han1meviewer.clear_success
import io.github.daisukikaffuchino.han1meviewer.current_version
import io.github.daisukikaffuchino.han1meviewer.fake_icon_hint
import io.github.daisukikaffuchino.han1meviewer.follow_system
import io.github.daisukikaffuchino.han1meviewer.local_data_export_failed
import io.github.daisukikaffuchino.han1meviewer.local_data_export_success
import io.github.daisukikaffuchino.han1meviewer.local_data_import_failed
import io.github.daisukikaffuchino.han1meviewer.local_data_import_success
import io.github.daisukikaffuchino.han1meviewer.login_first
import io.github.daisukikaffuchino.han1meviewer.not_set_sys_lock
import io.github.daisukikaffuchino.han1meviewer.online_data_export_failed
import io.github.daisukikaffuchino.han1meviewer.online_data_export_success
import io.github.daisukikaffuchino.han1meviewer.online_data_import_failed
import io.github.daisukikaffuchino.han1meviewer.online_data_import_success
import io.github.daisukikaffuchino.han1meviewer.request_pip_alert
import io.github.daisukikaffuchino.han1meviewer.simplified_chinese
import io.github.daisukikaffuchino.han1meviewer.success_value
import io.github.daisukikaffuchino.han1meviewer.sure_to_clear_cache
import io.github.daisukikaffuchino.han1meviewer.traditional_chinese
import io.github.daisukikaffuchino.han1meviewer.sure_to_clear
import io.github.daisukikaffuchino.han1meviewer.restart_needed
import io.github.daisukikaffuchino.han1meviewer.hanime_app_name
import io.github.daisukikaffuchino.han1meviewer.go_to_settings
import io.github.daisukikaffuchino.han1meviewer.fake_app_icon
import io.github.daisukikaffuchino.han1meviewer.confirm
import io.github.daisukikaffuchino.han1meviewer.cancel
import io.github.daisukikaffuchino.han1meviewer.backup_import_title
import io.github.daisukikaffuchino.han1meviewer.backup_import_confirm_message
import io.github.daisukikaffuchino.han1meviewer.attention
import io.github.daisukikaffuchino.han1meviewer.apply_deep_links_tips
import io.github.daisukikaffuchino.han1meviewer.apply_deep_links_summary
import io.github.daisukikaffuchino.han1meviewer.apply_deep_links
import io.github.daisukikaffuchino.han1meviewer.app_name_fake_xxt
import io.github.daisukikaffuchino.han1meviewer.app_name_fake_cornhub
import io.github.daisukikaffuchino.han1meviewer.app_name_fake_calc
import io.github.daisukikaffuchino.han1meviewer.ic_launcher_xxt
import io.github.daisukikaffuchino.han1meviewer.ic_launcher_new
import io.github.daisukikaffuchino.han1meviewer.ic_launcher_cornhub
import io.github.daisukikaffuchino.han1meviewer.ic_launcher_calc
import io.github.daisukikaffuchino.han1meviewer.logic.LocalListRepository
import io.github.daisukikaffuchino.han1meviewer.logic.OnlineListsBackup
import io.github.daisukikaffuchino.han1meviewer.logic.model.AppLanguage
import io.github.daisukikaffuchino.han1meviewer.logic.model.DisplayDensity
import io.github.daisukikaffuchino.han1meviewer.logic.model.PaletteStyle
import io.github.daisukikaffuchino.han1meviewer.logic.model.ThemeAccent
import io.github.daisukikaffuchino.han1meviewer.logic.model.ThemeMode
import io.github.daisukikaffuchino.han1meviewer.logic.model.VideoLandscapeLayoutStyle
import io.github.daisukikaffuchino.han1meviewer.ui.component.ConfirmDialog
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.HomeSettingsPage
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.HomeSettingsScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.model.HomeSettingsUiState
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.defaultHomeCategoryPreferenceItems
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.hiddenHomeCategoryKeys
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.homeCategoryOrder
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.saveHomeCategoryPreferences
import io.github.daisukikaffuchino.utils.SonnerToast
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
    // P6d-4E：下载设置页依赖 :app 的 SAF（SafFileManager/WorkManager），由 Android 壳注入；
    // 桌面/iOS 下载目录能力随 P7 提供，默认空占位
    downloadSettingsContent: @Composable () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    // P6d-3-C3：回调内非 suspend，固定串在此预解析
    val loginFirstText = stringResource(Res.string.login_first)
    val requestPipText = stringResource(Res.string.request_pip_alert)
    val notSetLockText = stringResource(Res.string.not_set_sys_lock)
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
        coroutineScope.launch(Dispatchers.IO) {
            runCatching { BackupManager.exportTo(uri) }
                .onSuccess { SonnerToast.success(getString(Res.string.backup_export_success)) }
                .onFailure { SonnerToast.error(getString(Res.string.backup_export_failed)) }
        }
    }
    val importLauncher = rememberBackupImportLauncher { pendingImportUri = it }
    val localListsExportLauncher = rememberBackupExportLauncher { uri ->
        uri ?: return@rememberBackupExportLauncher
        coroutineScope.launch(Dispatchers.IO) {
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
        coroutineScope.launch(Dispatchers.IO) {
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
        coroutineScope.launch(Dispatchers.IO) {
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
        coroutineScope.launch(Dispatchers.IO) {
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
                alias = "io.github.daisukikaffuchino.han1meviewer.LauncherAliasDefault",
            ),
            LauncherItem(
                name = fakeNameCalc,
                iconRes = Res.drawable.ic_launcher_calc,
                alias = "io.github.daisukikaffuchino.han1meviewer.LauncherFakeCalc",
            ),
            LauncherItem(
                name = fakeNameCornhub,
                iconRes = Res.drawable.ic_launcher_cornhub,
                alias = "io.github.daisukikaffuchino.han1meviewer.LauncherFakeCornhub",
            ),
            LauncherItem(
                name = fakeNameXXT,
                iconRes = Res.drawable.ic_launcher_xxt,
                alias = "io.github.daisukikaffuchino.han1meviewer.LauncherFakeXxt",
            ),
        )
    }

    var cacheSummary by remember { mutableStateOf("") }

    LaunchedEffect(cacheKey) {
        cacheSummary = withContext(Dispatchers.IO) {
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
        onVideoLanguageChange = { value ->
            if (value != SettingsRepository.videoLanguage) {
                coroutineScope.launch {
                    SettingsRepository.update { it.copy(videoLanguage = value) }
                    showRestartConfirmDialog = true
                }
            }
        },
        onVideoQualityChange = { value ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(videoQuality = value) }
                SonnerToast.success(getString(Res.string.success_value, value))
            }
        },
        onDarkModeChange = { value ->
            if (value != SettingsRepository.useDarkMode) {
                coroutineScope.launch { SettingsRepository.setThemeMode(ThemeMode.fromValue(value)) }
            }
        },
        onUseDynamicColorChange = { enabled ->
            coroutineScope.launch { SettingsRepository.setDynamicColor(enabled) }
        },
        onHapticFeedbackChange = { enabled ->
            coroutineScope.launch { SettingsRepository.setHapticFeedback(enabled) }
        },
        onFunLoadingHintsChange = { enabled ->
            coroutineScope.launch { SettingsRepository.update { it.copy(funLoadingHints = enabled) } }
        },
        onThemeAccentColorChange = { id ->
            coroutineScope.launch { SettingsRepository.setThemeAccent(ThemeAccent.fromId(id)) }
        },
        onAppPaletteStyleChange = { id ->
            coroutineScope.launch { SettingsRepository.setPaletteStyle(PaletteStyle.fromId(id)) }
        },
        onAllowPipModeChange = { enabled ->
            if (enabled && !isPipPermissionGranted()) {
                SonnerToast.warning(requestPipText)
                openPipPermissionSettings()
                coroutineScope.launch { SettingsRepository.update { it.copy(allowPipMode = false) } }
                return@HomeSettingsScreen
            }
            coroutineScope.launch { SettingsRepository.update { it.copy(allowPipMode = enabled) } }
        },
        onAllowResumePlaybackChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(allowResumePlayback = it) } }
        },
        onShowPlayedIndicatorChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(showPlayedIndicator = it) } }
        },
        onSearchArtistIgnoreVideoTypeChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(searchArtistIgnoreVideoType = it) } }
        },
        onDisableMobileDataWarningChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(disableMobileDataWarning = it) } }
        },
        onDisablePredictiveBackChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(disablePredictiveBack = it) } }
        },
        onTabletModeChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(tabletMode = it) } }
        },
        onVideoLandscapeLayoutStyleChange = { value ->
            coroutineScope.launch {
                SettingsRepository.setVideoLandscapeLayoutStyle(
                    VideoLandscapeLayoutStyle.fromValue(value)
                )
            }
        },
        onCheckInEnabledChange = {
            coroutineScope.launch {
                SettingsRepository.setCheckInEnabled(it)
                updateCheckInWidget()
            }
        },
        onDisableCommentsChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(disableComments = it) } }
        },
        onCollapseDownloadedGroupChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(collapseDownloadedGroup = it) } }
        },
        onSearchGridColumnsConfigChange = { config ->
            coroutineScope.launch { SettingsRepository.update { it.copy(searchGridColumnsCompact = config.compactColumns, searchGridColumnsMedium = config.mediumColumns, searchGridColumnsExpanded = config.expandedColumns, searchGridColumnsLarge = config.largeColumns) } }
        },
        onHorizontalCardCountConfigChange = { config ->
            coroutineScope.launch { SettingsRepository.update { it.copy(horizontalCardCountNarrow = config.narrowCount, horizontalCardCountCompact = config.compactCount, horizontalCardCountMedium = config.mediumCount, horizontalCardCountExpanded = config.expandedCount) } }
        },
        onHomeCategoryPreferencesChange = { order, hiddenKeys ->
            coroutineScope.launch { saveHomeCategoryPreferences(order, hiddenKeys) }
        },
        onUseLockScreenChange = { value ->
            if (value) {
                if (!isDeviceSecure()) {
                    SonnerToast.warning(notSetLockText)
                    return@HomeSettingsScreen
                }
            }
            coroutineScope.launch { SettingsRepository.update { it.copy(useLockScreen = value) } }
        },
        onSecureModeChange = { enabled ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(secureMode = enabled) }
                applySecureMode(enabled)
            }
        },
        onAlwaysShowUpdateCardChange = { enabled ->
            coroutineScope.launch { SettingsRepository.setAlwaysShowUpdateCard(enabled) }
        },
        onDisplayDensityChange = { percent ->
            coroutineScope.launch {
                SettingsRepository.setDisplayDensity(DisplayDensity.fromPercent(percent))
            }
        },
        onTriggerCrash = {
            throw RuntimeException("Crash triggered from developer options")
        },
        hKeyframeSettingsContent = {
            HKeyframeSettingsRouteScreen(
                onNavigateToHKeyframes = onNavigateToHKeyframes,
                onNavigateToSharedHKeyframes = onNavigateToSharedHKeyframes,
                embedded = true,
            )
        },
        networkSettingsContent = { NetworkSettingsRouteScreen(embedded = true) },
        downloadSettingsContent = downloadSettingsContent,
        onOpenAppLanguageSettings = { value ->
            val language = AppLanguage.fromPreference(value)
            if (SettingsRepository.current.appLanguage != language) {
                coroutineScope.launch {
                    SettingsRepository.setLanguage(language)
                    applyAppLanguage(language)
                }
            }
        },
        onOpenApplyDeepLinks = {
            if (!supportsPerAppLinks()) {
                SonnerToast.warning(deepLinksWarnText)
            } else {
                showApplyDeepLinksDialog = true
            }
        },
        onOpenFakeLauncherIcon = { showLauncherPicker = true },
        onOpenOpenSourceLicense = onNavigateToOpenSourceLicenses,
        onClearCache = {
            coroutineScope.launch {
                if (getCacheDirSize() == 0L) SonnerToast.info(cacheEmptyText)
                else showClearCacheConfirm = true
            }
        },
        onExportBackup = {
            exportLauncher("Han1meViewer-backup-${currentEpochMillis()}.json")
        },
        onImportBackup = {
            importLauncher()
        },
        onExportLocalLists = {
            localListsExportLauncher(
                "Han1meViewer-local-lists-${currentEpochMillis()}.json"
            )
        },
        onImportLocalLists = {
            localListsImportLauncher()
        },
        onExportOnlineLists = {
            if (!SettingsRepository.isAlreadyLogin) {
                SonnerToast.warning(loginFirstText)
                return@HomeSettingsScreen
            }
            onlineListsExportLauncher(
                "Han1meViewer-online-lists-${currentEpochMillis()}.json"
            )
        },
        onImportOnlineLists = {
            if (!SettingsRepository.isAlreadyLogin) {
                SonnerToast.warning(loginFirstText)
                return@HomeSettingsScreen
            }
            onlineListsImportLauncher()
        },
        onSubmitBug = { uriHandler.openUri(HA1_GITHUB_ISSUE_URL) },
        onOpenForum = { uriHandler.openUri(HA1_GITHUB_FORUM_URL) },
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
            coroutineScope.launch(Dispatchers.IO) {
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
            coroutineScope.launch(Dispatchers.IO) {
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

    if (showLauncherPicker) {
        Dialog(
            onDismissRequest = { showLauncherPicker = false },
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(Res.string.fake_app_icon),
                        style = MaterialTheme.typography.titleLarge,
                    )
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
            }
        }
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
        tabletMode = SettingsRepository.tabletMode,
        videoLandscapeLayoutStyle = SettingsRepository.videoLandscapeLayoutStyle.value,
        disableComments = SettingsRepository.current.disableComments,
        collapseDownloadedGroup = SettingsRepository.collapseDownloadedGroup,
        useDynamicColor = SettingsRepository.useDynamicColor,
        hapticFeedbackEnabled = SettingsRepository.hapticFeedbackEnabled,
        funLoadingHints = SettingsRepository.funLoadingHints,
        useLockScreen = SettingsRepository.current.useLockScreen,
        secureMode = SettingsRepository.secureMode,
        fakeLauncherIconName = currentItem.name,
        cacheSummary = cacheSummary,
        versionSummary = versionSummary,
        dynamicColorEnabled = supportsPerAppLinks(),
        themeAccentColorId = SettingsRepository.current.themeAccent.id,
        appPaletteStyleId = SettingsRepository.current.paletteStyle.id,
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
