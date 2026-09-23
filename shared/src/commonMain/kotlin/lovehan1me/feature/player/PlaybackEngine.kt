package lovehan1me.feature.player

import kotlinx.coroutines.flow.StateFlow

typealias PlayerKernel = lovehan1me.core.domain.model.PlayerKernel
typealias VideoAspectMode = lovehan1me.core.domain.model.VideoAspectMode
typealias PictureAdjust = lovehan1me.core.domain.model.PictureAdjust

object PlayerDefaults {
    const val DEFAULT_SPEED = 1f
    const val DEFAULT_SPEED_INDEX = 2
    const val DEFAULT_PROGRESS_SLIDE_SENSITIVITY = 4
    const val DEFAULT_LONG_PRESS_SPEED_MULTIPLIER = 2.5f
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

// 注：G2-3b 的 VideoAspectMode / PictureAdjust 定义在 core.domain.model（见文件头 typealias），
// 因为 AppSettings 要持久化它们 —— 放在本包会让 core 反向依赖 feature。

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
    /**
     * G2-3b：引擎**实际生效**的画面比例。
     *
     * 为什么放在引擎状态里而不是只记在 UI：有的引擎不支持某一档（Exo 没有 Stretch），
     * 请求 [VideoAspectMode.Stretch] 后它会静默降级到 [VideoAspectMode.Fit]。
     * 让引擎把真实生效值报出来，UI 才不会显示一个"选中了但其实没生效"的假状态。
     */
    val videoAspect: VideoAspectMode = VideoAspectMode.Fit,
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

    /**
     * 本次 load 是**切换画质**（而非开始播放一个新片子）。
     *
     * 引擎据此做"保面"处理：**不重置首帧标记与视频尺寸、不卸载旧文件**。
     * 这正是"切档先黑一下再回到海报"的来源 —— 站点片源是各自独立的 MP4
     * （不是 HLS/DASH 的多 rendition manifest），没有轨道级切换可用，
     * 能做到最接近无缝的就是"保留上一帧 + 精确保留位置"。
     */
    val isQualitySwitch: Boolean = false,
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
     * G2-3b：本引擎**真实支持**的画面比例档位（顺序即 UI 展示顺序）。
     *
     * 声明式而不是让 UI 去猜：Exo 只有 Fit / Crop（Media3 的 `setVideoScalingMode`
     * 没有"拉伸"这一档），mpv 与 AVPlayer 三档全有。空列表 = 不支持 → UI 不出入口。
     */
    fun supportedAspectModes(): List<VideoAspectMode> = emptyList()

    /** 便捷判断：至少两档可选时才值得出「画面比例」菜单（一档等于没得选）。 */
    fun supportsVideoAspect(): Boolean = supportedAspectModes().size > 1

    /**
     * G2-3b：切换画面比例。
     *
     * 实现方**必须在下一帧起生效，且不能打断播放**（不是"重载视频才行"那种实现）——
     * 否则用户拖着进度调的时候体验会很难看。
     *
     * 请求了 [supportedAspectModes] 之外的档位时，实现应当**降级到 Fit 并如实报状态**，
     * 而不是抛异常或假装生效。
     */
    fun setVideoAspect(mode: VideoAspectMode) {}

    /**
     * G2-3b：是否支持画面调节（亮度/对比/饱和）。
     *
     * 与 [supportsSuperResolution] 同样的理由：**按能力判断，不要按内核名字判断**。
     * 目前只有 mpv 内核（桌面 + Android mpv）返回 true。
     */
    fun supportsPictureAdjust(): Boolean = false

    /**
     * G2-3b：设置画面调节（各分量 -100 ~ 100，0 = 原始）。
     *
     * 与 [setVideoAspect] 同源约束：即时生效、不打断播放、不支持时静默忽略。
     */
    fun setPictureAdjust(brightness: Float, contrast: Float, saturation: Float) {}

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
