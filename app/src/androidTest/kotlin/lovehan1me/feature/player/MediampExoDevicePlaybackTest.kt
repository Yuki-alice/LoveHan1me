package lovehan1me.feature.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import lovehan1me.data.network.defaultPlayerNetworkConfig
import lovehan1me.video.contract.VideoEnhancementLevels
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

// Gate4-2：Android mediamp-exo 真机播放验证（emulator / 真机，需公网）。
// iOS 侧同等覆盖由 XCUITest PlayerUITests + iosTest 承担；Android 侧此前只有
// host 单测（无框架）与桌面端 live 测试，mediamp-exo 上线后这是第一条真播放链路。
// 用 Apple bipbop 公开流（与 iOS 测试同源），不断言站点内容（CF 环境相关）。
// 运行：`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...`
// 或 Android Studio 内直接运行（需连接设备/模拟器，CI 不跑）。
@RunWith(AndroidJUnit4::class)
class MediampExoDevicePlaybackTest {

    private val bipbopTs =
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"

    @Test
    fun mediampExoPlaysPublicHls() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = MediampExoPlaybackEngine(
            context = context,
            network = defaultPlayerNetworkConfig(),
        )
        try {
            engine.load(PlaybackRequest(uri = bipbopTs, playWhenReady = true))
            val ready = withTimeoutOrNull(60.seconds) {
                while (true) {
                    val s = engine.state.value
                    if (s.phase == PlaybackPhase.Ready && s.isPlaying) break
                    delay(500L)
                }
                true
            } ?: false
            assertTrue("60s 内未进入 Ready+isPlaying：${engine.state.value}", ready)

            val progressed = withTimeoutOrNull(30.seconds) {
                while (engine.state.value.positionMs <= 5_000L) delay(500L)
                true
            } ?: false
            assertTrue("进度未推进到 5s：${engine.state.value}", progressed)
            assertTrue(
                "duration 应为正值",
                engine.state.value.durationMs > 0L,
            )
            assertTrue(
                "mediamp 应上报真缓冲进度（换底收益点）：${engine.state.value}",
                engine.state.value.bufferedPositionMs > 0L,
            )

            // P4 真 CNN 链真机验证（Gate4-2 实机教训：模板拼接缺换行曾让 Mali
            // 编译失败 "No matching function for call to 'go_0'"，且编译是异步的，
            // 失败浮上来是播放错误而非挂载异常）。不断言画质（肉眼项），只断言：
            // 挂 PERFORMANCE 后不进 Error、位置继续推进（编译失败即红）。
            // 超分已从引擎方法迁到能力对象（不支持即 null）；Android 引擎必须表态支持，
            // 真机把 PERFORMANCE 档挂下去，验证 GL 链路不把播放搞挂。
            val enhancement = requireNotNull(engine.enhancement) { "Android 引擎应声明超分能力" }
            enhancement.setLevel(VideoEnhancementLevels.PERFORMANCE)
            val stillFine = withTimeoutOrNull(30.seconds) {
                while (true) {
                    val s = engine.state.value
                    if (s.phase == PlaybackPhase.Error) break
                    if (s.positionMs > 15_000L && s.isPlaying) break
                    delay(500L)
                }
                val s = engine.state.value
                s.phase != PlaybackPhase.Error && s.isPlaying
            } ?: false
            assertTrue(
                "挂超分 PERFORMANCE 后播放异常（GL 编译失败会进 Error）：${engine.state.value}",
                stillFine,
            )
        } finally {
            engine.release()
        }
    }
}
