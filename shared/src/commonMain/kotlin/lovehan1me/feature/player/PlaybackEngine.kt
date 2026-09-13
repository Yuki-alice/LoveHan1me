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

    /**
     * M3-b：本引擎是否支持抓取渲染帧（GIF 录制 / 截图分享）。
     *
     * 与 [supportsSuperResolution] 同样的理由：**按能力判断，不要按内核名字判断** ——
     * 用户设置里的 `switchPlayerKernel` 与运行时真正在用的引擎可能不一致，
     * 按名字判断会让某个平台永远看不到对应入口。
     */
    fun supportsFrameCapture(): Boolean = false

    /**
     * M3-b：抓取 [positionMs] 处的画面为 ARGB 像素（0xAARRGGBB，长度 = 宽*高）。
     *
     * 实现方**应当在解码侧直接产出 [targetWidth] × [targetHeight]** ——
     * 桌面端 mediamp 的 `FramePreview.getPreviewFrame(pos, w, h)` 就支持，
     * 这样能省掉一帧全尺寸位图的分配（1080p 一帧就是 8 MB）。
     * 若实现只能给源尺寸，返回源尺寸即可，`GifRecorder` 会补做缩放。
     *
     * @return null 表示不支持或抓取失败。**实现方不要抛异常**：
     *         录制 UI 需要把"失败"呈现为提示，而不是崩掉。
     */
    suspend fun grabFrameArgb(
        positionMs: Long,
        targetWidth: Int,
        targetHeight: Int,
    ): IntArray? = null
}
