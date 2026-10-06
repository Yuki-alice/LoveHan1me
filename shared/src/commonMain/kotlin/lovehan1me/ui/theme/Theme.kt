package lovehan1me.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.data.SettingsRepository

/**
 * 纯装配层：给定 [colorScheme] 装配完整主题（形状 / 字阶 / Expressive 组件族）。
 *
 * 与下面的运行时重载分开，是为了让 @Preview 能绕开两样东西：
 * 1. `SettingsRepository` —— 它是 lateinit 注入的（要先 `install(store)`），
 *    预览环境没装，直接读会 `lateinit property store has not been initialized`；
 * 2. `ConfigureSystemBars` —— 需要真实窗口。
 *
 * 预览统一走 `lovehan1me.ui.preview.HanimePreviewTheme`，复用这里的同一套
 * shapes / typography，所以预览与运行时的形状、字阶一致，只有配色由调用方决定。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HanimeTheme(
    colorScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // 审计 P1：显式形状阶梯（数值与 M3 默认一致），散装圆角已收敛到 token。
        shapes = AppShapes,
        // 审计 P0：不再传 Typography() 默认值（等于"不定制"），改用显式字阶。
        typography = AppTypography,
        content = content,
    )
}

// 色算三件套现走 materialkolor（commonMain 现场算，见 ThemeBoards）；
// 槽位配方见 ThemeBoards（8 槽 = 7 命名 + 1 跟随系统）。
@Composable
fun HanimeTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val systemDarkTheme = isSystemInDarkTheme()
    // B4：只订阅主题四量（mode/id/对比度/AMOLED），改弹幕字号等无关设置不再重组整树。
    val themeConfig by SettingsRepository.themeConfigFlow.collectAsStateWithLifecycle()
    val resolvedDarkTheme = darkTheme ?: when (themeConfig.themeMode.value) {
        "always_on" -> true
        "always_off" -> false
        else -> systemDarkTheme
    }
    // 命名槽位现场色算（materialkolor，真三端）。
    // 色算本身在 boardColorScheme 内部已 remember，不会每帧重算。
    val baseColorScheme = boardColorScheme(
        board = ThemeBoard.fromId(themeConfig.themeId),
        isDark = resolvedDarkTheme,
        // 高对比度接设置（默认档 = 0.0，即原先写死的值）。
        contrastLevel = themeConfig.contrastLevel.spec,
    )
    // AMOLED 是与深浅正交的独立开关，只在深色下叠加（Mihon 模式）。
    //
    // 这里必须 remember：amoled() 是一次 copy()，不加记忆的话主题过渡那 300ms 里
    // **每一帧都新造一个 ColorScheme 实例**。ColorScheme 没重写 equals（按引用比），
    // 于是 ConfigureSystemBars 每帧都认为入参变了而重组一次；记住后过渡期间传下去
    // 的是同一个实例，子项才能真的跳过。
    val targetColorScheme = remember(baseColorScheme, themeConfig.amoled, resolvedDarkTheme) {
        if (themeConfig.amoled && resolvedDarkTheme) baseColorScheme.amoled() else baseColorScheme
    }
    // 切槽位 / 深浅 / 对比度 / AMOLED 时平滑过渡（不再硬切）。
    //
    // 注意：这 48 个 animateColorAsState 的 .value 都在本作用域读，所以过渡的每一帧
    // 本函数都会重组一次、并向下分发一个新的 ColorScheme —— 这是"整棵子树跟着渐变"
    // 的既定代价，改不了；上面两步做的是把这一帧里能省的（色算 / 实例分配 / 子项跳过）省掉。
    val animatedColorScheme = animateColorScheme(targetColorScheme)
    ConfigureSystemBars(
        colorScheme = targetColorScheme,
        isDark = resolvedDarkTheme,
    )

    HanimeTheme(colorScheme = animatedColorScheme, content = content)
}
