package lovehan1me.feature.player

import lovehan1me.core.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.MpvMediampPlayerFactory
import org.openani.mediamp.source.UriMediaData

/**
 * M3：桌面 mpv 真引擎（mediamp-mpv，animeko 同款；native 库随
 * `mediamp-mpv-runtime-*` 自动加载，macOS arm64 已验证）。
 *
 * - 解码/状态/进度/音量/倍速全部走 mediamp 公开 API，映射进 [PlaybackEngineState]；
 * - 渲染不走 surface 回调（`attach/detach` 空实现，P5-2a Q1 裁定），由
 *   `PlatformVideoSurface.desktop` 的 Skia 面直接持有本引擎的 `mediampPlayer`；
 * - mpv 选项（user-agent/hwdec）暂用默认 + 请求头透传，调优随 M-后续。
 */
class DesktopMpvPlaybackEngine : PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * mediamp 要求所有播放控制（load/play/pause/seek/volume/speed/close）发在
     * 构造时传入的 main-dispatcher 线程（桌面 = Swing EDT，见 coroutines-swing）。
     * 状态收集仍在 Default。
     */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    internal val mediampPlayer: MpvMediampPlayer by lazy {
        // 原生库由引擎初始化链自行加载（mediamp-mpv-runtime-*）；首参在桌面端
        // 仅作透传 token（Android 侧为 Context），传中性非空常量。
        MpvMediampPlayerFactory().create(ENGINE_TOKEN, scope.coroutineContext)
    }

    private val _state = MutableStateFlow(PlaybackEngineState())
    override val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    private var released = false

    init {
        val player = mediampPlayer
        scope.launch {
            combine(
                player.state,
                player.currentPositionMillis,
                player.mediaProperties,
            ) { playerState, positionMs, props ->
                val phase = when (playerState.mediaStatus) {
                    is MediaStatus.Idle -> PlaybackPhase.Idle
                    is MediaStatus.Opening -> PlaybackPhase.Preparing
                    is MediaStatus.Ready -> PlaybackPhase.Ready
                    is MediaStatus.Ended -> PlaybackPhase.Ended
                    is MediaStatus.Error -> PlaybackPhase.Error
                    is MediaStatus.Released -> PlaybackPhase.Idle
                    else -> PlaybackPhase.Idle
                }
                val durationMs = props?.durationMillis ?: 0L
                PlaybackEngineState(
                    phase = phase,
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedPositionMs = positionMs,
                    playbackSpeed = runCatching {
                        player.features[PlaybackSpeed]?.value
                    }.getOrNull() ?: PlayerDefaults.DEFAULT_SPEED,
                    videoWidth = props?.videoWidth ?: 0,
                    videoHeight = props?.videoHeight ?: 0,
                    hasRenderedFirstFrame = positionMs > 0L,
                    errorMessage = if (phase == PlaybackPhase.Error) {
                        "mpv playback error"
                    } else {
                        null
                    },
                )
            }.collect { _state.value = it }
        }
    }

    override fun load(request: PlaybackRequest) {
        if (released) return
        LogUtil.d(TAG, "load: ${request.uri} (headers=${request.headers.keys})")
        mainScope.launch {
            runCatching {
                mediampPlayer.setMediaData(
                    UriMediaData(request.uri, request.headers),
                    request.playWhenReady,
                    request.startPositionMs,
                )
                if (request.playWhenReady) {
                    mediampPlayer.play()
                }
            }.onFailure {
                LogUtil.e(TAG, "load failed", it)
                _state.value = _state.value.copy(
                    phase = PlaybackPhase.Error,
                    errorMessage = it.message,
                )
            }
        }
    }

    override fun play() {
        if (released) return
        mainScope.launch { mediampPlayer.play() }
    }

    override fun pause() {
        if (released) return
        mainScope.launch { mediampPlayer.pause() }
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        mainScope.launch { mediampPlayer.seekTo(positionMs) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (released) return
        mainScope.launch {
            runCatching {
                mediampPlayer.features[PlaybackSpeed]?.set(speed)
            }.onFailure {
                LogUtil.w(TAG, "setPlaybackSpeed unsupported: $speed")
            }
        }
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun setVolume(volume: Float) {
        if (released) return
        mainScope.launch {
            runCatching {
                mediampPlayer.features[AudioLevelController]?.setVolume(volume)
            }.onFailure {
                LogUtil.w(TAG, "setVolume unsupported: $volume")
            }
        }
    }

    // 桌面渲染由 PlatformVideoSurface 的 Skia 面直接持有 mediampPlayer，
    // 不走 surface 回调（P5-2a Q1 裁定：no-op 是正确设计）。
    override fun attachSurface(surface: VideoSurface) {}
    override fun detachSurface(surface: VideoSurface) {}

    override fun release() {
        if (released) return
        released = true
        mainScope.launch {
            runCatching { mediampPlayer.close() }
            mainScope.cancel()
            scope.cancel()
        }
    }

    companion object {
        private const val TAG = "DesktopMpv"

        /** 传给 MpvMediampPlayerFactory 的中性 token（桌面端无 Context 概念）。 */
        private const val ENGINE_TOKEN = "LoveHan1meDesktop"
    }
}
