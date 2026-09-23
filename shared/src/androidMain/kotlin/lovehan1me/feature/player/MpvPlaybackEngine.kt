package lovehan1me.feature.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import lovehan1me.core.constant.USER_AGENT
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGatePolicy
import lovehan1me.data.network.HanimeProxySelector
import lovehan1me.core.util.AnimeShaders.getCert
import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.materializeMpvShaders
import lovehan1me.core.util.parseMpvCustomParams
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "MpvPlaybackEngine"

class MpvPlaybackEngine(
    private val context: Context,
) : PlaybackEngine, AndroidSurfaceSizeAware {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackEngineState())
    private var currentSurface: VideoSurface? = null
    private var currentPfd: ParcelFileDescriptor? = null
    private var detachedFd: Int? = null
    private var pendingRequest: PlaybackRequest? = null
    private var initialized = false
    private var released = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var lastVideoWidth = 0
    private var lastVideoHeight = 0
    private var hasRenderedFrame = false
    private var hasReachedEndOfFile = false
    private var lastKnownPositionMs = 0L
    private var lastKnownDurationMs = 0L
    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) = publishState()
        override fun eventProperty(property: String, value: Double) = publishState()
        override fun eventProperty(property: String, value: Long) = publishState()
        override fun eventProperty(property: String, value: Boolean) {
            if (property == "eof-reached") hasReachedEndOfFile = value
            publishState()
        }
        override fun eventProperty(property: String, value: String) = publishState()

        override fun event(eventId: Int) {
            when (eventId) {
                MPVLib.mpvEventId.MPV_EVENT_START_FILE -> {
                    hasReachedEndOfFile = false
                    lastKnownPositionMs = 0L
                    lastKnownDurationMs = 0L
                    mutableState.value = mutableState.value.copy(
                        phase = PlaybackPhase.Preparing,
                        isBuffering = true,
                        errorMessage = null,
                    )
                }

                MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                    pendingRequest?.let { request ->
                        MPVLib.setPropertyDouble("speed", requestSpeed.toDouble())
                        if (request.startPositionMs > 0L) {
                            seekTo(request.startPositionMs)
                        }
                        if (request.playWhenReady) startPlayback()
                    }
                    // G2-3b：换流后重下画面类偏好（与倍速同批，避免"换档后比例打回 Fit"）。
                    runCatching { applyVideoAspect() }
                    runCatching { applyPictureAdjust() }
                    mutableState.value = mutableState.value.copy(
                        phase = PlaybackPhase.Ready,
                        isBuffering = false,
                    )
                }

                MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
                    val playbackState = mutableState.value
                    val reachedRecordedDuration = lastKnownDurationMs > 0L &&
                            lastKnownPositionMs >=
                            (lastKnownDurationMs - NORMAL_END_TOLERANCE_MS).coerceAtLeast(0L)
                    val endedNormally = hasReachedEndOfFile ||
                            MPVLib.getPropertyBoolean("eof-reached") == true ||
                            reachedRecordedDuration
                    mutableState.value = playbackState.copy(
                        phase = if (endedNormally) PlaybackPhase.Ended else PlaybackPhase.Error,
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = if (endedNormally) null else "Playback failed before reaching end of file",
                    )
                }

                MPVLib.mpvEventId.MPV_EVENT_SHUTDOWN -> {
                    // G2-3b：画面比例是**用户偏好**，不是播放状态 —— 关机事件重置播放状态时
                    // 要把它带过去，否则 UI 上"选中的 Crop"会在这一帧闪回 Fit。
                    mutableState.value = PlaybackEngineState(videoAspect = aspectMode)
                }
            }
        }
    }
    private var requestSpeed = PlayerDefaults.DEFAULT_SPEED

    /** G2-3b：当前画面比例（状态上报与 mpv 属性重放都以它为准）。 */
    private var aspectMode: VideoAspectMode = VideoAspectMode.Fit

    /** G2-3b：当前画面调节（mpv 的 brightness/contrast/saturation）。 */
    private var pictureAdjust: PictureAdjust = PictureAdjust.Neutral

    override val state: StateFlow<PlaybackEngineState> = mutableState.asStateFlow()

    override fun load(request: PlaybackRequest) {
        check(!released) { "Playback engine has already been released" }
        initializeIfNeeded()
        pendingRequest = request
        // 切画质：**保留画面与尺寸，也不先 `loadfile ""` 卸载旧文件** ——
        // 那一步保证旧画面先消失，正是"切档必黑一下"的直接原因。
        // 用 `loadfile <新> replace` 直接替换即可（replace 语义本就是替换当前文件）。
        if (!request.isQualitySwitch) {
            lastVideoWidth = 0
            lastVideoHeight = 0
            hasRenderedFrame = false
        }
        MPVLib.setPropertyBoolean("pause", true)
        if (!request.isQualitySwitch) {
            MPVLib.command(arrayOf("loadfile", "", "replace"))
        }
        val path = prepareUri(request.uri.toUri())
        if (path == null) {
            mutableState.value = mutableState.value.copy(
                phase = PlaybackPhase.Error,
                errorMessage = "Unable to open media URI",
            )
            return
        }
        // ECH 网关：mpv 不继承 OkHttp 拦截器，顶层 URL 在此改写
        // （与桌面 mediaUrlForGate 同语义，判定收敛到 EchGatePolicy）。
        val playbackPath = applyEchGateForLoad(request, path)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.command(arrayOf("loadfile", playbackPath, "replace"))
        currentSurface?.let {
            MPVLib.attachSurface(it)
            applySurfaceSize()
        }
        mutableState.value = mutableState.value.copy(
            phase = PlaybackPhase.Preparing,
            isBuffering = true,
            errorMessage = null,
            videoWidth = if (request.isQualitySwitch) lastVideoWidth else 0,
            videoHeight = if (request.isQualitySwitch) lastVideoHeight else 0,
            hasRenderedFirstFrame = request.isQualitySwitch && hasRenderedFrame,
        )
    }

    override fun play() = startPlayback()

    override fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
        publishState()
    }

    override fun seekTo(positionMs: Long) {
        MPVLib.command(arrayOf("seek", (positionMs.coerceAtLeast(0L) / 1000.0).toString(), "absolute", "exact"))
        publishState()
    }

    override fun setPlaybackSpeed(speed: Float) {
        requestSpeed = speed.coerceIn(0.25f, 5f)
        MPVLib.setPropertyDouble("speed", requestSpeed.toDouble())
        publishState()
    }

    override fun setVolume(volume: Float) {
        MPVLib.setPropertyDouble("volume", (volume.coerceIn(0f, 1f) * 100f).toDouble())
    }

    override fun attachSurface(surface: VideoSurface) {
        if (released) return
        currentSurface = surface
        if (initialized) {
            MPVLib.attachSurface(surface)
            MPVLib.setOptionString("force-window", "yes")
            MPVLib.setPropertyString("vo", videoOutput)
            applySurfaceSize()
        }
    }

    override fun detachSurface(surface: VideoSurface) {
        if (released) return
        if (currentSurface == surface) {
            currentSurface = null
            if (initialized) {
                MPVLib.setPropertyString("vo", "null")
                MPVLib.setOptionString("force-window", "no")
                MPVLib.detachSurface()
            }
        }
    }

    override fun updateSurfaceSize(width: Int, height: Int) {
        if (released || width <= 0 || height <= 0) return
        surfaceWidth = width
        surfaceHeight = height
        applySurfaceSize()
    }

    override fun supportsSuperResolution(): Boolean = true

    // ── G2-3b：画面比例 / 画面调节（mpv 原生三档全支持）──────────

    override fun supportedAspectModes(): List<VideoAspectMode> =
        listOf(VideoAspectMode.Fit, VideoAspectMode.Stretch, VideoAspectMode.Crop)

    override fun supportsPictureAdjust(): Boolean = true

    override fun setVideoAspect(mode: VideoAspectMode) {
        if (released) return
        aspectMode = mode
        mutableState.value = mutableState.value.copy(videoAspect = mode)
        if (!initialized) return
        runCatching { applyVideoAspect() }
            .onFailure { LogUtil.w(TAG, "setVideoAspect failed: ${it.message}") }
    }

    override fun setPictureAdjust(brightness: Float, contrast: Float, saturation: Float) {
        if (released) return
        pictureAdjust = PictureAdjust(
            brightness = PictureAdjust.clamp(brightness),
            contrast = PictureAdjust.clamp(contrast),
            saturation = PictureAdjust.clamp(saturation),
        )
        if (!initialized) return
        runCatching { applyPictureAdjust() }
            .onFailure { LogUtil.w(TAG, "setPictureAdjust failed: ${it.message}") }
    }

    /**
     * 画面比例 → mpv 属性。
     *
     * 与桌面端同一套映射（Fit = 关覆写 + panscan 0；Crop = panscan 1；Stretch = DAR
     * 覆写成容器比值），差别只在"容器尺寸从哪来"：桌面端问 mpv 的 `dwidth/dheight`，
     * Android 侧 [AndroidSurfaceSizeAware] 已经把 SurfaceView 的实测尺寸送进来了，
     * 直接除就行（也更准 —— 不依赖 mpv 侧窗口状态是否刷新）。
     */
    private fun applyVideoAspect() {
        when (aspectMode) {
            VideoAspectMode.Fit -> {
                MPVLib.setPropertyString("panscan", "0")
                MPVLib.setPropertyString("video-aspect-override", "-1")
            }

            VideoAspectMode.Crop -> {
                MPVLib.setPropertyString("video-aspect-override", "-1")
                MPVLib.setPropertyString("panscan", "1.0")
            }

            VideoAspectMode.Stretch -> {
                MPVLib.setPropertyString("panscan", "0")
                if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                    // 渲染面尺寸还没回调上来：先退回 Fit，等 surfaceChanged 补一次。
                    MPVLib.setPropertyString("video-aspect-override", "-1")
                    return
                }
                MPVLib.setPropertyString(
                    "video-aspect-override",
                    (surfaceWidth.toDouble() / surfaceHeight.toDouble()).toString(),
                )
            }
        }
    }

    /** 亮度/对比/饱和：mpv 三个属性都是 -100~100，0 = 原始。 */
    private fun applyPictureAdjust() {
        MPVLib.setPropertyString("brightness", pictureAdjust.brightness.toInt().toString())
        MPVLib.setPropertyString("contrast", pictureAdjust.contrast.toInt().toString())
        MPVLib.setPropertyString("saturation", pictureAdjust.saturation.toInt().toString())
    }

    // ── M3-b：抓帧（GIF 录制；M3-c 截图可复用）──────────────

    /** mpv 侧有 mpv-android 自带的取帧 API，恒支持（见 [grabFrameArgb] 的说明）。 */
    override fun supportsFrameCapture(): Boolean = true

    /**
     * 抓 [positionMs] 处的画面。
     *
     * ## 走的什么 API
     * `MPVLib.grabThumbnail(dim)`。它的语义**不是**文档里能查到的，是从 mpv-android 的
     * JNI 胶水层（`libplayer.so`，仅 23KB）反查出来的 —— 字符串表里有：
     * `screenshot-raw`、`screenshot w:%d h:%d stride:%d`、`libswscale.so`、`grabbing thumbnail`。
     * 即：发 mpv 的 **`screenshot-raw`** 抓**当前显示帧**，再用 libswscale 缩到指定尺寸返回 Bitmap。
     *
     * ## 因此必须先"等帧"
     * `screenshot-raw` 读的是**显示侧**缓冲，`seek` 命令返回时画面往往还是旧帧。
     * 所以这里 seek 后交给 [FrameReadyWaiter] 等位置追平，再抓；超时则返回 null
     * （让 `GifRecorder` 如实报失败，**绝不退化成抓旧帧**）。
     *
     * ## 录制期间暂停
     * 抓帧是"定位—抓—定位—抓"，播放继续往前跑只会让画面与目标错开，故先暂停。
     */
    override suspend fun grabFrameArgb(
        positionMs: Long,
        targetWidth: Int,
        targetHeight: Int,
    ): IntArray? {
        if (released || !initialized || currentSurface == null) return null
        if (targetWidth <= 0 || targetHeight <= 0 || positionMs < 0L) return null

        MPVLib.setPropertyBoolean("pause", true)
        seekTo(positionMs)

        val ready = FrameReadyWaiter(
            nowMs = { currentEpochMillis() },
            delayMs = { delay(it) },
        ).await(
            targetMs = positionMs,
            positionProvider = { lastKnownPositionMs },
            seekingProvider = { MPVLib.getPropertyBoolean("seeking") == true },
        )
        if (!ready) {
            LogUtil.w(TAG, "抓帧超时：目标 ${positionMs}ms，位置停在 ${lastKnownPositionMs}ms")
            return null
        }

        // 长边给 mpv，让它在解码/缩放侧直接产出小图（省掉一帧全尺寸 Bitmap 的分配）
        val dimension = maxOf(targetWidth, targetHeight)
        val bitmap = withContext(Dispatchers.Main) {
            runCatching { MPVLib.grabThumbnail(dimension) }.getOrNull()
        } ?: return null
        return try {
            bitmap.toArgbPixels(dimension)
        } finally {
            bitmap.recycle()
        }
    }

    override fun setSuperResolution(index: Int) {
        // 阶段一②：shader 统一走 composeResources/files（三端一份），落盘在 IO 线程。
        // 原 `AnimeShaders.getShader` 在目录缺失时会抛 IllegalStateException，
        // 这里改成失败即放弃挂 shader（与桌面端的降级策略一致）。
        scope.launch(Dispatchers.IO) {
            val paths = materializeMpvShaders(index) ?: return@launch
            MPVLib.command(arrayOf("change-list", "glsl-shaders", "set", paths))
        }
    }

    override fun release() {
        if (released) return
        released = true
        if (initialized) {
            MPVLib.setPropertyBoolean("pause", true)
            MPVLib.command(arrayOf("loadfile", "", "replace"))
            MPVLib.setOptionString("force-window", "no")
            MPVLib.detachSurface()
            currentSurface = null
            MPVLib.removeObserver(observer)
        }
        closeCurrentFile()
        scope.cancel()
        mutableState.value = PlaybackEngineState()
    }

    private fun initializeIfNeeded() {
        if (initialized) return
        mpvOptions().forEach { (key, value) -> MPVLib.setOptionString(key, value) }
        // 自定义参数最后写入（覆盖上面的同名项）；解析器与桌面侧共用一份
        // （commonMain 的 parseMpvCustomParams），避免两端对同一串输入理解不同。
        parseMpvCustomParams(SettingsRepository.customMpvParams)
            .forEach { (key, value) -> MPVLib.setOptionString(key, value) }
        MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("video-params/w", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("video-params/h", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("demuxer-cache-duration", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.addObserver(observer)
        initialized = true
        scope.launch {
            while (isActive) {
                publishState()
                delay(250L.milliseconds)
            }
        }
    }

    private fun startPlayback() {
        MPVLib.setPropertyBoolean("pause", false)
        publishState()
    }

    private fun applySurfaceSize() {
        if (!initialized || released || currentSurface == null) return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        MPVLib.setPropertyString("android-surface-size", "${surfaceWidth}x${surfaceHeight}")
        // G2-3b：Stretch 档的 DAR 是"容器比值"，容器一变（旋转/分屏）就要重算。
        if (aspectMode == VideoAspectMode.Stretch) applyVideoAspect()
        if (MPVLib.getPropertyBoolean("pause") == true) {
            MPVLib.command(arrayOf("seek", "0", "relative", "exact"))
        }
    }

    private fun publishState() {
        if (!initialized || released) return
        MPVLib.getPropertyDouble("time-pos")?.let {
            lastKnownPositionMs = (it * 1000).toLong().coerceAtLeast(0L)
        }
        MPVLib.getPropertyDouble("duration")?.let {
            lastKnownDurationMs = (it * 1000).toLong().coerceAtLeast(0L)
        }
        val buffered = MPVLib.getPropertyDouble("demuxer-cache-duration") ?: 0.0
        val paused = MPVLib.getPropertyBoolean("pause") ?: true
        val width = MPVLib.getPropertyInt("video-params/w") ?: 0
        val height = MPVLib.getPropertyInt("video-params/h") ?: 0
        if (width > 0 && height > 0) {
            lastVideoWidth = width
            lastVideoHeight = height
            hasRenderedFrame = true
        }
        mutableState.value = mutableState.value.copy(
            isPlaying = !paused,
            isBuffering = !paused && lastKnownDurationMs > 0L && lastKnownPositionMs == 0L,
            positionMs = lastKnownPositionMs,
            durationMs = lastKnownDurationMs,
            bufferedPositionMs = (lastKnownPositionMs + buffered * 1000).toLong().coerceAtLeast(0L),
            playbackSpeed = requestSpeed,
            videoWidth = lastVideoWidth,
            videoHeight = lastVideoHeight,
            hasRenderedFirstFrame = hasRenderedFrame,
        )
    }

    private fun prepareUri(uri: Uri): String? {
        return when (uri.scheme) {
            "http", "https" -> uri.toString()
            "file", "content" -> {
                closeCurrentFile()
                currentPfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                detachedFd = currentPfd?.detachFd()
                detachedFd?.let { "fd://$it" }
            }
            else -> null
        }
    }

    private fun closeCurrentFile() {
        currentPfd?.close()
        detachedFd?.let { runCatching { ParcelFileDescriptor.adoptFd(it).close() } }
        currentPfd = null
        detachedFd = null
    }

    /**
     * 本次 load 的网关 reconcilation（与桌面 `applyNetworkOptions` 同原则）。
     *
     * - 网关启用：URL 改写到回环 + `http-header-fields` 补 `X-Ech-Target`，
     *   同时**清空 `http-proxy`**——ffmpeg 的代理没有 bypass 概念，
     *   会把回环请求也送往外部代理，网关永远收不到；
     * - 网关未运行：按设置恢复代理（网关可能在初始化后才停止），清空网关头。
     *
     * @return 实际喂给 `loadfile` 的路径。
     */
    private fun applyEchGateForLoad(request: PlaybackRequest, path: String): String {
        val rewrite = EchGatePolicy.rewrite(request.uri, EchGate.port)
        if (rewrite == null) {
            runCatching { MPVLib.setPropertyString("http-header-fields", "") }
            runCatching { MPVLib.setPropertyString("http-proxy", settingsHttpProxy() ?: "") }
            return path
        }
        runCatching { MPVLib.setPropertyString("http-proxy", "") }
        runCatching {
            MPVLib.setPropertyString(
                "http-header-fields",
                "${EchGatePolicy.TARGET_HEADER}: ${rewrite.targetHost}",
            )
        }
        LogUtil.d(TAG, "ECH direct ${rewrite.targetHost}（代理已让路）")
        return rewrite.url
    }

    /** 初始化选项同款的手填 HTTP 代理（mpvOptions 的子集，供 per-load 恢复用）。 */
    private fun settingsHttpProxy(): String? {
        val ip = SettingsRepository.proxyIp
            .takeIf { it.isNotBlank() && SettingsRepository.proxyPort in 1..65535 }
            ?: return null
        if (SettingsRepository.proxyType != HanimeProxySelector.TYPE_HTTP) return null
        return "http://$ip:${SettingsRepository.proxyPort}"
    }

    private fun mpvOptions(): Map<String, String> = buildMap {
        put("vo", videoOutput)
        put("profile", SettingsRepository.mpvProfile.takeIf { it == "gpu-hq" || it == "fast" } ?: "default")
        put("hwdec", when (SettingsRepository.mpvHwdec) {
            "HW" -> "mediacodec-copy"
            "HW+" -> "mediacodec"
            "Vulkan" -> "vulkan-copy"
            "vulkan+" -> "vulkan"
            "SW" -> "no"
            else -> "auto"
        })
        // P5-1：原 BuildConfig.DEBUG（:app 构建产物，shared 拿不到）→ shared LogUtil.enabled
        // （:app 启动时按 BuildConfig.DEBUG 覆盖，语义不变）
        put("msg-level", "all=" + if (LogUtil.enabled) "debug" else "warn")
        put("cache", "yes")
        put("cache-secs", SettingsRepository.mpvCacheSecs.toString())
        put("vd-lavc-threads", Runtime.getRuntime().availableProcessors().toString())
        put("framedrop", if (SettingsRepository.mpvFramedrop) "vo" else "no")
        put("deband", if (SettingsRepository.mpvDeband) "yes" else "no")
        put("cache-pause", "no")
        put("network-timeout", SettingsRepository.mpvNetworkTimeout.toString())
        put("tls-ca-file", getCert(context))
        put("tls-verify", if (SettingsRepository.mpvTlsVerify) "no" else "yes")
        put("user-agent", USER_AGENT)
        SettingsRepository.proxyIp.takeIf { it.isNotBlank() && SettingsRepository.proxyPort != -1 }?.let { ip ->
            if (SettingsRepository.proxyType == HanimeProxySelector.TYPE_HTTP) {
                put("http-proxy", "http://$ip:${SettingsRepository.proxyPort}")
            }
        }
        if (SettingsRepository.mpvInterpolation) {
            put("interpolation", "yes")
            put("tscale", "oversample")
            put("video-sync", "display-resample")
        }
    }

    private val videoOutput: String
        get() = if (SettingsRepository.enableGPUNextRenderer) "gpu-next" else "gpu"

    private companion object {
        const val NORMAL_END_TOLERANCE_MS = 3_000L
    }
}
