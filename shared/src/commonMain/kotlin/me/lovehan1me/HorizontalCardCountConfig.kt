package me.lovehan1me

import me.lovehan1me.ui.adaptive.WindowWidthBreakpoints

data class HorizontalCardCountConfig(
    val narrowCount: Float = DEFAULT_NARROW_COUNT,
    val compactCount: Float = DEFAULT_COMPACT_COUNT,
    val mediumCount: Float = DEFAULT_MEDIUM_COUNT,
    val expandedCount: Float = DEFAULT_EXPANDED_COUNT,
) {
    /** 阈值统一到 [WindowWidthBreakpoints]（唯一断点源）。 */
    fun countForWidthDp(screenWidthDp: Int): Float {
        return when {
            screenWidthDp < WindowWidthBreakpoints.Narrow -> narrowCount
            screenWidthDp < WindowWidthBreakpoints.Medium -> compactCount
            screenWidthDp < WindowWidthBreakpoints.Expanded -> mediumCount
            else -> expandedCount
        }
    }

    companion object {
        const val DEFAULT_NARROW_COUNT = 1.5f
        const val DEFAULT_COMPACT_COUNT = 2.1f
        const val DEFAULT_MEDIUM_COUNT = 4.1f
        const val DEFAULT_EXPANDED_COUNT = 5.1f
    }
}
