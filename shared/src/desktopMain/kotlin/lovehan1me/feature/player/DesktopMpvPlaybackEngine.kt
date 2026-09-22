package lovehan1me.feature.player

import lovehan1me.core.util.MpvShaders
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.HanimeProxySelector
import lovehan1me.data.network.currentHttpUserAgent
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import lovehan1me.core.util.materializeMpvShaders
import lovehan1me.core.util.parseMpvCustomParams
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
import kotlin.concurrent.thread
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
 * - mpv 选项分两条路径下发，都在 `setMediaData` 之前：网络/身份（代理 + UA，见
 *   [applyNetworkOptions]）与「MPV 高级设置」页的用户选项（见 [applyMpvSettings]，
 *   每次 load 重发以覆盖上一次的状态）。
 * - **`vo` 不由本项目决定**：mediamp 持有 `vo=gpu-next`/`libmpv` 组合，故桌面端
 *   「GPU Next 渲染器」开关无法生效 → 设置页按平台能力隐藏（[applyMpvSettings] KDoc 有证据）。
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
     * 惰性初始化（解压 + dlopen + mpv_create）在 Default 线程上执行 —— 即使
     * 启动预载还没跑完就进了视频页，EDT 也只等一次返回，不会阻塞在
     * SynchronizedLazyImpl 的锁上把整个界面冻住。
     */
    suspend fun awaitPlayer(): MpvMediampPlayer =
        withContext(Dispatchers.Default) { mediampPlayer }

    init {
        // 预热放后台线程：mpv 原生初始化（解压 49MB dylib + dlopen + mpv_create）
        // 在 macOS 首次装载实测 10~15s，之前排进 EDT 异步队列，把视频详情页的
        // 首次组合后到数据到货前整段冻死（compose-done +40ms → info-ready +12s，
        // jstack 实证 EDT 全程卡在 NativeLibraries.load）。挪到 Default 后，
        // EDT 只负责 setMediaData 等真正的控制面调用。
        scope.launch {
            val player = mediampPlayer
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
            }.collect { state ->
                _state.value = state
            }
        }
    }

    override fun load(request: PlaybackRequest) {
        if (released) return
        LogUtil.d(TAG, "load: ${request.uri} (headers=${request.headers.keys})")
        issueLoad(request)
    }

    private fun issueLoad(request: PlaybackRequest) {
        mainScope.launch {
            runCatching {
                // 惰性初始化先在 Default 线程摸热：万一启动预载尚未完成，
                // 这里也只会挂起等待，不会把 EDT 阻塞在 lazy 锁上。
                val player = withContext(Dispatchers.Default) { mediampPlayer }
                // ⚠️ 必须在 setMediaData **之前**：mpv 的网络选项在"打开流"那一刻生效。
                // 不做这一步，mpv 会用**直连**去拉流（它不继承 OkHttp 的代理），
                // 在受限网络下表现为 mpv_error=-13（LOADING_FAILED）——页面能开、视频永远转圈。
                // 网络选项与「MPV 高级设置」同批下发，都在"打开流"那一步之前生效。
                mpvHandle()?.let { handle ->
                    applyNetworkOptions(handle)
                    applyMpvSettings(handle)
                }
                val (mediaUri, mediaHeaders) = mediaUrlForGate(request)
                player.setMediaData(
                    UriMediaData(mediaUri, mediaHeaders),
                    request.playWhenReady,
                    request.startPositionMs,
                )
                if (request.playWhenReady) {
                    player.play()
                }
            }.onFailure {
                // R2：失败即停（学 animeko）。此前这里 1.5s 后静默重载同一 URL，
                // 纹理未释放就再开一流；手动重试（错误卡按钮调 load()）还在。
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
        // ⚠️ ECH 网关启用时**不能再给 mpv 设 http-proxy**：媒体 URL 已被改写到
        // 127.0.0.1（见 [mediaUrlForGate]），而 ffmpeg 的 http_proxy 没有 bypass
        // 列表——它会把这条件对本地回环的请求也代理出去，网关永远收不到。
        if (EchGate.port > 0) {
            LogUtil.d(TAG, "ECH 网关启用，跳过 mpv http-proxy（媒体走本地回环）")
        } else {
            mediaProxyUrl()?.let { proxy ->
                val ok = handle.setPropertyString("http-proxy", proxy)
                LogUtil.d(TAG, "mpv http-proxy=$proxy set=$ok")
                if (!ok) LogUtil.w(TAG, "mpv 不接受运行时设置 http-proxy，视频可能仍走直连")
            }
        }
        // UA 与应用 HTTP 层保持一致（站点/CDN 可能按 UA 判定）
        val userAgent = currentHttpUserAgent()
        val uaOk = runCatching { handle.setPropertyString("user-agent", userAgent) }.getOrDefault(false)
        LogUtil.d(TAG, "mpv user-agent set=$uaOk")
    }

    /**
     * 把「MPV 高级设置」页的用户选项下发到 mpv。
     *
     * ## 时机：与 [applyNetworkOptions] 同批、且在 `setMediaData` 之前
     * 这些是"打开流那一刻"才读的选项（cache-secs / network-timeout / framedrop /
     * deband / interpolation …），必须在 load 之前写入。**每次 load 都重发一遍是刻意的**：
     * mediamp 的 player 跨视频复用（不会重建），只有重发才能让"改完设置 → 下一个视频
     * 即生效"，也才能把上一个视频留下的状态（如 display-resample）洗干净。
     *
     * ## 与 Android 侧（`androidMain/MpvPlaybackEngine.mpvOptions`）的三处必要差异
     *
     * 1. **不下发 `vo`** —— `enableGpuNextRenderer` 在桌面**无法生效，故设置页隐藏该行**
     *    （`SettingsPlatformCapabilities.mpvVideoOutput = false`）。
     *    证据（反查 `mediamp-mpv-desktop-0.3.2.jar` 的 `JvmMpvMediampPlayer`）：
     *    它自己写死了 `vo=gpu-next` / `vo=libmpv` + `gpu-context` + `gpu-dumb-mode`
     *    的一整套组合，配合它自己的 render API 交付帧。改写 `vo` 会让渲染面收不到帧。
     *    换言之：桌面上"用 gpu-next"本就是底色，那个开关没有可翻转的余地。
     * 2. **`hwdec` 只有两档**：桌面没有 mediacodec / vulkan-copy，`HW`/`HW+`/`Vulkan`/
     *    `Vulkan+` 一律折叠成 mpv 的自动硬解（macOS=videotoolbox / Windows=d3d11va /
     *    Linux=vaapi）。与设置页收敛后的两档选项一致（`mpvMediacodecHwdec = false`）。
     *    注意 mediamp 自己也会设 `hwdec`（`OpenGLRenderContextLifecycle` 随渲染上下文
     *    创建设置），所以这里属于"覆盖它的默认值"。
     * 3. **不设 `vd-lavc-threads` / `cache` / `cache-pause`**：这三条在 Android 侧是解码器
     *    与缓冲调优（初始化前生效），桌面属 mediamp 的职责范围，抢过来只会和它的 render
     *    配置打架。
     *
     * ## 顺序即优先级
     * `profile` 最先（`gpu-hq` 会顺带改一串 `scale`/`deband` 缩放属性），随后逐项写入
     * 用户设置（覆盖 profile 的副作用值），最后 [parseMpvCustomParams] 收尾 —— 与 Android
     * 侧"`mpvOptions()` → `parseCustomMpvParams()`"的顺序一致，保证用户手写的
     * 「自定义参数」永远是最终裁决者。
     *
     * ## 失败不致命，逐条记录
     * mpv 有一部分选项只在初始化前可改，运行时下发会返回 false。这里不因单项失败中断播放，
     * 而是把 `set=false` 的项写进日志 —— 那就是"这一项在桌面不生效"的直接证据，不必猜。
     */
    private fun applyMpvSettings(handle: MPVHandle) {
        val options = buildMap {
            put(
                "profile",
                SettingsRepository.mpvProfile.takeIf { it == "gpu-hq" || it == "fast" } ?: "default",
            )
            put("hwdec", if (SettingsRepository.mpvHwdec == "SW") "no" else "auto")
            put("msg-level", "all=" + if (LogUtil.enabled) "debug" else "warn")
            put("cache-secs", SettingsRepository.mpvCacheSecs.toString())
            put("framedrop", if (SettingsRepository.mpvFramedrop) "vo" else "no")
            put("deband", if (SettingsRepository.mpvDeband) "yes" else "no")
            put("network-timeout", SettingsRepository.mpvNetworkTimeout.toString())
            // ⚠️ 字段名与文案相反（历史遗留，勿"修正"）：`mpvTlsVerify = true` 的 UI 文案是
            // 「忽略 HTTPS 证书验证」，对应 mpv 的 `tls-verify=no`。与 Android 侧同一约定。
            put("tls-verify", if (SettingsRepository.mpvTlsVerify) "no" else "yes")
            // 补帧：关掉时必须显式回落 `video-sync=audio`（mpv 默认值），否则上一次的
            // display-resample 会残留到下一个视频（player 跨视频复用）。
            put("interpolation", if (SettingsRepository.mpvInterpolation) "yes" else "no")
            if (SettingsRepository.mpvInterpolation) {
                put("tscale", "oversample")
                put("video-sync", "display-resample")
            } else {
                put("video-sync", "audio")
            }
            putAll(parseMpvCustomParams(SettingsRepository.customMpvParams))
        }
        val rejected = mutableListOf<String>()
        options.forEach { (key, value) ->
            val ok = runCatching { handle.setPropertyString(key, value) }.getOrDefault(false)
            if (ok) {
                LogUtil.d(TAG, "mpv setting $key=$value")
            } else {
                rejected += "$key=$value"
            }
        }
        if (rejected.isNotEmpty()) {
            LogUtil.w(
                TAG,
                "mpv 拒绝运行时设置 ${rejected.size}/${options.size} 项：$rejected" +
                    "（多为只在初始化前生效的选项；自定义参数写错键名也会落到这里）",
            )
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

        /**
         * 启动预载：把 mediamp 的 mpv 原生运行时在**后台线程**提前消化。
         *
         * 这是"点开第一个视频卡冻结 2-3 分钟"的根因修复：此前解压 + dlopen 的
         * 大成本在首次进视频页时由 EDT 承担，期间重组、数据到货的 collect、
         * 一切输入全部停摆。预载后用户点进视频页时原生库已在进程内，引擎
         * 惰性初始化只剩 mpv_create（几十毫秒，且在后台线程）。
         *
         * ## 为什么是"固定缓存目录"而不是临时目录
         * mediamp 默认每次进程运行都 `Files.createTempDirectory("mediamp-mpv")`
         * 解压 ~50MB dylib 进新目录并 `deleteOnExit` —— 解压是**每次运行**都逃
         * 不掉、跟 dlopen 叠加才有的 10~15s。
         *
         * 这里把运行时目录固定在 `~/.lovehan1me/mpv_runtime`：
         * - 首次：解压一次 + dlopen（后台线程，首页不受阻）；
         * - 以后每次启动：产物已存在 → 直接 load，**省掉解压**，只剩 dlopen。
         *   于是"构建后首次点开卡顿"不再逐次复现（除真·首次装机）。
         *
         * ## 实现
         * mediamp 的 `LibraryLoader.setRuntimeLibraryDirectory(path, extractIfNeeded)`
         * 在 Kotlin 编译层是 internal（这正是源码里用 create-temp-player 走公开 API
         * 的原因），故用反射调用它来固定目录。反射失败只记一行日志，退回
         * 原临时目录复用创建临时 player 的公开路径兜底 —— 功能不受损，只是
         * 每次运行仍要重新解压。两套路径都 safe。
         */
        fun preloadAsync() {
            thread(name = "mpv-native-preload", isDaemon = true) {
                val startedAt = System.currentTimeMillis()
                val cacheDir = mpvRuntimeDirectory()
                val hadExisting = cacheDir.resolve(
                    "libmpv.dylib",
                ).isFile // 粗判：上次是否已解压过
                runCatching {
                    try {
                        // 反射：固定运行时目录并触发"解压(首次)/直接 load(复用)+dlopen"。
                        // extractIfNeeded=true：产物缺失时才解压（见 LibraryLoader.desktop.kt）。
                        val cls = Class.forName("org.openani.mediamp.mpv.LibraryLoader")
                        val instance = cls.getField("INSTANCE").get(null)
                        val method = cls.getMethod(
                            "setRuntimeLibraryDirectory",
                            String::class.java,
                            Boolean::class.javaPrimitiveType,
                        )
                        method.invoke(instance, cacheDir.absolutePath, true)
                    } catch (reflEx: Throwable) {
                        LogUtil.w(
                            TAG,
                            "mpv 反射固定目录不可用（mediamp 升级？退回临时目录路径）: ${reflEx.message}",
                        )
                        // 兜底：公开 API 创建临时 player，仍能达成"原生库已入进程"。
                        val preloadScope =
                            CoroutineScope(SupervisorJob() + Dispatchers.Default)
                        try {
                            MpvMediampPlayerFactory()
                                .create(ENGINE_TOKEN, preloadScope.coroutineContext)
                                .close()
                        } finally {
                            preloadScope.cancel()
                        }
                    }
                }.onSuccess {
                    LogUtil.i(
                        TAG,
                        "mpv natives preloaded in ${System.currentTimeMillis() - startedAt}ms"
                            + if (hadExisting) "（复用缓存，豁免解压）" else "（首次解压）",
                    )
                }.onFailure {
                    LogUtil.w(TAG, "mpv preload failed（视频页将退回懒加载）: ${it.message}")
                }
            }
        }

        /** mpv 原生库固定缓存目录：首次解压后跨**进程**运行复用（见 [preloadAsync]）。 */
        private fun mpvRuntimeDirectory(): java.io.File {
            val dir = java.io.File(System.getProperty("user.home"), ".lovehan1me/mpv_runtime")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }
}

/**
 * 把媒体 URL 改写到本地 ECH 网关（网关没运行则原样返回）。
 *
 * 判定收敛到 commonMain [EchGatePolicy]（与 OkHttp 拦截器 / Ktor 插件同一份逻辑）。
 *
 * ## 为什么视频必须单独处理
 * mpv 有自己的网络栈（ffmpeg），**不继承 OkHttp 的拦截器**——页面能开、视频打不开，
 * 根因就在这。而实测视频直链在 `vdownload.hembed.com`（CDN77），和站点一样被 SNI 阻断，
 * 所以要把 URL 也交给网关，由它按域名的 CNAME 真名出站。
 *
 * 改写形状与 HTTP 层一致：`http://127.0.0.1:<port>/path?query` + `X-Ech-Target: <原 host>`。
 * 网关按该头还原目标，Host 头则由它自己按策略决定（CNAME 降级时要换成真名）。
 *
 * 只处理 https：本地文件、http 直链不掺和。
 */
private fun mediaUrlForGate(request: PlaybackRequest): Pair<String, Map<String, String>> {
    val rewrite = EchGatePolicy.rewrite(request.uri, EchGate.port)
        ?: return request.uri to request.headers
    return rewrite.url to (request.headers + (EchGatePolicy.TARGET_HEADER to rewrite.targetHost))
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
