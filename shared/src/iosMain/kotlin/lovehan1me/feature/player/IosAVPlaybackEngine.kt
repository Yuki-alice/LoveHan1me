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
import platform.AVFoundation.tracksWithMediaType
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVMediaTypeVideo
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
    private var released = false

    init {
        // 阶段一⑨：向画中画 holder 注册当前 AVPlayer（每条视频一个引擎实例，
        // release 时解绑；PiP 用独立 AVPlayerLayer，不碰渲染面的 layer）。
        IosPipPlayerHolder.attach(avPlayer)
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
        IosPipPlayerHolder.detach(avPlayer)
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
        val track = avPlayer.currentItem?.asset
            ?.tracksWithMediaType(AVMediaTypeVideo)
            ?.firstOrNull() as? AVAssetTrack ?: return 0 to 0
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
            hasRenderedFirstFrame = positionMs > 0L,
            errorMessage = if (failed) {
                itemError?.localizedDescription ?: "AVPlayer error"
            } else {
                null
            },
        )
    }

    companion object {
        private const val TAG = "IosAVPlayer"
    }
}
