package lovehan1me.feature.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.cinterop.toKString
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Gate3-P5：mediamp-avkit 引擎真实验证（iosSimulatorArm64Test / 模拟器）。
 *
 * 用例骨架沿用旧 IosAVPlaybackEngineTest（**该文件已随引擎在 Gate3-P6 删除**，
 * 需要时可回 git 历史找；这里保留的是它的方法学）—— Apple bipbop 公开流 + preflight
 * 环境门控：test.kexe 进程 HTTPS 受限时 SKIP 而非误报引擎回归
 * （2026-09-09/10 实测记录见 git 历史）。
 * 断言口径对齐换底收益点：mediamp 三轴状态机给真值（Ready/isPlaying/缓冲/seek 完成）。
 */
class MediampAvPlaybackEngineTest {

    /** Apple bipbop：经典 HLS（MPEG-TS 分片），10 分钟。 */
    private val bipbopTs = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"

    /** Apple bipbop：fMP4 分片 HLS（iOS 10+ 支持），约 30 分钟（旧引擎 fMP4 用例的延续）。 */
    private val bipbopFmp4 = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

    private suspend fun MediampAvPlaybackEngine.awaitPlaying(
        timeout: kotlin.time.Duration = 60.seconds,
    ): PlaybackEngineState? = withTimeoutOrNull(timeout) {
        while (true) {
            val s = state.value
            if (s.phase == PlaybackPhase.Ready && s.isPlaying) return@withTimeoutOrNull s
            delay(250L)
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    private suspend fun MediampAvPlaybackEngine.awaitPosition(
        timeout: kotlin.time.Duration = 60.seconds,
        predicate: (Long) -> Boolean,
    ): Long? = withTimeoutOrNull(timeout) {
        while (true) {
            val p = state.value.positionMs
            if (predicate(p)) return@withTimeoutOrNull p
            delay(250L)
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    private suspend fun preflight(url: String): Boolean {
        try {
            val client = HttpClient(Darwin)
            try {
                val resp = withTimeoutOrNull(15_000L) { client.get(url) } ?: return false
                resp.bodyAsText().take(16)
                return resp.status.value in 200..299
            } finally {
                client.close()
            }
        } catch (e: Exception) {
            println("[SKIP] preflight 失败（环境出网受限，非引擎问题）: ${e.message?.take(160)}")
            return false
        }
    }

    @Test
    fun loadAndProgressHlsTs() = runBlocking {
        if (!preflight(bipbopTs)) {
            println("[SKIP] loadAndProgressHlsTs：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = MediampAvPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = bipbopTs, playWhenReady = true))

            val s = engine.awaitPlaying()
            assertTrue(s != null, "60s 内未进入 Ready+isPlaying：state=${engine.state.value}")
            s!!

            val pos = engine.awaitPosition { it > 5_000L }
            assertTrue(pos != null, "进度未推进到 5s 以上：state=${engine.state.value}")
            assertTrue(engine.state.value.durationMs > 0L, "durationMs 应为正值")
            assertTrue(
                engine.state.value.videoWidth > 0 && engine.state.value.videoHeight > 0,
                "应读到视频宽高",
            )
        } finally {
            engine.release()
        }
    }

    @Test
    fun seekTakesEffect() = runBlocking {
        if (!preflight(bipbopTs)) {
            println("[SKIP] seekTakesEffect：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = MediampAvPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = bipbopTs, playWhenReady = true))
            assertTrue(engine.awaitPlaying() != null, "加载失败：state=${engine.state.value}")

            engine.seekTo(60_000L)
            val pos = engine.awaitPosition { kotlin.math.abs(it - 60_000L) < 3_000L }
            assertTrue(pos != null, "seek(60s) 未生效：state=${engine.state.value}")
        } finally {
            engine.release()
        }
    }

    @Test
    fun badUrlGoesError() = runBlocking {
        val badUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/not_exist.m3u8"
        if (!preflight("https://devstreaming-cdn.apple.com/")) {
            println("[SKIP] badUrlGoesError：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = MediampAvPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = badUrl, playWhenReady = true))
            val errorPhase = withTimeoutOrNull(60.seconds) {
                while (engine.state.value.phase != PlaybackPhase.Error) delay(250L)
                true
            } ?: false
            assertTrue(errorPhase, "坏 URL 应进入 Error：state=${engine.state.value}")
            assertTrue(
                !engine.state.value.errorMessage.isNullOrEmpty(),
                "Error 态应带错误信息",
            )
        } finally {
            engine.release()
        }
    }

    @Test
    fun loadAndProgressHlsFmp4() = runBlocking {
        if (!preflight(bipbopFmp4)) {
            println("[SKIP] loadAndProgressHlsFmp4：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = MediampAvPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = bipbopFmp4, playWhenReady = true))

            val s = engine.awaitPlaying()
            assertTrue(s != null, "fMP4 60s 内未进入 Ready+isPlaying：state=${engine.state.value}")

            val pos = engine.awaitPosition { it > 5_000L }
            assertTrue(pos != null, "fMP4 进度未推进到 5s 以上：state=${engine.state.value}")
        } finally {
            engine.release()
        }
    }

    // 倍速通路（旧引擎用例的延续）：mediamp 封装后 avPlayer 不可见，
    // 只断言公开状态收敛 + 位置仍在推进（不碰私有后端）。
    @Test
    fun playbackSpeedTakesEffect() = runBlocking {
        if (!preflight(bipbopTs)) {
            println("[SKIP] playbackSpeedTakesEffect：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = MediampAvPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = bipbopTs, playWhenReady = true))
            assertTrue(engine.awaitPlaying() != null, "加载失败：state=${engine.state.value}")

            engine.setPlaybackSpeed(1.5f)
            val converged = withTimeoutOrNull(10.seconds) {
                while (engine.state.value.playbackSpeed != 1.5f) delay(250L)
                true
            } ?: false
            assertTrue(converged, "倍速未收敛到 1.5x：state=${engine.state.value}")

            val pos = engine.awaitPosition { it > 5_000L }
            assertTrue(pos != null, "1.5x 下进度未推进：state=${engine.state.value}")
        } finally {
            engine.release()
        }
    }

    // 真源入口（旧引擎 hanimeRealSource 用例的延续）：设 HANIME_TEST_M3U8
    // 环境变量（CF 放行网络下从视频页提取的真实 m3u8 直链）后运行。
    @Test
    fun hanimeRealSource() = runBlocking {
        val url = PlatformEnvironment.get("HANIME_TEST_M3U8")?.takeIf { it.isNotBlank() }
        if (url == null) {
            println("[SKIP] hanimeRealSource：未设 HANIME_TEST_M3U8")
            return@runBlocking
        }
        if (!preflight(url)) {
            println("[SKIP] hanimeRealSource：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = MediampAvPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = url, playWhenReady = true))
            val s = engine.awaitPlaying()
            assertTrue(s != null, "真源 60s 内未进入 Ready+isPlaying：state=${engine.state.value}")
        } finally {
            engine.release()
        }
    }

    // 常跑空转：不依赖网络，suite 永不"零执行通过"（preflight 全 SKIP 时仍有此条作证）。
    @Test
    fun initialStateIsIdle() {
        val engine = MediampAvPlaybackEngine()
        try {
            val s = engine.state.value
            assertTrue(s.phase == PlaybackPhase.Idle, "初始应为 Idle：$s")
            assertTrue(!s.isPlaying && !s.isBuffering, "初始不应播/不应缓冲：$s")
        } finally {
            engine.release()
        }
    }
}

// native 测试读环境变量的小工具（posix getenv，沿用旧 IosAVPlaybackEngineTest 同款）。
private object PlatformEnvironment {
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    fun get(name: String): String? = platform.posix.getenv(name)?.toKString()
}
