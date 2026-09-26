package lovehan1me.video.ui

/**
 * 引擎未上报画面比例时的兜底比例。
 *
 * 兜底**只此一份**：调用方一律上报 `0f` 表示"还不知道"，由 UI 在布局时补上。
 * 此前调用方与 UI 各算一次默认值，两边都以为对方拥有它。
 */
const val DEFAULT_VIDEO_ASPECT_RATIO: Float = 16f / 9f

/** 把"引擎上报的比例"折成可布局的正数：[reported] 非正即视为未上报。 */
fun resolveVideoAspectRatio(reported: Float): Float =
    if (reported > 0f) reported else DEFAULT_VIDEO_ASPECT_RATIO

/**
 * 画面宽高比不小于容器宽高比 → 按宽度撑满、上下留黑边；否则按高度撑满、左右留黑边。
 *
 * 这是**纯布局决策**：比例的每一次上报都只影响它，不构成渲染面的身份 ——
 * 渲染面只在引擎实例更换时重建。
 */
fun videoFillsWidth(reported: Float, containerAspectRatio: Float): Boolean =
    resolveVideoAspectRatio(reported) >= containerAspectRatio