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

/**
 * Ended 到达时的页面动作（纯函数，可单测）。
 *
 * 循环优先于连播：单集循环开时同一集无限重播，不再进下一集
 * （与 misaka 的引擎级 `loop-file` 同语义；本仓 mediamp 0.5.0 无循环语义，
 * 由页面调 `replay()` 实现，三端一致）。
 */
enum class EndedAction {
    /** 重播本集（单集循环开）。 */
    ReplayCurrent,

    /** 进下一集（连播开且有下一集）。 */
    AdvanceNext,

    /** 原地停在结束态。 */
    Stay,
}

fun resolveEndedAction(
    phase: PlaybackPhase,
    loopSingle: Boolean,
    autoPlayNextEnabled: Boolean,
    hasNext: Boolean,
): EndedAction = when {
    phase != PlaybackPhase.Ended -> EndedAction.Stay
    loopSingle -> EndedAction.ReplayCurrent
    autoPlayNextEnabled && hasNext -> EndedAction.AdvanceNext
    else -> EndedAction.Stay
}
