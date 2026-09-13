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
    private var engineCollectionJob: Job = scope.launch {
        playbackEngine.state.collect { engineState ->
            mutableState.update { it.copy(engine = engineState) }
        }
    }
    private var requestedPlaybackSpeed = PlayerDefaults.DEFAULT_SPEED

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
        loadQuality(index, engineState.positionMs, engineState.isPlaying)
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
    ) {
        val quality = mutableState.value.qualities[index]
        mutableState.update { it.copy(selectedQualityIndex = index) }
        playbackEngine.load(
            PlaybackRequest(
                uri = quality.uri,
                headers = quality.headers,
                title = mutableState.value.title,
                artworkUri = artworkUri,
                mimeType = quality.mimeType,
                startPositionMs = positionMs,
                playWhenReady = playWhenReady,
            )
        )
        playbackEngine.setPlaybackSpeed(requestedPlaybackSpeed)
    }
}
