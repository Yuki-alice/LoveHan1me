package me.lovehan1me

import me.lovehan1me.ui.adaptive.WindowWidthBreakpoints

data class SearchGridColumnsConfig(
    val compactColumns: Int = DEFAULT_COMPACT_COLUMNS,
    val mediumColumns: Int = DEFAULT_MEDIUM_COLUMNS,
    val expandedColumns: Int = DEFAULT_EXPANDED_COLUMNS,
    val largeColumns: Int = DEFAULT_LARGE_COLUMNS,
) {
    /**
     * 按宽度取列数。
     *
     * 阈值统一到 [WindowWidthBreakpoints]（600 / 840 / 1200）——历史上这里用的是
     * 600 / **900** / 1200，与导航 chrome 的 840 分档不同步：窗口 850dp 时常驻抽屉
     * 已经出现，网格却还停在「手机档」。
     */
    fun columnsForWidthDp(screenWidthDp: Int): Int {
        return when {
            screenWidthDp < WindowWidthBreakpoints.Medium -> compactColumns
            screenWidthDp < WindowWidthBreakpoints.Expanded -> mediumColumns
            screenWidthDp < WindowWidthBreakpoints.Large -> expandedColumns
            else -> largeColumns
        }.coerceAtLeast(1)
    }

    companion object {
        const val DEFAULT_COMPACT_COLUMNS = 2
        const val DEFAULT_MEDIUM_COLUMNS = 3
        const val DEFAULT_EXPANDED_COLUMNS = 4
        const val DEFAULT_LARGE_COLUMNS = 5
    }
}
