package lovehan1me.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lovehan1me.feature.video.FakePlaybackEngine
import lovehan1me.feature.video.fakePlaybackController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// B5 守卫：位置/缓冲推进不得进组合期。
//
// 背景：PlaybackSessionState 每个推送周期都会因位置前进而重发（Android 250ms /
// iOS 500ms / 桌面事件驱动，见 rememberDanmakuSession 的注释），而播放页是
// "整页读一个状态对象、逐层往下传"的结构 —— 位置一推进，整棵播放页组合树重算。
// PlaybackUiState 是组合期唯一入口，位置被剔除。
//
// 两条腿各管一半：
// 1. 流级：投影后位置推进不再发射（uiState 自己的契约）；
// 2. 组合级：不发射 ⇒ 读它那一层不重组（端到端效果，含"没人再读原始状态"这一半）。
// 组合级用例自带**正向对照**（相位变化必须重组）：否则"零重组"可能只是用例
// 自己没跑起来 —— 那正是这个仓库最该防的假绿。
//
// ⚠️ 帧预算纪律：状态投递跑在场景自己的协程上下文里，**落到哪一帧不保证**
// （2026-10-05 实测：全量并行跑时固定 3 帧的窗口不够，正向对照假红过一次）。
// 所以静置与观测都不写死帧数：静置到"连续三帧计数不变"，观测给足 30 帧窗口。
//
// 跑法：`:shared:desktopTest --tests "lovehan1me.feature.player.PlaybackUiStateTest" --offline`
class PlaybackUiStateTest {

    private fun playingEngine() = FakePlaybackEngine(
        PlaybackEngineState(
            phase = PlaybackPhase.Ready,
            isPlaying = true,
            durationMs = 100_000L,
            videoWidth = 1600,
            videoHeight = 900,
            hasRenderedFirstFrame = true,
        ),
    )

    @Test
    fun `位置与缓冲推进不发射uiState`() {
        val engine = playingEngine()
        val controller = fakePlaybackController(engine)
        val seen = mutableListOf<PlaybackUiState>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.uiState.collect { seen += it }
        }
        assertEquals(1, seen.size, "uiState 应当先给出当前值，实际 ${seen.size} 次")

        repeat(5) { tick ->
            val position = (tick + 1) * 250L
            engine.emit { it.copy(positionMs = position, bufferedPositionMs = position + 500L) }
        }

        assertEquals(
            1,
            seen.size,
            "位置推进发射了 ${seen.size - 1} 次 uiState：投影漏了位置/缓冲字段",
        )
        job.cancel()
    }

    @Test
    fun `相位变化必须发射uiState`() {
        val engine = playingEngine()
        val controller = fakePlaybackController(engine)
        val seen = mutableListOf<PlaybackUiState>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.uiState.collect { seen += it }
        }

        engine.emit { it.copy(phase = PlaybackPhase.Preparing) }

        assertEquals(2, seen.size, "相位变化没发射 uiState，投影把真变化也一起吃了")
        assertEquals(PlaybackPhase.Preparing, seen.last().phase)
        job.cancel()
    }

    @Test
    fun `位置推进不重组读uiState的那一层`() {
        val engine = playingEngine()
        val controller = fakePlaybackController(engine)
        var compositions = 0
        val scene = ImageComposeScene(
            width = 320,
            height = 180,
            density = Density(1f),
            content = {
                val uiState by controller.uiState.collectAsStateWithLifecycle()
                SideEffect { compositions++ }
                // 真读一下相位：读到就参与判定，别让"没读过"冒充"没重组"。
                Box(Modifier.fillMaxSize()) {
                    if (uiState.phase != PlaybackPhase.Idle) Box(Modifier.fillMaxSize())
                }
            },
        )
        try {
            renderUntilStable(scene) { compositions }
            val settled = compositions

            repeat(5) { tick -> engine.emit { it.copy(positionMs = (tick + 1) * 250L) } }
            renderFrames(scene, from = OBSERVE_WINDOW_START, count = OBSERVE_FRAMES)
            assertEquals(
                settled,
                compositions,
                "位置推进让读到 uiState 的那一层重组了 ${compositions - settled} 次",
            )

            // 正向对照：同一套观测窗口里，真变化必须能穿透到组合期
            engine.emit { it.copy(phase = PlaybackPhase.Preparing) }
            renderFrames(scene, from = OBSERVE_WINDOW_START * 2, count = OBSERVE_FRAMES)
            assertTrue(
                compositions > settled,
                "相位变化在 $OBSERVE_FRAMES 帧窗口内没触发重组：读数链路没接上，本用例的零重组结论不成立",
            )
        } finally {
            scene.close()
        }
    }

    /**
     * 渲到"连续三帧组合计数不变"即视为静置。
     *
     * 不写死帧数：首次投递（lifecycle 转 STARTED、流开始收）落在哪一帧由场景的调度决定，
     * 写死就是赌调度 —— 全量并行跑时赌输过一次。
     */
    private fun renderUntilStable(scene: ImageComposeScene, count: () -> Int) {
        var last = -1
        var stable = 0
        repeat(MAX_SETTLE_FRAMES) { frame ->
            scene.render(BASE_NANOS + frame * FRAME_STEP_NANOS).close()
            val now = count()
            stable = if (now == last) stable + 1 else 0
            last = now
            if (stable >= STABLE_FRAMES) return
        }
    }

    private fun renderFrames(scene: ImageComposeScene, from: Int, count: Int) {
        repeat(count) { offset ->
            scene.render(BASE_NANOS + (from + offset) * FRAME_STEP_NANOS).close()
        }
    }

    private companion object {
        const val BASE_NANOS = 1_000_000_000L
        const val FRAME_STEP_NANOS = 16_666_666L
        const val STABLE_FRAMES = 3
        const val MAX_SETTLE_FRAMES = 120
        const val OBSERVE_FRAMES = 30
        const val OBSERVE_WINDOW_START = 1_000
    }
}
