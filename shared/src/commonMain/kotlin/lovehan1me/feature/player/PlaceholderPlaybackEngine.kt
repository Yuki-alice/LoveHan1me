package lovehan1me.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * P5-1：desktop / iOS 共用的占位引擎（真引擎为后续 P5-2：桌面 mpv-libmpv / iOS AVPlayer）。
 *
 * 无引擎时的可见行为：初始 state 即 [PlaybackPhase.Error] + 提示；[load] 再次 post 同样状态。
 */
internal class PlaceholderPlaybackEngine(
    private val message: String = "Playback engine not yet available on this platform",
) : PlaybackEngine {
    private val mutableState = MutableStateFlow(
        PlaybackEngineState(phase = PlaybackPhase.Error, errorMessage = message),
    )

    override val state: StateFlow<PlaybackEngineState> = mutableState.asStateFlow()

    override fun load(request: PlaybackRequest) {
        mutableState.value = mutableState.value.copy(
            phase = PlaybackPhase.Error,
            errorMessage = message,
        )
    }

    override fun play() = Unit

    override fun pause() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun setPlaybackSpeed(speed: Float) = Unit

    override fun setVolume(volume: Float) = Unit

    override fun attachSurface(surface: VideoSurface) = Unit

    override fun detachSurface(surface: VideoSurface) = Unit

    override fun release() = Unit
}
