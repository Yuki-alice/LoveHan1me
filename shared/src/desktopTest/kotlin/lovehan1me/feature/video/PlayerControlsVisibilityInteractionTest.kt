package lovehan1me.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.feature.danmaku.DanmakuControls
import lovehan1me.ui.preview.HanimePreviewTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 控件可见性与弹窗存活的**可交互**用例：真实 [VideoPlayerUi] + 真实指针事件。
 *
 * 两条不变量：
 *  - 单击画面切控件可见性，再点又回来（可见性只有一个所有者，点几次都不会卡住）；
 *  - 弹幕设置弹窗挂在最上层槽位，**底栏因控件隐藏而销毁后弹窗仍在**。
 *
 * 探针不去数像素：底栏内容与弹窗各用 `SideEffect` 记"组合"、`DisposableEffect` 记"销毁"。
 * 底栏内容被 `PlayerBottomBar` 的 `AnimatedVisibility` 包着 —— 只有真人看得见时才组合，
 * 于是这两个计数就是可见性的直接证据。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.video.PlayerControlsVisibilityInteractionTest" --offline`
 */
class PlayerControlsVisibilityInteractionTest {

    private class Probe {
        var bottomComposed = 0
        var bottomDisposed = 0
        var dialogComposed = 0
        var dialogDisposed = 0
    }

    private class InMemorySettingsStore : SettingsStore {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    init {
        // 真按钮点击会读触感开关（SettingsRepository），不装 store 就 UninitializedPropertyAccess。
        // 同 JVM 多测试类时只允许 install 一次，故与既有用例同法 runCatching。
        runCatching { SettingsRepository.install(InMemorySettingsStore()) }
    }

    private val dialogOpen = mutableStateOf(false)

    /** 弹幕设置弹窗的开关：只由设置钮的点击回调打开，与页面层的接线同构。 */
    private fun sceneOf(probe: Probe): ImageComposeScene = ImageComposeScene(
        width = SCENE_WIDTH_PX,
        height = SCENE_HEIGHT_PX,
        density = Density(2f),
        content = {
            HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                VideoPlayerUi(
                    modifier = Modifier.fillMaxSize(),
                    title = "控件可见性交互用例",
                    currentTime = "00:00",
                    totalTime = "04:47",
                    progress = 0.1f,
                    bufferedProgress = 0.2f,
                    currentVolume = 1f,
                    currentBrightness = 1f,
                    // 播放在跑才会启动自动隐藏倒计时。本用例只验"交互改可见性"与
                    // "弹窗存活"，把 5 秒真实倒计时拉进来只会让用例变成对时钟的赌注。
                    isPlaying = false,
                    superResolutionLabel = "关闭",
                    // 弹幕双钮只存在于宽屏底栏的中间位：窄屏底栏没有中间位，槽永远不组合，
                    // 用例也就永远找不到它。宽屏形态由 expanded 决定。
                    expanded = true,
                    danmakuControls = {
                        // 外面套一层只为让用例按 testTag 找到它的位置（真按钮本身没有 testTag）。
                        Box(modifier = Modifier.testTag(DANMAKU_SLOT_TAG)) {
                            SideEffect { probe.bottomComposed++ }
                            DisposableEffect(Unit) {
                                onDispose { probe.bottomDisposed++ }
                            }
                            // 真实双钮；session = null → 只剩设置钮。
                            DanmakuControls(
                                session = null,
                                onOpenSettings = { dialogOpen.value = true },
                            )
                        }
                    },
                    dialogHost = {
                        if (dialogOpen.value) {
                            SideEffect { probe.dialogComposed++ }
                            DisposableEffect(Unit) {
                                onDispose { probe.dialogDisposed++ }
                            }
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    },
                )
            }
        },
    )

    @Test
    fun `D11 单击画面切换控件可见性_再点回来`() {
        val probe = Probe()
        val scene = sceneOf(probe)
        try {
            scene.renderFrames()
            assertTrue(probe.bottomComposed > 0, "首帧底栏就没组合出来，用例本身没跑起来")
            assertEquals(0, probe.bottomDisposed, "没人操作时控件不该消失")

            scene.tap(EMPTY_SPOT_PX)
            assertTrue(
                scene.awaitCondition { probe.bottomDisposed > 0 },
                "点了画面控件却没隐藏：可见性没跟着交互走",
            )

            val composedBefore = probe.bottomComposed
            scene.tap(EMPTY_SPOT_PX)
            assertTrue(
                scene.awaitCondition { probe.bottomComposed > composedBefore },
                "再点一下控件没回来：可见性被上一次隐藏卡死了",
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun `D10 底栏随控件隐藏销毁后弹幕设置弹窗仍在`() {
        val probe = Probe()
        val scene = sceneOf(probe)
        try {
            scene.renderFrames()
            assertEquals(0, probe.dialogComposed, "还没点设置，弹窗不该出现")

            scene.tap(scene.boundsOfTestTag(DANMAKU_SLOT_TAG).center)
            assertTrue(
                scene.awaitCondition { probe.dialogComposed > 0 },
                "点了设置钮弹窗没出来：点击没走到上报回调",
            )
            assertEquals(0, probe.dialogDisposed, "弹窗刚打开就被销毁了")

            scene.tap(EMPTY_SPOT_PX)
            assertTrue(
                scene.awaitCondition { probe.bottomDisposed > 0 },
                "控件没被隐藏，D10 的前提没成立",
            )
            assertEquals(
                0,
                probe.dialogDisposed,
                "底栏销毁把弹窗一起带走了：弹窗又挂回了会自动隐藏的槽里",
            )
        } finally {
            scene.close()
        }
    }

    /**
     * 帧时刻必须**单调往前**：`AnimatedVisibility` 的进出场动画靠帧时钟推进，不走完不销毁内容。
     * 计数器挂在实例上（不是每次从常量重来）—— 回到旧时刻等于动画原地踏步，隐身永远不完成。
     */
    private var frameNanos = FIRST_FRAME_NANOS

    private fun ImageComposeScene.renderFrames(frames: Int = 30) {
        repeat(frames) {
            render(frameNanos).close()
            frameNanos += FRAME_STEP_NANOS
        }
    }

    /**
     * 一边推帧、一边等真实时间，直到 [condition] 成立（超时即返回最后一次判定）。
     *
     * 两件事都省不掉：进/出场动画只认帧时钟；而画面单击的 `onTap` 要被双击窗口
     * （300ms）延后才派发，那个窗口走的是真实时间。所以不预设"要等多久"，
     * 只轮询到"看得见"为止 —— 阈值一旦写死，用例就变成对时钟的赌注。
     */
    private fun ImageComposeScene.awaitCondition(
        timeoutMillis: Long = 3_000L,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            renderFrames(frames = 10)
            if (condition()) return true
            Thread.sleep(5L)
        }
        return condition()
    }

    /** 真实指针事件：先移入定位，再按下抬起 —— 与桌面鼠标点击同一条路径。 */
    private fun ImageComposeScene.tap(position: Offset) {
        sendPointerEvent(PointerEventType.Move, position, timeMillis = 0L)
        sendPointerEvent(PointerEventType.Press, position, timeMillis = 0L)
        sendPointerEvent(PointerEventType.Release, position, timeMillis = 40L)
    }

    private fun ImageComposeScene.boundsOfTestTag(tag: String): Rect =
        semanticsOwners
            .flatMap { it.getAllSemanticsNodes(false) }
            .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            ?.boundsInRoot
            ?: error("找不到 testTag=$tag 的节点：控件没组合出来？")

    private companion object {
        const val SCENE_WIDTH_PX = 1_280
        const val SCENE_HEIGHT_PX = 720
        const val DANMAKU_SLOT_TAG = "danmaku-slot"

        /**
         * 画面上的空白处：避开顶栏、底栏与中央播放键。
         * 中央键在 (640, 360) 附近、半径约 64dp，本点距它有 440px，够远。
         */
        val EMPTY_SPOT_PX = Offset(200f, 360f)

        const val FIRST_FRAME_NANOS = 1_000_000_000L
        const val FRAME_STEP_NANOS = 16_666_666L
    }
}