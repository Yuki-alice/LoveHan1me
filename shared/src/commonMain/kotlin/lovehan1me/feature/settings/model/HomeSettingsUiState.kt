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
    val autoPlayOnEnter: Boolean,
    val showPlayedIndicator: Boolean,
    val searchArtistIgnoreVideoType: Boolean,
    val disableMobileDataWarning: Boolean,
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
    // ── 平台能力决定的可见性 ────────────────────────────────────────────────
    // 由 SettingsPlatformCapabilities 统一裁决（单一真相源），把"三端列出同一份开关、
    // 其中两端点了没反应"改成"没有这个能力的平台不显示这一行"。
    // 注意：字段值本身照常读写，只是不出现对应的行 —— 跨端各自设置互不干扰。
    /** 安全模式：仅 Android 有真实实现（FLAG_SECURE），桌面/iOS 的 applySecureMode 是空实现。 */
    val showSecureMode: Boolean,
    /** 触感反馈：仅 Android 有真实实现，桌面/iOS 的 HapticFeedback 是空实现。 */
    val showHapticFeedback: Boolean,
    /** 画中画：桌面恒不进入 PiP（阶段一决策⑨），Android/iOS 支持。 */
    val showPipMode: Boolean,
    /** 移动数据提醒：桌面/iOS 的 isActiveNetworkMetered() 恒 false，守卫永不触发。 */
    val showMeteredDataWarning: Boolean,
)
