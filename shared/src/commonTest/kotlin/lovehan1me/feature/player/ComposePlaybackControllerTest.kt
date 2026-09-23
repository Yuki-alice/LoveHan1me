package lovehan1me.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lovehan1me.core.domain.model.PictureAdjust
import lovehan1me.core.domain.model.VideoAspectMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 播放器协调器回归（纯逻辑，离线可跑）。
 *
 * 覆盖 G2 播放器深度的"记忆语义"：偏好画质选择、换档保面与位置、
 * 倍速/画面偏好重载后不丢失、输入钳制。引擎用 Fake，不碰原生解码。
 */
class ComposePlaybackControllerTest {

    private class FakeEngine : PlaybackEngine {
        val engineState = MutableStateFlow(PlaybackEngineState())
        override val state: StateFlow<PlaybackEngineState> = engineState

        val loads = mutableListOf<PlaybackRequest>()
        val speeds = mutableListOf<Float>()
        val aspects = mutableListOf<VideoAspectMode>()
        val adjusts = mutableListOf<Triple<Float, Float, Float>>()
        var playCalls = 0
        var pauseCalls = 0
        val seeks = mutableListOf<Long>()

        override fun load(request: PlaybackRequest) {
            loads += request
        }

        override fun play() {
            playCalls++
        }

        override fun pause() {
            pauseCalls++
        }

        override fun seekTo(positionMs: Long) {
            seeks += positionMs
        }

        override fun setPlaybackSpeed(speed: Float) {
            speeds += speed
        }

        override fun setVolume(volume: Float) {}

        override fun attachSurface(surface: VideoSurface) {}

        override fun detachSurface(surface: VideoSurface) {}

        override fun setVideoAspect(mode: VideoAspectMode) {
            aspects += mode
        }

        override fun setPictureAdjust(brightness: Float, contrast: Float, saturation: Float) {
            adjusts += Triple(brightness, contrast, saturation)
        }

        override fun release() {}
    }

    private fun controller(engine: FakeEngine = FakeEngine()): Pair<ComposePlaybackController, FakeEngine> {
        val c = ComposePlaybackController(
            playbackEngine = engine,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        return c to engine
    }

    private fun qualities() = listOf(
        PlaybackQuality("1080P", "https://cdn.example/1080.mp4"),
        PlaybackQuality("720P", "https://cdn.example/720.mp4"),
    )

    @Test
    fun `load命中偏好画质`() {
        val (c, engine) = controller()
        try {
            c.load("t", qualities(), preferredQuality = "720P")
            assertEquals(1, c.state.value.selectedQualityIndex)
            assertEquals("https://cdn.example/720.mp4", engine.loads.single().uri)
            assertFalse(engine.loads.single().isQualitySwitch)
        } finally {
            c.release()
        }
    }

    @Test
    fun `load偏好缺失回落末档_空列表不load`() {
        val (c, engine) = controller()
        try {
            c.load("t", qualities(), preferredQuality = "4K")
            assertEquals(1, c.state.value.selectedQualityIndex)
            assertEquals(1, engine.loads.size)
        } finally {
            c.release()
        }
        val (c2, engine2) = controller()
        try {
            c2.load("t", emptyList())
            assertEquals(-1, c2.state.value.selectedQualityIndex)
            assertTrue(engine2.loads.isEmpty())
        } finally {
            c2.release()
        }
    }

    @Test
    fun `selectQuality同档与越界是空操作`() {
        val (c, engine) = controller()
        try {
            c.load("t", qualities(), preferredQuality = "1080P")
            assertEquals(1, engine.loads.size)
            c.selectQuality(0)
            c.selectQuality(5)
            c.selectQuality(-1)
            assertEquals(1, engine.loads.size)
            assertFalse(c.state.value.isSwitchingQuality)
        } finally {
            c.release()
        }
    }

    @Test
    fun `selectQuality保面保位置保播放态`() {
        val (c, engine) = controller()
        try {
            c.load("t", qualities(), preferredQuality = "1080P")
            engine.engineState.value = PlaybackEngineState(
                phase = PlaybackPhase.Ready,
                isPlaying = true,
                positionMs = 5_000L,
            )
            c.selectQuality(1)
            assertTrue(c.state.value.isSwitchingQuality)
            val req = engine.loads.last()
            assertTrue(req.isQualitySwitch)
            assertEquals("https://cdn.example/720.mp4", req.uri)
            assertEquals(5_000L, req.startPositionMs)
            assertTrue(req.playWhenReady)

            // Preparing 不撤销切换中；Ready/Error 撤销。
            engine.engineState.value = PlaybackEngineState(phase = PlaybackPhase.Preparing)
            assertTrue(c.state.value.isSwitchingQuality)
            engine.engineState.value = PlaybackEngineState(phase = PlaybackPhase.Ready)
            assertFalse(c.state.value.isSwitchingQuality)
        } finally {
            c.release()
        }
    }

    @Test
    fun `selectQuality失败同样撤销切换中`() {
        val (c, engine) = controller()
        try {
            c.load("t", qualities(), preferredQuality = "1080P")
            c.selectQuality(1)
            assertTrue(c.state.value.isSwitchingQuality)
            engine.engineState.value = PlaybackEngineState(
                phase = PlaybackPhase.Error,
                errorMessage = "boom",
            )
            assertFalse(c.state.value.isSwitchingQuality)
        } finally {
            c.release()
        }
    }

    @Test
    fun `倍速钳制并透传`() {
        val (c, engine) = controller()
        try {
            c.setPlaybackSpeed(10f)
            c.setPlaybackSpeed(0f)
            assertEquals(listOf(5f, 0.25f), engine.speeds)
        } finally {
            c.release()
        }
    }

    @Test
    fun `画面调节钳制并在每次load后重下`() {
        val (c, engine) = controller()
        try {
            c.setPictureAdjust(150f, -150f, 10f)
            assertEquals(Triple(100f, -100f, 10f), engine.adjusts.single())
            c.load("t", qualities())
            // load 后重下一次（换流会丢引擎侧状态）。
            assertEquals(Triple(100f, -100f, 10f), engine.adjusts.last())
            assertEquals(2, engine.adjusts.size)
        } finally {
            c.release()
        }
    }

    @Test
    fun `画面比例记忆并在load后重下`() {
        val (c, engine) = controller()
        try {
            c.setVideoAspect(VideoAspectMode.Crop)
            assertEquals(listOf(VideoAspectMode.Crop), engine.aspects)
            c.load("t", qualities())
            assertEquals(
                listOf(VideoAspectMode.Crop, VideoAspectMode.Crop),
                engine.aspects,
            )
        } finally {
            c.release()
        }
    }

    @Test
    fun `seekBy负数钳零_toggle按播放态分发_replay从零重载`() {        val (c, engine) = controller()
        try {
            c.seekBy(-5_000L)
            assertEquals(listOf(0L), engine.seeks)

            engine.engineState.value = PlaybackEngineState(isPlaying = false)
            c.togglePlayPause()
            assertEquals(1, engine.playCalls)
            engine.engineState.value = PlaybackEngineState(isPlaying = true)
            c.togglePlayPause()
            assertEquals(1, engine.pauseCalls)

            c.load("t", qualities(), preferredQuality = "720P")
            c.replay()
            val req = engine.loads.last()
            assertEquals("https://cdn.example/720.mp4", req.uri)
            assertEquals(0L, req.startPositionMs)
            assertTrue(req.playWhenReady)
        } finally {
            c.release()
        }
    }
}

/**
 * 系列自动连播触发判定回归。
 */
class AutoPlayNextTest {

    @Test
    fun `三条件齐才触发`() {
        assertTrue(shouldAutoPlayNext(PlaybackPhase.Ended, true, true))
    }

    @Test
    fun `缺任一条件都不触发`() {
        // 没播完
        assertFalse(shouldAutoPlayNext(PlaybackPhase.Ready, true, true))
        assertFalse(shouldAutoPlayNext(PlaybackPhase.Preparing, true, true))
        assertFalse(shouldAutoPlayNext(PlaybackPhase.Error, true, true))
        assertFalse(shouldAutoPlayNext(PlaybackPhase.Idle, true, true))
        // 开关关了
        assertFalse(shouldAutoPlayNext(PlaybackPhase.Ended, false, true))
        // 单片或最后一集
        assertFalse(shouldAutoPlayNext(PlaybackPhase.Ended, true, false))
    }
}
