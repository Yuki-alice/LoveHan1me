package io.github.daisukikaffuchino.han1meviewer.ui.player

import io.github.daisukikaffuchino.han1meviewer.USER_AGENT
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.network.HProxySelector
import io.github.daisukikaffuchino.utils.LogUtil
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
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.source.UriMediaData
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P5-2a：桌面 mpv 引擎 spike（animeko 同款 mediamp-mpv）。
 *
 * - 只求"出画面 + 状态机可观测"，不求完整引擎（无 Cast/着色器/字幕）。
 * - 渲染归 `PlatformVideoSurface.desktop` 的 `MpvMediampPlayerSurface` 所有；
 *   [attachSurface]/[detachSurface] 为 no-op（见接口冻结问卷 Q1）。
 */
class DesktopMpvPlaybackEngine : PlaybackEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player: MpvMediampPlayer

    private val mutableState = MutableStateFlow(PlaybackEngineState())
    override val state: StateFlow<PlaybackEngineState> = mutableState.asStateFlow()

    private var released = false

    init {
        ensureLibraries()
        player = MpvMediampPlayer(Any(), scope.coroutineContext)
        applyMpvOptions()
        scope.launch {
            combine(
                player.state,
                player.currentPositionMillis,
                player.mediaProperties,
            ) { playerState, positionMs, props ->
                val phase = when (val status = playerState.mediaStatus) {
                    is MediaStatus.Idle -> PlaybackPhase.Idle
                    is MediaStatus.Opening -> PlaybackPhase.Preparing
                    is MediaStatus.Ready -> PlaybackPhase.Ready
                    is MediaStatus.Ended -> PlaybackPhase.Ended
                    is MediaStatus.Error -> PlaybackPhase.Error
                    is MediaStatus.Released -> PlaybackPhase.Idle
                }
                val errorMessage = (playerState.mediaStatus as? MediaStatus.Error)
                    ?.error?.message
                mutableState.value.copy(
                    phase = phase,
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = props?.durationMillis ?: 0L,
                    playbackSpeed = requestedSpeed,
                    videoWidth = props?.videoWidth ?: 0,
                    videoHeight = props?.videoHeight ?: 0,
                    hasRenderedFirstFrame = (props?.videoWidth ?: 0) > 0,
                    errorMessage = errorMessage,
                )
            }.collect { mapped -> mutableState.value = mapped }
        }
    }

    private var requestedSpeed: Float = PlayerDefaults.DEFAULT_SPEED

    override fun load(request: PlaybackRequest) {
        check(!released) { "Playback engine has already been released" }
        mutableState.value = mutableState.value.copy(
            phase = PlaybackPhase.Preparing,
            isBuffering = true,
            errorMessage = null,
        )
        scope.launch {
            runCatching {
                player.setMediaData(
                    UriMediaData(request.uri, request.headers),
                    playWhenReady = request.playWhenReady,
                )
                if (request.startPositionMs > 0L) {
                    player.seekTo(request.startPositionMs)
                }
                // P5-2a：倍速经 features[PlaybackSpeed]（animeko PlaybackSpeedExtension 同款）
                player.features[PlaybackSpeed]?.set(requestedSpeed)
            }.onFailure { error ->
                LogUtil.e(TAG, "Desktop mpv load failed", error)
                mutableState.value = mutableState.value.copy(
                    phase = PlaybackPhase.Error,
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.message,
                )
            }
        }
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setPlaybackSpeed(speed: Float) {
        requestedSpeed = speed.coerceIn(0.25f, 5f)
        player.features[PlaybackSpeed]?.set(requestedSpeed)
    }

    override fun setVolume(volume: Float) {
        player.features[AudioLevelController]?.setVolume(volume.coerceIn(0f, 1f))
    }

    override fun attachSurface(surface: VideoSurface) = Unit

    override fun detachSurface(surface: VideoSurface) = Unit

    override fun release() {
        if (released) return
        released = true
        runCatching { player.close() }
        scope.cancel()
        mutableState.value = PlaybackEngineState()
    }

    internal fun mediampPlayer(): MpvMediampPlayer = player

    /**
     * P5-2a Q5 驗證點：全局 mpv 选项经 MPVHandle 生效（照抄 animeko applyMpvOptions）。
     * spike 只验证 user-agent + hwdec 两条， settings 全量搬运是 P5-2b 的事。
     */
    private fun applyMpvOptions() {
        // P5-2a：访问 impl 触发 mpv 实例懒创建（照抄 animeko applyMpvOptions）
        val handle = runCatching { player.impl as? MPVHandle }.getOrNull() ?: run {
            LogUtil.w(TAG, "MPVHandle unavailable, skip custom options")
            return
        }
        val options = buildMap {
            put("user-agent", USER_AGENT)
            put("hwdec", "auto")
            SettingsRepository.proxyIp.takeIf { it.isNotBlank() && SettingsRepository.proxyPort != -1 }?.let { ip ->
                if (SettingsRepository.proxyType == HProxySelector.TYPE_HTTP) {
                    put("http-proxy", "http://$ip:${SettingsRepository.proxyPort}")
                }
            }
        }
        for ((key, value) in options) {
            val applied = runCatching { handle.option(key, value) }.getOrDefault(false)
            LogUtil.d(TAG, "mpv option $key=$value applied=$applied")
        }
    }

    private companion object {
        const val TAG = "DesktopMpvPlaybackEngine"
        val librariesPrepared = AtomicBoolean(false)

        fun ensureLibraries() {
            if (librariesPrepared.compareAndSet(false, true)) {
                MpvMediampPlayer.prepareLibraries()
                MPVHandle.setLogHandler { msg ->
                    LogUtil.d(TAG, "[mpv/${msg.prefix}] ${msg.line}")
                }
            }
        }
    }
}
