package lovehan1me.feature.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import lovehan1me.video.contract.safeCombine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lovehan1me.core.util.LogUtil
import lovehan1me.video.contract.VideoEnhancementController
import lovehan1me.video.contract.VideoEnhancementLevels
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayerFactory
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData

/**
 * Gate3-P5：Android 换底 mediamp-exo（v0.5.0）。
 *
 * 相比旧 Exo 引擎（`ExoPlaybackEngine`，已随 Gate3-P6 删除）的收益：
 * - 缓冲/isBuffering 由 mediamp 状态机给真值（含 opening/stall/post-seek 区分）；
 * - 画面比例三档全真（走它家 PlayerView.resizeMode，不再是 setVideoScalingMode 两档）；
 * - aspect fallback 修复：挂 video effects 后 media3 不上报真实尺寸，它家 Surface
 *   从选中轨道 Format 兜底（对我们 P4 超分链是免费修复）。
 *
 * 网络/ECH/超分全复用 P1-P4 成果：
 * - [mediaSourceInterceptor] 里按 UriMediaData 重建 MediaSource，HTTP 栈仍是
 *   本仓 [EchGateDataSourceFactory]（逐请求 ECH 改写，顶层与 HLS 分片各自携带网关头）；
 * - 超分档位照旧 [ExoSuperResolution.effectsFor] 热换，失败降级 OFF 不断播。
 *
 * impl 直调边界：mediamp 只禁 transport/lifecycle 类调用（play/seek/release 等），
 * setVideoEffects/volume 属效果类，允许直调（animeko 同款用法）。
 */
@OptIn(UnstableApi::class)
class MediampExoPlaybackEngine(
    context: Context,
    private val network: PlayerNetworkConfig,
) : MediampPlaybackEngineBase(), AndroidSurfaceSizeAware {

    private val appContext = context.applicationContext

    override val mediampPlayer: ExoPlayerMediampPlayer = ExoPlayerMediampPlayerFactory().create(
        context = appContext,
        parentCoroutineContext = scope.coroutineContext,
        mediaSourceInterceptor = { source, data -> interceptMediaSource(source, data) },
    )

    private val exoPlayer: ExoPlayer get() = mediampPlayer.impl as ExoPlayer

    init {
        startObserving()
        startEnhancementObserver()
    }

    // ── 网络：ECH 逐请求改写（复用 P1 的 EchGateDataSource） ──

    /**
     * mediamp 默认 MediaSource 用它自己的 DefaultHttpDataSource（无 ECH/无自定义 UA 路径）。
     * 这里在 open 钩子里原样重建为旧引擎的 HTTP 栈：UA/headers/ECH 三者行为不变。
     */
    private fun interceptMediaSource(source: MediaSource, data: MediaData): MediaSource {
        val uriData = data as? UriMediaData ?: return source
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(network.userAgent)
            .setDefaultRequestProperties(uriData.headers)
        // ECH 网关：顶层 manifest 与 HLS 分片各自在 open() 时改写
        // （工厂级加头会把顶层域名错贴到分片上，故不在此加）。
        val dataSourceFactory =
            DefaultDataSource.Factory(appContext, EchGateDataSourceFactory(httpFactory, network))
        val item = MediaItem.fromUri(uriData.uri)
        return if (uriData.uri.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
    }

    // ── 超分（视频增强） ──
    //
    // 生效档位是**流**，而且有两个"用户没碰过任何东西"也会变的触发源：视口尺寸
    // （转屏、进/出全屏）与片源尺寸（换清晰度）。落地 scaler 的目标尺寸就是视口，
    // 尺寸一变整条 effect 表必须重挂 —— 不重挂的话 scaler 仍按旧视口出图，
    // 表现就是画面被压扁或拉伸，且用户看不出自己该做什么来恢复。
    //
    // 失败一律降回 OFF 而不是上抛：GL 编译是异步的，失败浮上来时已经是播放错误，
    // 那时只剩"放弃超分"这一条路能保住播放。

    private val requestedLevel = MutableStateFlow(VideoEnhancementLevels.OFF)
    private val mutableEnhancementLevel = MutableStateFlow(VideoEnhancementLevels.OFF)
    private val enhancementLock = Mutex()

    /** 渲染面尺寸（px）。0 = 尚未量出；0 参与时不挂 scaler，见 [ExoSuperResolution.effectsFor]。 */
    private val surfaceSize = MutableStateFlow(0 to 0)

    override val enhancement: VideoEnhancementController = object : VideoEnhancementController {
        override val levels: List<Int> = VideoEnhancementLevels.ALL
        override val level: StateFlow<Int> = mutableEnhancementLevel.asStateFlow()

        override suspend fun setLevel(level: Int): Int {
            if (isReleased) return mutableEnhancementLevel.value
            requestedLevel.value = level
            return applyEnhancement()
        }
    }

    private fun startEnhancementObserver() {
        scope.launch {
            safeCombine(
                state.map { it.videoWidth to it.videoHeight },
                surfaceSize,
            ) { video, surface -> video to surface }
                .distinctUntilChanged()
                .collect {
                    if (requestedLevel.value != ExoSuperResolution.OFF) applyEnhancement()
                }
        }
    }

    /** 加锁重挂当前请求档位，返回**实际生效**档位（失败即 OFF）。 */
    private suspend fun applyEnhancement(): Int = enhancementLock.withLock {
        val level = requestedLevel.value
        val applied = if (runCatching { applyEffects(level) }.isSuccess) {
            level
        } else {
            LogUtil.w(TAG, "超分档位 $level 挂载失败，降回 OFF")
            runCatching { applyEffects(ExoSuperResolution.OFF) }
            ExoSuperResolution.OFF
        }
        mutableEnhancementLevel.value = applied
        applied
    }

    /**
     * 按当前档位与现取尺寸挂 effect。
     *
     * 资产读取（首次约几百 KB，解 APK）留在调用线程，只有 `setVideoEffects` 切 Main ——
     * 那是 Media3 的线程约束，而把 IO 也搬过去会让首开在主线程上读文件。
     */
    private suspend fun applyEffects(level: Int) {
        val video: VideoSize = exoPlayer.videoSize
        val (width, height) = surfaceSize.value
        val effects = ExoSuperResolution.effectsFor(
            level = level,
            videoWidth = (video.width * video.pixelWidthHeightRatio).toInt(),
            videoHeight = video.height,
            surfaceWidth = width,
            surfaceHeight = height,
        )
        withContext(Dispatchers.Main) { exoPlayer.setVideoEffects(effects) }
        LogUtil.i(TAG, "超分档位 $level：挂 ${effects.size} 条 effect（片源 ${video.width}x${video.height}，渲染面 ${width}x$height）")
    }

    /**
     * 超分致错自愈：GL 编译是异步的，失败浮上来时已经是播放错误。
     * 这里把请求档位降回 OFF（下次 load/onBeforeOpen 即空表），用户点重试就回到正常播放；
     * 不自动重载、不吞错误卡；档位已是 OFF 时不动作，不可能循环。
     */
    override fun onEnteredError() {
        if (mutableEnhancementLevel.value == ExoSuperResolution.OFF) return
        LogUtil.w(TAG, "疑似超分致错，自动降回 OFF，用户重试即恢复播放")
        requestedLevel.value = ExoSuperResolution.OFF
        scope.launch { applyEnhancement() }
    }

    /** 渲染面尺寸（超分 scaler 的落地目标）：由 PlatformVideoSurface 按布局尺寸转交。 */
    override fun updateSurfaceSize(width: Int, height: Int) {
        val current = surfaceSize.value
        val next = (if (width > 0) width else current.first) to
            (if (height > 0) height else current.second)
        if (next != current) surfaceSize.value = next
    }

    /**
     * 每次 open 前预置 effect 表（Media3 约束：`setVideoEffects` 须先于 prepare，
     * 否则播放中途换表不生效；mediamp openImpl 内部自己 prepare，这个钩子正好卡在它之前）。
     * OFF 也走同一条路：挂空表就是"把 effect 图建起来但什么都不做"。
     */
    override suspend fun onBeforeOpen() {
        runCatching { applyEffects(requestedLevel.value) }
            .onFailure { LogUtil.w(TAG, "预置 effect 表失败，按 OFF 播放", it) }
    }

    // ── 音量（mediamp-exo 无 AudioLevelController feature，直调，一行） ──

    override fun setVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    private companion object {
        const val TAG = "MediampExoEngine"
    }
}
