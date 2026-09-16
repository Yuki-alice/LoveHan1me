package lovehan1me.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.video_language
import lovehan1me.contrast_level
import lovehan1me.contrast_level_standard
import lovehan1me.contrast_level_medium
import lovehan1me.contrast_level_high
import lovehan1me.nav_bar_style
import lovehan1me.nav_bar_style_standard
import lovehan1me.nav_bar_style_floating
import lovehan1me.traditional_chinese
import lovehan1me.simplified_chinese
import lovehan1me.follow_system
import lovehan1me.default_video_quilty
import lovehan1me.application_dpi
import lovehan1me.app_lang
import lovehan1me.ui.component.ChoiceDialog
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.adaptive.WindowWidthSizeClass
import lovehan1me.ui.adaptive.rememberContentWidthSizeClass
import lovehan1me.feature.settings.dialog.HomeCategoryLayoutDialog
import lovehan1me.feature.settings.dialog.HorizontalCardCountDialog
import lovehan1me.feature.settings.dialog.SearchGridColumnsDialog
import lovehan1me.feature.settings.model.HomeSettingsUiState
import lovehan1me.feature.settings.model.HomeSettingsActions
import lovehan1me.ui.theme.HanimeDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.UriHandler
import lovehan1me.ui.model.HorizontalCardCountConfig
import lovehan1me.core.constant.HA1_GITHUB_URL
import lovehan1me.core.constant.UPSTREAM_GITHUB_URL
import lovehan1me.amoled_mode
import lovehan1me.amoled_mode_summary
import lovehan1me.ic_dark_mode
import lovehan1me.theme_board
import lovehan1me.theme_board_summary
import lovehan1me.contrast_level_summary
import lovehan1me.nav_bar_style_summary
import lovehan1me.video
import lovehan1me.version
import lovehan1me.user_terms_summary
import lovehan1me.user_terms
import lovehan1me.trigger_crash_summary
import lovehan1me.trigger_crash
import lovehan1me.theme_audit_summary
import lovehan1me.theme_audit
import lovehan1me.temporarily_unavailable
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
import lovehan1me.enable_check_in_feature_summary
import lovehan1me.enable_check_in_feature
import lovehan1me.dynamic_color_title
import lovehan1me.dynamic_color_summary
import lovehan1me.display_density
import lovehan1me.display
import lovehan1me.disable_mobile_data_warning_summary
import lovehan1me.disable_mobile_data_warning
import lovehan1me.disable_comments_title
import lovehan1me.disable_comments_sum
import lovehan1me.developer_options
import lovehan1me.developer
import lovehan1me.upstream_project
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
import lovehan1me.app_lang_sum
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
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.ic_video_quilty
import lovehan1me.ui.model.SearchGridColumnsConfig
import lovehan1me.ui.component.SettingInfoItem
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.SettingSwitchItem
import lovehan1me.ui.component.SettingsAnimatedVisibility
import lovehan1me.ui.component.SettingsSegmentedGroup
import lovehan1me.ui.component.lazy.AnimatedLazyListScope

enum class HomeSettingsPage {
    VideoPlayback,
    NetworkDownload,
    Appearance,
    InterfaceInteraction,
    DataPrivacy,
    DeveloperOptions,
    About,
}

/** 与 `NavBarStyle.Floating.value` 对齐。 */
internal const val NAV_BAR_STYLE_FLOATING = "floating"

/** 与 `ContrastLevel.Medium/High.value` 对齐（标签本身在 UI 层用 stringResource 算）。 */
internal const val CONTRAST_LEVEL_MEDIUM = "medium"
internal const val CONTRAST_LEVEL_HIGH = "high"

internal enum class HomeSettingsChoiceDialog {
    VideoLanguage,
    VideoQuality,
    AppLanguage,
    DisplayDensity,
    NavBarStyle,
    ContrastLevel,
}

/**
 * 设置页的**分类路由**（审计重构：原先是 43 个回调 + 500 行巨型函数）。
 *
 * 这里只做三件事：装配弹窗、算几个页面级派生值、按 [page] 路由到对应分类。
 * 每个分类的具体条目在文件末尾的 `LazyListScope` 扩展函数里，可以各自独立修改。
 *
 * 回调由 [HomeSettingsActions] 聚合传入 —— 路由层只需要构造一次，
 * 加一个设置项时改动面从「改 4 个文件的参数列表」收敛到「改 1 个类 + 1 个分类函数」。
 */
@Composable
fun HomeSettingsScreen(
    page: HomeSettingsPage,
    state: HomeSettingsUiState,
    isLoggedIn: Boolean,
    actions: HomeSettingsActions,
    networkSettingsContent: @Composable () -> Unit,
    downloadSettingsContent: @Composable () -> Unit,
    onOpenThemeAudit: () -> Unit = {},
) {
    var activeDialog by rememberSaveable { mutableStateOf<HomeSettingsChoiceDialog?>(null) }
    var showSearchGridColumnsDialog by rememberSaveable { mutableStateOf(false) }
    var showHorizontalCardCountDialog by rememberSaveable { mutableStateOf(false) }
    var showHomeCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showUsageTerms by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    // 网格列数 / 横向卡片数量已改为按宽度自适应（「平板模式」开关已随 P0 删除），
    // 因此宽屏也应暴露这两个设置——此前仅当「平板模式」开启才可见，属于自适应被开关
    // 门控时代的遗留。窄屏仍维持原样，避免给手机用户多加条目。
    val showDensitySettings =
        rememberContentWidthSizeClass() >= WindowWidthSizeClass.Expanded

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
            actions.videoLanguageChange(it)
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
            actions.videoQualityChange(it)
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
            actions.openAppLanguageSettings(it)
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
            actions.displayDensityChange(value.toInt())
        },
    )
    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.ContrastLevel,
        title = stringResource(Res.string.contrast_level),
        options = listOf(
            stringResource(Res.string.contrast_level_standard) to "standard",
            stringResource(Res.string.contrast_level_medium) to "medium",
            stringResource(Res.string.contrast_level_high) to "high",
        ),
        selectedValue = state.contrastLevel,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            actions.contrastLevelChange(it)
        },
    )
    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.NavBarStyle,
        title = stringResource(Res.string.nav_bar_style),
        options = listOf(
            stringResource(Res.string.nav_bar_style_standard) to "standard",
            stringResource(Res.string.nav_bar_style_floating) to "floating",
        ),
        selectedValue = state.navBarStyle,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            actions.navBarStyleChange(it)
        },
    )

    if (showSearchGridColumnsDialog) {
        SearchGridColumnsDialog(
            initialConfig = state.searchGridColumnsConfig,
            onDismiss = { showSearchGridColumnsDialog = false },
            onConfirm = {
                showSearchGridColumnsDialog = false
                actions.searchGridColumnsConfigChange(it)
            },
        )
    }
    if (showHorizontalCardCountDialog) {
        HorizontalCardCountDialog(
            initialConfig = state.horizontalCardCountConfig,
            onDismiss = { showHorizontalCardCountDialog = false },
            onConfirm = {
                showHorizontalCardCountDialog = false
                actions.horizontalCardCountConfigChange(it)
            },
        )
    }
    if (showHomeCategoryDialog) {
        HomeCategoryLayoutDialog(
            state = state,
            onDismiss = { showHomeCategoryDialog = false },
            onConfirm = { order, hiddenKeys ->
                showHomeCategoryDialog = false
                actions.homeCategoryPreferencesChange(order, hiddenKeys)
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
            HomeSettingsPage.VideoPlayback -> videoPlaybackSection(state, actions, openChoice = { activeDialog = it })

            HomeSettingsPage.NetworkDownload -> networkDownloadSection(state, actions, networkSettingsContent, downloadSettingsContent)

            HomeSettingsPage.Appearance -> appearanceSection(state, actions, openChoice = { activeDialog = it })

            HomeSettingsPage.InterfaceInteraction -> interfaceInteractionSection(
                state = state,
                actions = actions,
                openChoice = { activeDialog = it },
                showDensitySettings = showDensitySettings,
                openSearchGridColumns = { showSearchGridColumnsDialog = true },
                openHomeCategory = { showHomeCategoryDialog = true },
                openHorizontalCardCount = { showHorizontalCardCountDialog = true },
            )

            HomeSettingsPage.DataPrivacy -> dataPrivacySection(state, actions, isLoggedIn)

            HomeSettingsPage.DeveloperOptions -> developerOptionsSection(
                state, actions,
                openChoice = { activeDialog = it },
                onOpenThemeAudit = onOpenThemeAudit,
            )

            HomeSettingsPage.About -> aboutSection(state, actions, uriHandler = uriHandler, openUsageTerms = { showUsageTerms = true })
        }
    }
}
