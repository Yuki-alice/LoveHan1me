package lovehan1me.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.ui.model.HorizontalCardCountConfig
import lovehan1me.core.constant.HA1_GITHUB_URL
import lovehan1me.Res
import lovehan1me.video_language
import lovehan1me.video
import lovehan1me.version
import lovehan1me.user_terms_summary
import lovehan1me.user_terms
import lovehan1me.trigger_crash_summary
import lovehan1me.trigger_crash
import lovehan1me.traditional_chinese
import lovehan1me.temporarily_unavailable
import lovehan1me.tablet_mode_summary
import lovehan1me.tablet_mode
import lovehan1me.submit_bug_summary
import lovehan1me.submit_bug
import lovehan1me.simulated_update_data
import lovehan1me.simplified_chinese
import lovehan1me.show_played_indicator_summary
import lovehan1me.show_played_indicator
import lovehan1me.settings_layout_content
import lovehan1me.settings_data
import lovehan1me.select_fake_icon
import lovehan1me.secure_mode_summary
import lovehan1me.secure_mode
import lovehan1me.search_grid_columns_title
import lovehan1me.search_grid_columns_summary
import lovehan1me.search_artist_ignore_video_type_summary
import lovehan1me.search_artist_ignore_video_type
import lovehan1me.resume_playback_title
import lovehan1me.resume_playback_summary
import lovehan1me.project_repository
import lovehan1me.privacy
import lovehan1me.perception
import lovehan1me.open_source_license_summary
import lovehan1me.open_source_license
import lovehan1me.online_data_section
import lovehan1me.online_data_import_title
import lovehan1me.online_data_import_summary
import lovehan1me.online_data_export_title
import lovehan1me.online_data_export_summary
import lovehan1me.local_data_section
import lovehan1me.local_data_import_title
import lovehan1me.local_data_import_summary
import lovehan1me.local_data_export_title
import lovehan1me.local_data_export_summary
import lovehan1me.information
import lovehan1me.horizontal_card_count_title
import lovehan1me.horizontal_card_count_summary
import lovehan1me.home_category_layout_summary
import lovehan1me.home_category_layout
import lovehan1me.haptic_feedback_summary
import lovehan1me.haptic_feedback
import lovehan1me.fun_loading_hints_summary
import lovehan1me.fun_loading_hints
import lovehan1me.forum_summary
import lovehan1me.forum
import lovehan1me.follow_system
import lovehan1me.fake_app_icon
import lovehan1me.enable_check_in_feature_summary
import lovehan1me.enable_check_in_feature
import lovehan1me.dynamic_color_title
import lovehan1me.dynamic_color_summary
import lovehan1me.display_density
import lovehan1me.display
import lovehan1me.disable_predictive_back_title
import lovehan1me.disable_mobile_data_warning_summary
import lovehan1me.disable_mobile_data_warning
import lovehan1me.disable_comments_title
import lovehan1me.disable_comments_sum
import lovehan1me.developer_options
import lovehan1me.developer
import lovehan1me.default_video_quilty
import lovehan1me.collapse_downloaded_groups_summary
import lovehan1me.collapse_downloaded_groups
import lovehan1me.clear_cache
import lovehan1me.cache_section
import lovehan1me.backup_import_title
import lovehan1me.backup_import_summary
import lovehan1me.backup_export_title
import lovehan1me.backup_export_summary
import lovehan1me.apply_deep_links_summary
import lovehan1me.apply_deep_links
import lovehan1me.application_dpi
import lovehan1me.app_lang_sum
import lovehan1me.app_lang
import lovehan1me.always_show_update_card
import lovehan1me.allow_pip_title
import lovehan1me.allow_pip_disc
import lovehan1me.accent_color
import lovehan1me.ic_add_link
import lovehan1me.ic_admin_panel_settings
import lovehan1me.ic_bug_report
import lovehan1me.ic_clear_all
import lovehan1me.ic_comments
import lovehan1me.ic_download
import lovehan1me.ic_export
import lovehan1me.ic_ext_link
import lovehan1me.ic_fold
import lovehan1me.ic_forum
import lovehan1me.ic_fullscreen
import lovehan1me.ic_gavel
import lovehan1me.ic_grid
import lovehan1me.ic_history
import lovehan1me.ic_inbox_text
import lovehan1me.ic_info
import lovehan1me.ic_mask
import lovehan1me.ic_mobile_data
import lovehan1me.ic_mobile_vibrate
import lovehan1me.ic_palette
import lovehan1me.ic_person
import lovehan1me.ic_pet_supplies
import lovehan1me.ic_pip_mode
import lovehan1me.ic_prohibit
import lovehan1me.ic_row
import lovehan1me.ic_security_update
import lovehan1me.ic_setting_applock
import lovehan1me.ic_setting_lang
import lovehan1me.ic_simp_to_trad
import lovehan1me.ic_skip
import lovehan1me.ic_sort
import lovehan1me.ic_swipe_right
import lovehan1me.ic_tablet
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.ic_video_quilty
import lovehan1me.ui.model.SearchGridColumnsConfig
import lovehan1me.ui.component.ChoiceDialog
import lovehan1me.ui.component.SettingInfoItem
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.SettingSwitchItem
import lovehan1me.ui.component.SettingsAnimatedVisibility
import lovehan1me.ui.component.SettingsSegmentedGroup
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.adaptive.WindowWidthSizeClass
import lovehan1me.ui.adaptive.rememberContentWidthSizeClass
import lovehan1me.feature.settings.dialog.HomeCategoryLayoutDialog
import lovehan1me.feature.settings.dialog.HorizontalCardCountDialog
import lovehan1me.feature.settings.dialog.SearchGridColumnsDialog
import lovehan1me.feature.settings.model.HomeSettingsUiState
import lovehan1me.ui.theme.HanimeDefaults

enum class HomeSettingsPage {
    VideoPlayback,
    NetworkDownload,
    Appearance,
    InterfaceInteraction,
    DataPrivacy,
    DeveloperOptions,
    About,
}

private enum class HomeSettingsChoiceDialog {
    VideoLanguage,
    VideoQuality,
    AppLanguage,
    DisplayDensity,
}

/** Renders one settings category while keeping the existing preference callbacks intact. */
@Composable
fun HomeSettingsScreen(
    page: HomeSettingsPage,
    state: HomeSettingsUiState,
    isLoggedIn: Boolean,
    onVideoLanguageChange: (String) -> Unit,
    onVideoQualityChange: (String) -> Unit,
    onDarkModeChange: (String) -> Unit,
    onUseDynamicColorChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onFunLoadingHintsChange: (Boolean) -> Unit,
    onThemeAccentColorChange: (Int) -> Unit,
    onAppPaletteStyleChange: (Int) -> Unit,
    onAllowPipModeChange: (Boolean) -> Unit,
    onAllowResumePlaybackChange: (Boolean) -> Unit,
    onShowPlayedIndicatorChange: (Boolean) -> Unit,
    onSearchArtistIgnoreVideoTypeChange: (Boolean) -> Unit,
    onDisableMobileDataWarningChange: (Boolean) -> Unit,
    onDisablePredictiveBackChange: (Boolean) -> Unit,
    onTabletModeChange: (Boolean) -> Unit,
    onVideoLandscapeLayoutStyleChange: (String) -> Unit,
    onCheckInEnabledChange: (Boolean) -> Unit,
    onDisableCommentsChange: (Boolean) -> Unit,
    onCollapseDownloadedGroupChange: (Boolean) -> Unit,
    onSearchGridColumnsConfigChange: (SearchGridColumnsConfig) -> Unit,
    onHorizontalCardCountConfigChange: (HorizontalCardCountConfig) -> Unit,
    onSecureModeChange: (Boolean) -> Unit,
    onAlwaysShowUpdateCardChange: (Boolean) -> Unit,
    onDisplayDensityChange: (Int) -> Unit,
    onTriggerCrash: () -> Unit,
    onHomeCategoryPreferencesChange: (List<String>, Set<String>) -> Unit,
    hKeyframeSettingsContent: @Composable () -> Unit,
    networkSettingsContent: @Composable () -> Unit,
    downloadSettingsContent: @Composable () -> Unit,
    onOpenAppLanguageSettings: (String) -> Unit,
    onOpenApplyDeepLinks: () -> Unit,
    onOpenFakeLauncherIcon: () -> Unit,
    onOpenOpenSourceLicense: () -> Unit,
    onClearCache: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportLocalLists: () -> Unit,
    onImportLocalLists: () -> Unit,
    onExportOnlineLists: () -> Unit,
    onImportOnlineLists: () -> Unit,
    onSubmitBug: () -> Unit,
    onOpenForum: () -> Unit,
) {
    var activeDialog by rememberSaveable { mutableStateOf<HomeSettingsChoiceDialog?>(null) }
    var showSearchGridColumnsDialog by rememberSaveable { mutableStateOf(false) }
    var showHorizontalCardCountDialog by rememberSaveable { mutableStateOf(false) }
    var showHomeCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showUsageTerms by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    // 网格列数 / 横向卡片数量已改为按宽度自适应（不再被「平板模式」门控），因此宽屏
    // 也应暴露这两个设置——此前仅当「平板模式」开启才可见，属于自适应被开关门控时代的
    // 遗留。窄屏仍维持原样，避免给手机用户多加条目。
    val showDensitySettings =
        state.tabletMode || rememberContentWidthSizeClass() >= WindowWidthSizeClass.Expanded

    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.VideoLanguage,
        title = stringResource(Res.string.video_language),
        options = listOf(
            stringResource(Res.string.traditional_chinese) to "zht",
            stringResource(Res.string.simplified_chinese) to "zhs",
        ),
        selectedValue = state.videoLanguage,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onVideoLanguageChange(it)
        },
    )
    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.VideoQuality,
        title = stringResource(Res.string.default_video_quilty),
        options = listOf("480P" to "480P", "720P" to "720P", "1080P" to "1080P"),
        selectedValue = state.defaultVideoQuality,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onVideoQualityChange(it)
        },
    )
    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.AppLanguage,
        title = stringResource(Res.string.app_lang),
        options = listOf(
            stringResource(Res.string.follow_system) to "system",
            "English" to "en",
            stringResource(Res.string.simplified_chinese) to "zh-CN",
            stringResource(Res.string.traditional_chinese) to "zh-TW",
        ),
        selectedValue = state.appLanguage,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onOpenAppLanguageSettings(it)
        },
    )
    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.DisplayDensity,
        title = stringResource(Res.string.application_dpi),
        options = listOf("75%" to "75", "100%" to "100", "125%" to "125"),
        selectedValue = state.displayDensityPercent.toString(),
        onDismiss = { activeDialog = null },
        onSelect = { value ->
            activeDialog = null
            onDisplayDensityChange(value.toInt())
        },
    )

    if (showSearchGridColumnsDialog) {
        SearchGridColumnsDialog(
            initialConfig = state.searchGridColumnsConfig,
            onDismiss = { showSearchGridColumnsDialog = false },
            onConfirm = {
                showSearchGridColumnsDialog = false
                onSearchGridColumnsConfigChange(it)
            },
        )
    }
    if (showHorizontalCardCountDialog) {
        HorizontalCardCountDialog(
            initialConfig = state.horizontalCardCountConfig,
            onDismiss = { showHorizontalCardCountDialog = false },
            onConfirm = {
                showHorizontalCardCountDialog = false
                onHorizontalCardCountConfigChange(it)
            },
        )
    }
    if (showHomeCategoryDialog) {
        HomeCategoryLayoutDialog(
            state = state,
            onDismiss = { showHomeCategoryDialog = false },
            onConfirm = { order, hiddenKeys ->
                showHomeCategoryDialog = false
                onHomeCategoryPreferencesChange(order, hiddenKeys)
            },
        )
    }
    UsageTermsDialog(
        visible = showUsageTerms,
        onDismiss = { showUsageTerms = false },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize(),
        enableItemAnimation = false,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.small),
    ) {
        when (page) {
            HomeSettingsPage.VideoPlayback -> {
                item {
                    SettingsSection(stringResource(Res.string.video)) {
                        SettingNavigationItem(
                            title = stringResource(Res.string.video_language),
                            valueText = state.videoLanguageLabel,
                            iconRes = Res.drawable.ic_simp_to_trad,
                            onClick = { activeDialog = HomeSettingsChoiceDialog.VideoLanguage },
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.default_video_quilty),
                            valueText = state.defaultVideoQuality,
                            iconRes = Res.drawable.ic_video_quilty,
                            onClick = { activeDialog = HomeSettingsChoiceDialog.VideoQuality },
                        )
                        SettingSwitchItem(
                            title = stringResource(Res.string.allow_pip_title),
                            summary = stringResource(Res.string.allow_pip_disc),
                            checked = state.allowPipMode,
                            iconRes = Res.drawable.ic_pip_mode,
                            onCheckedChange = onAllowPipModeChange,
                        )
                        SettingSwitchItem(
                            title = stringResource(Res.string.resume_playback_title),
                            summary = stringResource(Res.string.resume_playback_summary),
                            checked = state.allowResumePlayback,
                            iconRes = Res.drawable.ic_skip,
                            onCheckedChange = onAllowResumePlaybackChange,
                        )
                        SettingSwitchItem(
                            title = stringResource(Res.string.show_played_indicator),
                            summary = stringResource(Res.string.show_played_indicator_summary),
                            checked = state.showPlayedIndicator,
                            iconRes = Res.drawable.ic_history,
                            onCheckedChange = onShowPlayedIndicatorChange,
                        )
                    }
                }
                item {
                    hKeyframeSettingsContent()
                }
            }

            HomeSettingsPage.NetworkDownload -> {
                item {
                    networkSettingsContent()
                }
                item {
                    SettingsSegmentedGroup {
                        SettingSwitchItem(
                            title = stringResource(Res.string.disable_mobile_data_warning),
                            summary = stringResource(Res.string.disable_mobile_data_warning_summary),
                            checked = state.disableMobileDataWarning,
                            iconRes = Res.drawable.ic_mobile_data,
                            onCheckedChange = onDisableMobileDataWarningChange,
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.apply_deep_links),
                            summary = stringResource(Res.string.apply_deep_links_summary),
                            iconRes = Res.drawable.ic_add_link,
                            onClick = onOpenApplyDeepLinks,
                        )
                    }
                }
                item {
                    downloadSettingsContent()
                }
                item {
                    SettingsSegmentedGroup {
                        SettingSwitchItem(
                            title = stringResource(Res.string.collapse_downloaded_groups),
                            summary = stringResource(Res.string.collapse_downloaded_groups_summary),
                            checked = state.collapseDownloadedGroup,
                            iconRes = Res.drawable.ic_fold,
                            onCheckedChange = onCollapseDownloadedGroupChange,
                        )
                    }
                }
            }

            HomeSettingsPage.Appearance -> {
                item {
                    SettingsSection(stringResource(Res.string.accent_color)) {
                        SettingSwitchItem(
                            title = stringResource(Res.string.dynamic_color_title),
                            summary = stringResource(Res.string.dynamic_color_summary),
                            checked = state.useDynamicColor,
                            enabled = state.dynamicColorEnabled,
                            iconRes = Res.drawable.ic_palette,
                            onCheckedChange = onUseDynamicColorChange,
                        )
                        SettingsAnimatedVisibility(
                            visible = !state.useDynamicColor || !state.dynamicColorEnabled,
                        ) {
                            ThemeAccentColorPicker(
                                selectedId = state.themeAccentColorId,
                                onSelect = onThemeAccentColorChange,
                            )
                        }
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.display)) {
                        DarkModePicker(
                            selectedValue = state.darkMode,
                            onSelect = onDarkModeChange,
                        )
                        AppPalettePicker(
                            selectedId = state.appPaletteStyleId,
                            accentColorId = state.themeAccentColorId,
                            dynamicColor = state.useDynamicColor,
                            darkMode = state.darkMode,
                            onSelect = onAppPaletteStyleChange,
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.app_lang)) {
                        SettingNavigationItem(
                            title = stringResource(Res.string.app_lang),
                            summary = stringResource(Res.string.app_lang_sum),
                            valueText = state.appLanguageLabel,
                            iconRes = Res.drawable.ic_setting_lang,
                            onClick = { activeDialog = HomeSettingsChoiceDialog.AppLanguage },
                        )
                    }
                }
            }

            HomeSettingsPage.InterfaceInteraction -> {
                item {
                    SettingsSection(stringResource(Res.string.perception)) {
                        SettingSwitchItem(
                            title = stringResource(Res.string.haptic_feedback),
                            summary = stringResource(Res.string.haptic_feedback_summary),
                            checked = state.hapticFeedbackEnabled,
                            iconRes = Res.drawable.ic_mobile_vibrate,
                            onCheckedChange = onHapticFeedbackChange,
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.settings_layout_content)) {
                        SettingNavigationItem(
                            title = stringResource(Res.string.horizontal_card_count_title),
                            summary = stringResource(Res.string.horizontal_card_count_summary),
                            valueText = state.horizontalCardCountSummary,
                            iconRes = Res.drawable.ic_row,
                            onClick = { showHorizontalCardCountDialog = true },
                        )
                        SettingSwitchItem(
                            title = stringResource(Res.string.search_artist_ignore_video_type),
                            summary = stringResource(Res.string.search_artist_ignore_video_type_summary),
                            checked = state.searchArtistIgnoreVideoType,
                            iconRes = Res.drawable.ic_prohibit,
                            onCheckedChange = onSearchArtistIgnoreVideoTypeChange,
                        )
                        SettingSwitchItem(
                            title = stringResource(Res.string.disable_predictive_back_title),
                            summary = stringResource(Res.string.temporarily_unavailable),
                            checked = state.disablePredictiveBack,
                            iconRes = Res.drawable.ic_swipe_right,
                            onCheckedChange = onDisablePredictiveBackChange,
                            enabled = false,
                        )
                        SettingSwitchItem(
                            title = stringResource(Res.string.tablet_mode),
                            summary = stringResource(Res.string.tablet_mode_summary),
                            checked = state.tabletMode,
                            iconRes = Res.drawable.ic_tablet,
                            onCheckedChange = onTabletModeChange,
                        )
                        SettingsAnimatedVisibility(visible = state.tabletMode) {
                            VideoLandscapeLayoutStylePicker(
                                selectedValue = state.videoLandscapeLayoutStyle,
                                onSelect = onVideoLandscapeLayoutStyleChange,
                            )
                        }
                        SettingSwitchItem(
                            title = stringResource(Res.string.enable_check_in_feature),
                            summary = stringResource(Res.string.enable_check_in_feature_summary),
                            checked = state.checkInEnabled,
                            iconRes = Res.drawable.ic_thumb_up_off_alt,
                            onCheckedChange = onCheckInEnabledChange,
                        )
                        SettingSwitchItem(
                            title = stringResource(Res.string.fun_loading_hints),
                            summary = stringResource(Res.string.fun_loading_hints_summary),
                            checked = state.funLoadingHints,
                            iconRes = Res.drawable.ic_pet_supplies,
                            onCheckedChange = onFunLoadingHintsChange,
                        )
                        SettingsAnimatedVisibility(visible = showDensitySettings) {
                            SettingNavigationItem(
                                title = stringResource(Res.string.search_grid_columns_title),
                                summary = stringResource(Res.string.search_grid_columns_summary),
                                valueText = state.searchGridColumnsSummary,
                                iconRes = Res.drawable.ic_grid,
                                onClick = { showSearchGridColumnsDialog = true },
                            )
                        }
                        SettingNavigationItem(
                            title = stringResource(Res.string.home_category_layout),
                            summary = stringResource(Res.string.home_category_layout_summary,
                                state.homeCategoryItems.size - state.hiddenHomeCategoryKeys.size,
                                state.homeCategoryItems.size,
                            ),
                            iconRes = Res.drawable.ic_sort,
                            onClick = { showHomeCategoryDialog = true },
                        )
                    }
                }
            }

            HomeSettingsPage.DataPrivacy -> {
                item {
                    SettingsSection(stringResource(Res.string.privacy)) {
                        SettingSwitchItem(
                            title = stringResource(Res.string.secure_mode),
                            summary = stringResource(Res.string.secure_mode_summary),
                            checked = state.secureMode,
                            iconRes = Res.drawable.ic_admin_panel_settings,
                            onCheckedChange = onSecureModeChange,
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.fake_app_icon),
                            summary = stringResource(Res.string.select_fake_icon),
                            valueText = state.fakeLauncherIconName,
                            iconRes = Res.drawable.ic_mask,
                            onClick = onOpenFakeLauncherIcon,
                        )
                        SettingSwitchItem(
                            title = stringResource(Res.string.disable_comments_title),
                            summary = stringResource(Res.string.disable_comments_sum),
                            checked = state.disableComments,
                            iconRes = Res.drawable.ic_comments,
                            onCheckedChange = onDisableCommentsChange,
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.settings_data)) {
                        SettingNavigationItem(
                            title = stringResource(Res.string.backup_export_title),
                            summary = stringResource(Res.string.backup_export_summary),
                            iconRes = Res.drawable.ic_export,
                            onClick = onExportBackup,
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.backup_import_title),
                            summary = stringResource(Res.string.backup_import_summary),
                            iconRes = Res.drawable.ic_download,
                            onClick = onImportBackup,
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.cache_section)) {
                        SettingNavigationItem(
                            title = stringResource(Res.string.clear_cache),
                            summary = state.cacheSummary,
                            iconRes = Res.drawable.ic_clear_all,
                            onClick = onClearCache,
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.local_data_section)) {
                        SettingNavigationItem(
                            title = stringResource(Res.string.local_data_export_title),
                            summary = stringResource(Res.string.local_data_export_summary),
                            iconRes = Res.drawable.ic_export,
                            onClick = onExportLocalLists,
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.local_data_import_title),
                            summary = stringResource(Res.string.local_data_import_summary),
                            iconRes = Res.drawable.ic_download,
                            onClick = onImportLocalLists,
                        )
                    }
                }
                if (isLoggedIn) {
                    item {
                        SettingsSection(stringResource(Res.string.online_data_section)) {
                            SettingNavigationItem(
                                title = stringResource(Res.string.online_data_export_title),
                                summary = stringResource(Res.string.online_data_export_summary),
                                iconRes = Res.drawable.ic_export,
                                onClick = onExportOnlineLists,
                            )
                            SettingNavigationItem(
                                title = stringResource(Res.string.online_data_import_title),
                                summary = stringResource(Res.string.online_data_import_summary),
                                iconRes = Res.drawable.ic_download,
                                onClick = onImportOnlineLists,
                            )
                        }
                    }
                }
            }

            HomeSettingsPage.DeveloperOptions -> {
                item {
                    SettingsSection(stringResource(Res.string.developer_options)) {
                        SettingSwitchItem(
                            title = stringResource(Res.string.always_show_update_card),
                            summary = stringResource(Res.string.simulated_update_data),
                            checked = state.alwaysShowUpdateCard,
                            iconRes = Res.drawable.ic_security_update,
                            onCheckedChange = onAlwaysShowUpdateCardChange,
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.application_dpi),
                            summary = stringResource(Res.string.display_density),
                            valueText = "${state.displayDensityPercent}%",
                            iconRes = Res.drawable.ic_fullscreen,
                            onClick = { activeDialog = HomeSettingsChoiceDialog.DisplayDensity },
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.trigger_crash),
                            summary = stringResource(Res.string.trigger_crash_summary),
                            iconRes = Res.drawable.ic_bug_report,
                            onClick = onTriggerCrash,
                        )
                    }
                }
            }

            HomeSettingsPage.About -> {
                item {
                    SettingsSection(stringResource(Res.string.information)) {
                        SettingInfoItem(
                            title = stringResource(Res.string.version),
                            summary = state.versionSummary,
                            iconRes = Res.drawable.ic_info,
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.developer),
                            summary = "@daisukiKaffuChino",
                            iconRes = Res.drawable.ic_person,
                            onClick = { uriHandler.openUri("https://github.com/daisukiKaffuChino") },
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.user_terms),
                            summary = stringResource(Res.string.user_terms_summary),
                            iconRes = Res.drawable.ic_inbox_text,
                            onClick = { showUsageTerms = true },
                        )
                    }
                }
                item {
                    SettingsSection("GitHub") {
                        SettingNavigationItem(
                            title = stringResource(Res.string.project_repository),
                            summary = "daisukiKaffuChino/Han1meViewer",
                            iconRes = Res.drawable.ic_ext_link,
                            onClick = { uriHandler.openUri(HA1_GITHUB_URL) },
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.submit_bug),
                            summary = stringResource(Res.string.submit_bug_summary),
                            iconRes = Res.drawable.ic_bug_report,
                            onClick = onSubmitBug,
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.forum),
                            summary = stringResource(Res.string.forum_summary),
                            iconRes = Res.drawable.ic_forum,
                            onClick = onOpenForum,
                        )
                        SettingNavigationItem(
                            title = stringResource(Res.string.open_source_license),
                            summary = stringResource(Res.string.open_source_license_summary),
                            iconRes = Res.drawable.ic_gavel,
                            onClick = onOpenOpenSourceLicense,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        SettingsSegmentedGroup(content = content)
    }
}
private fun previewHomeSettingsState() = HomeSettingsUiState(
    videoLanguage = "zhs",
    videoLanguageLabel = "Simplified Chinese",
    defaultVideoQuality = "1080P",
    darkMode = "follow_system",
    appLanguage = "system",
    appLanguageLabel = "Follow system",
    allowPipMode = true,
    allowResumePlayback = true,
    showPlayedIndicator = true,
    searchArtistIgnoreVideoType = false,
    disableMobileDataWarning = false,
    disablePredictiveBack = false,
    tabletMode = false,
    videoLandscapeLayoutStyle = "classic",
    disableComments = false,
    collapseDownloadedGroup = false,
    useDynamicColor = false,
    hapticFeedbackEnabled = false,
    funLoadingHints = true,
    secureMode = false,
    fakeLauncherIconName = "Han1meViewer",
    cacheSummary = "12 MB",
    versionSummary = "v26.1.0",
    dynamicColorEnabled = true,
    themeAccentColorId = 0,
    appPaletteStyleId = 1,
    searchGridColumnsSummary = "2 / 3 / 4 / 5",
    searchGridColumnsConfig = SearchGridColumnsConfig(),
    horizontalCardCountSummary = "1.5 / 2.1 / 4.1 / 5.1",
    horizontalCardCountConfig = HorizontalCardCountConfig(),
    checkInEnabled = true,
    homeCategoryItems = emptyList(),
    homeCategoryOrder = emptyList(),
    hiddenHomeCategoryKeys = emptySet(),
    useAvHomeCategoryTitles = false,
    alwaysShowUpdateCard = false,
    displayDensityPercent = 100,
)
