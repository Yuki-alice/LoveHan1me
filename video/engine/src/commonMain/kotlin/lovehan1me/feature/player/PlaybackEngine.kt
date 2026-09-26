package lovehan1me.feature.player

import kotlinx.coroutines.flow.StateFlow
import lovehan1me.video.contract.VideoEnhancementController

typealias PlayerKernel = lovehan1me.video.contract.PlayerKernel
typealias VideoAspectMode = lovehan1me.video.contract.VideoAspectMode
typealias PictureAdjust = lovehan1me.video.contract.PictureAdjust

// 默认值的真身在 :video:contract —— UI 要拿它当默认参数，而引擎实现不能被 UI 依赖。
// 留别名是因为全仓都按本包 import。
typealias PlayerDefaults = lovehan1me.video.contract.PlayerDefaults

// 播放阶段的真身已下沉到 :video:contract。留别名是因为全仓都按本包 import，
// 一次性改所有 import 会牵出一堆无关改动，先断依赖方向，包路径下次再收。
public typealias PlaybackPhase = lovehan1me.video.contract.PlaybackPhase

// 开流请求同理，真身在契约层（字段与本仓原定义逐一同名同义）。
public typealias PlaybackRequest = lovehan1me.video.contract.PlaybackRequest

// 注：VideoAspectMode / PictureAdjust 的真身在 :video:contract（见文件头 typealias），
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
     * 超分（视频增强）控制器。**null = 本端不支持**，UI 入口整块隐藏。
     *
     * 取代此前的 `supportsSuperResolution(): Boolean = false` + `setSuperResolution()` 默认实现对：
     * 那两个默认实现让"平台没做"和"平台做了但返回 false"长得一样，调用方分不出来；
     * 而且**不要用「内核名字 == MpvPlayer」来判断** —— 桌面端引擎恒为 mpv，
     * 但用户设置里的 `switchPlayerKernel` 可能是 ExoPlayer（Android 默认值），
     * 按名字判断会让桌面端永远看不到超分入口。
     *
     * **刻意不给默认实现**：新引擎必须显式表态（不支持也要写 `= null`），漏写会编译失败，
     * 而不是静默退化成一个"不支持"的默认值。
     */
    val enhancement: VideoEnhancementController?

    /**
     * G2-3b：本引擎**真实支持**的画面比例档位（顺序即 UI 展示顺序）。
     *
     * 声明式而不是让 UI 去猜：换底前 Exo 只有 Fit / Crop（Media3 的 `setVideoScalingMode`
     * 没有"拉伸"这一档）；Gate3-P5 后三端统一为 mediamp 自家 Surface 的三档真实现
     * （resizeMode / videoGravity），`supportedAspectModes()` 一律三档。
     * 空列表 = 不支持 → UI 不出入口。
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
     * 与 [enhancement] 同样的理由：**按能力判断，不要按内核名字判断**。
     * 换底前只有 mpv 内核返回 true；Gate3-P6 后 Android 已无 mpv，
     * 只有桌面 mpv 返回 true（iOS 无对等能力，如实 false）。
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
     * 与 [enhancement] 同样的理由：**按能力判断，不要按内核名字判断** ——
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
