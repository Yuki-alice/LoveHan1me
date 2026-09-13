package lovehan1me.feature.player

import lovehan1me.core.util.MpvShaders
import lovehan1me.data.network.HanimeProxySelector
import lovehan1me.data.network.currentHttpUserAgent
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
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
import kotlinx.coroutines.withContext
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.FramePreview
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
class DesktopMpvPlaybackEngine(
    /**
     * mpv 用的 HTTP 代理（null = 直连）。默认按应用设置解析，可注入以便真实网络冒烟。
     */
    private val mediaProxyUrl: () -> String? = ::resolveMediaProxyUrl,
) : PlaybackEngine {

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

    /**
     * 是否已经渲染过首帧（**锁存**，只置真不置假）。
     *
     * 原来用 `positionMs > 0` 近似，切画质时位置会短暂回到 0 → 海报闪回；
     * 锁存后只受"重新 load 新片子"影响。
     */
    private var hasRenderedFrame = false

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
                if (positionMs > 0L || (props?.videoWidth ?: 0) > 0) hasRenderedFrame = true
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
                    hasRenderedFirstFrame = hasRenderedFrame,
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
                // ⚠️ 必须在 setMediaData **之前**：mpv 的网络选项在"打开流"那一刻生效。
                // 不做这一步，mpv 会用**直连**去拉流（它不继承 OkHttp 的代理），
                // 在受限网络下表现为 mpv_error=-13（LOADING_FAILED）——页面能开、视频永远转圈。
                mpvHandle()?.let { applyNetworkOptions(it) }
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

    /**
     * M3-b：桌面端支持抓帧 —— 走 mediamp 的 `FramePreview` feature
     * （`MpvFramePreview` 编译在 mediamp-mpv-desktop 里）。
     *
     * ⚠️ **这里必须返回常量，不能去查 `mediampPlayer.features`**：
     * [mediampPlayer] 是 lazy，首次访问会触发 mpv 原生初始化（几百毫秒），
     * 而本方法是**非挂起**的、会被组合期调用（见 [awaitPlayer] 的 KDoc 警告）。
     * 真正的能力在 [grabFrameArgb] 里按 feature 查询，拿不到就返回 null ——
     * 于是"声明支持"与"实际可用"解耦，既不会卡组合，也不会假装成功。
     */
    override fun supportsFrameCapture(): Boolean = true

    /**
     * M3-b：抓取 [positionMs] 处的画面。
     *
     * 两个实现要点：
     * 1. **必须发在 main dispatcher**：mediamp 要求播放器操作走构造时传入的
     *    主线程（桌面 = Swing EDT），与 load/play/seek 同一约束。
     * 2. **尺寸由解码侧产出**：`getPreviewFrame(pos, w, h)` 直接给目标尺寸，
     *    省掉一帧 1080p（8 MB）的中间位图 —— 这是选它而不是"抓全尺寸再自己缩"的原因。
     */
    override suspend fun grabFrameArgb(
        positionMs: Long,
        targetWidth: Int,
        targetHeight: Int,
    ): IntArray? {
        if (targetWidth <= 0 || targetHeight <= 0 || positionMs < 0L) return null
        return runCatching {
            withContext(Dispatchers.Main) {
                val preview = mediampPlayer.features[FramePreview.Key] ?: return@withContext null
                preview.getPreviewFrame(positionMs, targetWidth, targetHeight)?.pixels
            }
        }.getOrNull()
    }

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
     * 把应用的网络配置（代理 + UA）透传给 mpv。
     *
     * ## 为什么必须做：**mpv 是独立的原生网络栈，不继承 OkHttp 的代理设置**
     * 实测（2026-09-13，同一流地址、同一台机器）：
     * - 直连 → `curl: (35) Recv failure: Connection was reset`（15 秒、0 字节）
     * - 经 `127.0.0.1:7897` → **206**（0.3 秒、200 KB）
     * 于是症状是"CF 验证过了、页面也出来了，视频就是打不开"（`mpv_error=-13`）。
     *
     * mpv 的代理选项是 `http-proxy`，透传给 ffmpeg 的 `http_proxy`；
     * 用 [MPVHandle.setPropertyString] 设置并把返回值记进日志 ——
     * 若某天 mpv 把它标成不可运行时修改，日志里会直接看到 `set=false`，不必猜。
     */
    private fun applyNetworkOptions(handle: MPVHandle) {
        mediaProxyUrl()?.let { proxy ->
            val ok = handle.setPropertyString("http-proxy", proxy)
            LogUtil.d(TAG, "mpv http-proxy=$proxy set=$ok")
            if (!ok) LogUtil.w(TAG, "mpv 不接受运行时设置 http-proxy，视频可能仍走直连")
        }
        // UA 与应用 HTTP 层保持一致（站点/CDN 可能按 UA 判定）
        val userAgent = currentHttpUserAgent()
        val uaOk = runCatching { handle.setPropertyString("user-agent", userAgent) }.getOrDefault(false)
        LogUtil.d(TAG, "mpv user-agent set=$uaOk")
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
            // 状态归位：三个 Android 引擎都会重置，桌面此前不重置 —— UI 复用同一个 controller 时
            // 会读到上一部片子的时长/位置（例如"退出播放页后进度条还停在半途"）。
            _state.value = PlaybackEngineState()
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

/**
 * 解析 mpv 该用的 HTTP 代理 URL（null = 直连）。
 *
 * 复用应用自己的 [HanimeProxySelector]：Direct/System/Http/Socks 四种模式与 HTTP 层
 * **同一个判定**（System 模式依赖 JVM 的 `java.net.useSystemProxies`，见 desktopApp 的 jvmArgs）。
 *
 * SOCKS 返回 null 并打日志：FFmpeg 的 `http_proxy` 只支持 HTTP 代理（CONNECT 语义），
 * 把 `socks5://…` 塞进去只会让流更打不开 —— 宁可不设，也不要设错。
 *
 * 设置未就绪（极早的调用/单测）时退回 JVM 默认选择器，再不行就直连。
 */
internal fun resolveMediaProxyUrl(): String? {
    val uri = runCatching { URI("https://hanime1.me/") }.getOrNull() ?: return null
    val selected = runCatching { HanimeProxySelector().select(uri) }
        .recoverCatching { ProxySelector.getDefault()?.select(uri) ?: emptyList() }
        .getOrNull()
    val proxy = selected?.firstOrNull() ?: return null
    return when (proxy.type()) {
        java.net.Proxy.Type.HTTP -> (proxy.address() as? InetSocketAddress)
            ?.let { "http://${it.hostString}:${it.port}" }

        java.net.Proxy.Type.SOCKS -> {
            LogUtil.w("DesktopMpv", "当前是 SOCKS 代理：mpv/ffmpeg 无法透传（只支持 HTTP 代理）")
            null
        }

        else -> null
    }
}
