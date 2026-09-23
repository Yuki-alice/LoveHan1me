package lovehan1me.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackQuality(
    val label: String,
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
)

data class ComposePlaybackState(
    val title: String = "",
    val artworkUri: String? = null,
    val qualities: List<PlaybackQuality> = emptyList(),
    val selectedQualityIndex: Int = -1,
    val engine: PlaybackEngineState = PlaybackEngineState(),
    /**
     * 位置停滞（看门狗判定）。UI 据此显示缓冲反馈 —— 引擎的 isBuffering 三端语义不一致，
     * 只有它才能覆盖"画面定住但状态看着正常"这种情况。
     */
    val isStalled: Boolean = false,

    /**
     * 正在**切换画质**（重载同一部片子的另一档）。
     *
     * UI 据此不显示全屏转圈/海报：切档期间画面保留上一帧，看起来才像"无缝换档"；
     * 引擎到达 Ready 或 Error 时自动撤销。
     */
    val isSwitchingQuality: Boolean = false,
)

/**
 * UI-facing coordinator. It owns source switching and preserves the current position while
 * delegating actual decoding and rendering to a selected [PlaybackEngine].
 */
class ComposePlaybackController(
    private val playbackEngine: PlaybackEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val mutableState = MutableStateFlow(ComposePlaybackState())
    private val stallDetector = PlaybackStallDetector()
    private var engineCollectionJob: Job = scope.launch {
        playbackEngine.state.collect { engineState ->
            mutableState.update {
                it.copy(
                    engine = engineState,
                    isStalled = stallDetector.update(
                        positionMs = engineState.positionMs,
                        isPlaying = engineState.isPlaying,
                        isBuffering = engineState.isBuffering,
                    ),
                    // 到达 Ready（换档成功）或 Error（换档失败）即撤销"切换中"
                    isSwitchingQuality = it.isSwitchingQuality &&
                        engineState.phase != PlaybackPhase.Ready &&
                        engineState.phase != PlaybackPhase.Error,
                )
            }
        }
    }
    private var requestedPlaybackSpeed = PlayerDefaults.DEFAULT_SPEED

    /**
     * G2-3b：请求的画面比例 / 画面调节。
     *
     * 与 [requestedPlaybackSpeed] 同一套套路：引擎每 `load` 一次都丢状态
     * （mpv 换了流、Exo 换了 player item），所以必须记住"用户想要什么"，
     * 在每次 `load` 之后重下一次，否则换画质/重播会把画面比例打回 Fit。
     */
    private var requestedVideoAspect: VideoAspectMode = VideoAspectMode.Fit
    private var requestedPictureAdjust: PictureAdjust = PictureAdjust.Neutral

    val state: StateFlow<ComposePlaybackState> = mutableState.asStateFlow()

    fun load(
        title: String,
        qualities: List<PlaybackQuality>,
        preferredQuality: String? = null,
        artworkUri: String? = null,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        val selectedIndex = qualities.indexOfFirst { it.label == preferredQuality }
            .takeIf { it >= 0 }
            ?: qualities.lastIndex
        mutableState.value = ComposePlaybackState(
            title = title,
            artworkUri = artworkUri,
            qualities = qualities,
            selectedQualityIndex = selectedIndex,
        )
        if (selectedIndex >= 0) {
            loadQuality(selectedIndex, startPositionMs, playWhenReady, artworkUri)
        }
    }

    fun selectQuality(index: Int) {
        if (index !in mutableState.value.qualities.indices || index == mutableState.value.selectedQualityIndex) {
            return
        }
        val engineState = mutableState.value.engine
        // 标记"切换中"：引擎重载期间不显示全屏转圈/海报（保面切换）
        mutableState.update { it.copy(isSwitchingQuality = true) }
        loadQuality(
            index = index,
            positionMs = engineState.positionMs,
            playWhenReady = engineState.isPlaying,
            isQualitySwitch = true,
        )
    }

    fun play() = playbackEngine.play()

    fun pause() = playbackEngine.pause()

    fun togglePlayPause() {
        if (mutableState.value.engine.isPlaying) pause() else play()
    }

    fun replay() {
        val selectedIndex = mutableState.value.selectedQualityIndex
        if (selectedIndex in mutableState.value.qualities.indices) {
            loadQuality(selectedIndex, positionMs = 0L, playWhenReady = true)
        }
    }

    fun seekTo(positionMs: Long) = playbackEngine.seekTo(positionMs)

    fun seekBy(deltaMs: Long) {
        val state = mutableState.value.engine
        seekTo((state.positionMs + deltaMs).coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        requestedPlaybackSpeed = speed.coerceIn(0.25f, 5f)
        playbackEngine.setPlaybackSpeed(requestedPlaybackSpeed)
    }

    fun setVolume(volume: Float) = playbackEngine.setVolume(volume)

    // ── G2-3b：画面比例 / 画面调节 ──────────────────────────

    /** 引擎真实支持的画面比例档位（空 = 不支持 → UI 不出入口）。 */
    val supportedAspectModes: List<VideoAspectMode> get() = playbackEngine.supportedAspectModes()

    /** 是否值得出「画面比例」菜单（≥2 档）。 */
    val supportsVideoAspect: Boolean get() = playbackEngine.supportsVideoAspect()

    fun setVideoAspect(mode: VideoAspectMode) {
        requestedVideoAspect = mode
        playbackEngine.setVideoAspect(mode)
    }

    /** 是否支持画面调节（亮度/对比/饱和）。目前只有 mpv 内核为真。 */
    val supportsPictureAdjust: Boolean get() = playbackEngine.supportsPictureAdjust()

    fun setPictureAdjust(brightness: Float, contrast: Float, saturation: Float) {
        requestedPictureAdjust = PictureAdjust(
            brightness = PictureAdjust.clamp(brightness),
            contrast = PictureAdjust.clamp(contrast),
            saturation = PictureAdjust.clamp(saturation),
        )
        playbackEngine.setPictureAdjust(
            requestedPictureAdjust.brightness,
            requestedPictureAdjust.contrast,
            requestedPictureAdjust.saturation,
        )
    }

    fun setPictureAdjust(adjust: PictureAdjust) =
        setPictureAdjust(adjust.brightness, adjust.contrast, adjust.saturation)

    // ── M3-b：抓帧能力（GIF 录制 / 后续截图分享复用）──────────────

    /**
     * 本引擎是否支持抓帧。UI 用它决定要不要显示「录 GIF」入口。
     *
     * 与超分同理：这是**能力查询**，不要按内核名字判断 ——
     * 用户设置里的 `switchPlayerKernel` 与运行时真正在用的引擎可能不一致。
     */
    val supportsFrameCapture: Boolean get() = playbackEngine.supportsFrameCapture()

    /**
     * 抓取 [positionMs] 处的画面为 ARGB 像素（0xAARRGGBB，长度 = `targetWidth * targetHeight`）。
     *
     * **刻意不在这里 `seekTo`**：引擎实现本身就要保证"取的是 positionMs 那一刻的画面"
     * —— 桌面端 mediamp 的 `FramePreview.getPreviewFrame(pos, w, h)` 自带定位语义
     * （它本就是给进度条缩略图用的）。在这里多一次 seek 既不必要，还会引入
     * "seek 未完成就抓帧"的竞态。
     *
     * @return null 表示不支持或抓取失败；调用方据此中断录制并提示，**不要**当成空帧继续
     */
    suspend fun grabFrameArgb(
        positionMs: Long,
        targetWidth: Int,
        targetHeight: Int,
    ): IntArray? = playbackEngine.grabFrameArgb(positionMs, targetWidth, targetHeight)

    fun release() {
        engineCollectionJob.cancel()
        playbackEngine.release()
        scope.coroutineContext.cancel()
    }

    private fun loadQuality(
        index: Int,
        positionMs: Long,
        playWhenReady: Boolean,
        artworkUri: String? = mutableState.value.artworkUri,
        isQualitySwitch: Boolean = false,
    ) {
        val quality = mutableState.value.qualities[index]
        mutableState.update {
            it.copy(
                selectedQualityIndex = index,
                // 全新加载（换片子/重试）不是"切换"，要正常显示海报与转圈
                isSwitchingQuality = isQualitySwitch,
            )
        }
        playbackEngine.load(
            PlaybackRequest(
                uri = quality.uri,
                headers = quality.headers,
                title = mutableState.value.title,
                artworkUri = artworkUri,
                mimeType = quality.mimeType,
                startPositionMs = positionMs,
                playWhenReady = playWhenReady,
                isQualitySwitch = isQualitySwitch,
            )
        )
        playbackEngine.setPlaybackSpeed(requestedPlaybackSpeed)
        applyPicturePreferences()
    }

    /**
     * 每次 load 之后重下"画面"类偏好。
     *
     * 换画质/重播会走一次新的 load，而部分引擎（mpv 新实例、Exo 首次 prepare 前）
     * 会在这一刻丢掉画面比例与调节值。统一在这里补一次，语义与上面的
     * `setPlaybackSpeed` 一致：**用户选过什么，重载后还是什么**。
     */
    private fun applyPicturePreferences() {
        playbackEngine.setVideoAspect(requestedVideoAspect)
        playbackEngine.setPictureAdjust(
            requestedPictureAdjust.brightness,
            requestedPictureAdjust.contrast,
            requestedPictureAdjust.saturation,
        )
    }
}
