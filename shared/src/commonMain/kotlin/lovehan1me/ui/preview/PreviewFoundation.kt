package lovehan1me.ui.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import lovehan1me.ui.theme.HanimeTheme
import lovehan1me.ui.theme.ThemeBoard
import lovehan1me.ui.theme.boardColorScheme

/**
 * 所有 `@Preview` 的唯一入口 —— 新写预览时只包这一层，不要再自己套主题。
 *
 * 为什么需要它：`HanimeTheme(darkTheme)` 那个运行时重载会读 `SettingsRepository`
 * （lateinit 注入，预览环境没 install 会崩）并调 `ConfigureSystemBars`（要真实窗口）。
 * 这里改走 `HanimeTheme(colorScheme)` 纯装配层，用同一套 shapes / typography，
 * 所以预览与运行时的形状、字阶一致，只有配色由 [board] / [isDark] 决定。
 *
 * 用法：
 * ```
 * @Preview
 * @Composable
 * private fun MyCardPreview() = HanimePreviewTheme {
 *     MyCard(...)
 * }
 * ```
 * 需要撑满画布时传 `modifier = Modifier.fillMaxSize()`（默认 hug contents）。
 *
 * @param board 配色槽，默认樱（与 `ThemeBoard.fromId` 的回退一致）。
 * @param isDark 深色；明暗各写一个预览函数即可（commonMain 拿不到
 *   `Configuration.UI_MODE_NIGHT_YES`，所以不做组合注解）。
 */
@Composable
fun HanimePreviewTheme(
    modifier: Modifier = Modifier,
    board: ThemeBoard = ThemeBoard.Sakura,
    isDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    HanimeTheme(
        colorScheme = boardColorScheme(board = board, isDark = isDark),
    ) {
        // Surface 提供背景色与内容色，避免预览里文字"浮在透明底上"看不清
        Surface(modifier = modifier) {
            content()
        }
    }
}
