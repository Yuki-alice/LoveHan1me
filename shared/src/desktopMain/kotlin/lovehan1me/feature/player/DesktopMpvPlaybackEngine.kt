package lovehan1me.feature.player

import lovehan1me.core.util.MpvShaders
import lovehan1me.core.util.materializeMpvShaders
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
import org.openani.mediamp.mpv.MPVHandle
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

    /** mpv 缩放相关属性的原始值（首次切档时记录，OFF 时还原）。 */
    private var originalScaling: Map<String, String>? = null

    /**
     * 给渲染面用的挂起获取：返回预热好的 player。
     *
     * 注意调用方必须在 composition 之外调用（LaunchedEffect），不能在组合/布局
     * 阶段直接读 [mediampPlayer]——mpv 原生初始化几百毫秒，会把跳转过渡卡死。
     */
    suspend fun awaitPlayer(): MpvMediampPlayer = mediampPlayer

    init {
        // 预热放进 EDT 异步队列：构造（含 remember）与首帧合成立即返回，
        // 详情页先画出来（简介缓存/骨架），播放器面就绪后挂载。
        // 之前这里同步 touch lazy，点卡片后转场直接冻住等 mpv_create。
        mainScope.launch {
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

    // 阶段一②：视频超分（Anime4K）。mpv 通过 `change-list glsl-shaders` 挂 shader。
    //
    // 与 animeko 的差异（它的两个短板）：
    // 1. shader 失败/不支持时**自动降级**而不是抛异常——animeko 会抛
    //    VideoFrameProcessingException，本项目则 QUALITY → PERFORMANCE → OFF；
    // 2. 切档前先记住原始缩放属性，OFF 时精确还原，而不是写死默认值。
    override fun setSuperResolution(index: Int) {
        if (released) return
        mainScope.launch {
            val ok = runCatching { applySuperResolution(index) }.getOrDefault(false)
            if (ok || index == MpvShaders.OFF) return@launch

            val fallback = if (index == MpvShaders.QUALITY) MpvShaders.PERFORMANCE else MpvShaders.OFF
            LogUtil.w(TAG, "超分档位 $index 不可用，自动降级到 $fallback")
            val okFallback = runCatching { applySuperResolution(fallback) }.getOrDefault(false)
            if (!okFallback && fallback != MpvShaders.OFF) {
                LogUtil.w(TAG, "降级档位仍然不可用，关闭超分")
                runCatching { applySuperResolution(MpvShaders.OFF) }
            }
        }
    }

    override fun supportsSuperResolution(): Boolean = true

    /** @return 是否成功应用。shader 落盘失败或 mpv 命令失败都返回 false。 */
    private suspend fun applySuperResolution(level: Int): Boolean {
        val paths = materializeMpvShaders(level) ?: return false
        val handle = mpvHandle() ?: return false
        if (!handle.command("change-list", "glsl-shaders", "set", paths)) return false
        applyScalingOptions(handle, level)
        return true
    }

    private fun applyScalingOptions(handle: MPVHandle, level: Int) {
        // 首次调用时记下原始值，之后 OFF 才能精确还原
        if (originalScaling == null) {
            originalScaling = SCALING_KEYS.associateWith { handle.getPropertyString(it).orEmpty() }
        }
        if (level == MpvShaders.OFF) {
            originalScaling?.forEach { (key, value) -> handle.setPropertyString(key, value) }
        } else {
            SCALING_KEYS.forEach { key ->
                val value = if (key == "sigmoid-upscaling") "yes" else "ewa_lanczossharp"
                handle.setPropertyString(key, value)
            }
        }
    }

    /**
     * mediamp 把 mpv 句柄的 getter 标成 internal，JVM 名字被 mangled 成
     * `getHandle$mediamp_mpv`——Kotlin 源码里既不能直呼其名、也没法用反引号
     * 转义（`$` 不允许出现在标识符里），所以走反射。
     *
     * 反射失败（比如 mediamp 升级后改名）只会返回 null，随后由
     * [setSuperResolution] 的降级链把超分关掉，不会崩。
     */
    private fun mpvHandle(): MPVHandle? = runCatching {
        val method = mediampPlayer.javaClass.getMethod(MPV_HANDLE_GETTER)
        @Suppress("UNCHECKED_CAST")
        method.invoke(mediampPlayer) as? MPVHandle
    }.onFailure {
        LogUtil.w(TAG, "无法获取 mpv 句柄：${it.message}")
    }.getOrNull()

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

        /** 随超分一起调整的 mpv 缩放属性（animeko 同款组合）。 */
        private val SCALING_KEYS = arrayOf("scale", "cscale", "dscale", "sigmoid-upscaling")

        /** mediamp 里 mpv 句柄 getter 的 JVM 名字（internal 成员被 mangled）。 */
        private const val MPV_HANDLE_GETTER = "getHandle\$mediamp_mpv"
    }
}
