package lovehan1me.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lovehan1me.video.contract.VideoEnhancementController
import lovehan1me.video.contract.resolveEnhancementLevel

// 真身在 :video:contract —— 播放控件将来要搬进 :video:ui，而那个模块看不到本模块的声明。
// 留别名让 :shared 内既有 import（含测试）继续解析。
typealias PlaybackQuality = lovehan1me.video.contract.PlaybackQuality

data class PlaybackSessionState(
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
 * 组合期唯一该读的播放状态投影。
 *
 * 与 [PlaybackSessionState] 只差一件事：**剔除每个推送周期都会变的字段**
 * （`engine.positionMs` / `engine.bufferedPositionMs`）。
 *
 * 为什么必须剔除：位置由后端按推送周期前进（Android 250ms / iOS 500ms / 桌面事件驱动，
 * 见 `rememberDanmakuSession`）。组合期读的状态只要跟着它变，读数的那一层组合作用域
 * 就会以这个频率被无效化 —— 而播放页是"整页读一个状态对象、再逐层传下去"的结构，
 * 于是一次位置推进就把整棵播放页组合树重算一遍。位置本该只驱动**绘制**（进度条画到哪）
 * 与**命令式取值**（落盘续播、抓帧），两者都不需要组合期参与：
 * - 控件层的画面内事实（位置/时长/播放态/缓冲）直读 mediamp 的 `player`（见
 *   `VideoPlayerShell` 文件头）；
 * - 弹幕层与续播落盘各走流或 `state.value` 命令式读。
 *
 * 去重键是本 data class 的 `equals`：字段全部是基础类型 + 一次性赋予的
 * `qualities`（`load()` 时整组替换，播放期间同一实例），诚实不额外断言稳定。
 *
 * 字段是"组合期真有消费点"的那一批，不是"状态里有什么就搬什么"：
 * `title` / `artworkUri` 是喂引擎的开流参数（见 `loadQuality`），页面另持自己的标题，
 * 组合期没有读者，故不进投影。
 */
data class PlaybackUiState(
    val qualities: List<PlaybackQuality> = emptyList(),
    val selectedQualityIndex: Int = -1,
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** 位置停滞（看门狗判定）。UI 据此转圈 —— 引擎的 isBuffering 三端语义不一致。 */
    val isStalled: Boolean = false,
    /** 正在切换画质：UI 据此不显示全屏转圈/海报，画面保留上一帧。 */
    val isSwitchingQuality: Boolean = false,
    val hasRenderedFirstFrame: Boolean = false,
    val errorMessage: String? = null,
    val durationMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

/** 见 [PlaybackUiState]：位置与缓冲被刻意留在这里，不进组合期。 */
fun PlaybackSessionState.toUiState(): PlaybackUiState = PlaybackUiState(
    qualities = qualities,
    selectedQualityIndex = selectedQualityIndex,
    phase = engine.phase,
    isPlaying = engine.isPlaying,
    isBuffering = engine.isBuffering,
    isStalled = isStalled,
    isSwitchingQuality = isSwitchingQuality,
    hasRenderedFirstFrame = engine.hasRenderedFirstFrame,
    errorMessage = engine.errorMessage,
    durationMs = engine.durationMs,
    videoWidth = engine.videoWidth,
    videoHeight = engine.videoHeight,
)

/**
 * UI-facing coordinator. It owns source switching and preserves the current position while
 * delegating actual decoding and rendering to a selected [PlaybackEngine].
 */
class PlaybackController(
    val playbackEngine: PlaybackEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val mutableState = MutableStateFlow(PlaybackSessionState())
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

    val state: StateFlow<PlaybackSessionState> = mutableState.asStateFlow()

    /**
     * 组合期只订这条（见 [PlaybackUiState]）。照 B4 既有 `themeConfigFlow` 模式
     * （`map + distinctUntilChanged + stateIn(Eagerly)`）：位置每 tick 变一次，
     * 投影后与上一份相等，`distinctUntilChanged` 把它挡在 `stateIn` 之前，
     * 于是播放页的组合树不再跟着位置走。
     *
     * 需要位置的地方（弹幕时钟、续播落盘、抓帧）继续用完好的 [state]。
     */
    val uiState: StateFlow<PlaybackUiState> by lazy {
        state.map { it.toUiState() }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, state.value.toUiState())
    }

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
        mutableState.value = PlaybackSessionState(
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

    /**
     * 跳到绝对位置。上下界都钳：长按快进/键盘 seek 越过片尾时位置不再大于时长，
     * 否则进度条与续播落点行为未定义。时长未知（直播/未就绪，durationMs<=0）时
     * 只保下界，保持旧语义。
     */
    fun seekTo(positionMs: Long) {
        val durationMs = mutableState.value.engine.durationMs
        val clamped = if (durationMs > 0) positionMs.coerceIn(0L, durationMs)
        else positionMs.coerceAtLeast(0L)
        playbackEngine.seekTo(clamped)
    }

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

    // ── 超分（视频增强）─────────────────────────────────────

    /**
     * 引擎的超分控制器；**null = 本端不支持** → UI 入口整块隐藏。
     *
     * 不再另包一层 `supportsXxx(): Boolean`：控制器自带
     * [VideoEnhancementController.levels]，UI 既判"有没有"也能列"有哪些档"。
     */
    val enhancement: VideoEnhancementController? get() = playbackEngine.enhancement

    /**
     * 切到 [level]，返回**实际生效**档位；本端不支持超分时返回 null。
     *
     * 落盘值在**读侧**收敛：老存档没有这个键、换了引擎导致档位越界、值被写坏 ——
     * 一律降到 OFF，不把无效索引交给引擎。引擎自己还会再降一次（shader 不可用），
     * 所以 UI 显示值必须取本函数的返回值，而不是用户点的那一项。
     */
    suspend fun setEnhancementLevel(level: Int): Int? {
        val controller = playbackEngine.enhancement ?: return null
        return controller.setLevel(resolveEnhancementLevel(level, controller.levels))
    }

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
