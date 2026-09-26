package lovehan1me.video.contract

/**
 * 一次性事件（与"当前状态"相对）。状态可以反复读，事件只发一次，
 * 比如"播放结束"要触发连播，靠状态判断会重复触发。
 */
sealed interface PlaybackEvent {

    data object Ended : PlaybackEvent

    data class Error(val message: String) : PlaybackEvent

    data object FirstFrameRendered : PlaybackEvent

    /** 画质切换结束。引擎到达 Ready 视为成功，落到 Error 视为失败。 */
    data class QualitySwitchFinished(val success: Boolean) : PlaybackEvent
}
