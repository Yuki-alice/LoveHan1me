package io.github.daisukikaffuchino.han1meviewer.ui.player

import io.github.daisukikaffuchino.utils.LogUtil
import kotlinx.cinterop.ExperimentalForeignApi
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
    private var released = false

    init {
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
        avPlayer.rate = speed
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun setVolume(volume: Float) {
        if (released) return
        avPlayer.volume = volume
    }

    override fun attachSurface(surface: VideoSurface) {}
    override fun detachSurface(surface: VideoSurface) {}

    override fun release() {
        if (released) return
        released = true
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        scope.cancel()
    }

    private fun publishState() {
        val item = avPlayer.currentItem
        val status = avPlayer.status
        val itemError = item?.error
        val failed = status == AVPlayerStatusFailed || itemError != null
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
            bufferedPositionMs = positionMs,
            playbackSpeed = avPlayer.rate,
            videoWidth = 0,
            videoHeight = 0,
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
