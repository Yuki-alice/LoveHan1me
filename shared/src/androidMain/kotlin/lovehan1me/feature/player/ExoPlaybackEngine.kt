package lovehan1me.feature.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import lovehan1me.core.constant.USER_AGENT
import lovehan1me.core.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class ExoPlaybackEngine(
    context: Context,
) : PlaybackEngine, Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        addListener(this@ExoPlaybackEngine)
    }
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(PlaybackEngineState())
    private var progressJob: Job? = null
    private var released = false

    // 阶段一②：Exo 超分档位（0/1/2，与 MpvShaders 编号对齐，默认 OFF）。
    // Media3 要求 effect graph 在首次 prepare 前预置，播放中换档必须重走
    // prepare（见 setSuperResolution 的重建续播）；档位只记在引擎内，
    // commonMain UI 侧无感（仍调 setSuperResolution + supportsSuperResolution）。
    private var superResolutionLevel = ExoSuperResolution.OFF
    private var lastRequest: PlaybackRequest? = null
    private var lastSpeed = PlayerDefaults.DEFAULT_SPEED

    override val state: StateFlow<PlaybackEngineState> = mutableState.asStateFlow()

    override fun load(request: PlaybackRequest) {
        check(!released) { "Playback engine has already been released" }
        lastRequest = request
        mutableState.value = mutableState.value.copy(
            phase = PlaybackPhase.Preparing,
            isBuffering = true,
            errorMessage = null,
            videoWidth = 0,
            videoHeight = 0,
            hasRenderedFirstFrame = false,
        )
        preparePlayer(request)
        startProgressUpdates()
        publishState()
    }

    override fun play() {
        player.play()
        publishState()
    }

    override fun pause() {
        player.pause()
        publishState()
    }

    override fun seekTo(positionMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        player.seekTo(positionMs.coerceIn(0L, duration ?: Long.MAX_VALUE))
        publishState()
    }

    override fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 5f)
        lastSpeed = safeSpeed
        player.playbackParameters = PlaybackParameters(safeSpeed)
        publishState()
    }

    // 阶段一②：Exo 超分入口（三档，与 mpv 侧编号一致）。
    //
    // 切档 = 重建续播：effect graph 必须在 prepare 前预置，播放中换档只能
    // 重走 prepare；用当前位置 + 当前播放状态重载同一条 request，
    // 体验为“闪一下回到原位置继续播”（与 Controller 切清晰度的语义一致）。
    // 失败一律降级 OFF 并保证仍在播，绝不抛给 UI（与桌面 mpv 侧的降级链同原则）。
    override fun supportsSuperResolution(): Boolean = true

    override fun setSuperResolution(index: Int) {
        val level = index.coerceIn(ExoSuperResolution.OFF, ExoSuperResolution.QUALITY)
        if (level == superResolutionLevel) return
        superResolutionLevel = level
        val request = lastRequest ?: return
        if (released) return
        // ExoPlayer 实例方法要求主线程；Compose 回调本就在主线程，
        // 经 scope 规范化一次，防未来从 IO 线程误调。
        scope.launch {
            if (released) return@launch
            val resumePositionMs = player.currentPosition.coerceAtLeast(0L)
            val resumePlaying = player.playWhenReady
            load(
                request.copy(
                    startPositionMs = resumePositionMs,
                    playWhenReady = resumePlaying,
                )
            )
            // load 不管倍速（平时由 Controller 在 load 后补调）；
            // 引擎内重建绕过了 Controller，这里自己恢复，避免倍速被吞回 1x。
            player.playbackParameters = PlaybackParameters(lastSpeed)
            publishState()
        }
    }

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    override fun attachSurface(surface: VideoSurface) {
        if (released) return
        player.setVideoSurface(surface)
    }

    override fun detachSurface(surface: VideoSurface) {
        if (released) return
        player.clearVideoSurface(surface)
    }

    override fun release() {
        if (released) return
        released = true
        progressJob?.cancel()
        player.removeListener(this)
        player.clearVideoSurface()
        player.release()
        scope.cancel()
        mutableState.value = PlaybackEngineState()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        publishState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        publishState()
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        publishState()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        publishState()
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        publishState(videoSize)
    }

    override fun onRenderedFirstFrame() {
        mutableState.value = mutableState.value.copy(hasRenderedFirstFrame = true)
    }

    override fun onPlayerError(error: PlaybackException) {
        progressJob?.cancel()
        LogUtil.e(TAG, "Playback failed", error)
        mutableState.value = mutableState.value.copy(
            phase = PlaybackPhase.Error,
            isPlaying = false,
            isBuffering = false,
            errorMessage = error.localizedMessage,
        )
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                publishState()
                delay(PROGRESS_UPDATE_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun publishState(videoSize: VideoSize = player.videoSize) {
        if (released) return
        val duration = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        mutableState.value = mutableState.value.copy(
            phase = when (player.playbackState) {
                Player.STATE_BUFFERING -> PlaybackPhase.Preparing
                Player.STATE_READY -> PlaybackPhase.Ready
                Player.STATE_ENDED -> PlaybackPhase.Ended
                else -> PlaybackPhase.Idle
            },
            isPlaying = player.isPlaying,
            isBuffering = player.isLoading || player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            playbackSpeed = player.playbackParameters.speed,
            videoWidth = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt(),
            videoHeight = videoSize.height,
            errorMessage = null,
        )
    }

    /**
     * 真正走播放器管线的 prepare（load 与切档重建共用）。
     *
     * 顺序敏感：[ExoPlayer.setVideoEffects] 必须在 `prepare()` 之前调用，
     * 否则本条播放沿用旧 graph（Media3 的约束，不是本项目的选择）。
     * surface 不动（attach 时已绑，重 prepare 后渲染器会复用它），view 层零改动。
     */
    private fun preparePlayer(request: PlaybackRequest) {
        applyVideoEffects()
        player.repeatMode = if (request.looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaSource(createMediaSource(request))
        player.prepare()
        if (request.startPositionMs > 0L) {
            player.seekTo(request.startPositionMs)
        }
        player.playWhenReady = request.playWhenReady
    }

    /**
     * 按当前档位挂 effect，失败即降级 OFF。
     *
     * GL program 编译失败会以 [VideoFrameProcessingException] 抛到调用方
     * （animeko 的短板就是这里直接崩）；这里吞掉并回到空管线，
     * 最差情况是“超分没生效但片子照播”。
     */
    private fun applyVideoEffects() {
        try {
            player.setVideoEffects(ExoSuperResolution.effectsFor(superResolutionLevel))
        } catch (e: Exception) {
            LogUtil.e(TAG, "setVideoEffects failed, fallback to OFF", e)
            superResolutionLevel = ExoSuperResolution.OFF
            try {
                player.setVideoEffects(emptyList())
            } catch (fallbackError: Exception) {
                LogUtil.e(TAG, "fallback to OFF also failed", fallbackError)
            }
        }
    }

    private fun createMediaSource(request: PlaybackRequest): MediaSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(request.headers)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
        val item = MediaItem.fromUri(request.uri.toUri())
        return if (request.uri.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
    }

    private companion object {
        const val TAG = "ExoPlaybackEngine"
        const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }
}
