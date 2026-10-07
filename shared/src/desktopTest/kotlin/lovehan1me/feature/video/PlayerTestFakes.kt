package lovehan1me.feature.video

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.feature.player.PlaybackController
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackEngineState
import lovehan1me.feature.player.PlaybackRequest
import lovehan1me.feature.player.VideoSurface
import lovehan1me.video.contract.VideoEnhancementController
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackEvent
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.PlayerState
import org.openani.mediamp.features.PlayerFeatures
import org.openani.mediamp.features.playerFeaturesOf
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.source.MediaData

/**
 * 控件用例的驱动源：一个纯内存的 [MediampPlayer]。
 *
 * 只把"读侧 + 播放意图"做成真的，因为用例要认的就是**状态流 → 控件**这一段接线。
 * [setMediaData] / [stopPlayback] / [close] 刻意什么都不做：开流状态机属于后端，
 * 在这里实现它只会让用例变成对那份状态机的赌注。
 *
 * `mainDispatcher = Unconfined` —— 命令在调用线程上直接生效，用例不必再猜哪条线程。
 */
@OptIn(InternalForInheritanceMediampApi::class)
class FakeMediampPlayer(
    durationMillis: Long = 287_000L,
    videoWidth: Int = 1600,
    videoHeight: Int = 900,
    playWhenReady: Boolean = false,
) : MediampPlayer {
    private val mutableState = MutableStateFlow(
        PlayerState(
            mediaStatus = MediaStatus.Ready,
            playWhenReady = playWhenReady,
            isBuffering = false,
        ),
    )
    private val mutableProperties = MutableStateFlow<MediaProperties?>(
        MediaProperties(
            title = "控件用例",
            durationMillis = durationMillis,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
        ),
    )
    private val mutablePositionMillis = MutableStateFlow(0L)

    override val impl: Any = this
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()
    override val events: SharedFlow<PlaybackEvent> = MutableSharedFlow()
    override val mediaData: StateFlow<MediaData?> = MutableStateFlow(null)
    override val mediaProperties: StateFlow<MediaProperties?> = mutableProperties.asStateFlow()
    override val currentPositionMillis: StateFlow<Long> = mutablePositionMillis.asStateFlow()

    override val playbackProgress: Flow<Float>
        get() = mutablePositionMillis.map { position ->
            val total = mutableProperties.value?.durationMillis ?: 0L
            if (total <= 0L) 0f else (position.toFloat() / total).coerceIn(0f, 1f)
        }

    /** 空 feature 集：倍速 / 画面比例 / 预览帧 / 音量全部走"本端不支持"的分支。 */
    override val features: PlayerFeatures = playerFeaturesOf()
    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    @Suppress("DEPRECATION")
    override val playbackState: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState.READY)

    override suspend fun setMediaData(
        data: MediaData,
        playWhenReady: Boolean,
        startPositionMillis: Long,
    ) {
        mutablePositionMillis.value = startPositionMillis
        mutableState.update { it.copy(playWhenReady = playWhenReady) }
    }

    override fun play() {
        mutableState.update { it.copy(playWhenReady = true) }
    }

    override fun pause() {
        mutableState.update { it.copy(playWhenReady = false) }
    }

    override fun stopPlayback() {
        mutableState.update {
            it.copy(mediaStatus = MediaStatus.Idle, playWhenReady = false, isBuffering = false)
        }
    }

    override fun seekTo(positionMillis: Long) {
        mutablePositionMillis.value = positionMillis
    }

    override fun close() {
        mutableState.update { it.copy(mediaStatus = MediaStatus.Released, playWhenReady = false) }
    }
}

/**
 * 惰性引擎：只回报一个固定状态，开流/换档/挂面全是 no-op。
 *
 * 存在只为满足 [PlaybackController] 的构造 —— 控件层用到它的只有"支不支持画面比例"
 * 与"把用户意图落定"，两者都由 [supportedAspectModes] 的空列表关掉。
 */
class FakePlaybackEngine(
    initialState: PlaybackEngineState = PlaybackEngineState(),
) : PlaybackEngine {
    private val mutableState = MutableStateFlow(initialState)

    override val state: StateFlow<PlaybackEngineState> = mutableState.asStateFlow()
    override val enhancement: VideoEnhancementController? = null

    fun emit(transform: (PlaybackEngineState) -> PlaybackEngineState) {
        mutableState.update(transform)
    }

    override fun load(request: PlaybackRequest) {}

    override fun play() {}

    override fun pause() {}

    override fun seekTo(positionMs: Long) {
        mutableState.update { it.copy(positionMs = positionMs) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        mutableState.update { it.copy(playbackSpeed = speed) }
    }

    override fun setVolume(volume: Float) {}

    override fun attachSurface(surface: VideoSurface) {}

    override fun detachSurface(surface: VideoSurface) {}

    override fun release() {}
}

/**
 * 用例用的 [PlaybackController]。
 *
 * scope 必须是 Unconfined：默认的 `Dispatchers.Main.immediate` 在无窗口的 desktopTest
 * 里根本没有 Main 调度器，构造时那个 `engine.state.collect` 就会炸。
 */
fun fakePlaybackController(engine: FakePlaybackEngine): PlaybackController =
    PlaybackController(engine, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

/**
 * 内存设置仓库。
 *
 * 播放器外壳要读主题（控件恒为深色场景，配色得从用户选的板子里取），没装 store 就是
 * `IllegalStateException`。[SettingsRepository.install] 现已可重复调用（替换时旧代次的
 * 派生流会一并重建），故每个用例都装上自己的一份，不再需要 `runCatching` 兜底。
 */
class InMemorySettingsStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

fun installInMemorySettingsStore() {
    SettingsRepository.install(InMemorySettingsStore())
}
