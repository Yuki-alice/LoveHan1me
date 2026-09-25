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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lovehan1me.core.util.LogUtil
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

    // ── 超分（P4 成果原样接续） ──

    private var superResolutionLevel = ExoSuperResolution.OFF

    override fun supportsSuperResolution(): Boolean = true

    override fun setSuperResolution(index: Int) {
        val level = index.coerceIn(ExoSuperResolution.OFF, ExoSuperResolution.QUALITY)
        if (level == superResolutionLevel) return
        superResolutionLevel = level
        scope.launch(Dispatchers.Main) { applyVideoEffects() }
    }

    // 超分致错自愈（Gate4-2 真机教训）：GL 编译是异步的，失败浮上来时已经是播放错误。
    // 这里把档位降回 OFF（下次 load/onBeforeOpen 即空表），用户点重试就回到正常播放；
    // 不自动重载、不吞错误卡；档位已是 OFF 时不动作，不可能循环。
    override fun onEnteredError() {
        if (superResolutionLevel != ExoSuperResolution.OFF) {
            LogUtil.w(TAG, "疑似超分致错，自动降回 OFF，用户重试即恢复播放")
            setSuperResolution(ExoSuperResolution.OFF)
        }
    }

    /** 渲染面尺寸（超分 scaler 判断 needsUpscale 用）：由 PlatformVideoSurface 按布局尺寸转交。 */
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    override fun updateSurfaceSize(width: Int, height: Int) {
        if (width > 0) surfaceWidth = width
        if (height > 0) surfaceHeight = height
    }

    /**
     * 每次 open 前预置 effect 表（Media3 约束：setVideoEffects 须先于 prepare；
     * mediamp openImpl 内部自己 prepare，这个钩子正好卡在它之前）。
     * 档位记忆沿用旧引擎：OFF 挂空表，非 OFF 挂真表（跨 load 保留档位）。
     *
     * B1 修复：此前 OFF 分支在 Default 线程同步直调（线程违规），非 OFF 分支
     * fire-and-forget 到 Main（可能晚于 prepare，首开超分不生效）。
     * 现统一切 Main 且挂起等完成，基类随后才 setMediaData。
     */
    override suspend fun onBeforeOpen() {
        // openMedia 跑在 Default 线程；setVideoEffects 必须 Main，整个切过去
        // （applyVideoEffects 内的资产 IO 走内存缓存，首次约几百 KB，主线程可接受）。
        withContext(Dispatchers.Main) {
            if (superResolutionLevel == ExoSuperResolution.OFF) {
                runCatching { exoPlayer.setVideoEffects(emptyList()) }
            } else {
                applyVideoEffects()
            }
        }
    }

    /**
     * 按当前档位挂 effect，失败即降级 OFF（吞异常回空管线，最差"超分没生效但片子照播"）。
     */
    private suspend fun applyVideoEffects() {
        try {
            val videoSize: VideoSize = exoPlayer.videoSize
            exoPlayer.setVideoEffects(
                ExoSuperResolution.effectsFor(
                    level = superResolutionLevel,
                    videoWidth = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt(),
                    videoHeight = videoSize.height,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                )
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "setVideoEffects failed, fallback to OFF", e)
            superResolutionLevel = ExoSuperResolution.OFF
            try {
                exoPlayer.setVideoEffects(emptyList())
            } catch (fallbackError: Exception) {
                LogUtil.e(TAG, "fallback to OFF also failed", fallbackError)
            }
        }
    }

    // ── 音量（mediamp-exo 无 AudioLevelController feature，直调，一行） ──

    override fun setVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    private companion object {
        const val TAG = "MediampExoEngine"
    }
}
