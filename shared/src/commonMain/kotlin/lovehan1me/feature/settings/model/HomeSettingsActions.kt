package lovehan1me.feature.settings.model

import lovehan1me.ui.model.HorizontalCardCountConfig
import lovehan1me.ui.model.SearchGridColumnsConfig

/**
 * 设置页用到的全部回调，聚合为一个对象（审计重构）。
 *
 * **为什么要有这个类**：`HomeSettingsScreen` 此前是 43 个参数 —— 每加一个设置项，
 * 路由层（`HomeSettingsRoute`）、设置页、双栏壳三处的参数列表都要动一遍，
 * 属于「改不动」的典型形态。聚合后路由层只构造一次，新增设置项的成本收敛为
 * 「加一个属性 + 改一个分类函数」。
 *
 * ⚠️ 关于 Compose 稳定性：这是普通 class（不是 data class），Compose 视为不稳定类型，
 * 实例变化会让设置页重组。当前路由层每次重组都会重建它 —— 与重构前 39 个 lambda 参数
 * 逐个传入的行为一致，**没有引入性能回退**。真要消除这层重组，应在路由层用
 * `remember` 固定实例，但那会让 lambda 捕获的变量（如 `isPipPermissionGranted`）
 * 变成陈旧值，风险大于收益，故不做。
 */
class HomeSettingsActions(
    /** 视频语言（需重启生效，路由层负责弹确认框）。 */
    val videoLanguageChange: (String) -> Unit,
    /** 默认画质。 */
    val videoQualityChange: (String) -> Unit,
    val darkModeChange: (String) -> Unit,
    /** 命名主题槽位切换（themeId）。 */
    val themeIdChange: (String) -> Unit,
    /** AMOLED 纯黑开关。 */
    val amoledChange: (Boolean) -> Unit,
    val hapticFeedbackChange: (Boolean) -> Unit,
    val funLoadingHintsChange: (Boolean) -> Unit,
    /** 动态对比度档位（standard / medium / high）。 */
    val contrastLevelChange: (String) -> Unit,
    val allowPipModeChange: (Boolean) -> Unit,
    val allowResumePlaybackChange: (Boolean) -> Unit,
    val showPlayedIndicatorChange: (Boolean) -> Unit,
    val searchArtistIgnoreVideoTypeChange: (Boolean) -> Unit,
    val disableMobileDataWarningChange: (Boolean) -> Unit,
    val disablePredictiveBackChange: (Boolean) -> Unit,
    val videoLandscapeLayoutStyleChange: (String) -> Unit,
    val navBarStyleChange: (String) -> Unit,
    val checkInEnabledChange: (Boolean) -> Unit,
    val disableCommentsChange: (Boolean) -> Unit,
    val collapseDownloadedGroupChange: (Boolean) -> Unit,
    val searchGridColumnsConfigChange: (SearchGridColumnsConfig) -> Unit,
    val horizontalCardCountConfigChange: (HorizontalCardCountConfig) -> Unit,
    val secureModeChange: (Boolean) -> Unit,
    val alwaysShowUpdateCardChange: (Boolean) -> Unit,
    val displayDensityChange: (Int) -> Unit,
    val triggerCrash: () -> Unit,
    val homeCategoryPreferencesChange: (List<String>, Set<String>) -> Unit,
    val openAppLanguageSettings: (String) -> Unit,
    val openApplyDeepLinks: () -> Unit,
    val openFakeLauncherIcon: () -> Unit,
    val openOpenSourceLicense: () -> Unit,
    val clearCache: () -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
    val exportLocalLists: () -> Unit,
    val importLocalLists: () -> Unit,
    val exportOnlineLists: () -> Unit,
    val importOnlineLists: () -> Unit,
    val submitBug: () -> Unit,
    val openForum: () -> Unit,
)
