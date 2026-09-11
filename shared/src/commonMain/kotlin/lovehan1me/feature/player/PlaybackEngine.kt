package lovehan1me.feature.player

import kotlinx.coroutines.flow.StateFlow

typealias PlayerKernel = lovehan1me.core.domain.model.PlayerKernel

object PlayerDefaults {
    const val DEFAULT_SPEED = 1f
    const val DEFAULT_SPEED_INDEX = 2
    const val DEFAULT_PROGRESS_SLIDE_SENSITIVITY = 4
    const val DEFAULT_LONG_PRESS_SPEED_MULTIPLIER = 2.5f
    const val DEFAULT_COUNTDOWN_SECONDS = 10

    val speeds = floatArrayOf(
        0.5f,
        0.75f,
        DEFAULT_SPEED,
        1.25f,
        1.5f,
        1.75f,
        2f,
        2.25f,
        2.5f,
        2.75f,
        3f,
    )

    val speedLabels: Array<String>
        get() = Array(speeds.size) { "${speeds[it]}x" }
}

enum class PlaybackPhase {
    Idle,
    Preparing,
    Ready,
    Ended,
    Error,
}

data class PlaybackEngineState(
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasRenderedFirstFrame: Boolean = false,
    val errorMessage: String? = null,
)

data class PlaybackRequest(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val title: String = "",
    val artworkUri: String? = null,
    val mimeType: String? = null,
    val startPositionMs: Long = 0L,
    val playWhenReady: Boolean = true,
    val looping: Boolean = false,
)

interface PlaybackEngine {
    val state: StateFlow<PlaybackEngineState>

    fun load(request: PlaybackRequest)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun attachSurface(surface: VideoSurface)
    fun detachSurface(surface: VideoSurface)
    fun release()

    /**
     * M3：超分着色器档位（mpv `glsl-shaders`）。仅 MPV 引擎实现，
     * 其余默认空实现（调用方无需类型判断）。
     */
    fun setSuperResolution(index: Int) {}

    /**
     * 阶段一②：本引擎是否支持视频超分。
     *
     * **不要用「内核名字 == MpvPlayer」来判断**：桌面端引擎恒为 mpv，
     * 但用户设置里的 `switchPlayerKernel` 可能是 ExoPlayer（Android 默认值），
     * 按名字判断会让桌面端永远看不到超分入口。
     */
    fun supportsSuperResolution(): Boolean = false
}
