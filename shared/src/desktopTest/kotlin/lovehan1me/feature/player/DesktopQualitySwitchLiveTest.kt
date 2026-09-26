package lovehan1me.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import lovehan1me.feature.player.PlayerMpvOptions
import lovehan1me.feature.player.PlayerNetworkConfig

/**
 * 桌面**切画质保面**的真实验证（默认 skip，手工开启）。
 *
 * 为什么要它：站点片源是各自独立的 MP4（不是 HLS/DASH 的多 rendition manifest），
 * 没有真正的轨道级切换可用 —— 能做到最接近无缝的就是"保留上一帧 + 精确保留位置"。
 * 这条结论只能在"真引擎 + 真流 + 真代理"下验证：
 * 断言 ① 切档期间 isSwitchingQuality 置位（UI 据此不显示海报/全屏转圈）
 * ② 切档后位置延续（不被重置到 0）③ 首帧标记不丢（不闪海报）。
 *
 * 跑法（PowerShell，注意先 --stop 让新环境变量传进守护进程）：
 * ```
 * $env:HAN1ME_QUALITY_LIVE_URL_A='https://vdownload.hembed.com/xxx-1080p.mp4?secure=...'
 * $env:HAN1ME_QUALITY_LIVE_URL_B='https://vdownload.hembed.com/xxx-480p.mp4?secure=...'
 * $env:HAN1ME_MPV_LIVE_PROXY='http://127.0.0.1:7897'
 * ./gradlew --stop
 * ./gradlew :shared:desktopTest --tests '*DesktopQualitySwitchLiveTest*'
 * ```
 */
class DesktopQualitySwitchLiveTest {

    @Test
    fun `切画质保面且位置延续`() {
        val urlA = System.getenv("HAN1ME_QUALITY_LIVE_URL_A")?.takeIf { it.isNotBlank() }
        val urlB = System.getenv("HAN1ME_QUALITY_LIVE_URL_B")?.takeIf { it.isNotBlank() }
        if (urlA == null || urlB == null) {
            println("[skip] 未设 HAN1ME_QUALITY_LIVE_URL_A/B —— 跳过切档验证（见类 KDoc）")
            return
        }
        val proxy = System.getenv("HAN1ME_MPV_LIVE_PROXY")?.takeIf { it.isNotBlank() }
        println("[live] A=${urlA.take(70)}  B=${urlB.take(70)}  proxy=$proxy")

        runBlocking {
            val engine = DesktopMpvPlaybackEngine(
                network = liveNetwork(proxy),
                mpvOptions = { PlayerMpvOptions() },
            )
            // 独立作用域：controller.release() 会 cancel 传进去的 scope，
            // 用 runBlocking 的 context 会把测试自己取消掉。
            val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = PlaybackController(engine, playerScope)
            try {
                controller.load(
                    title = "quality-switch-probe",
                    qualities = listOf(
                        PlaybackQuality("1080P", urlA),
                        PlaybackQuality("480P", urlB),
                    ),
                    preferredQuality = "1080P",
                    artworkUri = null,
                    startPositionMs = 0L,
                    playWhenReady = true,
                )
                val ready = awaitPhase(controller, PlaybackPhase.Ready, 30_000)
                assertTrue(ready, "首档未能进入 Ready")
                delay(4_000) // 播几秒，让位置有意义

                val beforePosition = controller.state.value.engine.positionMs
                val beforeFrame = controller.state.value.engine.hasRenderedFirstFrame
                println("[live] 切换前 position=${beforePosition}ms hasFrame=$beforeFrame")
                assertTrue(beforePosition > 0L, "切换前位置应已推进")
                assertTrue(beforeFrame, "切换前应已渲染首帧")

                controller.selectQuality(1)
                val switching = controller.state.value.isSwitchingQuality
                println("[live] 切换中标记=$switching")
                assertTrue(switching, "selectQuality 后应置位 isSwitchingQuality（UI 据此不显示海报/转圈）")

                // ⚠️ 不能用 first { phase == Ready }：切换**前**引擎就已经是 Ready，
                // StateFlow 会立刻返回那个旧状态（本测试第一版就踩了这个竞态）。
                // 正确做法是等"切换中标记被撤销"——它只在引擎到达 Ready/Error 时撤销。
                val backToReady = awaitCondition(controller, 30_000) {
                    !it.isSwitchingQuality
                }
                val after = controller.state.value.engine
                println(
                    "[live] 切换后 phase=${after.phase} position=${after.positionMs}ms " +
                        "hasFrame=${after.hasRenderedFirstFrame} switching=${controller.state.value.isSwitchingQuality}",
                )
                assertTrue(backToReady, "切档后未回到 Ready（${after.errorMessage}）")
                assertEquals(
                    "480P",
                    controller.state.value.qualities[controller.state.value.selectedQualityIndex].label,
                    "应已切到 480P",
                )
                // 位置延续：允许解码/缓冲带来的偏差，但不允许被重置回 0
                assertTrue(
                    after.positionMs >= beforePosition - 3_000L,
                    "切档后位置被回退过多：${beforePosition} -> ${after.positionMs}",
                )
                assertTrue(after.hasRenderedFirstFrame, "切档后首帧标记丢了 → UI 会闪回海报")
                assertTrue(
                    !controller.state.value.isSwitchingQuality,
                    "到达 Ready 后应撤销切换中标记",
                )
            } finally {
                controller.release()
                playerScope.cancel()
            }
        }
    }

    /** 等"本次加载"进入某个阶段（用于首次加载：那时状态还没到过该阶段）。 */
    private suspend fun awaitPhase(
        controller: PlaybackController,
        phase: PlaybackPhase,
        timeoutMs: Long,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        controller.state.first { it.engine.phase == phase }
        true
    } ?: false

    /** 等某个条件成立（用于"切换完成"这类**不能靠阶段判定**的场景，见调用点注释）。 */
    private suspend fun awaitCondition(
        controller: PlaybackController,
        timeoutMs: Long,
        predicate: (PlaybackSessionState) -> Boolean,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        controller.state.first(predicate)
        true
    } ?: false
}
