package lovehan1me.feature.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import lovehan1me.core.util.LogUtil
import lovehan1me.video.contract.PlaybackLoadStatus
import lovehan1me.video.contract.PlaybackRequests
import lovehan1me.video.contract.PlaybackState
import lovehan1me.video.contract.PlaybackTruth
import lovehan1me.video.contract.coercePlaybackSpeed
import lovehan1me.video.contract.derivePlaybackState
import lovehan1me.video.contract.enteredError
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.source.UriMediaData

/**
 * mediamp 0.5.0 换底的桥接基类。
 *
 * 职责：把 mediamp 的 `PlayerState` 三轴状态机（mediaStatus / playWhenReady /
 * isBuffering）折算成本仓快照，既有播放 UI 零改动消费。
 * 子类只管构造后端实例（Android=exo / iOS=avkit）并转交 [mediampPlayer]，
 * 构造完成后调一次 [startObserving]。
 *
 * ## 状态只有一个写者
 *
 * 本类不再直接 `copy` 状态：唯一入口是 [publish]，它把「后端真值 + 用户请求值 +
 * 开流意图」交给契约层的 [derivePlaybackState] 一次性算出新快照。
 * 此前 `load()` 往状态里写 `phase=Preparing` / `phase=Error`，下一次 publish 又按
 * 后端快照无条件覆盖 —— 开流失败写的 Error 会在下一帧被抹掉，UI 永远转圈。
 * 现在"开流意图"是 [loadStatus]，属于派生入参，抹不掉。
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

    /** 契约状态是唯一真源。[mutableState] 只是给既有 UI 消费的过渡视图。 */
    private var contractState = PlaybackState()

    private val mutableState = MutableStateFlow(PlaybackEngineState())
    override val state: StateFlow<PlaybackEngineState> = mutableState.asStateFlow()

    /** 子类构造的后端实例（exo / avkit）。public 只读：PlatformVideoSurface 要转交它家 Surface。 */
    abstract val mediampPlayer: MediampPlayer

    /** 用户请求值（意图）。引擎每次开流都会丢画面类偏好，必须记住并在开流后重下。 */
    private var requests = PlaybackRequests()

    /**
     * 开流意图。单独一列而不是塞进真值：后端状态描述不了"刚下发开流、后端还没翻状态"
     * 以及"开流同步失败、后端还没翻 Error"这两个窗口，而这两个窗口正是旧实现丢状态的源头。
     */
    private var loadStatus: PlaybackLoadStatus = PlaybackLoadStatus.Idle

    /** 实际生效的画面比例（请求了不支持的档位时会降级到 Fit）。 */
    private var appliedAspect: VideoAspectMode = VideoAspectMode.Fit

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
                    // 只存不发会导致初始缓冲期 UI 缓冲条恒 0
                    //（position 不动、状态机稳在 Opening，combine 不触发）。
                    publish()
                }
        }
    }

    final override fun load(request: PlaybackRequest) {
        if (released) return
        loadStatus = PlaybackLoadStatus.Opening(request.isQualitySwitch)
        // 立刻发布一次：后端可能要过一会才翻到 Opening，UI 得先进入"准备中"。
        publish()
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
                    // 同步抛（未及状态机翻 Error）此前永留 Preparing 转圈。
                    // 不是顶掉就是真失败，记进 loadStatus 由派生函数落成 Error。
                    LogUtil.e(TAG, "openMedia failed", e)
                    loadStatus = PlaybackLoadStatus.Failed(e.message ?: e.toString())
                    publish()
                }
            }
        }
    }

    /**
     * 执行 open。默认 = [onBeforeOpen] 钩子 + `setMediaData`。
     *
     * 桌面 mpv 覆盖它：加载前要先做 ECH 网关改写与 mpv 选项下发，网关失败还要补一次
     * 直连重试 —— 这套是桌面独有的链路，不适合抬进基类。
     */
    protected open suspend fun openMedia(request: PlaybackRequest) {
        onBeforeOpen()
        mediampPlayer.setMediaData(
            data = UriMediaData(
                uri = request.uri,
                headers = request.headers,
            ),
            playWhenReady = request.playWhenReady,
            startPositionMillis = startPositionFor(request),
        )
    }

    /** 开流起始位：切画质从当前播放位置续，新片用请求的起始位（0 或续播位）。 */
    protected fun startPositionFor(request: PlaybackRequest): Long =
        // 用播放器即时位置而非上次发布值（最大 250ms+ 延迟，切档系统性偏小）。
        if (request.isQualitySwitch) {
            mediampPlayer.currentPositionMillis.value.coerceAtLeast(0L)
        } else {
            request.startPositionMs
        }

    /** 每次 open 前的钩子（如 Android 预置空超分表：setVideoEffects 须先于 prepare）。
     *
     * 挂起函数：实现方按需切线程，但**必须在返回前做完**（基类随后立即 setMediaData，
     * fire-and-forget 会导致配置晚于 prepare，首开档位不生效）。
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
        if (isReleased) return
        val safeSpeed = speed.coercePlaybackSpeed()
        val previous = requests.speed
        requests = requests.copy(speed = safeSpeed)
        commandScope.launch {
            val applied = runCatching { mediampPlayer.features[PlaybackSpeed.Key]?.set(safeSpeed) }
            if (applied.isFailure) {
                // feature 缺失/设置失败时回滚请求值并重发，否则 UI 显示假倍速。
                LogUtil.w(TAG, "setPlaybackSpeed failed, revert to $previous: ${applied.exceptionOrNull()?.message}")
                requests = requests.copy(speed = previous)
            }
            // 无论成败都要发一次：成功时后端状态未必变化，不主动发 actual 就不会更新。
            publish()
        }
    }

    // mediamp Surface 直绑后端 impl，本仓 VideoSurface 链路与其无关。
    final override fun attachSurface(surface: VideoSurface) {}
    final override fun detachSurface(surface: VideoSurface) {}

    // ── 画面比例 ─────────────────────────────────────
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
        appliedAspect = effective
        requests = requests.copy(aspect = effective)
        publish()
        commandScope.launch { applyAspectToBackend(effective) }
    }

    /**
     * 把 [mode] 落到后端。默认走 mediamp 的 `VideoAspectRatio` feature
     * （Android 的 resizeMode / iOS 的 videoGravity 都是它）。
     *
     * 桌面 mpv 覆盖为属性级实现：Stretch 档要按**渲染面尺寸**重算，渲染面变化时
     * 由子类自己的看门狗补发（mediamp 的 `keepaspect` 路径给不了这个尺寸依赖）。
     */
    protected open suspend fun applyAspectToBackend(mode: VideoAspectMode) {
        mediampPlayer.features[VideoAspectRatio.Key]?.setMode(mode.toMediampAspect())
    }

    /** 当前生效的画面比例（桌面 Stretch 看门狗按渲染面重算时读它）。 */
    protected val currentAspect: VideoAspectMode get() = appliedAspect

    /** 已释放。子类的命令入口据此短路，语义与基类其余命令一致。 */
    protected val isReleased: Boolean get() = released

    final override fun release() {
        if (released) return
        released = true
        onRelease()
        mediampPlayer.close()
        scope.cancel()
        commandScope.cancel()
        contractState = PlaybackState()
        loadStatus = PlaybackLoadStatus.Idle
        mutableState.value = PlaybackEngineState()
    }

    /** release 前的子类钩子（如 iOS 解绑 PiP）。 */
    protected open fun onRelease() {}

    /**
     * 刚进入 Error 相时调用一次（错误信息已写入后）。
     *
     * 用途举例：超分 effect 编译失败是异步的（Media3 在 GL 线程编，调 setVideoEffects
     * 时不报错），失败会以播放错误浮上来；子类在此把档位降回 OFF，用户点重试即走
     * 空表重播 —— 不自动重载（避免无意义重试与循环），不吞错误卡。
     */
    protected open fun onEnteredError() {}

    /** 状态的唯一写者。任何地方都不要再逐字段改状态。 */
    internal fun publish() {
        if (released) return
        val next = derivePlaybackState(contractState, readTruth(), requests, loadStatus)
        if (enteredError(contractState, next)) onEnteredError()
        contractState = next
        mutableState.value = next.toEngineState()
    }

    /** 从后端读回这一刻的真值。读不到的字段留 null，由派生函数沿用上一次真值。 */
    private fun readTruth(): PlaybackTruth {
        val snapshot = mediampPlayer.state.value
        val props = mediampPlayer.mediaProperties.value
        val status = snapshot.mediaStatus
        return PlaybackTruth(
            phase = when (status) {
                MediaStatus.Idle -> PlaybackPhase.Idle
                MediaStatus.Opening -> PlaybackPhase.Preparing
                MediaStatus.Ready -> PlaybackPhase.Ready
                MediaStatus.Ended -> PlaybackPhase.Ended
                is MediaStatus.Error -> PlaybackPhase.Error
                MediaStatus.Released -> PlaybackPhase.Idle
            },
            isPlaying = snapshot.isPlaying,
            isBuffering = snapshot.isBuffering,
            positionMs = mediampPlayer.currentPositionMillis.value.coerceAtLeast(0L),
            durationMs = props?.durationMillis?.coerceAtLeast(0L) ?: 0L,
            bufferedPositionMs = bufferedPositionMs,
            speed = runCatching { mediampPlayer.features[PlaybackSpeed.Key]?.value }.getOrNull(),
            aspect = appliedAspect,
            videoWidth = props?.videoWidth ?: 0,
            videoHeight = props?.videoHeight ?: 0,
            errorMessage = (status as? MediaStatus.Error)?.error?.let { it.message ?: it.toString() },
        )
    }

    /**
     * 契约快照 → 既有 UI 消费的视图。
     *
     * 注意 [PlaybackEngineState.playbackSpeed] 取的是 **actual** 而不是请求值：
     * 请求了引擎不接受（或尚未接受）的倍速时，UI 必须显示真正生效的值。
     */
    private fun PlaybackState.toEngineState(): PlaybackEngineState = PlaybackEngineState(
        phase = phase,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedPositionMs = bufferedPositionMs,
        playbackSpeed = actualSpeed,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        hasRenderedFirstFrame = hasRenderedFirstFrame,
        errorMessage = errorMessage,
        videoAspect = actualAspect,
    )

    private fun VideoAspectMode.toMediampAspect(): AspectRatioMode = when (this) {
        VideoAspectMode.Fit -> AspectRatioMode.FIT
        VideoAspectMode.Stretch -> AspectRatioMode.STRETCH
        VideoAspectMode.Crop -> AspectRatioMode.CROP
    }

    private companion object {
        const val TAG = "MediampEngineBase"
    }
}
