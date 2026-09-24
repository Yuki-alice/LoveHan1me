package lovehan1me.feature.player

import lovehan1me.core.util.LogUtil
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.asset
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracks
import platform.AVFoundation.mediaType
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerStatusFailed
import platform.AVFoundation.AVPlayerStatusReadyToPlay
import platform.AVFoundation.AVPlayerStatusUnknown
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.error
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.status
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

/**
 * M3：iOS 真引擎（AVPlayer）。
 *
 * - HLS（m3u8）与渐进 mp4 由系统原生支持；Cookie 走共享 `NSHTTPCookieStorage`
 *  （登录态与系统行为一致，无需手动注入）；
 * - 状态机不走 KVO（桥接成本高），以 500ms 协程轮询 + `status/rate/error` 推导
 *  （Playing/Ready/Error/Buffering），精度满足播放器 UI；
 * - 渲染不走 surface 回调，由 `PlatformVideoSurface.ios` 的 `AVPlayerLayer`
 *   直接持有 [avPlayer]（与桌面端相同的无 surface 设计）。
 */
@OptIn(ExperimentalForeignApi::class)
class IosAVPlaybackEngine : PlaybackEngine {

    internal val avPlayer = AVPlayer()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PlaybackEngineState())
    override val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    private var playWhenReady = true

    /** 请求的倍速（AVPlayer 的 `rate` 会被暂停清零，不能当作倍速状态；见 setPlaybackSpeed）。 */
    private var requestedSpeed = PlayerDefaults.DEFAULT_SPEED

    /** 是否已渲染过首帧（锁存：切画质时位置短暂归零也不让海报闪回）。 */
    private var hasRenderedFrame = false
    private var released = false

    /** G2-3b：生效中的画面比例（渲染面按它挑 `AVPlayerLayer.videoGravity`）。 */
    private var aspectMode = VideoAspectMode.Fit

    init {
        // 阶段一⑨：向画中画 holder 注册当前 AVPlayer（每条视频一个引擎实例，
        // release 时解绑；PiP 用独立 AVPlayerLayer，不碰渲染面的 layer）。
        // Gate3-P5：:player 不能反向引用 :shared 的 IosPipPlayerHolder，经桥转交。
        IosPlayerPipBridge.onPlayerCreated?.invoke(avPlayer)
        scope.launch {
            while (isActive) {
                publishState()
                delay(500L)
            }
        }
    }

    override fun load(request: PlaybackRequest) {
        if (released) return
        LogUtil.d(TAG, "load: ${request.uri}")
        // iOS 网关运行时待接入；即使将来网关就绪，AVPlayer 也不能走
        // AVURLAsset 自定义头那条路：Xcode 26 SDK 已不再公开
        // `AVURLAssetHTTPHeaderFieldsKey`（仅剩 HTTPCookiesKey，传不了
        // X-Ech-Target），且 HLS 分片本就不继承初始请求的头。
        // 到时的正确形态是 AVAssetResourceLoaderDelegate 在原生侧按
        // EchGatePolicy 逐请求改写（与 Android EchGateDataSource 同思路）。
        // 在此之前 AVPlayer 走现有机制（直连/代理）兜底。
        val url = NSURL.URLWithString(request.uri) ?: run {
            _state.value = _state.value.copy(
                phase = PlaybackPhase.Error,
                errorMessage = "bad url: ${request.uri}",
            )
            return
        }
        playWhenReady = request.playWhenReady
        avPlayer.replaceCurrentItemWithPlayerItem(AVPlayerItem.playerItemWithURL(url))
        if (request.startPositionMs > 0L) {
            avPlayer.seekToTime(CMTimeMakeWithSeconds(request.startPositionMs / 1000.0, 600))
        }
        if (request.playWhenReady) {
            avPlayer.play()
        }
        scope.launch { publishState() }
    }

    override fun play() {
        if (released) return
        playWhenReady = true
        avPlayer.play()
        // 恢复播放时补上暂停期间设置的倍速（见 setPlaybackSpeed 的说明）
        if (requestedSpeed != PlayerDefaults.DEFAULT_SPEED) avPlayer.rate = requestedSpeed
        scope.launch { publishState() }
    }

    override fun pause() {
        if (released) return
        playWhenReady = false
        avPlayer.pause()
        scope.launch { publishState() }
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        avPlayer.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 600))
        scope.launch { publishState() }
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (released) return
        requestedSpeed = speed
        // ⚠️ AVPlayer 把 `rate` 同时当"倍速"和"播放开关"：暂停时写 rate 会把视频**直接播起来**。
        // 所以只在正在播放时写；暂停期间只记录请求值，等 play() 时带上。
        if (avPlayer.rate != 0f) avPlayer.rate = speed
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun setVolume(volume: Float) {
        if (released) return
        avPlayer.volume = volume
    }

    override fun attachSurface(surface: VideoSurface) {}
    override fun detachSurface(surface: VideoSurface) {}

    // ── G2-3b：画面比例（AVPlayerLayer.videoGravity 三档全有）────

    override fun supportedAspectModes(): List<VideoAspectMode> =
        listOf(VideoAspectMode.Fit, VideoAspectMode.Stretch, VideoAspectMode.Crop)

    /**
     * 三档与 `videoGravity` 一一对应，切档**立即生效、不打断播放**：
     * Fit → `resizeAspect`、Stretch → `resize`、Crop → `resizeAspectFill`。
     *
     * 值只落在引擎状态里，渲染面（`PlatformVideoSurface.ios`）订阅这个状态改图层 ——
     * 引擎自己不持 layer（与桌面端"引擎不持渲染面"同一分工）。
     */
    override fun setVideoAspect(mode: VideoAspectMode) {
        if (released) return
        aspectMode = mode
        _state.value = _state.value.copy(videoAspect = mode)
    }

    // 画面调节：AVPlayer 侧没有对等能力（只有 AVVideoComposition + CIFilter 那条
    // 逐帧 CPU 路线，代价与收益不成比例），如实声明不支持，不做假开关。

    // ── M3-b：抓帧（实现见 IosFrameCapture.kt）──────────────

    override fun supportsFrameCapture(): Boolean = true

    /**
     * 抓 [positionMs] 处的画面。
     *
     * 返回的已是**目标尺寸**像素：iOS 侧能拿到像素缓冲的确切宽高，故在那边就地用
     * `FrameScaler` 缩放（见 [grabIosFrameArgb] 的说明）——
     * 这样整条链路不依赖引擎上报的 `videoWidth/videoHeight`，旋转视频也不会失配。
     */
    override suspend fun grabFrameArgb(
        positionMs: Long,
        targetWidth: Int,
        targetHeight: Int,
    ): IntArray? {
        if (released || positionMs < 0L) return null
        if (targetWidth <= 0 || targetHeight <= 0) return null
        return grabIosFrameArgb(avPlayer, positionMs, targetWidth, targetHeight)
    }

    override fun release() {
        if (released) return
        released = true
        IosPlayerPipBridge.onPlayerReleased?.invoke(avPlayer)
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        scope.cancel()
    }

    /**
     * 读取视频轨的天然尺寸。
     *
     * M5-2：此前 [PlaybackEngineState] 的 videoWidth/Height 恒为 0，导致依赖宽高比的
     * UI（播放器尺寸自适应、竖屏视频判定）在 iOS 上失效。
     *
     * 注意 [AVAssetTrack.naturalSize] 不含旋转信息：手机竖拍/横拍素材需要按
     * `preferredTransform` 判断是否交换宽高，否则 1080x1920 的竖屏片源会被误判为横屏。
     */
    private fun readVideoSize(): Pair<Int, Int> {
        // 不直接用 AVMediaTypeVideo 常量：各 KN 版本对 NS_EXTENSIBLE_STRING_ENUM
        // 的映射有差异（本机工具链下该符号不可见，编译器会验；IosBackupFiles 的
        // UTType 处是同一处理）。"vide" 即 AVMediaTypeVideo 的字面值，行为等价。
        val track = avPlayer.currentItem?.asset?.tracks
            ?.firstOrNull { (it as? AVAssetTrack)?.mediaType?.toString() == VIDEO_MEDIA_TYPE }
            as? AVAssetTrack ?: return 0 to 0
        val size = track.naturalSize
        val width = size.useContents { width.toInt() }
        val height = size.useContents { height.toInt() }
        val rotated = kotlin.math.abs(track.preferredTransform.useContents { b }) > 0.5
        return if (rotated) height to width else width to height
    }

    private fun publishState() {
        val item = avPlayer.currentItem
        val status = avPlayer.status
        val itemError = item?.error
        val failed = status == AVPlayerStatusFailed || itemError != null
        val (videoWidth, videoHeight) = readVideoSize()
        val positionMs = (CMTimeGetSeconds(avPlayer.currentTime()) * 1000).toLong()
            .coerceAtLeast(0L)
        if (positionMs > 0L) hasRenderedFrame = true
        val durationSec = item?.let { CMTimeGetSeconds(it.duration) } ?: Double.NaN
        val durationMs = if (durationSec.isNaN() || durationSec.isInfinite()) {
            0L
        } else {
            (durationSec * 1000).toLong().coerceAtLeast(0L)
        }
        val playing = avPlayer.rate != 0f && !failed
        val phase = when {
            failed -> PlaybackPhase.Error
            status == AVPlayerStatusUnknown && item == null -> PlaybackPhase.Idle
            status == AVPlayerStatusUnknown -> PlaybackPhase.Preparing
            playing -> PlaybackPhase.Ready
            playWhenReady && positionMs == 0L -> PlaybackPhase.Preparing
            else -> PlaybackPhase.Ready
        }
        _state.value = PlaybackEngineState(
            phase = phase,
            isPlaying = playing,
            isBuffering = phase == PlaybackPhase.Preparing && playWhenReady,
            positionMs = positionMs,
            durationMs = durationMs,
            // 桌面/iOS 拿不到"已缓冲到哪"，如实给 positionMs（UI 的缓冲条与已播层重合，不显示假进度）；
            // 真正的"卡住"由 PlaybackStallDetector 判定。
            bufferedPositionMs = positionMs,
            // 上报**请求的**倍速而不是 avPlayer.rate：暂停时 rate 恒为 0，
            // 会让 UI 显示 "0.0x"（此前就是这个表现）。
            playbackSpeed = requestedSpeed,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            hasRenderedFirstFrame = hasRenderedFrame,
            // G2-3b：publishState 每 500ms 重建一次 state，画面比例要从字段回填。
            videoAspect = aspectMode,
            errorMessage = if (failed) {
                itemError?.localizedDescription ?: "AVPlayer error"
            } else {
                null
            },
        )
    }

    companion object {
        private const val TAG = "IosAVPlayer"

        /** `AVMediaTypeVideo` 的字面值（见 readVideoSize 注释）。 */
        private const val VIDEO_MEDIA_TYPE = "vide"
    }
}
