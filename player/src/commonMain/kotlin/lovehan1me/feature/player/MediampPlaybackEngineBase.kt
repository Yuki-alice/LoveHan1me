package lovehan1me.feature.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import lovehan1me.core.util.LogUtil
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData

/**
 * Gate3-P5：mediamp 0.5.0 换底的桥接基类。
 *
 * 职责：把 mediamp 的 `PlayerState` 三轴状态机（mediaStatus / playWhenReady /
 * isBuffering）折算成本仓 [PlaybackEngineState] 单轴快照，既有播放 UI 零改动消费。
 * 子类只管构造后端实例（Android=exo / iOS=avkit）并转交 [mediampPlayer]，
 * 构造完成后调一次 [startObserving]。
 *
 * 线程契约（mediamp 规定）：play/pause/seekTo/stopPlayback 必须在主线程，
 * setMediaData/close 任意线程——这里统一经 [commandScope] 规范化。
 *
 * 渲染面：mediamp 自家 Surface 直接持有后端 impl，[attachSurface]/[detachSurface]
 * 为 no-op（旧 VideoSurface 链路仅 mpv-android 保留使用）。
 */
abstract class MediampPlaybackEngineBase : PlaybackEngine {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    protected val mutableState = MutableStateFlow(PlaybackEngineState())
    override val state: StateFlow<PlaybackEngineState> = mutableState.asStateFlow()

    /** 子类构造的后端实例（exo / avkit）。public 只读：PlatformVideoSurface 要转交它家 Surface。 */
    abstract val mediampPlayer: MediampPlayer

    /** 请求的倍速（跨 load 生效：mediamp PlaybackSpeed feature 自带该语义，这里只做状态回显）。 */
    private var requestedSpeed = PlayerDefaults.DEFAULT_SPEED

    /** 首帧近似锁存：mediamp 无 onRenderedFirstFrame 事件，以"拿到过视频尺寸"代替。 */
    private var seenVideoSize = false

    /** mediamp Buffering feature 的缓冲前沿（接口面是冷 Flow，单独收集成字段）。 */
    private var bufferedPositionMs = 0L

    /** 释放后不再接受命令/不再发布状态（与旧引擎同语义）。 */
    private var released = false

    /** 子类构造完 [mediampPlayer] 后调用一次。 */
    protected fun startObserving() {
        scope.launch {
            combine(
                mediampPlayer.state,
                mediampPlayer.currentPositionMillis,
                mediampPlayer.mediaProperties,
            ) { _, _, _ -> Unit }.collect { publish() }
        }
        scope.launch {
            (mediampPlayer.features[Buffering.Key] as? Buffering)
                ?.bufferedPositionMillis
                ?.collect {
                    bufferedPositionMs = it.coerceAtLeast(0L)
                    // B3 修复：只存不发会导致初始缓冲期 UI 缓冲条恒 0
                    //（position 不动、状态机稳在 Opening，combine 不触发）。
                    publish()
                }
        }
    }

    final override fun load(request: PlaybackRequest) {
        if (released) return
        // 切画质保面（与旧引擎同款）：尺寸/首帧标记不清零，否则 UI 立刻盖回海报。
        // 位置由 startPositionMillis 承接（见 openMedia）。
        mutableState.value = mutableState.value.copy(
            phase = PlaybackPhase.Preparing,
            isBuffering = true,
            errorMessage = null,
            videoWidth = if (request.isQualitySwitch) mutableState.value.videoWidth else 0,
            videoHeight = if (request.isQualitySwitch) mutableState.value.videoHeight else 0,
            hasRenderedFirstFrame =
            request.isQualitySwitch && mutableState.value.hasRenderedFirstFrame,
        )
        if (!request.isQualitySwitch) seenVideoSize = false
        scope.launch {
            try {
                openMedia(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 被更新的 load 顶掉（MediaLoadCancellationException）时状态机
                // 已在 Opening 新媒体——只有真 Error 才落到 UI。
                if (mediampPlayer.state.value.mediaStatus is MediaStatus.Error) {
                    publish()
                } else {
                    // B4 修复：同步抛（未及状态机翻 Error）此前永留 Preparing 转圈。
                    // 不是顶掉就是真失败，直接落 Error 带异常信息。
                    LogUtil.e(TAG, "openMedia failed", e)
                    mutableState.value = mutableState.value.copy(
                        phase = PlaybackPhase.Error,
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    /** 挂起执行 open；子类可在 [onBeforeOpen] 钩子里抢先配置后端（如预置空 effect 表）。 */
    private suspend fun openMedia(request: PlaybackRequest) {
        onBeforeOpen()
        mediampPlayer.setMediaData(
            data = UriMediaData(
                uri = request.uri,
                headers = request.headers,
            ),
            playWhenReady = request.playWhenReady,
            // 切画质从当前播放位置续（mediamp 的 open 天然支持起始位），
            // 新片用请求的起始位（0 或续播位）。
            // B6 修复：用播放器即时位置而非上次发布值（最大 250ms+ 延迟，切档系统性偏小）。
            startPositionMillis = if (request.isQualitySwitch) {
                mediampPlayer.currentPositionMillis.value.coerceAtLeast(0L)
            } else {
                request.startPositionMs
            },
        )
    }

    /** 每次 open 前的钩子（如 Android 预置空超分表：setVideoEffects 须先于 prepare）。
     *
     * 挂起函数：实现方按需切线程，但**必须在返回前做完**（基类随后立即 setMediaData，
     * fire-and-forget 会导致配置晚于 prepare，首开档位不生效，见 B1）。
     */
    protected open suspend fun onBeforeOpen() {}

    final override fun play() {
        if (released) return
        commandScope.launch { mediampPlayer.play() }
    }

    final override fun pause() {
        if (released) return
        commandScope.launch { mediampPlayer.pause() }
    }

    final override fun seekTo(positionMs: Long) {
        if (released) return
        commandScope.launch { mediampPlayer.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    final override fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 5f)
        val previous = requestedSpeed
        requestedSpeed = safeSpeed
        commandScope.launch {
            // B5 修复：feature 缺失/设置失败时回滚本地乐观值并重发，否则 UI 显示假倍速。
            runCatching { mediampPlayer.features[PlaybackSpeed.Key]?.set(safeSpeed) }
                .onFailure {
                    LogUtil.w(TAG, "setPlaybackSpeed failed, revert to $previous: ${it.message}")
                    requestedSpeed = previous
                    publish()
                }
        }
    }

    // mediamp Surface 直绑后端 impl，本仓 VideoSurface 链路与其无关。
    final override fun attachSurface(surface: VideoSurface) {}
    final override fun detachSurface(surface: VideoSurface) {}

    // ── G2-3b：画面比例 ─────────────────────────────────────
    //
    // mediamp 自家 Surface（PlayerView.resizeMode / AVPlayerLayer.videoGravity）
    // 三档全真实现，换底后从"Exo 只有 Fit/Crop"升为三档全支持。
    final override fun supportedAspectModes(): List<VideoAspectMode> = listOf(
        VideoAspectMode.Fit,
        VideoAspectMode.Stretch,
        VideoAspectMode.Crop,
    )

    final override fun setVideoAspect(mode: VideoAspectMode) {
        val effective = if (mode in supportedAspectModes()) mode else VideoAspectMode.Fit
        mutableState.value = mutableState.value.copy(videoAspect = effective)
        commandScope.launch {
            mediampPlayer.features[VideoAspectRatio.Key]?.setMode(effective.toMediampAspect())
        }
    }

    final override fun release() {
        if (released) return
        released = true
        onRelease()
        mediampPlayer.close()
        scope.cancel()
        commandScope.cancel()
        mutableState.value = PlaybackEngineState()
    }

    /** release 前的子类钩子（如 iOS 解绑 PiP）。 */

    protected open fun onRelease() {}

    private fun publish() {
        if (released) return
        val snapshot = mediampPlayer.state.value
        val props = mediampPlayer.mediaProperties.value
        val width = props?.videoWidth ?: 0
        val height = props?.videoHeight ?: 0
        if (width > 0 && height > 0) seenVideoSize = true

        val phase = when (val status = snapshot.mediaStatus) {
            MediaStatus.Idle -> PlaybackPhase.Idle
            MediaStatus.Opening -> PlaybackPhase.Preparing
            MediaStatus.Ready -> PlaybackPhase.Ready
            MediaStatus.Ended -> PlaybackPhase.Ended
            is MediaStatus.Error -> PlaybackPhase.Error
            MediaStatus.Released -> PlaybackPhase.Idle
        }
        // 错误信息只在进入 Error 时写入一次（publish 高频触发，
        // 无条件写会反复覆盖；load 已负责清空）。
        if (phase == PlaybackPhase.Error && mutableState.value.phase != PlaybackPhase.Error) {
            val err = (snapshot.mediaStatus as? MediaStatus.Error)?.error
            mutableState.value = mutableState.value.copy(
                errorMessage = err?.message ?: err?.toString() ?: "playback error",
            )
        }

        mutableState.value = mutableState.value.copy(
            phase = phase,
            isPlaying = snapshot.isPlaying,
            // Opening 也算缓冲中：旧引擎 load() 即置 isBuffering=true，UI 依赖它出转圈。
            isBuffering = snapshot.isBuffering || snapshot.mediaStatus == MediaStatus.Opening,
            positionMs = mediampPlayer.currentPositionMillis.value.coerceAtLeast(0L),
            durationMs = props?.durationMillis?.coerceAtLeast(0L) ?: 0L,
            bufferedPositionMs = bufferedPositionMs,
            playbackSpeed = requestedSpeed,
            // 尺寸未知时保留旧值（切画质保面）；新片 load() 已清零。
            videoWidth = if (width > 0) width else mutableState.value.videoWidth,
            videoHeight = if (height > 0) height else mutableState.value.videoHeight,
            hasRenderedFirstFrame = seenVideoSize && phase != PlaybackPhase.Preparing,
        )
    }

    private fun VideoAspectMode.toMediampAspect(): AspectRatioMode = when (this) {
        VideoAspectMode.Fit -> AspectRatioMode.FIT
        VideoAspectMode.Stretch -> AspectRatioMode.STRETCH
        VideoAspectMode.Crop -> AspectRatioMode.CROP
    }

    private companion object {
        const val TAG = "MediampEngineBase"
    }
}
