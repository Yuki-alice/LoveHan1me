package lovehan1me.feature.settings.model

import lovehan1me.ui.model.HorizontalCardCountConfig
import lovehan1me.ui.model.SearchGridColumnsConfig
import lovehan1me.feature.home.homepage.HomeCategoryPreferenceItem

data class HomeSettingsUiState(
    val videoLanguage: String,
    val videoLanguageLabel: String,
    val defaultVideoQuality: String,
    val darkMode: String,
    val appLanguage: String,
    val appLanguageLabel: String,
    val allowPipMode: Boolean,
    val allowResumePlayback: Boolean,
    val showPlayedIndicator: Boolean,
    val searchArtistIgnoreVideoType: Boolean,
    val disableMobileDataWarning: Boolean,
    val disablePredictiveBack: Boolean,
    val videoLandscapeLayoutStyle: String,
    val navBarStyle: String,
    val disableComments: Boolean,
    val collapseDownloadedGroup: Boolean,
    /** 命名主题槽位 id（见 ThemeBoard：sakura/take/sou/yuzu/midnight/nord/mono/system）。 */
    val themeId: String,
    /** AMOLED 纯黑（深色正交叠加）。 */
    val amoled: Boolean,
    val hapticFeedbackEnabled: Boolean,
    val funLoadingHints: Boolean,
    val secureMode: Boolean,
    val fakeLauncherIconName: String,
    val cacheSummary: String,
    val versionSummary: String,
    /** 只存值（standard/medium/high），标签在 UI 层用 stringResource 算。 */
    val contrastLevel: String,
    val searchGridColumnsSummary: String,
    val searchGridColumnsConfig: SearchGridColumnsConfig,
    val horizontalCardCountSummary: String,
    val horizontalCardCountConfig: HorizontalCardCountConfig,
    val checkInEnabled: Boolean,
    val homeCategoryItems: List<HomeCategoryPreferenceItem>,
    val homeCategoryOrder: List<String>,
    val hiddenHomeCategoryKeys: Set<String>,
    val useAvHomeCategoryTitles: Boolean,
    val alwaysShowUpdateCard: Boolean,
    val displayDensityPercent: Int,
)
