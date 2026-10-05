package lovehan1me.core.domain.model

/**
 * 主题子树真正关心的四个量（B4 从 [AppSettings] 派生）。
 *
 * [HanimeTheme] 此前订阅整份 `AppSettings`：改 1 个弹幕字号也会重组整棵主题树。
 * 本类是派生流的去重键 —— 只有这四个量变化时才发射，`distinctUntilChanged` 靠
 * data class 的 `equals` 生效（四个字段全是稳定类型：enum / String / Boolean）。
 */
data class ThemeConfig(
    val themeMode: ThemeMode = ThemeMode.Light,
    val themeId: String = "sakura",
    val contrastLevel: ContrastLevel = ContrastLevel.Standard,
    val amoled: Boolean = false,
)

/** [AppSettings] → [ThemeConfig] 的唯一映射点（初值与派生流共用，不分头写）。 */
fun AppSettings.themeConfig(): ThemeConfig = ThemeConfig(
    themeMode = themeMode,
    themeId = themeId,
    contrastLevel = contrastLevel,
    amoled = amoled,
)
