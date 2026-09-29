package lovehan1me.feature.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import lovehan1me.video.contract.VideoEnhancementController
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackEvent
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.PlayerState
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.PlayerFeatures
import org.openani.mediamp.features.playerFeaturesOf
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData

/**
 * 桥接基类的线程契约与"切档沿用即时真值"用例。
 *
 * 为什么要它：mediamp 0.5.0 把 play/pause/seekTo/stopPlayback/PlaybackSpeed.set 都关进
 * `checkMainThread`，而基类的开流协程跑在 Default 上 —— 子类覆盖 [MediampPlaybackEngineBase.openMedia]
 * 时顺手调一次 `player.play()` 就能让"切画质"整条链路变成错误卡。这类回归只有拿真
 * dispatcher 跑一遍才看得见，所以这里用 [Dispatchers.Main] 当机器线程，逐条命令核对线程。
 */
class MediampPlaybackEngineBaseTest {

    @Test
    fun `T1 transport 命令全部落在 mainDispatcher 线程`() = runBlocking {
        val mainThread = withContext(Dispatchers.Main) { Thread.currentThread() }
        val player = FakeMediampPlayer()
        val engine = TestEngine(player)
        try {
            engine.play()
            engine.pause()
            engine.seekTo(1_000L)
            engine.setPlaybackSpeed(1.5f)

            val seen = awaitUntil { player.commands.size >= 4 }
            assertTrue(seen, "命令未全部下发：${player.commands.map { it.name }}")
            val offThread = player.commands.filter { it.thread !== mainThread }
            assertTrue(
                offThread.isEmpty(),
                "mediamp 会在这些命令上抛 IllegalStateException: " +
                    offThread.joinToString { "${it.name}@${it.thread.name}" },
            )
        } finally {
            engine.release()
        }
    }

    @Test
    fun `T2 切画质沿用即时的播放意图与位置`() = runBlocking {
        val player = FakeMediampPlayer()
        val engine = TestEngine(player)
        try {
            // 缓冲中：mediamp 的 isPlaying 为 false，playWhenReady 才是用户意图。
            player.setTransport(playWhenReady = true, isBuffering = true, positionMillis = 42_000L)
            engine.load(
                PlaybackRequest(
                    uri = "https://cdn.example/480p.mp4",
                    startPositionMs = 0L,
                    playWhenReady = false,
                    isQualitySwitch = true,
                ),
            )

            assertTrue(awaitUntil { player.opens.isNotEmpty() }, "setMediaData 未被调用")
            val open = player.opens.last()
            assertTrue(open.playWhenReady, "缓冲期间切档不得把正在放的片子切成暂停")
            assertEquals(42_000L, open.startPositionMillis, "切档要从播放器的即时位置续，不是请求里的 0")
        } finally {
            engine.release()
        }
    }

    @Test
    fun `T3 新 load 接管后旧请求的失败不得钉住错误态`() = runBlocking {
        val player = FakeMediampPlayer()
        val staleReached = CompletableDeferred<Unit>()
        val releaseStale = CompletableDeferred<Unit>()
        val engine = TestEngine(player) { uri ->
            if (uri == STALE_URI) {
                staleReached.complete(Unit)
                releaseStale.await()
                error("旧请求在新一代开流之后才失败")
            }
        }
        try {
            engine.load(PlaybackRequest(uri = STALE_URI))
            assertTrue(withTimeoutOrNull(3_000L) { staleReached.await() } != null, "旧请求未开始")

            engine.load(PlaybackRequest(uri = "https://cdn.example/720p.mp4"))
            releaseStale.complete(Unit)

            assertTrue(awaitUntil { player.opens.any { it.uri == "https://cdn.example/720p.mp4" } })
            val settled = awaitUntil { engine.state.value.phase == PlaybackPhase.Ready }
            assertTrue(settled, "新流应正常到达 Ready，实际 ${engine.state.value}")
            assertNotEquals(STALE_URI, player.opens.last().uri)
        } finally {
            engine.release()
        }
    }

    private companion object {
        const val STALE_URI = "https://cdn.example/stale.mp4"
    }
}

/** 只测基类的具体引擎：后端实例由用例给，开流前插一个钩子用来制造"迟到的失败"。 */
@OptIn(InternalForInheritanceMediampApi::class)
private class TestEngine(
    override val mediampPlayer: FakeMediampPlayer,
    private val onOpen: suspend (String) -> Unit = {},
) : MediampPlaybackEngineBase() {
    override val enhancement: VideoEnhancementController? = null

    init {
        startObserving()
    }

    override fun setVolume(volume: Float) {}

    override suspend fun openMedia(request: PlaybackRequest) {
        onOpen(request.uri)
        super.openMedia(request)
    }
}

/**
 * 纯内存的 [MediampPlayer]：把三轴状态做成可写的，并记下每条 transport 命令
 * 落在哪条线程 —— 用例断言的就是这个。
 */
@OptIn(InternalForInheritanceMediampApi::class)
private class FakeMediampPlayer : MediampPlayer {

    data class Command(val name: String, val thread: Thread)
    data class Open(val uri: String, val playWhenReady: Boolean, val startPositionMillis: Long)

    private val mutableState = MutableStateFlow(
        PlayerState(MediaStatus.Idle, playWhenReady = false, isBuffering = false),
    )
    private val mutablePositionMillis = MutableStateFlow(0L)
    private val mutableProperties = MutableStateFlow<MediaProperties?>(
        MediaProperties(title = "用例", durationMillis = 287_000L, videoWidth = 1600, videoHeight = 900),
    )

    val commands = CopyOnWriteArrayList<Command>()
    val opens = CopyOnWriteArrayList<Open>()

    private val speed = object : PlaybackSpeed {
        override var value: Float = 1f
            private set

        override val valueFlow: Flow<Float> = MutableStateFlow(1f)

        override fun set(speed: Float) {
            commands.add(Command("PlaybackSpeed.set", Thread.currentThread()))
            value = speed
        }
    }

    fun setTransport(playWhenReady: Boolean, isBuffering: Boolean, positionMillis: Long) {
        mutableState.value = mutableState.value.copy(
            mediaStatus = MediaStatus.Ready,
            playWhenReady = playWhenReady,
            isBuffering = isBuffering,
        )
        mutablePositionMillis.value = positionMillis
    }

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
    override val features: PlayerFeatures = playerFeaturesOf(PlaybackSpeed.Key to speed)
    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val playbackState: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState.CREATED)

    override suspend fun setMediaData(
        data: MediaData,
        playWhenReady: Boolean,
        startPositionMillis: Long,
    ) {
        val uri = (data as UriMediaData).uri
        opens.add(Open(uri, playWhenReady, startPositionMillis))
        mutablePositionMillis.value = startPositionMillis
        mutableState.value = PlayerState(
            mediaStatus = MediaStatus.Ready,
            playWhenReady = playWhenReady,
            isBuffering = false,
        )
    }

    override fun play() {
        commands.add(Command("play", Thread.currentThread()))
        mutableState.value = mutableState.value.copy(playWhenReady = true)
    }

    override fun pause() {
        commands.add(Command("pause", Thread.currentThread()))
        mutableState.value = mutableState.value.copy(playWhenReady = false)
    }

    override fun stopPlayback() {
        commands.add(Command("stopPlayback", Thread.currentThread()))
    }

    override fun seekTo(positionMillis: Long) {
        commands.add(Command("seekTo", Thread.currentThread()))
        mutablePositionMillis.value = positionMillis
    }

    override fun close() {
        mutableState.value = mutableState.value.copy(mediaStatus = MediaStatus.Released)
    }
}

private suspend fun awaitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean): Boolean =
    withTimeoutOrNull(timeoutMs) {
        while (!predicate()) delay(20L)
        true
    } ?: false
