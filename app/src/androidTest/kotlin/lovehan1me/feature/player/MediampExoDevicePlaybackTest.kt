package lovehan1me.feature.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import lovehan1me.data.network.defaultPlayerNetworkConfig
import lovehan1me.video.contract.VideoEnhancementLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

// Android mediamp-exo 真机播放验证（emulator / 真机，需公网；CI 不跑）。
// 用 Apple bipbop 公开流（与 iOS 侧同源），不断言站点内容（CF 环境相关）。
// 运行：`./gradlew :app:connectedDebugAndroidTest
//        -Pandroid.testInstrumentationRunnerArguments.class=lovehan1me.feature.player.MediampExoDevicePlaybackTest`
// 或 Android Studio 内直接运行（需连接设备/模拟器）。
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

            // 真机挂 GL 效果链只验"不把播放搞挂"：画质是肉眼项，不断言。
            // 编译失败的表现形式是异步的播放 Error（不是挂载异常）——曾经模板拼接
            // 少一个换行就让 Mali 报 "No matching function for call to 'go_0'"。
            // 两档都过：PERFORMANCE 是"还原 + 落地 scaler"，QUALITY 再多一条放大 pass，
            // 两者用的是不同的 shader 程序，漏验其中一档等于没验。
            val enhancement = requireNotNull(engine.enhancement) { "Android 引擎应声明超分能力" }
            assertEquals(
                "PERFORMANCE 档不应被降级（降级说明引擎没真挂上）",
                VideoEnhancementLevels.PERFORMANCE,
                enhancement.setLevel(VideoEnhancementLevels.PERFORMANCE),
            )
            assertPlaysPast(engine, 15_000L, "挂超分 PERFORMANCE")

            assertEquals(
                "QUALITY 档不应被降级",
                VideoEnhancementLevels.QUALITY,
                enhancement.setLevel(VideoEnhancementLevels.QUALITY),
            )
            assertEquals(VideoEnhancementLevels.QUALITY, enhancement.level.value)
            assertPlaysPast(engine, 25_000L, "挂超分 QUALITY")
        } finally {
            engine.release()
        }
    }

    /** 档位挂下去之后仍要在播、且不能进 Error。 */
    private suspend fun assertPlaysPast(engine: MediampExoPlaybackEngine, targetMs: Long, what: String) {
        val stillFine = withTimeoutOrNull(30.seconds) {
            while (true) {
                val s = engine.state.value
                if (s.phase == PlaybackPhase.Error) break
                if (s.positionMs > targetMs && s.isPlaying) break
                delay(500L)
            }
            val s = engine.state.value
            s.phase != PlaybackPhase.Error && s.isPlaying
        } ?: false
        assertTrue("$what 后播放异常（GL 编译失败会进 Error）：${engine.state.value}", stillFine)
    }
}
