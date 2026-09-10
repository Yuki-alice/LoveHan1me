package me.lovehan1me.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自适应的**唯一一份**宽度断点定义（纯逻辑，不依赖 Compose 运行时）。
 *
 * 背景：此前项目内存在三套各自为政的宽度魔数——
 *  - `HorizontalCardCountConfig`：350 / 600 / 840
 *  - `SearchGridColumnsConfig`：600 / **900** / 1200
 *  - `App` 常驻抽屉判定：840
 * 同一个窗口宽度在不同组件里会落到不同档位：导航 chrome 与内容网格不同步。
 * 现在全部改为引用本文件的常量（见 [WindowWidthBreakpoints]），改一处即三端全局生效。
 *
 * 与 Compose 相关的采样 / 下发（`rememberContentWidthDp`、`ProvideContentWidth`）见
 * `WindowSize.kt`。
 */

/** 宽度分档。对齐 Material 3 的 compact/medium/expanded，另加桌面用的 [Large]。 */
enum class WindowWidthSizeClass { Compact, Medium, Expanded, Large }

/**
 * 断点常量：每个值是该档的**下界**（整型 dp）。
 *
 * 只提供数字 + `*Dp` 视图两个入口，避免再出现第二处真相。
 */
object WindowWidthBreakpoints {
    /** compact 内的亚档（超窄：分屏 / 小屏手机竖屏）。仅横向卡片密度使用。 */
    const val Narrow = 350

    /** medium 下界 —— 亦即 compact 的上界。手机竖屏。 */
    const val Medium = 600

    /** expanded 下界 —— 亦即 medium 的上界。平板竖屏 / 窄桌面窗口。 */
    const val Expanded = 840

    /** large 下界 —— 亦即 expanded 的上界。平板横屏 / 桌面窗口。 */
    const val Large = 1200

    val NarrowDp = Narrow.dp
    val MediumDp = Medium.dp
    val ExpandedDp = Expanded.dp
    val LargeDp = Large.dp
}

/** 按宽度（dp）分档。 */
fun windowWidthSizeClassOf(width: Dp): WindowWidthSizeClass = when {
    width.value < WindowWidthBreakpoints.Medium -> WindowWidthSizeClass.Compact
    width.value < WindowWidthBreakpoints.Expanded -> WindowWidthSizeClass.Medium
    width.value < WindowWidthBreakpoints.Large -> WindowWidthSizeClass.Expanded
    else -> WindowWidthSizeClass.Large
}

/**
 * 按最小项宽推算可容纳列数（宽屏网格的统一公式）。
 *
 * 保留 [minColumns] 下限，避免极窄窗口（分屏）退化成 1 列。
 */
fun columnsForMinItemWidth(
    availableWidth: Dp,
    minItemWidth: Dp,
    spacing: Dp = 0.dp,
    minColumns: Int = 2,
): Int {
    if (minItemWidth <= 0.dp) return minColumns
    return maxOf(minColumns, ((availableWidth + spacing) / (minItemWidth + spacing)).toInt())
}
