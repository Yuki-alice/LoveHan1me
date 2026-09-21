package lovehan1me.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.core.constant.HA1_GITHUB_URL
import lovehan1me.core.constant.UPSTREAM_GITHUB_URL
import lovehan1me.core.platform.settingsPlatformCapabilities
import lovehan1me.Res
import lovehan1me.amoled_mode
import lovehan1me.amoled_mode_summary
import lovehan1me.ic_dark_mode
import lovehan1me.theme_board
import lovehan1me.video_language
import lovehan1me.contrast_level
import lovehan1me.contrast_level_summary
import lovehan1me.contrast_level_standard
import lovehan1me.contrast_level_medium
import lovehan1me.contrast_level_high
import lovehan1me.nav_bar_style
import lovehan1me.nav_bar_style_summary
import lovehan1me.nav_bar_style_standard
import lovehan1me.nav_bar_style_floating
import lovehan1me.video
import lovehan1me.version
import lovehan1me.user_terms_summary
import lovehan1me.user_terms
import lovehan1me.trigger_crash_summary
import lovehan1me.trigger_crash
import lovehan1me.theme_audit_summary
import lovehan1me.theme_audit
import lovehan1me.submit_bug_summary
import lovehan1me.submit_bug
import lovehan1me.simulated_update_data
import lovehan1me.show_played_indicator_summary
import lovehan1me.show_played_indicator
import lovehan1me.settings_layout_content
import lovehan1me.settings_data
import lovehan1me.secure_mode_summary
import lovehan1me.secure_mode
import lovehan1me.search_grid_columns_title
import lovehan1me.search_grid_columns_summary
import lovehan1me.search_artist_ignore_video_type_summary
import lovehan1me.search_artist_ignore_video_type
import lovehan1me.resume_playback_title
import lovehan1me.auto_play_on_enter_summary
import lovehan1me.auto_play_on_enter_title
import lovehan1me.ic_play_circle
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
import lovehan1me.home_category_layout_summary
import lovehan1me.home_category_layout
import lovehan1me.haptic_feedback_summary
import lovehan1me.haptic_feedback
import lovehan1me.fun_loading_hints_summary
import lovehan1me.fun_loading_hints
import lovehan1me.forum_summary
import lovehan1me.forum
import lovehan1me.follow_system
import lovehan1me.enable_check_in_feature_summary
import lovehan1me.enable_check_in_feature
import lovehan1me.display_density
import lovehan1me.display
import lovehan1me.disable_mobile_data_warning_summary
import lovehan1me.disable_mobile_data_warning
import lovehan1me.disable_comments_title
import lovehan1me.disable_comments_sum
import lovehan1me.developer_options
import lovehan1me.developer
import lovehan1me.upstream_project
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
import lovehan1me.ic_mobile_data
import lovehan1me.ic_mobile_vibrate
import lovehan1me.ic_palette
import lovehan1me.ic_person
import lovehan1me.ic_pet_supplies
import lovehan1me.ic_pip_mode
import lovehan1me.ic_prohibit
import lovehan1me.ic_row
import lovehan1me.ic_security_update
import lovehan1me.ic_setting_lang
import lovehan1me.ic_simp_to_trad
import lovehan1me.ic_skip
import lovehan1me.ic_sort
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.ic_video_quilty
import lovehan1me.ui.model.SearchGridColumnsConfig
import lovehan1me.ui.component.SettingInfoItem
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.SettingSwitchItem
import lovehan1me.ui.component.SettingsAnimatedVisibility
import lovehan1me.ui.component.SettingsSegmentedGroup
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.component.lazy.AnimatedLazyListScope
import lovehan1me.feature.settings.model.HomeSettingsUiState
import lovehan1me.feature.settings.model.HomeSettingsActions
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import lovehan1me.theme_board_summary
import lovehan1me.traditional_chinese
import lovehan1me.simplified_chinese
import lovehan1me.dynamic_color_title
import lovehan1me.dynamic_color_summary
import lovehan1me.accent_color
import lovehan1me.ic_mask
import lovehan1me.ic_setting_applock
import lovehan1me.ui.component.ChoiceDialog
import lovehan1me.ui.adaptive.WindowWidthSizeClass
import lovehan1me.ui.adaptive.rememberContentWidthSizeClass
import lovehan1me.feature.settings.dialog.HomeCategoryLayoutDialog
import lovehan1me.feature.settings.dialog.SearchGridColumnsDialog
import lovehan1me.ui.theme.HanimeDefaults


@Composable
internal fun SettingsSection(
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
internal fun previewHomeSettingsState() = HomeSettingsUiState(
    videoLanguage = "zhs",
    videoLanguageLabel = "Simplified Chinese",
    defaultVideoQuality = "1080P",
    darkMode = "follow_system",
    appLanguage = "system",
    appLanguageLabel = "Follow system",
    allowPipMode = true,
    allowResumePlayback = true,
    autoPlayOnEnter = false,
    showPlayedIndicator = true,
    searchArtistIgnoreVideoType = false,
    disableMobileDataWarning = false,
    navBarStyle = "standard",
    disableComments = false,
    collapseDownloadedGroup = false,
    themeId = "sakura",
    amoled = false,
    hapticFeedbackEnabled = false,
    funLoadingHints = true,
    secureMode = false,
    cacheSummary = "12 MB",
    versionSummary = "v26.1.0",
    contrastLevel = "standard",
    searchGridColumnsSummary = "2 / 3 / 4 / 5 / 6",
    searchGridColumnsConfig = SearchGridColumnsConfig(),
    checkInEnabled = true,
    homeCategoryItems = emptyList(),
    homeCategoryOrder = emptyList(),
    hiddenHomeCategoryKeys = emptySet(),
    useAvHomeCategoryTitles = false,
    alwaysShowUpdateCard = false,
    displayDensityPercent = 100,
    // 可见性照真实平台能力取：预览与真机一致（桌面预览就不会画出安全模式/触感反馈，
    // 因为它们在本平台确实没有实现）。改能力只需改 SettingsPlatformCapabilities，这里无需跟改。
    showSecureMode = previewCapabilities.secureMode,
    showHapticFeedback = previewCapabilities.hapticFeedback,
    showPipMode = previewCapabilities.pipMode,
    showMeteredDataWarning = previewCapabilities.meteredDataWarning,
)

private val previewCapabilities = settingsPlatformCapabilities()

/**
 * 设置分类「VideoPlayback」的分支内容（审计后可独立编辑；原先是 HomeSettingsScreen 里的一段）。
 *
 * 是 `AnimatedLazyListScope`（项目自定义的 LazyColumn 作用域）扩展而非 @Composable：原实现就是往同一个 LazyColumn 里 `item {}`，
 * 抽出时保持这个形状 —— 不改动滚动结构（性能不变），也让每个分类各自成块。
 */
internal fun AnimatedLazyListScope.videoPlaybackSection(
    state: HomeSettingsUiState,
    actions: HomeSettingsActions,
    openChoice: (HomeSettingsChoiceDialog) -> Unit,
) {
    item {
        SettingsSection(stringResource(Res.string.video)) {
            SettingNavigationItem(
                title = stringResource(Res.string.video_language),
                valueText = state.videoLanguageLabel,
                iconRes = Res.drawable.ic_simp_to_trad,
                onClick = { openChoice(HomeSettingsChoiceDialog.VideoLanguage) },
            )
            SettingNavigationItem(
                title = stringResource(Res.string.default_video_quilty),
                valueText = state.defaultVideoQuality,
                iconRes = Res.drawable.ic_video_quilty,
                onClick = { openChoice(HomeSettingsChoiceDialog.VideoQuality) },
            )
            // 画中画：仅移动端（阶段一决策⑨），桌面恒不进入 PiP → 桌面不画这一行
            //（此前桌面也显示，且 isPipPermissionGranted() 恒 true，开关能打开却永不生效）。
            if (state.showPipMode) {
                SettingSwitchItem(
                    title = stringResource(Res.string.allow_pip_title),
                    summary = stringResource(Res.string.allow_pip_disc),
                    checked = state.allowPipMode,
                    iconRes = Res.drawable.ic_pip_mode,
                    onCheckedChange = actions.allowPipModeChange,
                )
            }
            SettingSwitchItem(
                title = stringResource(Res.string.resume_playback_title),
                summary = stringResource(Res.string.resume_playback_summary),
                checked = state.allowResumePlayback,
                iconRes = Res.drawable.ic_skip,
                onCheckedChange = actions.allowResumePlaybackChange,
            )
            SettingSwitchItem(
                title = stringResource(Res.string.auto_play_on_enter_title),
                summary = stringResource(Res.string.auto_play_on_enter_summary),
                checked = state.autoPlayOnEnter,
                iconRes = Res.drawable.ic_play_circle,
                onCheckedChange = actions.autoPlayOnEnterChange,
            )
            SettingSwitchItem(
                title = stringResource(Res.string.show_played_indicator),
                summary = stringResource(Res.string.show_played_indicator_summary),
                checked = state.showPlayedIndicator,
                iconRes = Res.drawable.ic_history,
                onCheckedChange = actions.showPlayedIndicatorChange,
            )
        }
    }
}

/**
 * 设置分类「NetworkDownload」的分支内容（审计后可独立编辑；原先是 HomeSettingsScreen 里的一段）。
 *
 * 是 `AnimatedLazyListScope`（项目自定义的 LazyColumn 作用域）扩展而非 @Composable：原实现就是往同一个 LazyColumn 里 `item {}`，
 * 抽出时保持这个形状 —— 不改动滚动结构（性能不变），也让每个分类各自成块。
 */
internal fun AnimatedLazyListScope.networkDownloadSection(
    state: HomeSettingsUiState,
    actions: HomeSettingsActions,
    networkSettingsContent: @Composable () -> Unit,
    downloadSettingsContent: @Composable () -> Unit,
) {
    item {
        networkSettingsContent()
    }
    item {
        SettingsSegmentedGroup {
            // 移动数据提醒：桌面/iOS 的 isActiveNetworkMetered() 恒 false，守卫永不触发
            // → 不画这一行（留着只会误导"我关了提醒"，实际它本来就从不提醒）。
            if (state.showMeteredDataWarning) {
                SettingSwitchItem(
                    title = stringResource(Res.string.disable_mobile_data_warning),
                    summary = stringResource(Res.string.disable_mobile_data_warning_summary),
                    checked = state.disableMobileDataWarning,
                    iconRes = Res.drawable.ic_mobile_data,
                    onCheckedChange = actions.disableMobileDataWarningChange,
                )
            }
            SettingNavigationItem(
                title = stringResource(Res.string.apply_deep_links),
                summary = stringResource(Res.string.apply_deep_links_summary),
                iconRes = Res.drawable.ic_add_link,
                onClick = actions.openApplyDeepLinks,
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
                onCheckedChange = actions.collapseDownloadedGroupChange,
            )
        }
    }
}

/**
 * 设置分类「Appearance」的分支内容（审计后可独立编辑；原先是 HomeSettingsScreen 里的一段）。
 *
 * 是 `AnimatedLazyListScope`（项目自定义的 LazyColumn 作用域）扩展而非 @Composable：原实现就是往同一个 LazyColumn 里 `item {}`，
 * 抽出时保持这个形状 —— 不改动滚动结构（性能不变），也让每个分类各自成块。
 */
internal fun AnimatedLazyListScope.appearanceSection(
    state: HomeSettingsUiState,
    actions: HomeSettingsActions,
    openChoice: (HomeSettingsChoiceDialog) -> Unit,
) {
    item {
        SettingsSection(stringResource(Res.string.theme_board)) {
            ThemeBoardPicker(
                selectedId = state.themeId,
                darkMode = state.darkMode,
                contrastLevel = state.contrastLevel,
                onSelect = actions.themeIdChange,
            )
        }
    }
    item {
        SettingsSection(stringResource(Res.string.display)) {
            DarkModePicker(
                selectedValue = state.darkMode,
                onSelect = actions.darkModeChange,
                boardId = state.themeId,
                contrastLevel = state.contrastLevel,
            )
            SettingSwitchItem(
                title = stringResource(Res.string.amoled_mode),
                summary = stringResource(Res.string.amoled_mode_summary),
                checked = state.amoled,
                iconRes = Res.drawable.ic_dark_mode,
                onCheckedChange = actions.amoledChange,
            )
            // 审计 P2：动态对比度（默认档 = 原写死的 0.0，老用户视觉不变）
            SettingNavigationItem(
                title = stringResource(Res.string.contrast_level),
                summary = stringResource(Res.string.contrast_level_summary),
                valueText = stringResource(
                    when (state.contrastLevel) {
                        CONTRAST_LEVEL_MEDIUM -> Res.string.contrast_level_medium
                        CONTRAST_LEVEL_HIGH -> Res.string.contrast_level_high
                        else -> Res.string.contrast_level_standard
                    }
                ),
                iconRes = Res.drawable.ic_palette,
                onClick = { openChoice(HomeSettingsChoiceDialog.ContrastLevel) },
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
                onClick = { openChoice(HomeSettingsChoiceDialog.AppLanguage) },
            )
        }
    }
}

/**
 * 设置分类「InterfaceInteraction」的分支内容（审计后可独立编辑；原先是 HomeSettingsScreen 里的一段）。
 *
 * 是 `AnimatedLazyListScope`（项目自定义的 LazyColumn 作用域）扩展而非 @Composable：原实现就是往同一个 LazyColumn 里 `item {}`，
 * 抽出时保持这个形状 —— 不改动滚动结构（性能不变），也让每个分类各自成块。
 */
internal fun AnimatedLazyListScope.interfaceInteractionSection(
    state: HomeSettingsUiState,
    actions: HomeSettingsActions,
    openChoice: (HomeSettingsChoiceDialog) -> Unit,
    showDensitySettings: Boolean,
    openSearchGridColumns: () -> Unit,
    openHomeCategory: () -> Unit,
) {
    // 触感反馈：仅 Android 有真实实现（桌面/iOS 的 HapticFeedback 是空实现）。
    // 这一段只有这一行，所以隐藏要连整个 item 一起 —— 否则桌面上会留一张空卡片。
    if (state.showHapticFeedback) {
        item {
            SettingsSection(stringResource(Res.string.perception)) {
                SettingSwitchItem(
                    title = stringResource(Res.string.haptic_feedback),
                    summary = stringResource(Res.string.haptic_feedback_summary),
                    checked = state.hapticFeedbackEnabled,
                    iconRes = Res.drawable.ic_mobile_vibrate,
                    onCheckedChange = actions.hapticFeedbackChange,
                )
            }
        }
    }
    item {
        SettingsSection(stringResource(Res.string.settings_layout_content)) {
            SettingsAnimatedVisibility(visible = showDensitySettings) {
                SettingNavigationItem(
                    title = stringResource(Res.string.search_grid_columns_title),
                    summary = stringResource(Res.string.search_grid_columns_summary),
                    valueText = state.searchGridColumnsSummary,
                    iconRes = Res.drawable.ic_grid,
                    onClick = { openSearchGridColumns() },
                )
            }
            SettingSwitchItem(
                title = stringResource(Res.string.search_artist_ignore_video_type),
                summary = stringResource(Res.string.search_artist_ignore_video_type_summary),
                checked = state.searchArtistIgnoreVideoType,
                iconRes = Res.drawable.ic_prohibit,
                onCheckedChange = actions.searchArtistIgnoreVideoTypeChange,
            )
            // P0：「平板模式」开关已删除，「双栏长什么样」的布局风格选择也已随
            // 详情页宽屏重构（照 animeko 双栏定稿）移除——双栏形态恒定，不再可配。
            // P6：底栏形态。**只影响 Compact 宽度** —— Medium+ 走 NavigationRail，
            // 该设置对它们无意义，故摘要里写明作用域，避免用户在大屏上调了没反应。
            SettingNavigationItem(
                title = stringResource(Res.string.nav_bar_style),
                summary = stringResource(Res.string.nav_bar_style_summary),
                valueText = stringResource(
                    if (state.navBarStyle == NAV_BAR_STYLE_FLOATING) {
                        Res.string.nav_bar_style_floating
                    } else {
                        Res.string.nav_bar_style_standard
                    }
                ),
                iconRes = Res.drawable.ic_row,
                onClick = { openChoice(HomeSettingsChoiceDialog.NavBarStyle) },
            )
            SettingSwitchItem(
                title = stringResource(Res.string.enable_check_in_feature),
                summary = stringResource(Res.string.enable_check_in_feature_summary),
                checked = state.checkInEnabled,
                iconRes = Res.drawable.ic_thumb_up_off_alt,
                onCheckedChange = actions.checkInEnabledChange,
            )
            SettingSwitchItem(
                title = stringResource(Res.string.fun_loading_hints),
                summary = stringResource(Res.string.fun_loading_hints_summary),
                checked = state.funLoadingHints,
                iconRes = Res.drawable.ic_pet_supplies,
                onCheckedChange = actions.funLoadingHintsChange,
            )
            SettingNavigationItem(
                title = stringResource(Res.string.home_category_layout),
                summary = stringResource(Res.string.home_category_layout_summary,
                    state.homeCategoryItems.size - state.hiddenHomeCategoryKeys.size,
                    state.homeCategoryItems.size,
                ),
                iconRes = Res.drawable.ic_sort,
                onClick = { openHomeCategory() },
            )
        }
    }
}

/**
 * 设置分类「DataPrivacy」的分支内容（审计后可独立编辑；原先是 HomeSettingsScreen 里的一段）。
 *
 * 是 `AnimatedLazyListScope`（项目自定义的 LazyColumn 作用域）扩展而非 @Composable：原实现就是往同一个 LazyColumn 里 `item {}`，
 * 抽出时保持这个形状 —— 不改动滚动结构（性能不变），也让每个分类各自成块。
 */
internal fun AnimatedLazyListScope.dataPrivacySection(
    state: HomeSettingsUiState,
    actions: HomeSettingsActions,
    isLoggedIn: Boolean,
) {
    item {
        SettingsSection(stringResource(Res.string.privacy)) {
            // 安全模式：仅 Android 有真实实现（FLAG_SECURE 防截屏）；桌面/iOS 是空实现，
            // 开关只写 DataStore、无任何可见效果 → 不画这一行。
            if (state.showSecureMode) {
                SettingSwitchItem(
                    title = stringResource(Res.string.secure_mode),
                    summary = stringResource(Res.string.secure_mode_summary),
                    checked = state.secureMode,
                    iconRes = Res.drawable.ic_admin_panel_settings,
                    onCheckedChange = actions.secureModeChange,
                )
            }
            SettingSwitchItem(
                title = stringResource(Res.string.disable_comments_title),
                summary = stringResource(Res.string.disable_comments_sum),
                checked = state.disableComments,
                iconRes = Res.drawable.ic_comments,
                onCheckedChange = actions.disableCommentsChange,
            )
        }
    }
    item {
        SettingsSection(stringResource(Res.string.settings_data)) {
            SettingNavigationItem(
                title = stringResource(Res.string.backup_export_title),
                summary = stringResource(Res.string.backup_export_summary),
                iconRes = Res.drawable.ic_export,
                onClick = actions.exportBackup,
            )
            SettingNavigationItem(
                title = stringResource(Res.string.backup_import_title),
                summary = stringResource(Res.string.backup_import_summary),
                iconRes = Res.drawable.ic_download,
                onClick = actions.importBackup,
            )
        }
    }
    item {
        SettingsSection(stringResource(Res.string.cache_section)) {
            SettingNavigationItem(
                title = stringResource(Res.string.clear_cache),
                summary = state.cacheSummary,
                iconRes = Res.drawable.ic_clear_all,
                onClick = actions.clearCache,
            )
        }
    }
    item {
        SettingsSection(stringResource(Res.string.local_data_section)) {
            SettingNavigationItem(
                title = stringResource(Res.string.local_data_export_title),
                summary = stringResource(Res.string.local_data_export_summary),
                iconRes = Res.drawable.ic_export,
                onClick = actions.exportLocalLists,
            )
            SettingNavigationItem(
                title = stringResource(Res.string.local_data_import_title),
                summary = stringResource(Res.string.local_data_import_summary),
                iconRes = Res.drawable.ic_download,
                onClick = actions.importLocalLists,
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
                    onClick = actions.exportOnlineLists,
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.online_data_import_title),
                    summary = stringResource(Res.string.online_data_import_summary),
                    iconRes = Res.drawable.ic_download,
                    onClick = actions.importOnlineLists,
                )
            }
        }
    }
}

/**
 * 设置分类「DeveloperOptions」的分支内容（审计后可独立编辑；原先是 HomeSettingsScreen 里的一段）。
 *
 * 是 `AnimatedLazyListScope`（项目自定义的 LazyColumn 作用域）扩展而非 @Composable：原实现就是往同一个 LazyColumn 里 `item {}`，
 * 抽出时保持这个形状 —— 不改动滚动结构（性能不变），也让每个分类各自成块。
 */
internal fun AnimatedLazyListScope.developerOptionsSection(
    state: HomeSettingsUiState,
    actions: HomeSettingsActions,
    openChoice: (HomeSettingsChoiceDialog) -> Unit,
    onOpenThemeAudit: () -> Unit,
) {
    item {
        SettingsSection(stringResource(Res.string.developer_options)) {
            SettingSwitchItem(
                title = stringResource(Res.string.always_show_update_card),
                summary = stringResource(Res.string.simulated_update_data),
                checked = state.alwaysShowUpdateCard,
                iconRes = Res.drawable.ic_security_update,
                onCheckedChange = actions.alwaysShowUpdateCardChange,
            )
            SettingNavigationItem(
                title = stringResource(Res.string.application_dpi),
                summary = stringResource(Res.string.display_density),
                valueText = "${state.displayDensityPercent}%",
                iconRes = Res.drawable.ic_fullscreen,
                onClick = { openChoice(HomeSettingsChoiceDialog.DisplayDensity) },
            )
            SettingNavigationItem(
                title = stringResource(Res.string.trigger_crash),
                summary = stringResource(Res.string.trigger_crash_summary),
                iconRes = Res.drawable.ic_bug_report,
                onClick = actions.triggerCrash,
            )
            SettingNavigationItem(
                title = stringResource(Res.string.theme_audit),
                summary = stringResource(Res.string.theme_audit_summary),
                iconRes = Res.drawable.ic_palette,
                onClick = onOpenThemeAudit,
            )
        }
    }
}

/**
 * 设置分类「About」的分支内容（审计后可独立编辑；原先是 HomeSettingsScreen 里的一段）。
 *
 * 是 `AnimatedLazyListScope`（项目自定义的 LazyColumn 作用域）扩展而非 @Composable：原实现就是往同一个 LazyColumn 里 `item {}`，
 * 抽出时保持这个形状 —— 不改动滚动结构（性能不变），也让每个分类各自成块。
 */
internal fun AnimatedLazyListScope.aboutSection(
    state: HomeSettingsUiState,
    actions: HomeSettingsActions,
    uriHandler: UriHandler,
    openUsageTerms: () -> Unit,
) {
    item {
        SettingsSection(stringResource(Res.string.information)) {
            SettingInfoItem(
                title = stringResource(Res.string.version),
                summary = state.versionSummary,
                iconRes = Res.drawable.ic_info,
            )
            SettingNavigationItem(
                title = stringResource(Res.string.developer),
                summary = "@Yuki-alice",
                iconRes = Res.drawable.ic_person,
                onClick = { uriHandler.openUri("https://github.com/Yuki-alice") },
            )
            // M1：原作者条目从「开发者」移到致谢区（GPLv3 诚实归属，见 NOTICE）
            SettingNavigationItem(
                title = stringResource(Res.string.upstream_project),
                summary = "daisukiKaffuChino/Han1meViewer",
                iconRes = Res.drawable.ic_ext_link,
                onClick = { uriHandler.openUri(UPSTREAM_GITHUB_URL) },
            )
            SettingNavigationItem(
                title = stringResource(Res.string.user_terms),
                summary = stringResource(Res.string.user_terms_summary),
                iconRes = Res.drawable.ic_inbox_text,
                onClick = { openUsageTerms() },
            )
        }
    }
    item {
        SettingsSection("GitHub") {
            SettingNavigationItem(
                title = stringResource(Res.string.project_repository),
                summary = "Yuki-alice/LoveHan1me",
                iconRes = Res.drawable.ic_ext_link,
                onClick = { uriHandler.openUri(HA1_GITHUB_URL) },
            )
            SettingNavigationItem(
                title = stringResource(Res.string.submit_bug),
                summary = stringResource(Res.string.submit_bug_summary),
                iconRes = Res.drawable.ic_bug_report,
                onClick = actions.submitBug,
            )
            SettingNavigationItem(
                title = stringResource(Res.string.forum),
                summary = stringResource(Res.string.forum_summary),
                iconRes = Res.drawable.ic_forum,
                onClick = actions.openForum,
            )
            SettingNavigationItem(
                title = stringResource(Res.string.open_source_license),
                summary = stringResource(Res.string.open_source_license_summary),
                iconRes = Res.drawable.ic_gavel,
                onClick = actions.openOpenSourceLicense,
            )
        }
    }
}
