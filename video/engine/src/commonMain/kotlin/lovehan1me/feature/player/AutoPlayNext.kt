package lovehan1me.feature.player

/**
 * 系列自动连播的触发判定（纯函数，可单测）。
 *
 * 三个条件缺一不可：
 * - 引擎到达 [PlaybackPhase.Ended]（播完；暂停/缓冲/失败都不触发）；
 * - 用户开着自动连播开关；
 * - 系列里存在"当前项的后一项"（单片、最后一集都不触发）。
 *
 * 调用方（视频页）在 phase 跃迁到 Ended 时调一次；导航到下一集后新页面
 * 的 phase 从 Idle/Preparing 重新开始，不会连环触发。
 */
fun shouldAutoPlayNext(
    phase: PlaybackPhase,
    autoPlayNextEnabled: Boolean,
    hasNext: Boolean,
): Boolean = phase == PlaybackPhase.Ended && autoPlayNextEnabled && hasNext
