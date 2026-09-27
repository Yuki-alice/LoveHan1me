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
import lovehan1me.feature.danmaku.DanmakuControls
import lovehan1me.feature.player.PlaybackEngineState
import lovehan1me.feature.player.PlaybackSessionState
import lovehan1me.ui.preview.HanimePreviewTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 播放器外壳的**可交互**用例：真实 [VideoPlayerShell] + 真实指针事件 + 真实 mediamp 契约。
 *
 * 三条不变量：
 *  - 进播放页控件就是亮的（触屏端没有"鼠标动一下"这回事，默认藏起来等于死屏）；
 *  - 单击画面真的把播放意图写进了 mediamp（桌面鼠标族的单击只管播放/暂停，不碰显隐）；
 *  - 弹幕设置弹窗挂在最上层槽位，**底栏整棵销毁后弹窗仍在**。
 *
 * 探针不去数像素：底栏内容与弹窗各用 `SideEffect` 记"组合"、`DisposableEffect` 记"销毁"。
 * 底栏内容被 `PlayerControllerBar` 外面那层 `AnimatedVisibility` 包着 —— 只有真人看得见时才
 * 组合，于是这两个计数就是可见性的直接证据。
 *
 * 自动隐藏是 3 秒**真实时间**的倒计时，所以每个断言都紧跟着事件跑完，不给它插手的机会。
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

    init {
        // 真按钮点击会读触感开关（SettingsRepository），不装 store 就 UninitializedPropertyAccess。
        installInMemorySettingsStore()
    }

    private val dialogOpen = mutableStateOf(false)
    private val showControls = mutableStateOf(true)
    private val player = FakeMediampPlayer()

    /** 只由设置钮的点击回调打开，与页面层的接线同构。 */
    private fun sceneOf(probe: Probe): ImageComposeScene {
        val engine = FakePlaybackEngine(
            PlaybackEngineState(
                videoWidth = 1600,
                videoHeight = 900,
                hasRenderedFirstFrame = true,
            ),
        )
        return ImageComposeScene(
            width = SCENE_WIDTH_PX,
            height = SCENE_HEIGHT_PX,
            density = Density(2f),
            content = {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                    VideoPlayerShell(
                        player = player,
                        controller = fakePlaybackController(engine),
                        playbackState = PlaybackSessionState(
                            title = "控件可见性交互用例",
                            engine = PlaybackEngineState(
                                videoWidth = 1600,
                                videoHeight = 900,
                                hasRenderedFirstFrame = true,
                            ),
                        ),
                        videoSurface = {},
                        modifier = Modifier.fillMaxSize(),
                        title = "控件可见性交互用例",
                        // 播放在跑才会启动自动隐藏倒计时；本用例自己掌握显隐时机
                        showControls = showControls.value,
                        expanded = true,
                        danmakuEnabled = true,
                        danmakuLayer = { Box(Modifier.fillMaxSize()) },
                        danmakuEditor = {
                            // 外面套一层只为让用例按 testTag 找到它的位置（真按钮本身没有 testTag）。
                            Box(modifier = Modifier.testTag(DANMAKU_SLOT_TAG)) {
                                SideEffect { probe.bottomComposed++ }
                                DisposableEffect(Unit) {
                                    onDispose { probe.bottomDisposed++ }
                                }
                                // 真实控件：底栏中间位只剩设置钮（开关在启停栏，见
                                // PlayerControllerDefaults.DanmakuIcon）。
                                DanmakuControls(
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
    }

    @Test
    fun `D11 进页面控件就亮_单击画面把播放意图写进mediamp`() {
        val probe = Probe()
        val scene = sceneOf(probe)
        try {
            scene.renderFrames()
            assertTrue(probe.bottomComposed > 0, "首帧底栏就没组合出来：控件默认是藏着的")
            assertEquals(0, probe.bottomDisposed, "没人操作时控件不该消失")
            assertEquals(false, player.state.value.playWhenReady, "用例前提：开场是暂停的")

            scene.tap(EMPTY_SPOT_PX)
            assertTrue(
                scene.awaitCondition { player.state.value.playWhenReady },
                "点了画面却没让 mediamp 的播放意图翻转：控件到后端这段没接上",
            )
            assertEquals(
                0,
                probe.bottomDisposed,
                "单击把控件弄没了：显隐又多了个所有者",
            )

            scene.tap(EMPTY_SPOT_PX)
            assertTrue(
                scene.awaitCondition { !player.state.value.playWhenReady },
                "再点一下没暂停：播放意图被上一次点击卡死了",
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun `D10 底栏整棵销毁后弹幕设置弹窗仍在`() {
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

            showControls.value = false
            assertTrue(
                scene.awaitCondition { probe.bottomDisposed > 0 },
                "底栏没被销毁，D10 的前提没成立",
            )
            assertEquals(
                0,
                probe.dialogDisposed,
                "底栏销毁把弹窗一起带走了：弹窗又挂回了会自动隐藏的槽里",
            )
        } finally {
            scene.close()
            dialogOpen.value = false
            showControls.value = true
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
        timeoutMillis: Long = 1_500L,
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

    /**
     * 真实指针事件：先移入定位，再按下抬起 —— 与桌面鼠标点击同一条路径。
     *
     * 事件时刻逐次推开 [TAP_INTERVAL_MILLIS]：都填 0 的话，第二次按下比第一次抬起还早，
     * 双击判定就变成看实现了。间隔必须大于双击窗口（300ms），否则两下并成一记双击全屏。
     */
    private var eventTimeMillis = 0L

    private fun ImageComposeScene.tap(position: Offset) {
        eventTimeMillis += TAP_INTERVAL_MILLIS
        sendPointerEvent(PointerEventType.Move, position, timeMillis = eventTimeMillis)
        sendPointerEvent(PointerEventType.Press, position, timeMillis = eventTimeMillis + 10L)
        sendPointerEvent(PointerEventType.Release, position, timeMillis = eventTimeMillis + 50L)
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
         * 画面上的空白处：避开顶栏、底栏与右侧键位列。
         * 场景 1280×720 @2f，本点是 (100dp, 180dp) —— 垂直正中、离右缘 540dp。
         */
        val EMPTY_SPOT_PX = Offset(200f, 360f)

        const val FIRST_FRAME_NANOS = 1_000_000_000L
        const val FRAME_STEP_NANOS = 16_666_666L
        const val TAP_INTERVAL_MILLIS = 500L
    }
}
